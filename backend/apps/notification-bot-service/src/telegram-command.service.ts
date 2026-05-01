import { Inject, Injectable, Logger, OnModuleInit } from '@nestjs/common';
import { ConfigService } from '@nestjs/config';
import { ClientProxy } from '@nestjs/microservices';
import { PrismaService } from '@app/database';
import { Telegraf, Markup } from 'telegraf';
import { firstValueFrom } from 'rxjs';
import { NotificationService } from './notification.service';
import {
  buildManualPaymentContactReviewText,
  parseManualPaymentConfirmation,
} from './manual-card-confirmation';
import { isTelegramAdminContext, normalizeTelegramId, parseAdminUserIds } from './telegram-admin-auth';
import { NOTIFICATION_BOT_COMMANDS, formatTelegramCommandHelp } from './telegram-command-menu';
import { selectPaymentCommandBotToken } from './telegram-token-routing';

@Injectable()
export class TelegramCommandService implements OnModuleInit {
  private readonly logger = new Logger(TelegramCommandService.name);
  private bot?: Telegraf;
  private adminChatId?: string;
  private reviewChatId?: string;
  private readonly pendingManualPayments = new Map<string, { orderRef: string; startedAt: number }>();
  private readonly pendingManualConfirmations = new Map<string, { orderRef: string; proofEventId: string }>();
  private readonly cardNumber?: string;
  private readonly adminUserIds: string[];

