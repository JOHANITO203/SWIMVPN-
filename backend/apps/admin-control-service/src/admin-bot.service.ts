import { Injectable, OnModuleDestroy, OnModuleInit, Logger, Inject } from '@nestjs/common';
import { ConfigService } from '@nestjs/config';
import { ClientProxy } from '@nestjs/microservices';
import { PrismaService } from '@app/database';
import { DEFAULT_RESALE_SLOT_CAP, DEFAULT_SUPPLIER_DEVICE_LIMIT } from '@app/contracts';
import { Telegraf } from 'telegraf';
import { firstValueFrom } from 'rxjs';
import { isAdminBotAuthorized, parseAdminUserIds } from './admin-bot-auth';
import {
  formatImportResult,
  formatInventoryOverview,
  mapBotPlanInputToCategory,
} from './admin-bot.formatter';

@Injectable()
export class AdminBotService implements OnModuleInit, OnModuleDestroy {
  private readonly logger = new Logger(AdminBotService.name);
  private readonly bot?: Telegraf;
  private readonly adminChatId?: string;
  private readonly adminUserIds: string[];

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

    this.bot.command('import', async (ctx) => {
      await ctx.reply([
        'Import a supplier config into the boutique inventory:',
        '',
        '/add basic <config-or-subscription-url>',
        '/add premium <config-or-subscription-url>',
        '/add platinum <config-or-subscription-url>',
        '',
        'Each supplier link is capped at 2 customer orders.',
        'Raw config is preserved in PostgreSQL.',
      ].join('\n'));
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
              telegramUserId: ctx.from?.id?.toString() || null,
              maxResaleSlots: DEFAULT_RESALE_SLOT_CAP,
              supplierDeviceLimit: DEFAULT_SUPPLIER_DEVICE_LIMIT,
              result,
              createdAt: new Date().toISOString(),
            } as any,
          },
        });

        await ctx.reply(formatImportResult(category, result));
      } catch (error) {
        this.logger.error('Failed to import config via admin bot', error as Error);
        await ctx.reply('Inventory service rejected the import. Check parser warnings or service logs.');
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
      '/add basic|premium|platinum <config> - import supplier config',
      '/orders - recent orders',
      '/users - customer statistics',
      '/healthcheck - run inventory health check',
      '/help - show this menu',
    ].join('\n');
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
