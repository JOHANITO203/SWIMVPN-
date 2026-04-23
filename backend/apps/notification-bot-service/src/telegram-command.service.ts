import { Inject, Injectable, Logger, OnModuleInit } from '@nestjs/common';
import { ConfigService } from '@nestjs/config';
import { ClientProxy } from '@nestjs/microservices';
import { PrismaService } from '@app/database';
import { Telegraf, Markup } from 'telegraf';
import { firstValueFrom } from 'rxjs';
import { NotificationService } from './notification.service';

@Injectable()
export class TelegramCommandService implements OnModuleInit {
  private readonly logger = new Logger(TelegramCommandService.name);
  private bot?: Telegraf;
  private adminChatId?: string;
  private reviewChatId?: string;
  private readonly pendingManualPayments = new Map<string, { orderRef: string; startedAt: number }>();
  private readonly cardNumber?: string;

  constructor(
    private readonly configService: ConfigService,
    private readonly prisma: PrismaService,
    private readonly notificationService: NotificationService,
    @Inject('CUSTOMER_SERVICE') private readonly customerClient: ClientProxy,
  ) {
    const token = this.configService.get<string>('NOTIFICATION_BOT_TOKEN');
    this.adminChatId = this.configService.get<string>('ADMIN_CHAT_ID');
    this.reviewChatId =
      this.configService.get<string>('PAYMENT_REVIEW_CHAT_ID') || this.adminChatId;
    this.cardNumber = this.configService.get<string>('MANUAL_CARD_NUMBER')?.trim() || undefined;

    if (!token || !this.adminChatId) {
      this.logger.warn('Telegram command bot disabled: NOTIFICATION_BOT_TOKEN or ADMIN_CHAT_ID is missing');
      return;
    }

    this.bot = new Telegraf(token);
  }

