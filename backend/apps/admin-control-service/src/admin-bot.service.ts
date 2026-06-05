import { Injectable, OnModuleDestroy, OnModuleInit, Logger, Inject } from '@nestjs/common';
import { ConfigService } from '@nestjs/config';
import { ClientProxy } from '@nestjs/microservices';
import { PrismaService } from '@app/database';
import {
  DEFAULT_SUPPLIER_DEVICE_LIMIT,
  getDefaultSupplierCapacityUnits,
} from '@app/contracts';
import { AccountingEntrySource, AccountingEntryType, InventoryHealthStatus, PlanCategory, TrialConfigStatus } from '@prisma/client';
import { Markup, Telegraf } from 'telegraf';
import { firstValueFrom } from 'rxjs';
import { isAdminBotAuthorized, normalizeTelegramId, parseAdminUserIds } from './admin-bot-auth';
import {
  ADMIN_BOT_COMMANDS,
  formatAccountingSummary,
  formatImportWizardCategoryPrompt,
  formatImportWizardConfigPrompt,
  formatImportWizardConfirmation,
  formatImportResult,
  formatCombinedInventoryOverview,
  formatFinanceDashboard,
  formatInventoryReview,
  getFinanceDashboardKeyboard,
  getInventoryActionKeyboard,
  getOrderActionKeyboard,
  getDeleteConfirmationKeyboard,
  getDeleteFailedKeyboard,
  getSuperDeleteConfirmationKeyboard,
  formatPendingFulfillment,
  formatTrialImportInstructions,
  formatTrialImportResult,
  formatTrialImportWizardConfirmation,
  formatTrialImportWizardConfigPrompt,
  isImportWizardCancel,
  isImportWizardConfirm,
  mapBotPlanInputToCategory,
  parseExpenseCommand,
  parseInventoryActionCommand,
  parseReviewCommand,
  parseRetryCommand,
  formatStockHealth,
  formatCockpitHub,
  cockpitHubKeyboard,
  formatCockpitPalette,
  cockpitPaletteKeyboard,
  type StockHealthItem,
} from './admin-bot.formatter';

