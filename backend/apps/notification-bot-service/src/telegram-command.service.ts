import { Injectable, Logger, OnModuleInit } from '@nestjs/common';
import { ConfigService } from '@nestjs/config';
import { PrismaService } from '@app/database';
import { Telegraf } from 'telegraf';
import { NotificationService } from './notification.service';

@Injectable()
export class TelegramCommandService implements OnModuleInit {
  private readonly logger = new Logger(TelegramCommandService.name);
  private bot?: Telegraf;
  private adminChatId?: string;

  constructor(
    private readonly configService: ConfigService,
    private readonly prisma: PrismaService,
    private readonly notificationService: NotificationService,
  ) {
    const token = this.configService.get<string>('NOTIFICATION_BOT_TOKEN');
    this.adminChatId = this.configService.get<string>('ADMIN_CHAT_ID');

    if (!token || !this.adminChatId) {
      this.logger.warn('Telegram command bot disabled: NOTIFICATION_BOT_TOKEN or ADMIN_CHAT_ID is missing');
      return;
    }

    this.bot = new Telegraf(token);
  }

  onModuleInit() {
    if (!this.bot) return;

    this.bot.use(async (ctx, next) => {
      const chatId = ctx.from?.id?.toString();
      if (chatId !== this.adminChatId) {
        await ctx.reply('Access denied');
        return;
      }
      return next();
    });

    this.bot.command('help', async (ctx) => {
      await ctx.reply('/order SW12345\n/status SW12345\n/resend SW12345\n/help');
    });

    this.bot.command('order', async (ctx) => {
      const orderRef = this.extractOrderRef(ctx.message.text);
      if (!orderRef) return ctx.reply('Usage: /order SW12345');
      const status = await this.notificationService.getDeliveryStatus(orderRef);
      await ctx.reply(JSON.stringify(status, null, 2));
    });

    this.bot.command('status', async (ctx) => {
      const orderRef = this.extractOrderRef(ctx.message.text);
      if (!orderRef) return ctx.reply('Usage: /status SW12345');
      const status = await this.notificationService.getDeliveryStatus(orderRef);
      await ctx.reply(JSON.stringify(status, null, 2));
    });

    this.bot.command('resend', async (ctx) => {
      const orderRef = this.extractOrderRef(ctx.message.text);
      if (!orderRef) return ctx.reply('Usage: /resend SW12345');
      const result = await this.notificationService.resendDeliveryEmail(orderRef, 'ru');
      await ctx.reply(JSON.stringify(result, null, 2));
    });

    this.bot.action(/resend:(.+)/, async (ctx) => {
      const orderRef = ctx.match[1];
      const result = await this.notificationService.resendDeliveryEmail(orderRef, 'ru');
      await ctx.answerCbQuery(result.success ? 'Email resent' : 'Resend failed');
      await ctx.reply(JSON.stringify(result, null, 2));
    });

    this.bot.action(/copy:(.+)/, async (ctx) => {
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

    this.bot.catch((error) => {
      this.logger.warn(`Telegram command handler error: ${(error as Error).message}`);
    });

    this.bot.launch().then(() => {
      this.logger.log('Telegram command bot started');
    });
  }

  private extractOrderRef(text: string): string | null {
    const parts = text.trim().split(/\s+/);
    return parts.length >= 2 ? parts[1] : null;
  }
}
