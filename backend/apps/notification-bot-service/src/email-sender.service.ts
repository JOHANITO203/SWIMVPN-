import { Injectable } from '@nestjs/common';
import { ConfigService } from '@nestjs/config';
import * as nodemailer from 'nodemailer';

@Injectable()
export class EmailSenderService {
  private readonly transporter;

  constructor(private readonly configService: ConfigService) {
    const smtpHost = this.configService.get<string>('SMTP_HOST');
    const smtpPort = Number(this.configService.get<string>('SMTP_PORT') || '587');
    const smtpUser = this.configService.get<string>('SMTP_USER');
    const smtpPass = this.configService.get<string>('SMTP_PASS');

    if (!smtpHost || !smtpUser || !smtpPass) {
      this.transporter = null;
      return;
    }

    this.transporter = nodemailer.createTransport({
      host: smtpHost,
      port: smtpPort,
      secure: smtpPort === 465,
      auth: {
        user: smtpUser,
        pass: smtpPass,
      },
    });
  }

  async sendDeliveryEmail(to: string, subject: string, body: string): Promise<void> {
    if (!this.transporter) {
      throw new Error('SMTP transport is not configured');
    }

    await this.transporter.sendMail({
      from: 'SWIMVPN+ Support <support@swimvpn.pro>',
      to,
      subject,
      text: body,
    });
  }
}
