import { Injectable, OnModuleDestroy, OnModuleInit, Logger, Inject } from '@nestjs/common';
import { ConfigService } from '@nestjs/config';
import { ClientProxy } from '@nestjs/microservices';
import { PrismaService } from '@app/database';
import { DEFAULT_RESALE_SLOT_CAP, DEFAULT_SUPPLIER_DEVICE_LIMIT } from '@app/contracts';
import { AccountingEntrySource, AccountingEntryType } from '@prisma/client';
import { Telegraf } from 'telegraf';
import { firstValueFrom } from 'rxjs';
import { isAdminBotAuthorized, parseAdminUserIds } from './admin-bot-auth';
import {
  formatAccountingSummary,
  formatImportWizardCategoryPrompt,
  formatImportWizardConfigPrompt,
  formatImportWizardConfirmation,
  formatImportResult,
  formatInventoryOverview,
  formatPendingFulfillment,
  isImportWizardCancel,
  isImportWizardConfirm,
  mapBotPlanInputToCategory,
  parseExpenseCommand,
  parseInventoryActionCommand,
  parseRetryCommand,
} from './admin-bot.formatter';

type ImportWizardSession =
  | { step: 'category' }
  | { step: 'config'; category: any }
  | { step: 'confirm'; category: any; config: string };

@Injectable()
export class AdminBotService implements OnModuleInit, OnModuleDestroy {
  private readonly logger = new Logger(AdminBotService.name);
  private readonly bot?: Telegraf;
  private readonly adminChatId?: string;
  private readonly adminUserIds: string[];
  private readonly importWizardSessions = new Map<string, ImportWizardSession>();

  constructor(
    private readonly configService: ConfigService,
    private readonly prisma: PrismaService,
    @Inject('INVENTORY_SERVICE') private readonly inventoryClient: ClientProxy,
  ) {
    const token = this.configService.get<string>('TELEGRAM_BOT_TOKEN')?.trim();
    this.adminChatId = this.configService.get<string>('ADMIN_CHAT_ID')?.trim() || undefined;
    this.adminUserIds = parseAdminUserIds(this.configService.get<string>('ADMIN_USER_IDS'));

    if (!token || token === 'TO_BE_REPLACED_BY_USER') {
      this.logger.error('TELEGRAM_BOT_TOKEN is not set or invalid');
      return;
    }

    this.bot = new Telegraf(token);
  }

  onModuleInit() {
    if (!this.bot) return;
    this.setupMiddleware();
    this.setupCommands();
    this.bot.launch().then(() => {
      this.logger.log('Admin operations bot started');
    });
  }

  private setupMiddleware() {
    if (!this.bot) return;

    this.bot.use(async (ctx, next) => {
      const authorized = isAdminBotAuthorized({
        fromId: ctx.from?.id,
        chatId: ctx.chat?.id,
        adminChatId: this.adminChatId,
        adminUserIds: this.adminUserIds,
      });

      if (!authorized) {
        this.logger.warn(`Unauthorized admin bot access from ID: ${ctx.from?.id || 'unknown'}`);
        await ctx.reply('Access denied. This bot is restricted to SWIMVPN admins.');
        return;
      }

      return next();
    });
  }

