import { Injectable, Logger, OnModuleInit } from '@nestjs/common';
import { ConfigService } from '@nestjs/config';
import { Markup, Telegraf } from 'telegraf';
import {
  SUPPORT_COPY,
  SUPPORT_TOPICS,
  SupportLanguage,
  SupportTopicKey,
  getTopicByKey,
  resolveSupportLanguage,
} from './admin-support-bot.templates';
import {
  buildTicketId,
  extractOptionalFields,
  formatAdminSupportReportMessage,
  formatEscalationRelayMessage,
} from './admin-support-bot.formatter';

type PendingState = {
  topicKey: SupportTopicKey;
  language: SupportLanguage;
  stage: 'awaitingMessage' | 'awaitingEmail';
  userMessage?: string;
  phone?: string;
  orderRef?: string;
};

@Injectable()
export class AdminSupportBotService implements OnModuleInit {
  private readonly logger = new Logger(AdminSupportBotService.name);
  private readonly bot?: Telegraf;
  private readonly supportChatId: string;
  private readonly reportChatId?: string;
  private readonly defaultLanguage: SupportLanguage;
  private readonly fallbackLanguage: SupportLanguage;

  private readonly pending = new Map<string, PendingState>();
  private readonly userLanguage = new Map<string, SupportLanguage>();
  private readonly rateLimit = new Map<string, number[]>();

  constructor(private readonly configService: ConfigService) {
    const token = this.configService.get<string>('ADMIN_SUPPORT_BOT_TOKEN');
    this.supportChatId =
      this.configService.get<string>('ADMIN_SUPPORT_CHAT_ID') || '-1003912107958';
    this.reportChatId = this.configService.get<string>('ADMIN_SUPPORT_REPORT_CHAT_ID') || undefined;
    this.defaultLanguage = resolveSupportLanguage(
      this.configService.get<string>('ADMIN_SUPPORT_DEFAULT_LANGUAGE'),
      'ru',
      'en',
    );
    this.fallbackLanguage = resolveSupportLanguage(
      this.configService.get<string>('ADMIN_SUPPORT_FALLBACK_LANGUAGE'),
      'en',
      'ru',
    );

    if (!token) {
      this.logger.warn('ADMIN_SUPPORT_BOT_TOKEN is missing: admin support bot disabled');
      return;
    }

    this.bot = new Telegraf(token);
  }