  onModuleInit() {
    if (!this.bot) return;

    this.bot.start(async (ctx) => {
      const payload = ctx.payload?.trim();
      if (payload?.startsWith('card_')) {
        await this.handleCardStart(ctx, payload.slice('card_'.length));
        return;
      }

      if (this.isAdmin(ctx)) {
        await ctx.reply('/order SW12345\n/status SW12345\n/resend SW12345\n/help');
        return;
      }

      await ctx.reply('Open the payment flow from the SWIMVPN+ app to continue.');
    });

    this.bot.command('help', async (ctx) => {
      if (!this.isAdmin(ctx)) {
        await ctx.reply('Open the payment flow from the SWIMVPN+ app to continue.');
        return;
      }
      await ctx.reply('/order SW12345\n/status SW12345\n/resend SW12345\n/help');
    });

    this.bot.command('order', async (ctx) => {
      if (!this.isAdmin(ctx)) return;
      const orderRef = this.extractOrderRef(ctx.message.text);
      if (!orderRef) return ctx.reply('Usage: /order SW12345');
      const status = await this.notificationService.getDeliveryStatus(orderRef);
      await ctx.reply(JSON.stringify(status, null, 2));
    });

    this.bot.command('status', async (ctx) => {
      if (!this.isAdmin(ctx)) return;
      const orderRef = this.extractOrderRef(ctx.message.text);
      if (!orderRef) return ctx.reply('Usage: /status SW12345');
      const status = await this.notificationService.getDeliveryStatus(orderRef);
      await ctx.reply(JSON.stringify(status, null, 2));
    });

    this.bot.command('resend', async (ctx) => {
      if (!this.isAdmin(ctx)) return;
      const orderRef = this.extractOrderRef(ctx.message.text);
      if (!orderRef) return ctx.reply('Usage: /resend SW12345');
      const result = await this.notificationService.resendDeliveryEmail(orderRef, 'ru');
      await ctx.reply(JSON.stringify(result, null, 2));
    });

    this.bot.on('photo', async (ctx) => {
      if (this.isAdmin(ctx)) {
        return;
      }

      const chatId = ctx.chat.id.toString();
      const pending = this.pendingManualPayments.get(chatId);
      if (!pending) {
        await ctx.reply('Open the card payment flow from the SWIMVPN+ app first.');
        return;
      }

      const order = await this.prisma.order.findUnique({
        where: { order_ref: pending.orderRef },
        include: {
          customer: true,
          plan: true,
        },
      });

      if (!order || order.status !== 'PENDING') {
        this.pendingManualPayments.delete(chatId);
        await ctx.reply('This payment request is no longer active.');
        return;
      }

      const photo = ctx.message.photo[ctx.message.photo.length - 1];
      const proofEvent = await this.prisma.adminEvent.create({
        data: {
          event_type: 'CARD_PAYMENT_PROOF_SUBMITTED',
          entity_type: 'ORDER',
          entity_id: order.order_ref,
          payload_json: {
            orderRef: order.order_ref,
            telegramChatId: chatId,
            telegramUserId: ctx.from.id.toString(),
            telegramUsername: ctx.from.username || null,
            firstName: ctx.from.first_name || null,
            fileId: photo.file_id,
            caption: ctx.message.caption || null,
            submittedAt: new Date().toISOString(),
          } as any,
        },
      });

      if (this.bot && this.reviewChatId) {
        await this.bot.telegram.sendPhoto(
          this.reviewChatId,
          photo.file_id,
          {
            caption: [
              'SWIMVPN+ CARD PAYMENT PROOF',
              `Order: ${order.order_ref}`,
              `Email: ${order.customer.email || '-'}`,
              `Phone: ${order.customer.phone || '-'}`,
              `Plan: ${order.plan.name}`,
              `Amount: ${order.amount_rub.toString()} RUB`,
              `Telegram: @${ctx.from.username || '-'} (${ctx.from.id})`,
            ].join('\n'),
            ...Markup.inlineKeyboard([
              [
                Markup.button.callback('approve', `approve_card:${order.order_ref}:${proofEvent.id}`),
                Markup.button.callback('reject', `reject_card:${order.order_ref}:${proofEvent.id}`),
              ],
            ]),
          },
        );
      }

      this.pendingManualPayments.delete(chatId);
      await ctx.reply('Payment proof received. We will review it and reply by email.');
    });

    this.bot.action(/resend:(.+)/, async (ctx) => {
      if (!this.isAdmin(ctx)) {
        await ctx.answerCbQuery('Access denied');
        return;
      }

      const orderRef = ctx.match[1];
      const result = await this.notificationService.resendDeliveryEmail(orderRef, 'ru');
      await ctx.answerCbQuery(result.success ? 'Email resent' : 'Resend failed');
      await ctx.reply(JSON.stringify(result, null, 2));
    });

    this.bot.action(/copy:(.+)/, async (ctx) => {
      if (!this.isAdmin(ctx)) {
        await ctx.answerCbQuery('Access denied');
        return;
      }

      const orderRef = ctx.match[1];
      const order = await this.prisma.order.findUnique({
        where: { order_ref: orderRef },
        include: { assignments: { include: { inventory_item: true } } },
      });
      const vpnLink = order?.assignments?.[0]?.inventory_item?.raw_config;
      await ctx.answerCbQuery(vpnLink ? 'VPN link ready' : 'VPN link not found');
      if (vpnLink) {
        await ctx.reply(`VPN link for ${orderRef}:\n${vpnLink}`);
      }
    });

    this.bot.action(/mark:(.+)/, async (ctx) => {
      if (!this.isAdmin(ctx)) {
        await ctx.answerCbQuery('Access denied');
        return;
      }

      const orderRef = ctx.match[1];
      const order = await this.prisma.order.findUnique({
        where: { order_ref: orderRef },
        include: { deliveries: { take: 1, orderBy: { sent_at: 'desc' } } },
      });
      const delivery = order?.deliveries?.[0];
      if (!delivery) {
        await ctx.answerCbQuery('Delivery not found');
        return;
      }

      await this.prisma.delivery.update({
        where: { id: delivery.id },
        data: {
          notes: JSON.stringify({
            status: 'MARKED_DELIVERED_MANUALLY',
            updatedAt: new Date().toISOString(),
          }),
        },
      });

      await ctx.answerCbQuery('Marked as delivered');
    });

    this.bot.action(/approve_card:(.+):(.+)/, async (ctx) => {
      if (!this.isAdmin(ctx)) {
        await ctx.answerCbQuery('Access denied');
        return;
      }

      const orderRef = ctx.match[1];
      const proofEventId = ctx.match[2];
      const result = await firstValueFrom(
        this.customerClient.send(
          { cmd: 'approve_manual_card_payment' },
          {
            orderRef,
            proofEventId,
            paymentRef: `CARD_MANUAL:APPROVED:${proofEventId}`,
          },
        ),
      );

      await ctx.answerCbQuery(result?.success ? 'Payment approved' : 'Approval failed');
      if (result?.success) {
        await ctx.reply(`Approved ${orderRef}. Fulfillment triggered.`);
      }
    });

    this.bot.action(/reject_card:(.+):(.+)/, async (ctx) => {
      if (!this.isAdmin(ctx)) {
        await ctx.answerCbQuery('Access denied');
        return;
      }

      const orderRef = ctx.match[1];
      const proofEventId = ctx.match[2];
      const result = await firstValueFrom(
        this.customerClient.send(
          { cmd: 'reject_manual_card_payment' },
          {
            orderRef,
            reason: 'Manual transfer not confirmed',
          },
        ),
      );

      if (result?.customerEmail && result?.planName) {
        await this.notificationService.sendManualPaymentReviewEmail({
          to: result.customerEmail,
          orderRef,
          planName: result.planName,
          approved: false,
          reason: 'Manual transfer not confirmed',
        });
      }

      await this.prisma.adminEvent.create({
        data: {
          event_type: 'CARD_PAYMENT_REJECTED_NOTICE_SENT',
          entity_type: 'ORDER',
          entity_id: orderRef,
          payload_json: {
            orderRef,
            proofEventId,
            emailed: !!result?.customerEmail,
            processedAt: new Date().toISOString(),
          } as any,
        },
      });

      await ctx.answerCbQuery(result?.success ? 'Payment rejected' : 'Reject failed');
      if (result?.success) {
        await ctx.reply(`Rejected ${orderRef}. Customer will be notified by email.`);
      }
    });

    this.bot.catch((error) => {
      this.logger.warn(`Telegram command handler error: ${(error as Error).message}`);
    });

    this.bot.launch().then(() => {
      this.logger.log('Telegram command bot started');
    });
  }