  private setupCommands() {
    if (!this.bot) return;

    this.bot.start(async (ctx) => ctx.reply(this.helpText()));
    this.bot.command('help', async (ctx) => ctx.reply(this.helpText()));

    this.bot.command('status', async (ctx) => {
      await ctx.reply('SWIMVPN+ Admin Operations Bot is online.');
    });

    this.bot.command('stock', async (ctx) => {
      await ctx.reply('Reading inventory...');
      try {
        const overview = await firstValueFrom(
          this.inventoryClient.send({ cmd: 'list_inventory_overview' }, {}),
        );
        await ctx.reply(formatInventoryOverview(Array.isArray(overview) ? overview : []));
      } catch (error) {
        this.logger.error('Failed to read inventory overview', error as Error);
        await ctx.reply('Unable to read inventory right now.');
      }
    });

    this.bot.command('orders', async (ctx) => {
      await ctx.reply('Reading recent orders...');
      try {
        const recentOrders = await this.prisma.order.findMany({
          take: 5,
          orderBy: { created_at: 'desc' },
          include: { customer: true, plan: true },
        });

        if (recentOrders.length === 0) {
          await ctx.reply('No orders found.');
          return;
        }

        const message = recentOrders.map((order) => [
          `Order: ${order.order_ref}`,
          `Customer: ${order.customer.public_id}`,
          `Plan: ${order.plan.name}`,
          `Amount: ${order.amount_rub.toString()} RUB`,
          `Status: ${order.status}`,
        ].join('\n')).join('\n\n');

        await ctx.reply(message);
      } catch (error) {
        this.logger.error('Failed to fetch recent orders', error as Error);
        await ctx.reply('Unable to fetch orders right now.');
      }
    });

    this.bot.command('pending', async (ctx) => {
      await ctx.reply('Reading pending fulfillment orders...');
      try {
        const pendingOrders = await this.prisma.order.findMany({
          where: { status: 'PENDING_FULFILLMENT' },
          take: 10,
          orderBy: { created_at: 'asc' },
          include: { customer: true, plan: true },
        });

        await ctx.reply(formatPendingFulfillment(pendingOrders.map((order) => ({
          orderRef: order.order_ref,
          planName: order.plan.name,
          planCode: order.plan.code,
          amountRub: order.amount_rub.toString(),
          customerEmail: order.customer.email,
          createdAt: order.created_at.toISOString(),
        }))));
      } catch (error) {
        this.logger.error('Failed to fetch pending fulfillment orders', error as Error);
        await ctx.reply('Unable to fetch pending fulfillment orders right now.');
      }
    });

    this.bot.command('retry', async (ctx) => {
      const parsed = parseRetryCommand(ctx.message.text || '');
      if (parsed.mode === 'invalid') {
        await ctx.reply('Usage: /retry <orderRef|all>');
        return;
      }

      await ctx.reply(parsed.mode === 'all' ? 'Retrying pending fulfillment orders...' : `Retrying ${parsed.orderRef}...`);
      try {
        const orders = parsed.mode === 'all'
          ? await this.prisma.order.findMany({
            where: { status: 'PENDING_FULFILLMENT' },
            take: 20,
            orderBy: { created_at: 'asc' },
            select: { id: true, order_ref: true },
          })
          : await this.prisma.order.findMany({
            where: {
              order_ref: parsed.orderRef,
              status: { in: ['PAID', 'PENDING_FULFILLMENT'] as any },
            },
            take: 1,
            select: { id: true, order_ref: true },
          });

        if (orders.length === 0) {
          await ctx.reply('No retryable order found.');
          return;
        }

        const results = [];
        for (const order of orders) {
          const result = await firstValueFrom(
            this.inventoryClient.send({ cmd: 'fulfill_order' }, { orderId: order.id }),
          );
          results.push(`${order.order_ref}: ${result?.pendingFulfillment ? 'pending capacity' : 'processed'}`);
        }

        await ctx.reply(['Retry finished', ...results].join('\n'));
      } catch (error) {
        this.logger.error('Failed to retry fulfillment', error as Error);
        await ctx.reply('Retry failed. Check inventory capacity and service logs.');
      }
    });

    this.bot.command('users', async (ctx) => {
      try {
        const totalUsers = await this.prisma.customer.count();
        const activeUsers = await this.prisma.order.groupBy({
          by: ['customer_id'],
          where: { status: 'FULFILLED' },
        });

        await ctx.reply([
          'User statistics',
          `Total registered: ${totalUsers}`,
          `Active paid/trial customers: ${activeUsers.length}`,
        ].join('\n'));
      } catch (error) {
        this.logger.error('Failed to fetch user stats', error as Error);
        await ctx.reply('Unable to fetch user stats right now.');
      }
    });

    this.bot.command('healthcheck', async (ctx) => {
      await ctx.reply('Starting inventory health check...');
      try {
        const result = await firstValueFrom(
          this.inventoryClient.send({ cmd: 'trigger_health_check' }, {}),
        );
        await ctx.reply(`Health check completed:\n${JSON.stringify(result, null, 2)}`);
      } catch (error) {
        this.logger.error('Health check failed', error as Error);
        await ctx.reply('Health check failed.');
      }
    });

    this.bot.command('expire', async (ctx) => {
      await this.updateInventoryHealthFromCommand(ctx, 'EXPIRED', 'SUPPLIER_EXPIRED');
    });

    this.bot.command('disable', async (ctx) => {
      await this.updateInventoryHealthFromCommand(ctx, 'DISABLED', 'ADMIN_DISABLED');
    });

    this.bot.command('quota_reached', async (ctx) => {
      await this.updateInventoryHealthFromCommand(ctx, 'DISABLED', 'SUPPLIER_QUOTA_REACHED');
    });

    this.bot.command('orders_today', async (ctx) => {
      await this.replyAccountingSummary(ctx, 'Orders today');
    });

    this.bot.command('revenue_today', async (ctx) => {
      await this.replyAccountingSummary(ctx, 'Revenue today');
    });

    this.bot.command('add_expense', async (ctx) => {
      const parsed = parseExpenseCommand(ctx.message.text || '');
      if (parsed.valid === false) {
        await ctx.reply(parsed.reason);
        return;
      }

      try {
        const entry = await this.prisma.accountingEntry.create({
          data: {
            type: AccountingEntryType.EXPENSE,
            source: AccountingEntrySource.MANUAL,
            amount: parsed.amount,
            currency: parsed.currency,
            note: parsed.note,
            created_by_admin: ctx.from?.id?.toString() || null,
          },
        });

        await ctx.reply([
          'Expense recorded',
          `Amount: ${parsed.amount} ${parsed.currency}`,
          `Note: ${parsed.note}`,
          `Entry: ${entry.id}`,
        ].join('\n'));
      } catch (error) {
        this.logger.error('Failed to record manual expense', error as Error);
        await ctx.reply('Unable to record expense right now.');
      }
    });

    this.bot.command('profit_month', async (ctx) => {
      const startOfMonth = new Date();
      startOfMonth.setDate(1);
      startOfMonth.setHours(0, 0, 0, 0);

      try {
        const entries = await this.prisma.accountingEntry.findMany({
          where: {
            created_at: { gte: startOfMonth },
            currency: 'RUB',
          },
          select: { type: true, amount: true },
        });

        const totals = entries.reduce(
          (acc, entry) => {
            const amount = Number(entry.amount);
            if (entry.type === AccountingEntryType.REVENUE) acc.revenue += amount;
            if (entry.type === AccountingEntryType.EXPENSE) acc.expense += amount;
            if (entry.type === AccountingEntryType.ADJUSTMENT) acc.adjustment += amount;
            return acc;
          },
          { revenue: 0, expense: 0, adjustment: 0 },
        );

        await ctx.reply([
          'Profit this month',
          `Revenue: ${totals.revenue.toFixed(2)} RUB`,
          `Expenses: ${totals.expense.toFixed(2)} RUB`,
          `Adjustments: ${totals.adjustment.toFixed(2)} RUB`,
          `Profit: ${(totals.revenue - totals.expense + totals.adjustment).toFixed(2)} RUB`,
          `Entries: ${entries.length}`,
        ].join('\n'));
      } catch (error) {
        this.logger.error('Failed to build monthly profit report', error as Error);
        await ctx.reply('Unable to build monthly profit report right now.');
      }
    });

    this.bot.command('import', async (ctx) => {
      await ctx.reply([
        'Import a supplier config into the boutique inventory:',
        '',
        '/add_wizard - guided secure import',
        '/add basic <config-or-subscription-url>',
        '/add premium <config-or-subscription-url>',
        '/add platinum <config-or-subscription-url>',
        '',
        'Each supplier link is capped at 2 customer orders.',
        'Raw config is preserved in PostgreSQL.',
      ].join('\n'));
    });

    this.bot.command('add_wizard', async (ctx) => {
      this.importWizardSessions.set(this.getWizardKey(ctx), { step: 'category' });
      await ctx.reply(formatImportWizardCategoryPrompt());
    });

    this.bot.command('cancel_import', async (ctx) => {
      this.importWizardSessions.delete(this.getWizardKey(ctx));
      await ctx.reply('Supplier config import cancelled.');
    });

    this.bot.command('add', async (ctx) => {
      const text = ctx.message.text || '';
      const match = text.match(/^\/add(?:@\w+)?\s+(\S+)\s+([\s\S]+)$/i);
      if (!match) {
        await ctx.reply('Usage: /add basic|premium|platinum <config-or-subscription-url>');
        return;
      }

      const category = mapBotPlanInputToCategory(match[1]);
      const config = match[2]?.trim();
      if (!category) {
        await ctx.reply('Invalid plan bucket. Use basic, premium, or platinum.');
        return;
      }

      if (!config) {
        await ctx.reply('Missing config or subscription URL.');
        return;
      }

      await ctx.reply('Importing supplier config...');
      try {
        const result = await this.importSupplierConfig(category, config, ctx.from?.id?.toString() || null);
        await ctx.reply(formatImportResult(category, result));
      } catch (error) {
        this.logger.error('Failed to import config via admin bot', error as Error);
        await ctx.reply('Inventory service rejected the import. Check parser warnings or service logs.');
      }
    });

    this.bot.on('text', async (ctx) => {
      const key = this.getWizardKey(ctx);
      const session = this.importWizardSessions.get(key);
      if (!session) return;

      const text = ctx.message.text || '';
      if (isImportWizardCancel(text)) {
        this.importWizardSessions.delete(key);
        await ctx.reply('Supplier config import cancelled.');
        return;
      }

      if (text.trim().startsWith('/')) {
        return;
      }

      if (session.step === 'category') {
        const category = mapBotPlanInputToCategory(text);
        if (!category) {
          await ctx.reply('Invalid bucket. Reply with basic, premium, or platinum.');
          return;
        }

        this.importWizardSessions.set(key, { step: 'config', category });
        await ctx.reply(formatImportWizardConfigPrompt(category));
        return;
      }

      if (session.step === 'config') {
        const config = text.trim();
        if (!config) {
          await ctx.reply('Missing supplier config or subscription URL. Send it as one message.');
          return;
        }

        this.importWizardSessions.set(key, { step: 'confirm', category: session.category, config });
        await ctx.reply(formatImportWizardConfirmation(session.category, config));
        return;
      }

      if (session.step === 'confirm') {
        if (!isImportWizardConfirm(text)) {
          await ctx.reply('Reply confirm to import, or cancel to stop.');
          return;
        }

        await ctx.reply('Importing supplier config...');
        try {
          const result = await this.importSupplierConfig(
            session.category,
            session.config,
            ctx.from?.id?.toString() || null,
          );
          this.importWizardSessions.delete(key);
          await ctx.reply(formatImportResult(session.category, result));
        } catch (error) {
          this.logger.error('Failed to import config via admin bot wizard', error as Error);
          await ctx.reply('Inventory service rejected the import. Check parser warnings or service logs.');
        }
      }
    });

    this.bot.catch((error, ctx) => {
      this.logger.error(`Telegraf error for ${ctx.updateType}:`, error as Error);
    });
  }