  onModuleInit() {
    if (!this.bot) return;

    this.bot.start(async (ctx) => {
      const userKey = String(ctx.from?.id || 'unknown');
      const language = this.resolveUserLanguage(userKey);
      await ctx.reply(
        SUPPORT_COPY[language].welcome,
        Markup.inlineKeyboard([
          [
            Markup.button.callback('Русский', 'lang:ru'),
            Markup.button.callback('English', 'lang:en'),
          ],
        ]),
      );
      await this.sendTopicMenu(ctx, language);
    });

    this.bot.action(/lang:(ru|en)/, async (ctx) => {
      const selected = ctx.match[1] as SupportLanguage;
      const userKey = String(ctx.from?.id || 'unknown');
      this.userLanguage.set(userKey, selected);
      await ctx.answerCbQuery(selected === 'ru' ? 'Язык: Русский' : 'Language: English');
      await this.sendTopicMenu(ctx, selected);
    });

    this.bot.action(/topic:(.+)/, async (ctx) => {
      const topicKey = ctx.match[1] as SupportTopicKey;
      const topic = getTopicByKey(topicKey);
      if (!topic) {
        await ctx.answerCbQuery('Unknown topic');
        return;
      }

      const userKey = String(ctx.from?.id || 'unknown');
      const language = this.resolveUserLanguage(userKey);
      this.pending.set(userKey, { topicKey, language, stage: 'awaitingMessage' });

      const answer = language === 'ru' ? topic.answerRu : topic.answerEn;
      await ctx.answerCbQuery();
      await ctx.reply(
        answer,
        Markup.inlineKeyboard([
          [
            Markup.button.callback(SUPPORT_COPY[language].unresolved, `unresolved:${topic.key}`),
            Markup.button.callback(SUPPORT_COPY[language].contact, `contact:${topic.key}`),
          ],
        ]),
      );
    });

    this.bot.action(/unresolved:(.+)/, async (ctx) => {
      const topicKey = ctx.match[1] as SupportTopicKey;
      const topic = getTopicByKey(topicKey);
      if (!topic) return ctx.answerCbQuery('Unknown topic');

      const userKey = String(ctx.from?.id || 'unknown');
      const language = this.resolveUserLanguage(userKey);
      this.pending.set(userKey, { topicKey, language, stage: 'awaitingMessage' });

      await ctx.answerCbQuery();
      await ctx.reply(SUPPORT_COPY[language].askMessage);
    });

    this.bot.action(/contact:(.+)/, async (ctx) => {
      const topicKey = ctx.match[1] as SupportTopicKey;
      const topic = getTopicByKey(topicKey);
      if (!topic) return ctx.answerCbQuery('Unknown topic');

      const userKey = String(ctx.from?.id || 'unknown');
      const language = this.resolveUserLanguage(userKey);
      this.pending.set(userKey, { topicKey, language, stage: 'awaitingMessage' });

      await ctx.answerCbQuery();
      await ctx.reply(SUPPORT_COPY[language].askMessage);
    });

    this.bot.action('change_language', async (ctx) => {
      await ctx.answerCbQuery();
      await ctx.reply(
        'Language / Язык',
        Markup.inlineKeyboard([
          [
            Markup.button.callback('Русский', 'lang:ru'),
            Markup.button.callback('English', 'lang:en'),
          ],
        ]),
      );
    });

    this.bot.on('text', async (ctx) => {
      const text = ctx.message.text?.trim() || '';
      const userKey = String(ctx.from?.id || 'unknown');
      const state = this.pending.get(userKey);
      const language = this.resolveUserLanguage(userKey);

      if (!state) {
        if (text.startsWith('/')) {
          return;
        }
        await this.sendTopicMenu(ctx, language);
        return;
      }

      if (state.stage === 'awaitingMessage') {
        if (text.length < 1 || text.length > 500) {
          await ctx.reply(SUPPORT_COPY[language].invalidMessage);
          return;
        }

        if (!this.consumeRateLimit(userKey)) {
          await ctx.reply(SUPPORT_COPY[language].rateLimited);
          return;
        }

        const optional = extractOptionalFields(text);
        this.pending.set(userKey, {
          ...state,
          language,
          stage: 'awaitingEmail',
          userMessage: text,
          phone: optional.phone,
          orderRef: optional.orderRef,
        });
        await ctx.reply(SUPPORT_COPY[language].askEmail);
        return;
      }

      if (!this.isValidEmail(text)) {
        await ctx.reply(SUPPORT_COPY[language].invalidEmail);
        return;
      }

      const topic = getTopicByKey(state.topicKey);
      if (!topic) {
        await ctx.reply('Topic not found');
        this.pending.delete(userKey);
        return;
      }

      const ticketId = buildTicketId();
      const timestampIso = new Date().toISOString();
      const relay = formatEscalationRelayMessage({
        ticketId,
        topic,
        userMessage: state.userMessage || '',
        timestampIso,
        language,
        email: text,
        phone: state.phone,
        orderRef: state.orderRef,
        telegramUserId: userKey,
        telegramUsername: ctx.from?.username,
      });
      const report = formatAdminSupportReportMessage({
        ticketId,
        topic,
        userMessage: state.userMessage || '',
        timestampIso,
        language,
        email: text,
        phone: state.phone,
        orderRef: state.orderRef,
        telegramUserId: userKey,
        telegramUsername: ctx.from?.username,
      });

      try {
        await this.bot!.telegram.sendMessage(this.supportChatId, relay);
        if (this.reportChatId) {
          await this.bot!.telegram.sendMessage(this.reportChatId, report);
        }
      } catch (error) {
        this.logger.error(`Failed to relay support case: ${(error as Error).message}`);
        await ctx.reply(language === 'ru' ? 'Не удалось отправить запрос. Попробуйте позже.' : 'Failed to send request. Please try later.');
        return;
      }

      this.pending.delete(userKey);
      await ctx.reply(SUPPORT_COPY[language].sent(ticketId, text));
      await this.sendTopicMenu(ctx, language);
    });

    this.bot.catch((error) => {
      this.logger.warn(`Admin support bot error: ${(error as Error).message}`);
    });

    this.bot.launch().then(() => {
      this.logger.log('Admin support bot started');
    });
  }

  private resolveUserLanguage(userKey: string): SupportLanguage {
    return this.userLanguage.get(userKey) || this.defaultLanguage || this.fallbackLanguage;
  }

  private async sendTopicMenu(ctx: any, language: SupportLanguage) {
    const buttons = SUPPORT_TOPICS.map((topic) => [
      Markup.button.callback(
        language === 'ru' ? topic.labelRu : topic.labelEn,
        `topic:${topic.key}`,
      ),
    ]);
    buttons.push([Markup.button.callback(SUPPORT_COPY[language].changeLanguage, 'change_language')]);

    await ctx.reply(SUPPORT_COPY[language].chooseTopic, Markup.inlineKeyboard(buttons));
  }

  private consumeRateLimit(userKey: string): boolean {
    const now = Date.now();
    const windowMs = 10 * 60 * 1000;
    const maxRequests = 3;

    const records = (this.rateLimit.get(userKey) || []).filter((t) => now - t < windowMs);
    if (records.length >= maxRequests) {
      this.rateLimit.set(userKey, records);
      return false;
    }

    records.push(now);
    this.rateLimit.set(userKey, records);
    return true;
  }

  private isValidEmail(value: string): boolean {
    return /^[A-Z0-9._%+-]+@[A-Z0-9.-]+\.[A-Z]{2,}$/i.test(value);
  }
}
