import { Injectable, Logger } from '@nestjs/common';
import { ConfigService } from '@nestjs/config';
import { Telegraf, Markup } from 'telegraf';
import { selectNotificationSenderBotToken } from './telegram-token-routing';

@Injectable()
export class TelegramSenderService {
  private readonly logger = new Logger(TelegramSenderService.name);
  private readonly bot?: Telegraf;
  private readonly adminChatId?: string;

  constructor(private readonly configService: ConfigService) {
    const token = selectNotificationSenderBotToken({
      paymentBotToken: this.configService.get<string>('PAYMENT_BOT_TOKEN'),
      notificationBotToken: this.configService.get<string>('NOTIFICATION_BOT_TOKEN'),
      telegramBotToken: this.configService.get<string>('TELEGRAM_BOT_TOKEN'),
    });
    this.adminChatId = this.configService.get<string>('ADMIN_CHAT_ID');

    if (!token || !this.adminChatId) {
      this.logger.warn('Telegram sender disabled: token or ADMIN_CHAT_ID is missing');
      return;
    }

    this.bot = new Telegraf(token);
  }

  async sendAdminMessage(message: string, orderRef: string, vpnLink: string): Promise<boolean> {
    if (!this.bot || !this.adminChatId) {
      return false;
    }

    try {
      await this.bot.telegram.sendMessage(
        this.adminChatId,
        message,
        Markup.inlineKeyboard([
          [Markup.button.callback(`resend email ${orderRef}`, `resend:${orderRef}`)],
          [Markup.button.callback('copy vpn link', `copy:${orderRef}`)],
          [Markup.button.callback(`mark delivered ${orderRef}`, `mark:${orderRef}`)],
        ]),
      );
      return true;
    } catch (error) {
      this.logger.warn(`Telegram send failed for ${orderRef}: ${(error as Error).message}`);
      return false;
    }
  }

  async sendDeliveryEvent(orderRef: string, event: 'EMAIL_SENT' | 'EMAIL_FAILED', errorMessage?: string) {
    if (!this.bot || !this.adminChatId) {
      return;
    }

    const base = event === 'EMAIL_SENT'
      ? `EMAIL_SENT: ${orderRef}`
      : `EMAIL_FAILED: ${orderRef} (${errorMessage || 'unknown'})`;

    try {
      await this.bot.telegram.sendMessage(this.adminChatId, base);
    } catch (error) {
      this.logger.warn(`Telegram event send failed for ${orderRef}: ${(error as Error).message}`);
    }
  }
}