  private helpText() {
    return [
      'SWIMVPN+ Admin Operations Bot',
      '',
      '/stock - inventory overview by plan bucket',
      '/import - import instructions',
      '/add_wizard - guided supplier config import',
      '/add basic|premium|platinum <config> - import supplier config',
      '/orders - recent orders',
      '/pending - paid orders waiting for supplier capacity',
      '/retry <orderRef|all> - retry pending fulfillment',
      '/expire <inventoryId> - mark supplier config expired',
      '/disable <inventoryId> - disable supplier config',
      '/quota_reached <inventoryId> - mark supplier quota exhausted',
      '/orders_today - today paid/fulfilled order count',
      '/revenue_today - today paid/fulfilled revenue',
      '/add_expense <amount> <currency> <note> - record supplier/business expense',
      '/profit_month - current month RUB profit report',
      '/users - customer statistics',
      '/healthcheck - run inventory health check',
      '/help - show this menu',
    ].join('\n');
  }

  private getWizardKey(ctx: any) {
    return `${ctx.chat?.id || 'unknown'}:${ctx.from?.id || 'unknown'}`;
  }

  private async importSupplierConfig(category: any, config: string, telegramUserId: string | null) {
    const result = await firstValueFrom(
      this.inventoryClient.send(
        { cmd: 'import_configs' },
        {
          category,
          configs: [config],
          batchName: `Admin bot import ${new Date().toISOString()}`,
          maxUsersPerConfig: DEFAULT_SUPPLIER_DEVICE_LIMIT,
          maxResaleSlots: DEFAULT_RESALE_SLOT_CAP,
          supplierDeviceLimit: DEFAULT_SUPPLIER_DEVICE_LIMIT,
        },
      ),
    );

    await this.prisma.adminEvent.create({
      data: {
        event_type: 'ADMIN_BOT_CONFIG_IMPORTED',
        entity_type: 'INVENTORY',
        entity_id: 'BOT_IMPORT',
        payload_json: {
          category,
          telegramUserId,
          maxResaleSlots: DEFAULT_RESALE_SLOT_CAP,
          supplierDeviceLimit: DEFAULT_SUPPLIER_DEVICE_LIMIT,
          result,
          createdAt: new Date().toISOString(),
        } as any,
      },
    });

    return result;
  }

