import { Injectable } from '@nestjs/common';
import { ConfigService } from '@nestjs/config';
import { Resend } from 'resend';

@Injectable()
export class EmailSenderService {
  private readonly resend: Resend | null;
  private readonly fromEmail: string;
  private readonly fromName: string;

  constructor(private readonly configService: ConfigService) {
    const resendApiKey = this.configService.get<string>('RESEND_API_KEY')?.trim();
    this.fromEmail = this.configService.get<string>('MAILER_FROM_EMAIL', 'support@swimvpn.pro').trim();
    this.fromName = this.configService.get<string>('MAILER_FROM_NAME', 'SWIMVPN+ Support').trim();

    if (!resendApiKey) {
      this.resend = null;
      return;
    }

    this.resend = new Resend(resendApiKey);
  }

  getTransportStatus() {
    const apiKeyConfigured = this.resend !== null;
    const fromEmailPresent = this.fromEmail.length > 0;
    const fromEmailLooksValid = /^[^\s@]+@[^\s@]+\.[^\s@]+$/.test(this.fromEmail);

    return {
      provider: 'resend',
      apiKeyConfigured,
      fromEmailPresent,
      fromEmailLooksValid,
      ready: apiKeyConfigured && fromEmailPresent && fromEmailLooksValid,
      fromEmail: this.fromEmail,
      fromName: this.fromName,
    };
  }

  async sendDeliveryEmail(to: string, subject: string, body: string): Promise<void> {
    if (!this.resend) {
      throw new Error('Resend transport is not configured');
    }

    const { error } = await this.resend.emails.send({
      from: `${this.fromName} <${this.fromEmail}>`,
      to,
      subject,
      text: body,
    });

    if (error) {
      throw new Error(`Resend send failed: ${error.message}`);
    }
  }

  async sendTextEmail(to: string, subject: string, body: string): Promise<void> {
    return this.sendDeliveryEmail(to, subject, body);
  }

  async sendOptInConfirmation(to: string, confirmUrl: string, locale?: string): Promise<void> {
    const lang = (locale || 'en').slice(0, 2).toLowerCase();
    const copy =
      lang === 'fr'
        ? {
            subject: 'Confirme ton inscription à SWIMVPN',
            body: `Merci de ton intérêt pour SWIMVPN.\n\nConfirme ton adresse en cliquant ici :\n${confirmUrl}\n\nSi tu n'es pas à l'origine de cette demande, ignore simplement ce message.`,
          }
        : lang === 'ru'
          ? {
              subject: 'Подтвердите подписку на SWIMVPN',
              body: `Спасибо за интерес к SWIMVPN.\n\nПодтвердите адрес, нажав здесь:\n${confirmUrl}\n\nЕсли это были не вы — просто игнорируйте это письмо.`,
            }
          : {
              subject: 'Confirm your SWIMVPN subscription',
              body: `Thanks for your interest in SWIMVPN.\n\nConfirm your address by clicking here:\n${confirmUrl}\n\nIf you didn't request this, just ignore this email.`,
            };
    return this.sendDeliveryEmail(to, copy.subject, copy.body);
  }
}
