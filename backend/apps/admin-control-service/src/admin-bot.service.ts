import { Injectable, OnModuleInit, Logger, Inject } from '@nestjs/common';
import { ConfigService } from '@nestjs/config';
import { ClientProxy } from '@nestjs/microservices';
import { PrismaService } from '@app/database';
import { Telegraf, Context } from 'telegraf';

@Injectable()
export class AdminBotService implements OnModuleInit {
  private readonly logger = new Logger(AdminBotService.name);
  private bot: Telegraf;

  constructor(
    private configService: ConfigService,
    private prisma: PrismaService,
    @Inject('INVENTORY_SERVICE') private inventoryClient: ClientProxy,
    @Inject('CUSTOMER_SERVICE') private customerClient: ClientProxy,
  ) {
    const token = this.configService.get<string>('TELEGRAM_BOT_TOKEN');
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
      this.logger.log('Telegram Bot started successfully');
    });
  }

  private setupMiddleware() {
    const adminId = this.configService.get<string>('ADMIN_CHAT_ID');

    this.bot.use(async (ctx, next) => {
      const chatId = ctx.from?.id.toString();
      if (chatId !== adminId) {
        this.logger.warn(`Unauthorized access attempt from ID: ${chatId}`);
        await ctx.reply('⛔ Access Denied. You are not the administrator.');
        return;
      }
      return next();
    });
  }

  private setupCommands() {
    this.bot.start((ctx) => {
      ctx.reply('👋 Welcome Admin! I am your SWIMVPN+ Control Bot.\n\nCommands:\n/status - System status\n/orders - Recent orders\n/users - User statistics\n/import - Import VPN configs\n/manual_fulfill [UserNumber] - Manually give access');
    });

    this.bot.command('status', async (ctx) => {
      await ctx.reply('📊 System Status: ONLINE\n- Gateway: OK\n- Inventory: OK\n- DB: Connected');
    });

    this.bot.command('orders', async (ctx) => {
      ctx.reply('⏳ Fetching recent orders...');
      try {
        const recentOrders = await this.prisma.order.findMany({
          take: 5,
          orderBy: { created_at: 'desc' },
          include: { customer: true, plan: true }
        });

        if (recentOrders.length === 0) {
          return ctx.reply('📭 No orders found.');
        }

        const msg = recentOrders.map(o =>
          `🔹 *${o.order_ref}*\n👤 ${o.customer.public_id}\n📦 ${o.plan.code}\n💰 ${o.amount_rub} RUB\n📅 ${o.created_at.toLocaleString()}\n✅ Status: ${o.status}`
        ).join('\n\n');

        ctx.reply(`📑 *Recent 5 Orders*:\n\n${msg}`, { parse_mode: 'Markdown' });
      } catch (e) {
        ctx.reply('❌ Error fetching orders.');
      }
    });

    this.bot.command('users', async (ctx) => {
      ctx.reply('⏳ Fetching user stats...');
      try {
        const totalUsers = await this.prisma.customer.count();
        const activeUsers = await this.prisma.order.count({
          where: { status: 'FULFILLED' },
          distinct: ['customer_id']
        });

        ctx.reply(`👥 *User Statistics*:\n\nTotal Registered: ${totalUsers}\nActive Subscriptions: ${activeUsers}`, { parse_mode: 'Markdown' });
      } catch (e) {
        ctx.reply('❌ Error fetching user stats.');
      }
    });

    this.bot.command('manual_fulfill', async (ctx) => {
      const parts = ctx.message.text.split(' ');
      if (parts.length < 2) return ctx.reply('❌ Usage: /manual_fulfill [UserNumber]');

      const userNumber = parts[1];
      ctx.reply(`⏳ Attempting manual fulfillment for ${userNumber}...`);

      this.customerClient.send({ cmd: 'start_trial' }, { deviceId: `MANUAL-${userNumber}` }).subscribe({
        next: (res) => ctx.reply(`✅ Manual fulfillment (Trial/Free) successful for ${userNumber}`),
        error: (err) => ctx.reply(`❌ Failed: ${err.message}`)
      });
    });

    this.bot.command('healthcheck', async (ctx) => {
      ctx.reply('🔍 Starting health check for all active nodes...');
      this.inventoryClient.send({ cmd: 'trigger_health_check' }, {}).subscribe({
        next: (result) => ctx.reply(`✅ Health check completed. Results: ${JSON.stringify(result)}`),
        error: (err) => ctx.reply('❌ Health check failed.')
      });
    });

    this.bot.command('import', (ctx) => {
      ctx.reply('📝 To import a config, use the following format:\n/add [category] [config]\n\nCategories: week, month, quarter\n\nExample:\n/add month vless://...');
    });

    this.bot.command('add', async (ctx) => {
      const text = ctx.message.text;
      const parts = text.split(/\s+/);

      if (parts.length < 3) {
        return ctx.reply('❌ Invalid format. Use: /add [category] [config]');
      }

      const categoryRaw = parts[1].toUpperCase();
      const config = parts.slice(2).join(' ');

      // Validation basique de la catégorie
      const validCategories = ['WEEK', 'MONTH', 'QUARTER'];
      if (!validCategories.includes(categoryRaw)) {
        return ctx.reply(`❌ Invalid category. Must be one of: ${validCategories.join(', ')}`);
      }

      ctx.reply('⏳ Processing import...');

      try {
        this.inventoryClient.send({ cmd: 'import_configs' }, {
          category: categoryRaw,
          configs: [config],
          batchName: `Telegram Import ${new Date().toISOString()}`
        }).subscribe({
          next: (result) => {
            ctx.reply(`✅ Success! Config imported to ${categoryRaw} inventory.`);
          },
          error: (err) => {
            this.logger.error('Failed to import config via TCP', err);
            ctx.reply('❌ Error: Inventory service rejected the import.');
          }
        });
      } catch (error) {
        ctx.reply('❌ Error: Could not reach Inventory service.');
      }
    });

    this.bot.catch((err, ctx) => {
      this.logger.error(`Telegraf Error for ${ctx.updateType}:`, err);
    });
  }

  async sendAdminAlert(message: string) {
    const adminId = this.configService.get<string>('ADMIN_CHAT_ID');
    if (!adminId || adminId === '0' || !this.bot) return;
    try {
      await this.bot.telegram.sendMessage(adminId, message, { parse_mode: 'Markdown' });
    } catch (e) {
      this.logger.error('Failed to send admin alert', e);
    }
  }

  onModuleDestroy() {
    if (this.bot) {
      this.bot.stop('SIGINT');
    }
  }
}