  private async updateInventoryHealthFromCommand(
    ctx: any,
    healthStatus: 'EXPIRED' | 'DISABLED',
    defaultReason: string,
  ) {
    const parsed = parseInventoryActionCommand(ctx.message?.text || '');
    if (!parsed.inventoryItemId) {
      await ctx.reply(`Usage: ${ctx.message?.text?.split(/\s+/)[0] || '/command'} <inventoryId> [reason]`);
      return;
    }

    try {
      const result = await firstValueFrom(
        this.inventoryClient.send(
          { cmd: 'update_inventory_health' },
          {
            inventoryItemId: parsed.inventoryItemId,
            healthStatus,
            reason: parsed.reason || defaultReason,
          },
        ),
      );

      await this.prisma.adminEvent.create({
        data: {
          event_type: 'ADMIN_BOT_INVENTORY_HEALTH_UPDATED',
          entity_type: 'INVENTORY',
          entity_id: parsed.inventoryItemId,
          payload_json: {
            telegramUserId: ctx.from?.id?.toString() || null,
            inventoryItemId: parsed.inventoryItemId,
            healthStatus,
            reason: parsed.reason || defaultReason,
            result,
            updatedAt: new Date().toISOString(),
          } as any,
        },
      });

      await ctx.reply(`Inventory ${parsed.inventoryItemId} marked ${healthStatus}.`);
    } catch (error) {
      this.logger.error('Failed to update inventory health via admin bot', error as Error);
      await ctx.reply('Unable to update inventory health. Check the inventory id and service logs.');
    }
  }

  private async replyAccountingSummary(ctx: any, title: string) {
    const startOfDay = new Date();
    startOfDay.setHours(0, 0, 0, 0);

    try {
      const orders = await this.prisma.order.findMany({
        where: {
          status: { in: ['PAID', 'PENDING_FULFILLMENT', 'FULFILLED'] as any },
          paid_at: { gte: startOfDay },
        },
        select: { amount_rub: true },
      });

      const total = orders.reduce((sum, order) => sum + Number(order.amount_rub), 0);
      await ctx.reply(formatAccountingSummary({
        title,
        orderCount: orders.length,
        amountRub: total.toFixed(2),
      }));
    } catch (error) {
      this.logger.error('Failed to build accounting summary', error as Error);
      await ctx.reply('Unable to build accounting summary right now.');
    }
  }

  async sendAdminAlert(message: string) {
    if (!this.adminChatId || !this.bot) return;
    try {
      await this.bot.telegram.sendMessage(this.adminChatId, message);
    } catch (error) {
      this.logger.error('Failed to send admin alert', error as Error);
    }
  }

  onModuleDestroy() {
    if (this.bot) {
      this.bot.stop('SIGINT');
    }
  }
}
