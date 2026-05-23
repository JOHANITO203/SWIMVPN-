import { Injectable, Logger, OnModuleInit } from '@nestjs/common';
import { ConfigService } from '@nestjs/config';
import { PrismaService } from '@app/database';
import { Telegraf, Markup } from 'telegraf';
import { NotificationService } from './notification.service';
import {
  buildActiveDeliverableAssignmentInclude,
  selectLatestDeliverableAssignment,
} from './delivery-policy';
import {
  isTelegramAdminContext,
  isTelegramAdminUser,
  normalizeTelegramId,
  parseAdminUserIds,
} from './telegram-admin-auth';
import { NOTIFICATION_BOT_COMMANDS, formatTelegramCommandHelp } from './telegram-command-menu';
import { selectNotificationCommandBotToken } from './telegram-token-routing';

@Injectable()
export class TelegramCommandService implements OnModuleInit {
  private readonly logger = new Logger(TelegramCommandService.name);
  private bot?: Telegraf;
  private adminChatId?: string;
  private readonly adminUserIds: string[];

  constructor(
    private readonly configService: ConfigService,
    private readonly prisma: PrismaService,
    private readonly notificationService: NotificationService,
  ) {
    const token = selectNotificationCommandBotToken({
      notificationBotToken: this.configService.get<string>('NOTIFICATION_BOT_TOKEN'),
      telegramBotToken: this.configService.get<string>('TELEGRAM_BOT_TOKEN'),
    });
    this.adminChatId = this.configService.get<string>('ADMIN_CHAT_ID');
    this.adminUserIds = parseAdminUserIds(this.configService.get<string>('ADMIN_USER_IDS'));

    if (!token || !this.adminChatId) {
      this.logger.warn('Telegram command bot disabled: NOTIFICATION_BOT_TOKEN/TELEGRAM_BOT_TOKEN or ADMIN_CHAT_ID is missing');
      return;
    }

    this.bot = new Telegraf(token);
  }

