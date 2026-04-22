import { Injectable } from '@nestjs/common';
import { ConfigService } from '@nestjs/config';
import { Resend } from 'resend';

@Injectable()
export class EmailSenderService {
  private readonly resend: Resend | null;
  private readonly fromEmail: string;
  private readonly fromName: string;

  constructor(private readonly configService: ConfigService) {
    const resendApiKey = this.configService.get<string>('RESEND_API_KEY');
    this.fromEmail = this.configService.get<string>('MAILER_FROM_EMAIL', 'support@swimvpn.pro');
    this.fromName = this.configService.get<string>('MAILER_FROM_NAME', 'SWIMVPN+ Support');

    if (!resendApiKey) {
      this.resend = null;
      return;
    }

    this.resend = new Resend(resendApiKey);
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
}
