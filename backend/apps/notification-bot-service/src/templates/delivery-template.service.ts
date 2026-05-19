import { Injectable } from '@nestjs/common';
import { DeliveryLanguage, DeliveryPayloadDto } from '../dto/delivery-payload.dto';

export interface EmailTemplateResult {
  subject: string;
  body: string;
  language: DeliveryLanguage;
}

@Injectable()
export class DeliveryTemplateService {
  getLanguage(preferred?: string | null): DeliveryLanguage {
    return preferred?.toLowerCase() === 'en' ? 'en' : 'ru';
  }

  renderEmail(payload: DeliveryPayloadDto, preferred?: string | null): EmailTemplateResult {
    const language = this.getLanguage(preferred ?? payload.customerLanguage);
    if (language === 'en') {
      return {
        language,
        subject: 'Your SWIMVPN+ Access',
        body: this.englishTemplate(payload),
      };
    }

    return {
      language,
      subject: 'Ваш доступ SWIMVPN+',
      body: this.russianTemplate(payload),
    };
  }

  buildAdminTelegramMessage(payload: DeliveryPayloadDto, status: string): string {
    return [
      'SWIMVPN+ DELIVERY',
      `Order: ${payload.orderRef}`,
      `Email: ${payload.customerEmail || '-'}`,
      `Phone: ${payload.customerPhone || '-'}`,
      `Plan: ${payload.planCode} (${payload.planLabel})`,
      `Expiry: ${payload.expiryLabel || '-'}`,
      `Status: ${status}`,
    ].join('\n');
  }

  private russianTemplate(payload: DeliveryPayloadDto): string {
    return [
      'Здравствуйте,',
      '',
      'Спасибо за покупку SWIMVPN+.',
      '',
      'Ваш VPN-доступ готов.',
      '',
      'Ссылка для подключения:',
      payload.vpnLink,
      '',
      'Как подключиться:',
      '',
      '1. Откройте приложение SWIMVPN+',
      '2. Перейдите в раздел "Добавить конфигурацию" или "Импорт"',
      '3. Вставьте или откройте VPN-ссылку',
      '4. Сохраните конфигурацию',
      '5. Нажмите "Подключиться"',
      '',
      `Тариф: ${payload.planLabel}`,
      `Срок действия: ${payload.expiryLabel || '-'}`,
      '',
      'Если возникнут вопросы, напишите в поддержку:',
      'support@swimvpn.pro',
      '',
      'С уважением,',
      'Команда SWIMVPN+',
    ].join('\n');
  }

  private englishTemplate(payload: DeliveryPayloadDto): string {
    return [
      'Hello,',
      '',
      'Thank you for purchasing SWIMVPN+.',
      '',
      'Your VPN access is ready.',
      '',
      'Connection link:',
      payload.vpnLink,
      '',
      'How to connect:',
      '',
      '1. Open the SWIMVPN+ app',
      '2. Go to "Add Configuration" or "Import"',
      '3. Paste or open the VPN link',
      '4. Save the configuration',
      '5. Tap "Connect"',
      '',
      `Plan: ${payload.planLabel}`,
      `Expiry: ${payload.expiryLabel || '-'}`,
      '',
      'If you have any issue, contact support:',
      'support@swimvpn.pro',
      '',
      'Best regards,',
      'SWIMVPN+ Team',
    ].join('\n');
  }
}