  onModuleInit() {
    if (!this.bot) return;

    this.bot.start(async (ctx) => {
      if (this.isAdmin(ctx)) {
        await ctx.reply(formatTelegramCommandHelp());
        return;
      }

      await ctx.reply('Open SWIMVPN to manage your order.');
    });

    this.bot.command('help', async (ctx) => {
      if (!this.isAdmin(ctx)) {
        await ctx.reply('Open SWIMVPN to manage your order.');
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

    this.bot.command('inventory', async (ctx) => {
      if (!this.isAdmin(ctx)) return;
      await this.sendInventoryOverview(ctx);
    });

    this.bot.command('resend', async (ctx) => {
      if (!this.isSensitiveAdminAction(ctx)) return;
      const orderRef = this.extractOrderRef(ctx.message.text);
      if (!orderRef) return ctx.reply('Usage: /resend SW12345');
      const result = await this.notificationService.resendDeliveryEmail(orderRef, 'ru');
      await ctx.reply(JSON.stringify(result, null, 2));
    });

    this.bot.action(/resend:(.+)/, async (ctx) => {
      if (!this.isSensitiveAdminAction(ctx)) {
        await ctx.answerCbQuery('Access denied');
        return;
      }

      const orderRef = ctx.match[1];
      const result = await this.notificationService.resendDeliveryEmail(orderRef, 'ru');
      await ctx.answerCbQuery(result.success ? 'Email resent' : 'Resend failed');
      await ctx.reply(JSON.stringify(result, null, 2));
    });

    this.bot.action(/copy:(.+)/, async (ctx) => {
      if (!this.isSensitiveAdminAction(ctx)) {
        await ctx.answerCbQuery('Access denied');
        return;
      }

      if (!this.isPrivateAdminChat(ctx)) {
        await ctx.answerCbQuery('Open a DM with the bot to copy VPN config');
        await ctx.reply('Raw VPN config is only available in an explicit admin DM. Use /copy from your private chat.');
        return;
      }

      const orderRef = ctx.match[1];
      const order = await this.prisma.order.findUnique({
        where: { order_ref: orderRef },
        include: {
          assignments: buildActiveDeliverableAssignmentInclude(),
        },
      });
      const vpnLink = selectLatestDeliverableAssignment(order?.assignments || [])?.inventory_item?.raw_config;
      await ctx.answerCbQuery(vpnLink ? 'VPN link ready' : 'VPN link not found');
      if (vpnLink) {
        await ctx.reply(`VPN link for ${orderRef}:\n${vpnLink}`);
      }
    });

    this.bot.action(/mark:(.+)/, async (ctx) => {
      if (!this.isSensitiveAdminAction(ctx)) {
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

    this.bot.action(/clear_stock:(.+)/, async (ctx) => {
      if (!this.isSensitiveAdminAction(ctx)) {
        await ctx.answerCbQuery('Access denied');
        return;
      }

      const category = ctx.match[1];
      const label = this.getCategoryLabel(category);

      await ctx.reply(`⚠️ Are you sure you want to clear ALL available ${label} stock?`,
        Markup.inlineKeyboard([
          [
            Markup.button.callback('✅ Yes, delete available', `confirm_clear:${category}`),
            Markup.button.callback('❌ Cancel', 'inventory_refresh'),
          ]
        ])
      );
      await ctx.answerCbQuery();
    });

    this.bot.action(/confirm_clear:(.+)/, async (ctx) => {
      if (!this.isSensitiveAdminAction(ctx)) {
        await ctx.answerCbQuery('Access denied');
        return;
      }

      const category = ctx.match[1];
      let result;
      if (category === 'TRIAL') {
        result = await this.notificationService.clearAvailableTrialConfigs();
      } else {
        result = await this.notificationService.clearAvailableConfigs(category);
      }

      await ctx.reply(`Done! Cleared ${result.count} items.`);
      await this.sendInventoryOverview(ctx);
      await ctx.answerCbQuery();
    });

    this.bot.action('inventory_refresh', async (ctx) => {
      if (!this.isAdmin(ctx)) return;
      await this.sendInventoryOverview(ctx);
      await ctx.answerCbQuery('Refreshed');
    });

    this.bot.catch((error) => {
      this.logger.warn(`Telegram command handler error: ${(error as Error).message}`);
    });

    this.bot.launch().then(async () => {
      await this.registerTelegramCommandMenu();
      this.logger.log('Telegram command bot started');
    });
  }

  private isAdmin(ctx: any) {
    return isTelegramAdminContext({
      fromId: ctx.from?.id,
      chatId: ctx.chat?.id,
      messageChatId: ctx.callbackQuery?.message?.chat?.id,
      adminChatId: this.adminChatId,
      adminUserIds: this.adminUserIds,
    });
  }

  private isSensitiveAdminAction(ctx: any) {
    return isTelegramAdminUser({
      fromId: ctx.from?.id,
      adminUserIds: this.adminUserIds,
    });
  }

  private isPrivateAdminChat(ctx: any) {
    const fromId = normalizeTelegramId(ctx.from?.id);
    const chatId = normalizeTelegramId(ctx.chat?.id || ctx.callbackQuery?.message?.chat?.id);

    return !!fromId && fromId === chatId && this.isSensitiveAdminAction(ctx);
  }

  private extractOrderRef(text: string): string | null {
    const parts = text.trim().split(/\s+/);
    return parts.length >= 2 ? parts[1] : null;
  }

  private async sendInventoryOverview(ctx: any) {
    const stats = await this.notificationService.getInventoryStats();
    const lines = ['<b>📦 SWIMVPN Inventory Stock</b>', ''];

    const categories = ['TRIAL', 'WEEK', 'MONTH', 'QUARTER'];
    const buttons = [];

    for (const cat of categories) {
      let count = 0;
      if (cat === 'TRIAL') {
        count = stats.trial.find((s: any) => s.status === 'AVAILABLE')?.count || 0;
      } else {
        count = stats.paid.find((s: any) => s.category === cat && s.status === 'AVAILABLE')?.count || 0;
      }

      const label = this.getCategoryLabel(cat);
      lines.push(`${label}: <b>${count}</b> available`);

      if (count > 0) {
        buttons.push([Markup.button.callback(`🗑️ Clear ${cat}`, `clear_stock:${cat}`)]);
      }
    }

    buttons.push([Markup.button.callback('🔄 Refresh', 'inventory_refresh')]);

    const messageOptions = {
      parse_mode: 'HTML' as const,
      ...Markup.inlineKeyboard(buttons),
    };

    if (ctx.callbackQuery) {
      await ctx.editMessageText(lines.join('\n'), messageOptions);
    } else {
      await ctx.reply(lines.join('\n'), messageOptions);
    }
  }

  private getCategoryLabel(category: string): string {
    const labels: Record<string, string> = {
      'WEEK': 'Basic (Week)',
      'MONTH': 'Premium (Month)',
      'QUARTER': 'Platinum (Quarter)',
      'TRIAL': 'Trial (3-Day)',
    };
    return labels[category] || category;
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