  constructor(
    private readonly configService: ConfigService,
    private readonly prisma: PrismaService,
    private readonly notificationService: NotificationService,
    @Inject('CUSTOMER_SERVICE') private readonly customerClient: ClientProxy,
  ) {
    const token = selectPaymentCommandBotToken({
      paymentBotToken: this.configService.get<string>('PAYMENT_BOT_TOKEN'),
      notificationBotToken: this.configService.get<string>('NOTIFICATION_BOT_TOKEN'),
      telegramBotToken: this.configService.get<string>('TELEGRAM_BOT_TOKEN'),
    });
    this.adminChatId = this.configService.get<string>('ADMIN_CHAT_ID');
    this.reviewChatId =
      this.configService.get<string>('PAYMENT_REVIEW_CHAT_ID') || this.adminChatId;
    this.cardNumber = this.configService.get<string>('MANUAL_CARD_NUMBER')?.trim() || undefined;
    this.adminUserIds = parseAdminUserIds(this.configService.get<string>('ADMIN_USER_IDS'));

    if (!token || !this.adminChatId) {
      this.logger.warn('Telegram command bot disabled: PAYMENT_BOT_TOKEN/NOTIFICATION_BOT_TOKEN or ADMIN_CHAT_ID is missing');
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
        await ctx.reply(formatTelegramCommandHelp());
        return;
      }

      await ctx.reply('Open the payment flow from the SWIMVPN+ app to continue.');
    });

    this.bot.command('help', async (ctx) => {
      if (!this.isAdmin(ctx)) {
        await ctx.reply('Open the payment flow from the SWIMVPN+ app to continue.');
        return;
      }
      await ctx.reply(formatTelegramCommandHelp());
    });

    this.bot.command('whoami', async (ctx) => {
      await ctx.reply(this.formatWhoami(ctx));
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

    this.bot.command('pending_cards', async (ctx) => {
      if (!this.isAdmin(ctx)) return;
      const orders = await this.prisma.order.findMany({
        where: {
          status: 'PENDING',
          payment_ref: { startsWith: 'CARD_MANUAL' },
        },
        include: {
          customer: true,
          plan: true,
        },
        orderBy: { created_at: 'desc' },
        take: 10,
      });

      if (orders.length === 0) {
        await ctx.reply('No pending manual card orders.');
        return;
      }

      const lines = await Promise.all(orders.map(async (order) => {
        const proofEvent = await this.findLatestManualCardProofEvent(order.order_ref);
        return [
          `${order.order_ref}`,
          `Plan: ${order.plan.name}`,
          `Amount: ${order.amount_rub.toString()} RUB`,
          `Email: ${order.customer.email || '-'}`,
          `Phone: ${order.customer.phone || '-'}`,
          `Proof: ${proofEvent ? `yes (${proofEvent.id})` : 'no'}`,
          `Review: /review_card ${order.order_ref}`,
          `Approve: /approve_card ${order.order_ref}`,
        ].join('\n');
      }));

      await ctx.reply(['Pending manual card orders:', '', lines.join('\n\n')].join('\n'));
    });

    this.bot.command('review_card', async (ctx) => {
      if (!this.isAdmin(ctx)) return;
      const orderRef = this.extractOrderRef(ctx.message.text);
      if (!orderRef) return ctx.reply('Usage: /review_card ORD-...');

      const proofEvent = await this.findLatestManualCardProofEvent(orderRef);
      if (!proofEvent) {
        await ctx.reply(`No stored manual card proof found for ${orderRef}.`);
        return;
      }

      await this.sendStoredManualCardProof(ctx, orderRef, proofEvent);
    });

    this.bot.command('approve_card', async (ctx) => {
      if (!this.isAdmin(ctx)) return;
      const orderRef = this.extractOrderRef(ctx.message.text);
      if (!orderRef) return ctx.reply('Usage: /approve_card ORD-...');

      const proofEvent = await this.findLatestManualCardProofEvent(orderRef);
      if (!proofEvent) {
        await ctx.reply(`Approval blocked for ${orderRef}: no stored payment proof found.`);
        return;
      }

      await this.approveManualCardOrder(ctx, orderRef, proofEvent.id);
    });

    this.bot.command('reject_card', async (ctx) => {
      if (!this.isAdmin(ctx)) return;
      const orderRef = this.extractOrderRef(ctx.message.text);
      if (!orderRef) return ctx.reply('Usage: /reject_card ORD-...');
      await this.rejectManualCardOrder(ctx, orderRef, 'Manual transfer rejected by admin command');
    });

    this.bot.on('photo', async (ctx) => {
      if (this.isAdmin(ctx)) {
        return;
      }

      const chatId = ctx.chat.id.toString();
      const awaitingConfirmation =
        this.pendingManualConfirmations.get(chatId) || await this.recoverPendingManualConfirmation(chatId);
      if (awaitingConfirmation) {
        await ctx.reply(this.buildManualPaymentConfirmationPrompt(undefined, undefined));
        return;
      }

      const pending = this.pendingManualPayments.get(chatId) || await this.recoverPendingManualPayment(chatId, ctx.message.caption);
      if (!pending) {
        await ctx.reply(this.missingManualPaymentContextMessage());
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
            proofType: 'photo',
            fileId: photo.file_id,
            caption: ctx.message.caption || null,
            submittedAt: new Date().toISOString(),
          } as any,
        },
      });

      this.pendingManualPayments.delete(chatId);
      this.pendingManualConfirmations.set(chatId, {
        orderRef: order.order_ref,
        proofEventId: proofEvent.id,
      });

      if (this.bot && this.reviewChatId) {
        try {
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
        } catch (error) {
          this.logger.warn(`Failed to forward manual card photo proof for ${order.order_ref}: ${(error as Error).message}`);
        }
      } else {
        this.logger.warn(`Manual card photo proof for ${order.order_ref} cannot be forwarded: review chat is not configured`);
      }

      await ctx.reply(this.buildManualPaymentConfirmationPrompt(order.customer.email, order.customer.phone));
    });

    this.bot.on('document', async (ctx) => {
      if (this.isAdmin(ctx)) {
        return;
      }

      const document = ctx.message.document;
      const mimeType = document.mime_type || '';
      if (!mimeType.startsWith('image/')) {
        await ctx.reply('Please send the payment proof as an image screenshot.');
        return;
      }

      const chatId = ctx.chat.id.toString();
      const awaitingConfirmation =
        this.pendingManualConfirmations.get(chatId) || await this.recoverPendingManualConfirmation(chatId);
      if (awaitingConfirmation) {
        await ctx.reply(this.buildManualPaymentConfirmationPrompt(undefined, undefined));
        return;
      }

      const pending = this.pendingManualPayments.get(chatId) || await this.recoverPendingManualPayment(chatId, ctx.message.caption);
      if (!pending) {
        await ctx.reply(this.missingManualPaymentContextMessage());
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
            proofType: 'document',
            mimeType,
            fileId: document.file_id,
            caption: ctx.message.caption || null,
            submittedAt: new Date().toISOString(),
          } as any,
        },
      });

      this.pendingManualPayments.delete(chatId);
      this.pendingManualConfirmations.set(chatId, {
        orderRef: order.order_ref,
        proofEventId: proofEvent.id,
      });

      if (this.bot && this.reviewChatId) {
        try {
          await this.bot.telegram.sendDocument(
            this.reviewChatId,
            document.file_id,
            {
              caption: this.buildManualPaymentReviewCaption(order, ctx.from),
              ...Markup.inlineKeyboard([
                [
                  Markup.button.callback('approve', `approve_card:${order.order_ref}:${proofEvent.id}`),
                  Markup.button.callback('reject', `reject_card:${order.order_ref}:${proofEvent.id}`),
                ],
              ]),
            },
          );
        } catch (error) {
          this.logger.warn(`Failed to forward manual card document proof for ${order.order_ref}: ${(error as Error).message}`);
        }
      } else {
        this.logger.warn(`Manual card document proof for ${order.order_ref} cannot be forwarded: review chat is not configured`);
      }

      await ctx.reply(this.buildManualPaymentConfirmationPrompt(order.customer.email, order.customer.phone));
    });

    this.bot.on('text', async (ctx) => {
      if (this.isAdmin(ctx)) return;
      const text = ctx.message.text?.trim() || '';
      if (!text || text.startsWith('/')) return;

      const pending = this.pendingManualConfirmations.get(ctx.chat.id.toString()) ||
        await this.recoverPendingManualConfirmation(ctx.chat.id.toString());
      if (!pending) return;

      const parsedConfirmation = parseManualPaymentConfirmation(text);
      const order = await this.prisma.order.findUnique({
        where: { order_ref: pending.orderRef },
        include: {
          customer: true,
        },
      });
      if (!order || order.status !== 'PENDING') {
        this.pendingManualConfirmations.delete(ctx.chat.id.toString());
        await ctx.reply('This payment request is no longer active.');
        return;
      }

      await this.prisma.adminEvent.create({
        data: {
          event_type: 'CARD_PAYMENT_CONTACT_CONFIRMED',
          entity_type: 'ORDER',
          entity_id: pending.orderRef,
          payload_json: {
            orderRef: pending.orderRef,
            proofEventId: pending.proofEventId,
            telegramChatId: ctx.chat.id.toString(),
            telegramUserId: ctx.from.id.toString(),
            telegramUsername: ctx.from.username || null,
            email: parsedConfirmation.email || null,
            phone: parsedConfirmation.phone || null,
            senderPhone: parsedConfirmation.senderPhone || null,
            confirmationText: text.slice(0, 2000),
            submittedAt: new Date().toISOString(),
          } as any,
        },
      });

      if (parsedConfirmation.email || parsedConfirmation.phone) {
        await this.prisma.customer.update({
          where: { id: order.customer_id },
          data: {
            email: parsedConfirmation.email || order.customer.email,
            phone: parsedConfirmation.phone || order.customer.phone,
          },
        });
      }

      if (!this.bot || !this.reviewChatId) {
        this.logger.warn(`Manual payment contact confirmation cannot be forwarded for ${pending.orderRef}: review chat is not configured`);
        await ctx.reply('Your details were received, but admin review chat is not configured. Please contact support.');
        return;
      }

      try {
        await this.bot.telegram.sendMessage(
          this.reviewChatId,
          buildManualPaymentContactReviewText({
            orderRef: pending.orderRef,
            proofEventId: pending.proofEventId,
            telegramUsername: ctx.from.username || null,
            telegramUserId: ctx.from.id.toString(),
            confirmationText: text,
            parsed: parsedConfirmation,
          }),
          Markup.inlineKeyboard([
            [
              Markup.button.callback('approve', `approve_card:${pending.orderRef}:${pending.proofEventId}`),
              Markup.button.callback('reject', `reject_card:${pending.orderRef}:${pending.proofEventId}`),
            ],
          ]),
        );
      } catch (error) {
        this.logger.warn(`Failed to forward manual payment contact confirmation for ${pending.orderRef}: ${(error as Error).message}`);
        await ctx.reply('Your details were received, but we could not forward them to admin review. Please try sending the same confirmation again.');
        return;
      }

      this.pendingManualConfirmations.delete(ctx.chat.id.toString());
      await ctx.reply('Thank you. Your payment proof and contact details are now under admin review.');
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
      const result = await this.approveManualCardOrder(ctx, orderRef, proofEventId);
      await ctx.answerCbQuery(result?.success ? 'Payment approved' : 'Approval failed');
    });

    this.bot.action(/reject_card:(.+):(.+)/, async (ctx) => {
      if (!this.isAdmin(ctx)) {
        await ctx.answerCbQuery('Access denied');
        return;
      }

      const orderRef = ctx.match[1];
      const proofEventId = ctx.match[2];
      const result = await this.rejectManualCardOrder(ctx, orderRef, 'Manual transfer not confirmed', proofEventId);
      await ctx.answerCbQuery(result?.rejected ? 'Payment rejected' : 'Rejection skipped');
    });

    this.bot.catch((error) => {
      this.logger.warn(`Telegram command handler error: ${(error as Error).message}`);
    });

    this.bot.launch().then(async () => {
      await this.registerTelegramCommandMenu();
      this.logger.log('Telegram command bot started');
    });
  }

  private async findLatestManualCardProofEvent(orderRef: string) {
    return this.prisma.adminEvent.findFirst({
      where: {
        event_type: 'CARD_PAYMENT_PROOF_SUBMITTED',
        entity_type: 'ORDER',
        entity_id: orderRef,
      },
      orderBy: { created_at: 'desc' },
    });
  }

  private async sendStoredManualCardProof(ctx: any, orderRef: string, proofEvent: any) {
    const order = await this.prisma.order.findUnique({
      where: { order_ref: orderRef },
      include: {
        customer: true,
        plan: true,
      },
    });
    if (!order) {
      await ctx.reply(`Order not found: ${orderRef}`);
      return;
    }

    const payload = proofEvent.payload_json as any;
    const fileId = payload?.fileId;
    const caption = [
      'SWIMVPN+ STORED CARD PAYMENT PROOF',
      `Order: ${order.order_ref}`,
      `Status: ${order.status}`,
      `Email: ${order.customer.email || '-'}`,
      `Phone: ${order.customer.phone || '-'}`,
      `Plan: ${order.plan.name}`,
      `Amount: ${order.amount_rub.toString()} RUB`,
      `Proof event: ${proofEvent.id}`,
      `Telegram: @${payload?.telegramUsername || '-'} (${payload?.telegramUserId || '-'})`,
      payload?.caption ? `Original caption: ${payload.caption}` : null,
    ].filter(Boolean).join('\n');
    const actions = Markup.inlineKeyboard([
      [
        Markup.button.callback('approve', `approve_card:${order.order_ref}:${proofEvent.id}`),
        Markup.button.callback('reject', `reject_card:${order.order_ref}:${proofEvent.id}`),
      ],
    ]);

    if (!fileId) {
      await ctx.reply(`${caption}\n\nNo Telegram file id was stored for this proof.`, actions);
      return;
    }

    try {
      if (payload?.proofType === 'document') {
        await this.bot?.telegram.sendDocument(ctx.chat.id, fileId, {
          caption,
          ...actions,
        });
      } else {
        await this.bot?.telegram.sendPhoto(ctx.chat.id, fileId, {
          caption,
          ...actions,
        });
      }
    } catch (error) {
      this.logger.warn(`Failed to resend stored manual card proof for ${orderRef}: ${(error as Error).message}`);
      await ctx.reply(`${caption}\n\nCould not resend the stored Telegram file. You can still approve with /approve_card ${orderRef}`);
    }
  }

  private async approveManualCardOrder(ctx: any, orderRef: string, proofEventId: string) {
    try {
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

      if (result?.success) {
        const suffix = result?.alreadyProcessed
          ? `Already processed (${result.currentStatus || 'unknown'}).`
          : 'Fulfillment triggered.';
        await ctx.reply(`Approved ${orderRef}. ${suffix}`);
      } else {
        await ctx.reply(`Approval failed for ${orderRef}: ${result?.error || 'unknown error'}`);
      }
      return result;
    } catch (error) {
      this.logger.warn(`Manual card approval failed for ${orderRef}: ${(error as Error).message}`);
      await ctx.reply(`Approval failed for ${orderRef}: ${(error as Error).message}`);
      return { success: false, error: (error as Error).message };
    }
  }

  private async rejectManualCardOrder(ctx: any, orderRef: string, reason: string, proofEventId?: string) {
    try {
      const result = await firstValueFrom(
        this.customerClient.send(
          { cmd: 'reject_manual_card_payment' },
          {
            orderRef,
            reason,
          },
        ),
      );

      if (result?.rejected && result?.customerEmail && result?.planName) {
        await this.notificationService.sendManualPaymentReviewEmail({
          to: result.customerEmail,
          orderRef,
          planName: result.planName,
          approved: false,
          reason,
        });
      }

      await this.prisma.adminEvent.create({
        data: {
          event_type: 'CARD_PAYMENT_REJECTED_NOTICE_SENT',
          entity_type: 'ORDER',
          entity_id: orderRef,
          payload_json: {
            orderRef,
            proofEventId: proofEventId || null,
            emailed: !!result?.customerEmail,
            processedAt: new Date().toISOString(),
          } as any,
        },
      });

      if (result?.rejected) {
        await ctx.reply(`Rejected ${orderRef}. Customer will be notified by email.`);
      } else {
        await ctx.reply(`Rejection skipped for ${orderRef}: ${result?.currentStatus || result?.error || 'already processed'}.`);
      }
      return result;
    } catch (error) {
      this.logger.warn(`Manual card rejection failed for ${orderRef}: ${(error as Error).message}`);
      await ctx.reply(`Rejection failed for ${orderRef}: ${(error as Error).message}`);
      return { rejected: false, error: (error as Error).message };
    }
  }

  private isAdmin(ctx: any) {
    return isTelegramAdminContext({
      fromId: ctx.from?.id,
      chatId: ctx.chat?.id,
      messageChatId: ctx.callbackQuery?.message?.chat?.id,
      adminChatId: this.adminChatId,
      reviewChatId: this.reviewChatId,
      adminUserIds: this.adminUserIds,
    });
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

  private async recoverPendingManualPayment(chatId: string, caption?: string) {
    const captionOrderRef = caption ? this.extractOrderRefFromText(caption) : null;
    if (captionOrderRef) {
      const order = await this.prisma.order.findUnique({
        where: { order_ref: captionOrderRef },
        select: { order_ref: true, status: true },
      });
      if (order?.status === 'PENDING') {
        return { orderRef: order.order_ref, startedAt: Date.now() };
      }
    }

    const events = await this.prisma.adminEvent.findMany({
      where: {
        event_type: 'CARD_PAYMENT_FLOW_OPENED',
        entity_type: 'ORDER',
      },
      orderBy: { created_at: 'desc' },
      take: 50,
    });

    const maxAgeMs = 24 * 60 * 60 * 1000;
    const event = events.find((item) => {
      const payload = item.payload_json as any;
      if (payload?.chatId !== chatId) return false;
      return Date.now() - item.created_at.getTime() <= maxAgeMs;
    });

    if (!event) {
      return null;
    }

    const order = await this.prisma.order.findUnique({
      where: { order_ref: event.entity_id },
      select: { order_ref: true, status: true },
    });

    if (order?.status !== 'PENDING') {
      return null;
    }

    const recovered = { orderRef: order.order_ref, startedAt: Date.now() };
    this.pendingManualPayments.set(chatId, recovered);
    return recovered;
  }

  private async recoverPendingManualConfirmation(chatId: string) {
    const events = await this.prisma.adminEvent.findMany({
      where: {
        event_type: 'CARD_PAYMENT_PROOF_SUBMITTED',
        entity_type: 'ORDER',
      },
      orderBy: { created_at: 'desc' },
      take: 50,
    });

    const maxAgeMs = 24 * 60 * 60 * 1000;
    for (const event of events) {
      const payload = event.payload_json as any;
      if (payload?.telegramChatId !== chatId) continue;
      if (Date.now() - event.created_at.getTime() > maxAgeMs) continue;

      const alreadyConfirmed = await this.prisma.adminEvent.findFirst({
        where: {
          event_type: 'CARD_PAYMENT_CONTACT_CONFIRMED',
          entity_type: 'ORDER',
          entity_id: event.entity_id,
          payload_json: {
            path: ['proofEventId'],
            equals: event.id,
          },
        },
        orderBy: { created_at: 'desc' },
      });
      if (alreadyConfirmed) continue;

      const order = await this.prisma.order.findUnique({
        where: { order_ref: event.entity_id },
        select: { order_ref: true, status: true },
      });
      if (order?.status !== 'PENDING') continue;

      const recovered = { orderRef: order.order_ref, proofEventId: event.id };
      this.pendingManualConfirmations.set(chatId, recovered);
      return recovered;
    }

    return null;
  }

  private buildManualPaymentReviewCaption(order: any, from: any) {
    return [
      'SWIMVPN+ CARD PAYMENT PROOF',
      `Order: ${order.order_ref}`,
      `Email: ${order.customer.email || '-'}`,
      `Phone: ${order.customer.phone || '-'}`,
      `Plan: ${order.plan.name}`,
      `Amount: ${order.amount_rub.toString()} RUB`,
      `Telegram: @${from.username || '-'} (${from.id})`,
    ].join('\n');
  }

  private buildManualPaymentConfirmationPrompt(email?: string | null, phone?: string | null) {
    return [
      'Payment proof received.',
      '',
      'Please reply in one message with final confirmation:',
      `Email: ${email || ''}`,
      `Phone: ${phone || ''}`,
      'Sender phone: ',
      '',
      'Your proof is under admin review. We will deliver access by email after approval.',
    ].join('\n');
  }

  private extractOrderRef(text: string): string | null {
    const parts = text.trim().split(/\s+/);
    return parts.length >= 2 ? parts[1] : null;
  }

  private extractOrderRefFromText(text: string): string | null {
    return text.match(/\b(?:ORD|SW|TRIAL|CODE)-[A-Za-z0-9-]+/i)?.[0] || null;
  }

  private missingManualPaymentContextMessage() {
    return [
      'Payment proof received, but no active order was found for this chat.',
      '',
      'Please open the card payment flow from the SWIMVPN+ app and send the proof here again.',
      'If you already have an order reference, resend the screenshot with the order reference in the caption.',
    ].join('\n');
  }

  private async registerTelegramCommandMenu() {
    if (!this.bot) return;
    try {
      await this.bot.telegram.setMyCommands(NOTIFICATION_BOT_COMMANDS);
      this.logger.log(`Registered ${NOTIFICATION_BOT_COMMANDS.length} Telegram notification bot commands`);
    } catch (error) {
      this.logger.error('Failed to register Telegram notification bot commands', error as Error);
    }
  }

  private formatWhoami(ctx: any) {
    const fromId = normalizeTelegramId(ctx.from?.id);
    const authorized = isTelegramAdminContext({
      fromId,
      chatId: ctx.chat?.id,
      messageChatId: ctx.callbackQuery?.message?.chat?.id,
      adminChatId: this.adminChatId,
      reviewChatId: this.reviewChatId,
      adminUserIds: this.adminUserIds,
    });
    const userInAllowList = !!fromId && this.adminUserIds.includes(fromId);

    return [
      'Telegram identity',
      `User id: ${ctx.from?.id || '-'}`,
      `Chat id: ${ctx.chat?.id || '-'}`,
      `Username: ${ctx.from?.username ? `@${ctx.from.username}` : '-'}`,
      `Authorized: ${authorized ? 'yes' : 'no'}`,
      `Configured admin ids: ${this.adminUserIds.length}`,
      `Current user in ADMIN_USER_IDS: ${userInAllowList ? 'yes' : 'no'}`,
      '',
      'Add the User id to ADMIN_USER_IDS for admin commands.',
    ].join('\n');
  }
}