  private isAdmin(ctx: { from?: { id: number } }) {
    return ctx.from?.id?.toString() === this.adminChatId;
  }

  private async handleCardStart(ctx: any, orderRef: string) {
    if (!this.cardNumber) {
      await ctx.reply('Card payment is not configured right now.');
      return;
    }

    const order = await this.prisma.order.findUnique({
      where: { order_ref: orderRef },
      include: {
        plan: true,
      },
    });

    if (!order || order.status !== 'PENDING') {
      await ctx.reply('This payment request is no longer active.');
      return;
    }

    this.pendingManualPayments.set(ctx.chat.id.toString(), {
      orderRef,
      startedAt: Date.now(),
    });

    await this.prisma.adminEvent.create({
      data: {
        event_type: 'CARD_PAYMENT_FLOW_OPENED',
        entity_type: 'ORDER',
        entity_id: orderRef,
        payload_json: {
          orderRef,
          chatId: ctx.chat.id.toString(),
          telegramUserId: ctx.from?.id?.toString() || null,
          openedAt: new Date().toISOString(),
        } as any,
      },
    });

    await ctx.reply(
      [
        'SWIMVPN+ card payment',
        '',
        `Order: ${orderRef}`,
        `Plan: ${order.plan.name}`,
        `Amount: ${order.amount_rub.toString()} RUB`,
        '',
        '1. Copy the card number below',
        '2. Pay from your banking app',
        '3. Send the payment screenshot here in this chat',
        '',
        `Card number: ${this.cardNumber}`,
      ].join('\n'),
    );
  }

  private extractOrderRef(text: string): string | null {
    const parts = text.trim().split(/\s+/);
    return parts.length >= 2 ? parts[1] : null;
  }
}