type ImportWizardSession =
  | { step: 'category' }
  | { step: 'config'; category: any }
  | { step: 'confirm'; category: any; config: string }
  | { step: 'trial_config' }
  | { step: 'trial_confirm'; config: string };

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
    this.bot.launch().then(async () => {
      await this.registerTelegramCommandMenu();
      this.logger.log('Admin operations bot started');
    });
  }

  private setupMiddleware() {
    if (!this.bot) return;

    this.bot.use(async (ctx, next) => {
      if (this.isWhoamiCommand(ctx)) {
        return next();
      }

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

    this.bot.start(async (ctx) => { await this.replyCockpitHub(ctx, false); });
    this.bot.command('help', async (ctx) => { await this.replyCockpitHub(ctx, false); });
    this.bot.command('cockpit', async (ctx) => { await this.replyCockpitHub(ctx, false); });
    this.bot.command('menu', async (ctx) => { await this.replyCockpitHub(ctx, false); });
    this.bot.command('whoami', async (ctx) => ctx.reply(this.formatWhoami(ctx), { parse_mode: 'Markdown' }));

    this.bot.command('status', async (ctx) => {
      await ctx.reply('SWIMVPN+ Admin Operations Bot is online.');
    });

    this.bot.command('stock', async (ctx) => {
      await this.replyStockOverview(ctx);
    });

    this.bot.command('stock_health', async (ctx) => {
      await this.replyStockHealth(ctx);
    });

    this.bot.command('review', async (ctx) => {
      const parsed = parseReviewCommand(ctx.message.text || '');
      if (parsed.valid === false) {
        await ctx.reply(parsed.reason);
        return;
      }

      await this.replyInventoryReviewSearch(ctx, parsed.scope, parsed.query);
    });

    this.bot.command('orders', async (ctx) => {
      await this.replyRecentOrders(ctx);
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

        if (pendingOrders.length === 0) {
          await ctx.reply('No pending fulfillment orders.');
          return;
        }

        for (const order of pendingOrders) {
          const text = [
            `📦 *Order:* \`${order.order_ref}\``,
            `Plan: ${order.plan.name} (${order.plan.code})`,
            `Amount: ${order.amount_rub.toString()} RUB`,
            `Customer: ${order.customer.email}`,
            `Created: ${order.created_at.toISOString()}`,
          ].join('\n');
          await ctx.reply(text, {
            parse_mode: 'Markdown',
            ...getOrderActionKeyboard(order.order_ref),
          });
        }
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
          results.push(`\`${order.order_ref}\`: ${result?.pendingFulfillment ? '⏳ pending' : '✅ processed'}`);
        }

        await ctx.reply(['*Retry finished*', ...results].join('\n'), { parse_mode: 'Markdown' });
      } catch (error) {
        this.logger.error('Failed to retry fulfillment', error as Error);
        await ctx.reply('Retry failed. Check inventory capacity and service logs.');
      }
    });

    this.bot.command('users', async (ctx) => {
      await this.replyUsersStats(ctx);
    });

    this.bot.command('healthcheck', async (ctx) => {
      await this.runHealthcheckReply(ctx);
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

    this.bot.command('delete', async (ctx) => {
      await this.handleDelete(ctx, 'paid');
    });

    this.bot.command('delete_trial', async (ctx) => {
      await this.handleDelete(ctx, 'trial');
    });

    this.bot.command('superdelete', async (ctx) => {
      const parsed = parseInventoryActionCommand(ctx.message?.text || '');
      const id = parsed.inventoryItemId;

      if (!id) {
        await ctx.reply('Usage: /superdelete <id-ou-code>');
        return;
      }

      await ctx.reply(
        `🛑 *ALERTE SUPERDELETE*\n\nCette action va :\n1. Révoquer l'accès de TOUS les utilisateurs sur cet item.\n2. Détacher les commandes/trials en cours.\n3. Supprimer définitivement l'item.\n\nItem: \`${id}\``,
        {
          parse_mode: 'Markdown',
          // Note: We need to know if it's paid or trial, we try to detect
          ...await this.getSuperDeleteKeyboard(id),
        }
      );
    });

    this.bot.command('finance', async (ctx) => {
      await this.replyFinanceDashboard(ctx);
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
          '✅ *Expense recorded*',
          `💰 *Amount:* ${parsed.amount} ${parsed.currency}`,
          `📝 *Note:* ${parsed.note}`,
          `🆔 *Entry ID:* \`${entry.id}\``,
        ].join('\n'), { parse_mode: 'Markdown' });
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
        'Trial Store:',
        '/trial_import - trial config import instructions',
        '/add_trial <config-or-subscription-url>',
        '',
        'Supplier stock capacity depends on the bucket: Basic 2, Premium 4, Platinum 6 internal units.',
        'Raw config is preserved in PostgreSQL.',
      ].join('\n'), this.importKeyboard());
    });

    this.bot.command('add_wizard', async (ctx) => {
      this.importWizardSessions.set(this.getWizardKey(ctx), { step: 'category' });
      await ctx.reply(formatImportWizardCategoryPrompt());
    });

    this.bot.command('trial_import', async (ctx) => {
      await ctx.reply(formatTrialImportInstructions());
    });

    this.bot.command('trial_wizard', async (ctx) => {
      this.importWizardSessions.set(this.getWizardKey(ctx), { step: 'trial_config' });
      await ctx.reply(formatTrialImportWizardConfigPrompt());
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
        await this.replyImportResult(ctx, formatImportResult(category, result), 'paid', result);
      } catch (error) {
        this.logger.error('Failed to import config via admin bot', error as Error);
        await ctx.reply('Inventory service rejected the import. Check parser warnings or service logs.');
      }
    });

    this.bot.command('add_trial', async (ctx) => {
      const text = ctx.message.text || '';
      const match = text.match(/^\/add_trial(?:@\w+)?\s+([\s\S]+)$/i);
      const config = match?.[1]?.trim();
      if (!config) {
        await ctx.reply('Usage: /add_trial <config-or-subscription-url>');
        return;
      }

      await ctx.reply('Importing trial config...');
      try {
        const result = await this.importTrialConfig(config, ctx.from?.id?.toString() || null);
        await this.replyImportResult(ctx, formatTrialImportResult(result), 'trial', result);
      } catch (error) {
        this.logger.error('Failed to import trial config via admin bot', error as Error);
        await ctx.reply('Trial Store rejected the import. Check parser warnings or service logs.');
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

      if (session.step === 'trial_config') {
        const config = text.trim();
        if (!config) {
          await ctx.reply('Missing trial config or subscription URL. Send it as one message.');
          return;
        }

        this.importWizardSessions.set(key, { step: 'trial_confirm', config });
        await ctx.reply(formatTrialImportWizardConfirmation(config));
        return;
      }

      if (session.step === 'trial_confirm') {
        if (!isImportWizardConfirm(text)) {
          await ctx.reply('Reply confirm to import, or cancel to stop.');
          return;
        }

        await ctx.reply('Importing trial config...');
        try {
          const result = await this.importTrialConfig(
            session.config,
            ctx.from?.id?.toString() || null,
          );
          this.importWizardSessions.delete(key);
          await this.replyImportResult(ctx, formatTrialImportResult(result), 'trial', result);
        } catch (error) {
          this.logger.error('Failed to import trial config via admin bot wizard', error as Error);
          await ctx.reply('Trial Store rejected the import. Check parser warnings or service logs.');
        }
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
          await this.replyImportResult(ctx, formatImportResult(session.category, result), 'paid', result);
        } catch (error) {
          this.logger.error('Failed to import config via admin bot wizard', error as Error);
          await ctx.reply('Inventory service rejected the import. Check parser warnings or service logs.');
        }
      }
    });

    this.bot.catch((error, ctx) => {
      this.logger.error(`Telegraf error for ${ctx.updateType}:`, error as Error);
    });

    this.bot.action(/^cockpit:home$/, async (ctx) => {
      await ctx.answerCbQuery();
      await this.replyCockpitHub(ctx, true);
    });

    this.bot.action(/^stock$/, async (ctx) => {
      await ctx.answerCbQuery();
      await this.replyStockOverview(ctx);
    });

    this.bot.action(/^import_menu$/, async (ctx) => {
      await ctx.answerCbQuery();
      await ctx.reply([
        'Choose an import flow:',
        '',
        'Paid stock is sold through Basic, Premium, and Platinum buckets.',
        'Trial stock is stored separately and can serve trial grants.',
      ].join('\n'), this.importKeyboard());
    });

    this.bot.action(/^import_menu:(basic|premium|platinum|trial)/, async (ctx) => {
      const target = ctx.match[1];
      await ctx.answerCbQuery();
      if (target === 'trial') {
        this.importWizardSessions.set(this.getWizardKey(ctx), { step: 'trial_config' });
        await ctx.reply(formatTrialImportWizardConfigPrompt());
        return;
      }

      const category = mapBotPlanInputToCategory(target);
      if (!category) {
        await ctx.reply('Invalid import target.');
        return;
      }

      this.importWizardSessions.set(this.getWizardKey(ctx), { step: 'config', category });
      await ctx.reply(formatImportWizardConfigPrompt(category));
    });

    this.bot.action(/^pending$/, async (ctx) => {
      await ctx.answerCbQuery();
      await ctx.reply('Use /pending to read paid orders waiting for supplier capacity.');
    });

    this.bot.action(/^review:(paid|trial):(.+)/, async (ctx) => {
      const scope = ctx.match[1] as 'paid' | 'trial';
      const id = ctx.match[2];
      await ctx.answerCbQuery('Chargement...');
      await this.replyInventoryReview(ctx, scope, id);
    });

    this.bot.action(/^disable:(paid|trial):(.+)/, async (ctx) => {
      const scope = ctx.match[1] as 'paid' | 'trial';
      const id = ctx.match[2];
      await ctx.answerCbQuery('Désactivation...');
      if (scope === 'paid') {
        await this.executeInventoryHealthUpdate(ctx, id, 'DISABLED', 'ADMIN_UI_DISABLE');
      } else {
        await this.prisma.trialConfig.update({ where: { id }, data: { status: 'DISABLED' } });
        await ctx.reply(`Trial ${id} désactivé.`);
      }
      await this.replyInventoryReview(ctx, scope, id);
    });

    this.bot.action(/^expire:(paid|trial):(.+)/, async (ctx) => {
      const scope = ctx.match[1] as 'paid' | 'trial';
      const id = ctx.match[2];
      await ctx.answerCbQuery('Marquage expiré...');
      if (scope === 'paid') {
        await this.executeInventoryHealthUpdate(ctx, id, InventoryHealthStatus.EXPIRED, 'ADMIN_UI_EXPIRE');
      } else {
        await this.prisma.trialConfig.update({ where: { id }, data: { status: 'EXPIRED' } });
        await ctx.reply(`Trial ${id} marqué expiré.`);
      }
      await this.replyInventoryReview(ctx, scope, id);
    });

    this.bot.action(/^delete_confirm:(paid|trial):(.+)/, async (ctx) => {
      const scope = ctx.match[1] as 'paid' | 'trial';
      const id = ctx.match[2];
      await ctx.answerCbQuery();
      await ctx.reply(
        `⚠️ *CONFIRMATION DE SUPPRESSION*\n\nVoulez-vous vraiment supprimer définitivement l'item \`${id}\` ? Cette action est irréversible si l'item n'a aucune commande liée.`,
        { parse_mode: 'Markdown', ...getDeleteConfirmationKeyboard(scope, id) },
      );
    });

    this.bot.action(/^delete_execute:(paid|trial):(.+)/, async (ctx) => {
      const scope = ctx.match[1] as 'paid' | 'trial';
      const id = ctx.match[2];
      await ctx.answerCbQuery('Suppression en cours...');
      await this.executeDirectDelete(ctx, scope, id);
    });

    this.bot.action(/^superdelete_confirm:(paid|trial):(.+)/, async (ctx) => {
      const scope = ctx.match[1] as 'paid' | 'trial';
      const id = ctx.match[2];
      await ctx.answerCbQuery();
      const keyboard = await this.getSuperDeleteKeyboard(id);
      await ctx.reply(
        `🛑 *ALERTE SUPERDELETE*\n\nCette action va :\n1. Révoquer l'accès de TOUS les utilisateurs sur cet item.\n2. Détacher les commandes/trials en cours.\n3. Supprimer définitivement l'item.\n\nItem: \`${id}\``,
        {
          parse_mode: 'Markdown',
          ...keyboard,
        }
      );
    });

    this.bot.action(/^superdelete_execute:(paid|trial):(.+)/, async (ctx) => {
      const scope = ctx.match[1] as 'paid' | 'trial';
      const id = ctx.match[2];
      await ctx.answerCbQuery('💀 SUPERDELETE EN COURS...');
      await this.executeSuperDelete(ctx, scope, id);
    });

    this.bot.action(/^retry_order:(.+)/, async (ctx) => {
      const orderRef = ctx.match[1];
      await ctx.answerCbQuery('Relance de la commande...');
      await this.executeOrderRetry(ctx, orderRef);
    });

    this.bot.action(/^finance_refresh$/, async (ctx) => {
      await ctx.answerCbQuery('Mise à jour...');
      await this.replyFinanceDashboard(ctx, true);
    });

    this.bot.action(/^profit_month_action$/, async (ctx) => {
      await ctx.answerCbQuery();
      // Logic from profit_month command but as action
      const startOfMonth = new Date();
      startOfMonth.setDate(1);
      startOfMonth.setHours(0, 0, 0, 0);
      try {
        const entries = await this.prisma.accountingEntry.findMany({
          where: { created_at: { gte: startOfMonth }, currency: 'RUB' },
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
        ].join('\n'), getFinanceDashboardKeyboard());
      } catch (error) {
        await ctx.reply('Erreur lors du calcul du profit.');
      }
    });

    this.bot.action(/^add_expense_start$/, async (ctx) => {
      await ctx.answerCbQuery();
      await ctx.reply('Utilisez la commande: /add_expense <montant> <devise> <note>\nExemple: `/add_expense 5000 RUB Serveurs Mai`', { parse_mode: 'Markdown' });
    });

    this.bot.action(/^cockpit:palette$/, async (ctx) => {
      await ctx.answerCbQuery();
      await ctx.editMessageText(formatCockpitPalette(), { parse_mode: 'Markdown', ...cockpitPaletteKeyboard() });
    });

    this.bot.action(/^cockpit:stock_health$/, async (ctx) => {
      await ctx.answerCbQuery('Lecture...');
      await this.replyStockHealth(ctx);
    });

    this.bot.action(/^cockpit:orders$/, async (ctx) => {
      await ctx.answerCbQuery('Lecture...');
      await this.replyRecentOrders(ctx);
    });

    this.bot.action(/^cockpit:users$/, async (ctx) => {
      await ctx.answerCbQuery('Lecture...');
      await this.replyUsersStats(ctx);
    });

    this.bot.action(/^cockpit:healthcheck$/, async (ctx) => {
      await ctx.answerCbQuery('Vérification...');
      await this.runHealthcheckReply(ctx);
    });

    this.bot.action(/^cockpit:danger$/, async (ctx) => {
      await ctx.answerCbQuery();
      await ctx.editMessageText(
        '🛑 *Danger* — choisis une config à gérer (désactiver / expirer / supprimer se font depuis sa fiche).',
        { parse_mode: 'Markdown', ...Markup.inlineKeyboard([[Markup.button.callback('🔧 Gérer une config', 'cockpit:manage')], [Markup.button.callback('⬅️ Retour', 'cockpit:home')]]) },
      );
    });
  }

  private async registerTelegramCommandMenu() {
    if (!this.bot) return;
    try {
      await this.bot.telegram.setMyCommands(ADMIN_BOT_COMMANDS);
      this.logger.log(`Registered ${ADMIN_BOT_COMMANDS.length} Telegram admin bot commands`);
    } catch (error) {
      this.logger.error('Failed to register Telegram admin bot commands', error as Error);
    }
  }

  private helpText() {
    return [
      'SWIMVPN+ Admin Operations Bot',
      '',
      '/stock - inventory overview by plan bucket',
      '/review <id-or-folder> - review existing paid/trial config',
      '/import - import instructions',
      '/add_wizard - guided supplier config import',
      '/add basic|premium|platinum <config> - import supplier config',
      '/trial_import - trial config import instructions',
      '/trial_wizard - guided trial config import',
      '/add_trial <config> - import config into Trial Store',
      '/orders - recent orders',
      '/pending - paid orders waiting for supplier capacity',
      '/retry <orderRef|all> - retry pending fulfillment',
      '/expire <inventoryId> - mark supplier config expired',
      '/disable <inventoryId> - disable supplier config',
      '/quota_reached <inventoryId> - mark supplier quota exhausted',
      '/delete <inventoryId> - delete a paid config',
      '/delete_trial <trialConfigId> - delete a trial config',
      '/orders_today - today paid/fulfilled order count',
      '/revenue_today - today paid/fulfilled revenue',
      '/add_expense <amount> <currency> <note> - record supplier/business expense',
      '/profit_month - current month RUB profit report',
      '/users - customer statistics',
      '/healthcheck - run inventory health check',
      '/whoami - show Telegram ids for ADMIN_USER_IDS setup',
      '/help - show this menu',
    ].join('\n');
  }

  private mainKeyboard() {
    return Markup.inlineKeyboard([
      [
        Markup.button.callback('Stock', 'stock'),
        Markup.button.callback('Importer', 'import_menu'),
      ],
      [
        Markup.button.callback('Pending', 'pending'),
      ],
    ]);
  }

  private importKeyboard() {
    return Markup.inlineKeyboard([
      [
        Markup.button.callback('Basic', 'import_menu:basic'),
        Markup.button.callback('Premium', 'import_menu:premium'),
        Markup.button.callback('Platinum', 'import_menu:platinum'),
      ],
      [
        Markup.button.callback('Trial Store', 'import_menu:trial'),
        Markup.button.callback('Stock', 'stock'),
      ],
    ]);
  }

  private postImportKeyboard(scope: 'paid' | 'trial', importedId: string | null) {
    const rows = [];
    if (importedId) {
      rows.push([Markup.button.callback('Review', `review:${scope}:${importedId}`)]);
    }
    rows.push([
      Markup.button.callback('Stock', 'stock'),
      Markup.button.callback('Aide', 'import_menu'),
    ]);
    return Markup.inlineKeyboard(rows);
  }

  private async replyStockOverview(ctx: any) {
    await ctx.reply('Reading inventory...');
    try {
      const [paidOverview, trialOverview] = await Promise.all([
        firstValueFrom(this.inventoryClient.send({ cmd: 'list_inventory_overview' }, {})),
        firstValueFrom(this.inventoryClient.send({ cmd: 'list_trial_inventory_overview' }, {})),
      ]);
      await ctx.reply(
        formatCombinedInventoryOverview({
          paid: Array.isArray(paidOverview) ? paidOverview : [],
          trial: Array.isArray(trialOverview) ? trialOverview : [],
        }),
        {
          parse_mode: 'Markdown',
          ...this.mainKeyboard(),
        },
      );
    } catch (error) {
      this.logger.error('Failed to read inventory overview', error as Error);
      await ctx.reply('Unable to read inventory right now.');
    }
  }

  private async replyStockHealth(ctx: any) {
    await ctx.reply('Lecture de la santé du stock...');
    try {
      const overview = await firstValueFrom(
        this.inventoryClient.send({ cmd: 'list_inventory_overview' }, {}),
      );
      const items: StockHealthItem[] = (Array.isArray(overview) ? overview : []).map((i: any) => ({
        category: i.category,
        healthStatus: i.healthStatus,
        usedResaleSlots: i.usedResaleSlots,
        maxResaleSlots: i.maxResaleSlots,
        supplierExpiresAtMs: i.supplierExpiresAt ? new Date(i.supplierExpiresAt).getTime() : null,
      }));
      await ctx.reply(formatStockHealth(items, Date.now()), { parse_mode: 'Markdown' });
    } catch (error) {
      this.logger.error('Failed to read stock health', error as Error);
      await ctx.reply('Lecture de la santé du stock impossible.');
    }
  }

  private async replyRecentOrders(ctx: any) {
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
        `📦 *Order:* \`${order.order_ref}\``,
        `👤 *Customer:* \`${order.customer.public_id}\``,
        `🏷️ *Plan:* ${order.plan.name}`,
        `💰 *Amount:* ${order.amount_rub.toString()} RUB`,
        `🚦 *Status:* ${order.status}`,
      ].join('\n')).join('\n\n');

      await ctx.reply(message, { parse_mode: 'Markdown' });
    } catch (error) {
      this.logger.error('Failed to fetch recent orders', error as Error);
      await ctx.reply('Unable to fetch orders right now.');
    }
  }

  private async replyUsersStats(ctx: any) {
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
  }

  private async runHealthcheckReply(ctx: any) {
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
  }

  private async replyImportResult(ctx: any, message: string, scope: 'paid' | 'trial', result: any) {
    const importedId = this.extractFirstImportedId(result);
    await ctx.reply(message, {
      parse_mode: 'Markdown',
      ...this.postImportKeyboard(scope, importedId),
    });
  }

  private async replyInventoryReview(ctx: any, scope: 'paid' | 'trial', id: string) {
    try {
      const pattern = scope === 'trial'
        ? { cmd: 'list_trial_inventory_overview' }
        : { cmd: 'list_inventory_overview' };
      const overview = await firstValueFrom(this.inventoryClient.send(pattern, {}));
      const item = Array.isArray(overview)
        ? overview.find((entry: any) =>
            entry.id === id ||
            (entry.folderCode && entry.folderCode.toLowerCase() === id.toLowerCase()) ||
            (entry.adminLabel && entry.adminLabel.toLowerCase() === id.toLowerCase()),
          )
        : null;

      const resolvedId = item?.id || id;

      await ctx.reply(
        formatInventoryReview(scope, item || null),
        {
          parse_mode: 'Markdown',
          ...getInventoryActionKeyboard(scope, resolvedId),
        },
      );
    } catch (error) {
      this.logger.error('Failed to build inventory review via admin bot', error as Error);
      await ctx.reply('Review unavailable right now. Check service logs.');
    }
  }

  private async replyInventoryReviewSearch(
    ctx: any,
    requestedScope: 'paid' | 'trial' | 'any',
    query: string,
  ) {
    try {
      const [paidOverview, trialOverview] = await Promise.all([
        requestedScope === 'trial'
          ? Promise.resolve([])
          : firstValueFrom(this.inventoryClient.send({ cmd: 'list_inventory_overview' }, {})),
        requestedScope === 'paid'
          ? Promise.resolve([])
          : firstValueFrom(this.inventoryClient.send({ cmd: 'list_trial_inventory_overview' }, {})),
      ]);

      const matches = [
        ...this.findReviewMatches('paid', Array.isArray(paidOverview) ? paidOverview : [], query),
        ...this.findReviewMatches('trial', Array.isArray(trialOverview) ? trialOverview : [], query),
      ];

      if (matches.length === 0) {
        await ctx.reply([
          'Review unavailable. No config matched your query.',
          '',
          'Try /stock, then use:',
          '/review <id-or-folder>',
          '/review paid <id-or-folder>',
          '/review trial <id-or-folder>',
        ].join('\n'), this.mainKeyboard());
        return;
      }

      if (matches.length > 1) {
        await ctx.reply([
          '⚠️ *Query ambiguous. Match multiple items:*',
          '',
          ...matches.slice(0, 8).map((match) => {
            const label = match.item.folderCode || match.item.adminLabel || match.item.id || 'unknown';
            return `- ${match.scope}: \`${label}\``;
          }),
          '',
          'Use a full UUID or exact folder code.',
        ].join('\n'), { parse_mode: 'Markdown', ...this.mainKeyboard() });
        return;
      }

      const [match] = matches;
      await this.replyInventoryReview(ctx, match.scope, match.item.id);
    } catch (error) {
      this.logger.error('Failed to search inventory review via admin bot', error as Error);
      await ctx.reply('Review unavailable right now. Check service logs.');
    }
  }

  private findReviewMatches(scope: 'paid' | 'trial', items: any[], query: string) {
    const normalizedQuery = query.trim().toLowerCase();
    if (!normalizedQuery) {
      return [];
    }

    return items
      .filter((item) => {
        const fields = [
          item.id,
          item.folderCode,
          item.adminLabel,
        ].filter((value): value is string => typeof value === 'string' && value.trim().length > 0);

        return fields.some((value) => {
          const normalized = value.toLowerCase();
          return normalized === normalizedQuery || normalized.startsWith(normalizedQuery);
        });
      })
      .map((item) => ({ scope, item }));
  }

  private extractFirstImportedId(result: any) {
    if (!Array.isArray(result?.details)) {
      return null;
    }

    const item = result.details.find((detail: any) => detail?.status === 'IMPORTED' && typeof detail.id === 'string');
    return item?.id || null;
  }

  private isWhoamiCommand(ctx: any) {
    const text = ctx.message?.text || '';
    return /^\/whoami(?:@\w+)?(?:\s|$)/i.test(text);
  }

  private formatWhoami(ctx: any) {
    const fromId = normalizeTelegramId(ctx.from?.id);
    const authorized = isAdminBotAuthorized({
      fromId,
      chatId: ctx.chat?.id,
      adminChatId: this.adminChatId,
      adminUserIds: this.adminUserIds,
    });
    const userInAllowList = !!fromId && this.adminUserIds.includes(fromId);

    return [
      '*Telegram identity*',
      `👤 User id: \`${ctx.from?.id || '-'}\``,
      `💬 Chat id: \`${ctx.chat?.id || '-'}\``,
      `🏷️ Username: ${ctx.from?.username ? `@${ctx.from.username}` : '-'}`,
      `🛡️ Authorized: ${authorized ? '✅' : '❌'}`,
      `👥 Configured admin ids: ${this.adminUserIds.length}`,
      `🔑 Current user in allow-list: ${userInAllowList ? '✅' : '❌'}`,
      '',
      '_Add the User id to ADMIN_USER_IDS for private admin commands._',
    ].join('\n');
  }

  private getWizardKey(ctx: any) {
    return `${ctx.chat?.id || 'unknown'}:${ctx.from?.id || 'unknown'}`;
  }

  private async importSupplierConfig(category: any, config: string, telegramUserId: string | null) {
    const maxSupplierCapacityUnits = getDefaultSupplierCapacityUnits(category);
    const result = await firstValueFrom(
      this.inventoryClient.send(
        { cmd: 'import_configs' },
        {
          category,
          configs: [config],
          batchName: `Admin bot import ${new Date().toISOString()}`,
          maxUsersPerConfig: DEFAULT_SUPPLIER_DEVICE_LIMIT,
          maxResaleSlots: maxSupplierCapacityUnits,
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
          maxResaleSlots: maxSupplierCapacityUnits,
          supplierDeviceLimit: DEFAULT_SUPPLIER_DEVICE_LIMIT,
          result,
          createdAt: new Date().toISOString(),
        } as any,
      },
    });

    return result;
  }

  private async importTrialConfig(config: string, telegramUserId: string | null) {
    const result = await firstValueFrom(
      this.inventoryClient.send(
        { cmd: 'import_trial_configs' },
        {
          configs: [config],
          batchName: `Admin bot trial import ${new Date().toISOString()}`,
        },
      ),
    );

    await this.prisma.adminEvent.create({
      data: {
        event_type: 'ADMIN_BOT_TRIAL_CONFIG_IMPORTED',
        entity_type: 'TRIAL_CONFIG',
        entity_id: 'BOT_TRIAL_IMPORT',
        payload_json: {
          telegramUserId,
          result,
          createdAt: new Date().toISOString(),
        } as any,
      },
    });

    return result;
  }

  private async getSuperDeleteKeyboard(query: string) {
    const [paid, trial] = await Promise.all([
      this.prisma.inventoryItem.findFirst({
        where: { OR: [{ id: query }, { folder_code: { equals: query, mode: 'insensitive' } }, { admin_label: { equals: query, mode: 'insensitive' } }] },
        select: { id: true }
      }),
      this.prisma.trialConfig.findFirst({
        where: { OR: [{ id: query }, { folder_code: { equals: query, mode: 'insensitive' } }, { admin_label: { equals: query, mode: 'insensitive' } }] },
        select: { id: true }
      })
    ]);

    if (paid) return getSuperDeleteConfirmationKeyboard('paid', paid.id);
    if (trial) return getSuperDeleteConfirmationKeyboard('trial', trial.id);
    return Markup.inlineKeyboard([Markup.button.callback('❌ Item introuvable', 'stock')]);
  }

  private async executeSuperDelete(ctx: any, scope: 'paid' | 'trial', id: string) {
    try {
      if (scope === 'paid') {
        await this.prisma.$transaction(async (tx) => {
          // 1. Find all affected order IDs before deleting assignments
          const assignments = await tx.orderAssignment.findMany({
            where: { inventory_item_id: id },
            select: { order_id: true },
          });
          const orderIds = [...new Set(assignments.map((a) => a.order_id))];

          // 2. Force remove assignments (this revokes VPN access immediately)
          await tx.orderAssignment.deleteMany({
            where: { inventory_item_id: id },
          });

          // 3. Revert Orders to PENDING_FULFILLMENT so they can be re-fulfilled
          if (orderIds.length > 0) {
            await tx.order.updateMany({
              where: {
                id: { in: orderIds },
                status: { in: ['FULFILLED', 'PAID'] as any },
              },
              data: { status: 'PENDING_FULFILLMENT', fulfilled_at: null },
            });
          }

          // 4. Detach from accounting records
          await tx.accountingEntry.updateMany({
            where: { inventory_item_id: id },
            data: { inventory_item_id: null },
          });

          // 5. Delete config events
          await tx.configEvent.deleteMany({
            where: { config_id: id, config_scope: 'PAID' },
          });

          // 6. Physical delete of the item
          await tx.inventoryItem.delete({ where: { id } });
        });
      } else {
        await this.prisma.$transaction(async (tx) => {
          // 1. Find all affected trial grant IDs
          const assignments = await tx.trialAssignment.findMany({
            where: { trial_config_id: id },
            select: { grant_id: true },
          });
          const grantIds = [...new Set(assignments.map((a) => a.grant_id))];

          // 2. Delete trial assignments
          await tx.trialAssignment.deleteMany({
            where: { trial_config_id: id },
          });

          // 3. Mark TrialGrants as REVOKED/FAILED
          if (grantIds.length > 0) {
            await tx.trialGrant.updateMany({
              where: { id: { in: grantIds } },
              data: {
                status: 'REVOKED',
                status_reason: 'Config deleted by admin',
                revoked_at: new Date(),
              },
            });
          }

          // 4. Delete config events
          await tx.configEvent.deleteMany({
            where: { config_id: id, config_scope: 'TRIAL' },
          });

          // 5. Physical delete of the config
          await tx.trialConfig.delete({ where: { id } });
        });
      }

      await this.prisma.adminEvent.create({
        data: {
          event_type: 'ADMIN_SUPERDELETE_EXECUTED',
          entity_type: scope === 'paid' ? 'INVENTORY' : 'TRIAL_CONFIG',
          entity_id: id,
          payload_json: {
            scope,
            telegramUserId: ctx.from?.id?.toString() || null,
            timestamp: new Date().toISOString(),
          } as any,
        },
      });

      await ctx.reply(`💀 *SUPERDELETE TERMINÉ*\n\nL'item \`${id}\` et tous ses accès utilisateurs ont été pulvérisés.`, { parse_mode: 'Markdown' });
      await this.replyStockOverview(ctx);
    } catch (error: any) {
      this.logger.error(`Superdelete failed for ${id}`, error);
      await ctx.reply(`❌ Échec du superdelete : ${error.message}`);
    }
  }

  private async executeInventoryHealthUpdate(
    ctx: any,
    inventoryItemId: string,
    healthStatus: 'EXPIRED' | 'DISABLED',
    reason: string,
  ) {
    try {
      const result = await firstValueFrom(
        this.inventoryClient.send(
          { cmd: 'update_inventory_health' },
          { inventoryItemId, healthStatus, reason },
        ),
      );

      await this.prisma.adminEvent.create({
        data: {
          event_type: 'ADMIN_BOT_INVENTORY_HEALTH_UPDATED',
          entity_type: 'INVENTORY',
          entity_id: inventoryItemId,
          payload_json: {
            telegramUserId: ctx.from?.id?.toString() || null,
            inventoryItemId,
            healthStatus,
            reason,
            result,
            updatedAt: new Date().toISOString(),
          } as any,
        },
      });

      await ctx.reply(`✅ Item \`${inventoryItemId}\` mis à jour : *${healthStatus}*.`, { parse_mode: 'Markdown' });
    } catch (error) {
      this.logger.error('Failed to update health via button', error as Error);
      await ctx.reply('❌ Échec de la mise à jour.');
    }
  }

  private async executeDirectDelete(ctx: any, scope: 'paid' | 'trial', id: string) {
    try {
      let targetId: string;

      if (scope === 'paid') {
        const item = await this.prisma.inventoryItem.findFirst({
          where: {
            OR: [
              { id },
              { folder_code: { equals: id, mode: 'insensitive' } },
              { admin_label: { equals: id, mode: 'insensitive' } },
            ],
          },
          include: {
            assignments: {
              include: { order: { select: { status: true } } },
            },
          },
        });

        if (!item) throw new Error(`Item \`${id}\` introuvable dans l'inventaire payant.`);
        targetId = item.id;

        const activeAssignments = item.assignments.filter((a) =>
          ['PAID', 'PENDING_FULFILLMENT', 'FULFILLED'].includes(a.order.status),
        );

        if (activeAssignments.length > 0) {
          const error = new Error(`Cet item est lié à ${activeAssignments.length} commande(s) active(s).`);
          (error as any).code = 'P2003';
          throw error;
        }

        // Smart Delete for non-active items
        await this.prisma.$transaction(async (tx) => {
          await tx.orderAssignment.deleteMany({ where: { inventory_item_id: targetId } });
          await tx.configEvent.deleteMany({ where: { config_id: targetId, config_scope: 'PAID' } });
          await tx.accountingEntry.updateMany({
            where: { inventory_item_id: targetId },
            data: { inventory_item_id: null },
          });
          await tx.inventoryItem.delete({ where: { id: targetId } });
        });
      } else {
        const item = await this.prisma.trialConfig.findFirst({
          where: {
            OR: [
              { id },
              { folder_code: { equals: id, mode: 'insensitive' } },
              { admin_label: { equals: id, mode: 'insensitive' } },
            ],
          },
          include: {
            assignments: {
              include: { grant: { select: { status: true } } },
            },
          },
        });

        if (!item) throw new Error(`Item \`${id}\` introuvable dans le Trial Store.`);
        targetId = item.id;

        const activeAssignments = item.assignments.filter((a) =>
          ['PENDING', 'ACTIVE'].includes(a.grant.status),
        );

        if (activeAssignments.length > 0) {
          const error = new Error(`Ce trial est utilisé par ${activeAssignments.length} grant(s) actif(s).`);
          (error as any).code = 'P2003';
          throw error;
        }

        await this.prisma.$transaction(async (tx) => {
          await tx.trialAssignment.deleteMany({ where: { trial_config_id: targetId } });
          await tx.configEvent.deleteMany({ where: { config_id: targetId, config_scope: 'TRIAL' } });
          await tx.trialConfig.delete({ where: { id: targetId } });
        });
      }

      await this.prisma.adminEvent.create({
        data: {
          event_type: scope === 'paid' ? 'ADMIN_BOT_INVENTORY_DELETE_SUCCESS' : 'ADMIN_BOT_TRIAL_DELETE_SUCCESS',
          entity_type: scope === 'paid' ? 'INVENTORY' : 'TRIAL_CONFIG',
          entity_id: targetId,
          payload_json: {
            inputQuery: id,
            telegramUserId: ctx.from?.id?.toString() || null,
            timestamp: new Date().toISOString(),
          } as any,
        },
      });

      await ctx.reply(`🗑️ Item \`${id}\` supprimé avec succès.`, { parse_mode: 'Markdown' });
      await this.replyStockOverview(ctx);
    } catch (error: any) {
      if (error.code === 'P2003') {
        const detail = error.message.includes('lié à') || error.message.includes('utilisé par')
          ? error.message
          : "L'item est lié à des commandes ou attributions existantes.";

        await ctx.reply(
          `❌ *Suppression impossible*\n\n${detail}\n\nSouhaitez-vous forcer la suppression (Super Delete) ? Cela réinitialisera les commandes liées en attente de stock.`,
          { parse_mode: 'Markdown', ...getDeleteFailedKeyboard(scope, id) },
        );
      } else if (error.message.includes('introuvable')) {
        await ctx.reply(`❓ *ID Inconnu* :\n${error.message}`, { parse_mode: 'Markdown' });
      } else {
        this.logger.error(`Delete failed for ${id}`, error);
        await ctx.reply(`❌ Erreur technique : ${error.message}`);
      }
    }
  }

  private async updateInventoryHealthFromCommand(
    ctx: any,
    healthStatus: 'EXPIRED' | 'DISABLED',
    defaultReason: string,
  ) {
    const parsed = parseInventoryActionCommand(ctx.message?.text || '');
    if (!parsed.inventoryItemId) {
      await ctx.reply(`Usage: ${ctx.message?.text?.split(/\s+/)[0] || '/command'} <id-ou-code> [raison]`);
      return;
    }

    let targetId = parsed.inventoryItemId;
    try {
      // Resolve ID first
      const item = await this.prisma.inventoryItem.findFirst({
        where: {
          OR: [
            { id: targetId },
            { folder_code: { equals: targetId, mode: 'insensitive' } },
            { admin_label: { equals: targetId, mode: 'insensitive' } },
          ],
        },
        select: { id: true },
      });

      if (item) {
        targetId = item.id;
      }
    } catch (e) {
      // Fallback to original ID if search fails
    }

    await this.executeInventoryHealthUpdate(ctx, targetId, healthStatus, parsed.reason || defaultReason);
  }

  private async handleDelete(ctx: any, scope: 'paid' | 'trial') {
    const parsed = parseInventoryActionCommand(ctx.message?.text || '');
    const id = parsed.inventoryItemId;

    if (!id) {
      await ctx.reply(`Usage: ${scope === 'paid' ? '/delete' : '/delete_trial'} <id>`);
      return;
    }

    await this.executeDirectDelete(ctx, scope, id);
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
      }), { parse_mode: 'Markdown' });
    } catch (error) {
      this.logger.error('Failed to build accounting summary', error as Error);
      await ctx.reply('Unable to build accounting summary right now.');
    }
  }

  private async replyFinanceDashboard(ctx: any, isUpdate = false) {
    const startOfDay = new Date();
    startOfDay.setHours(0, 0, 0, 0);

    const startOfMonth = new Date();
    startOfMonth.setDate(1);
    startOfMonth.setHours(0, 0, 0, 0);

    try {
      const [todayOrders, monthEntries] = await Promise.all([
        this.prisma.order.findMany({
          where: {
            status: { in: ['PAID', 'PENDING_FULFILLMENT', 'FULFILLED'] as any },
            paid_at: { gte: startOfDay },
          },
          select: { amount_rub: true },
        }),
        this.prisma.accountingEntry.findMany({
          where: { created_at: { gte: startOfMonth }, currency: 'RUB' },
          select: { type: true, amount: true },
        }),
      ]);

      const todayRevenue = todayOrders.reduce((sum, o) => sum + Number(o.amount_rub), 0);
      const totals = monthEntries.reduce(
        (acc, entry) => {
          const amount = Number(entry.amount);
          if (entry.type === AccountingEntryType.REVENUE) acc.revenue += amount;
          if (entry.type === AccountingEntryType.EXPENSE) acc.expense += amount;
          if (entry.type === AccountingEntryType.ADJUSTMENT) acc.adjustment += amount;
          return acc;
        },
        { revenue: 0, expense: 0, adjustment: 0 },
      );

      const message = formatFinanceDashboard({
        todayOrders: todayOrders.length,
        todayRevenue: todayRevenue.toFixed(2),
        monthRevenue: totals.revenue.toFixed(2),
        monthExpense: totals.expense.toFixed(2),
        monthProfit: (totals.revenue - totals.expense + totals.adjustment).toFixed(2),
      });

      if (isUpdate && ctx.callbackQuery) {
        await ctx.editMessageText(message, {
          parse_mode: 'Markdown',
          ...getFinanceDashboardKeyboard(),
        });
      } else {
        await ctx.reply(message, {
          parse_mode: 'Markdown',
          ...getFinanceDashboardKeyboard(),
        });
      }
    } catch (error) {
      this.logger.error('Failed to build finance dashboard', error as Error);
      await ctx.reply('Dashboard inaccessible pour le moment.');
    }
  }

  private async executeOrderRetry(ctx: any, orderRef: string) {
    try {
      const order = await this.prisma.order.findFirst({
        where: {
          order_ref: orderRef,
          status: { in: ['PAID', 'PENDING_FULFILLMENT'] as any },
        },
        select: { id: true, order_ref: true },
      });

      if (!order) {
        await ctx.reply(`Commande ${orderRef} introuvable ou déjà traitée.`);
        return;
      }

      const result = await firstValueFrom(
        this.inventoryClient.send({ cmd: 'fulfill_order' }, { orderId: order.id }),
      );

      const status = result?.pendingFulfillment ? '⏳ Toujours en attente (manque de stock)' : '✅ Traitée avec succès';
      await ctx.reply(`Relance \`${orderRef}\` : ${status}`, { parse_mode: 'Markdown' });
    } catch (error) {
      this.logger.error('Order retry failed', error as Error);
      await ctx.reply(`❌ Échec de la relance pour ${orderRef}.`);
    }
  }

  private async replyCockpitHub(ctx: any, edit: boolean) {
    const text = formatCockpitHub();
    const kb = cockpitHubKeyboard();
    if (edit && ctx.callbackQuery) {
      await ctx.editMessageText(text, { parse_mode: 'Markdown', ...kb });
    } else {
      await ctx.reply(text, { parse_mode: 'Markdown', ...kb });
    }
  }

  async sendAdminAlert(message: string) {
    if (!this.adminChatId || !this.bot) return;
    try {
      await this.bot.telegram.sendMessage(this.adminChatId, message, { parse_mode: 'Markdown' });
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
