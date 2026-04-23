import { Injectable } from '@nestjs/common';
import { PrismaService } from '@app/database';
import { DeliveryPayloadDto, DeliveryLanguage } from './dto/delivery-payload.dto';
import { DeliveryTemplateService } from './templates/delivery-template.service';
import { TelegramSenderService } from './telegram-sender.service';
import { EmailSenderService } from './email-sender.service';

@Injectable()
export class NotificationService {
  constructor(
    private readonly prisma: PrismaService,
    private readonly templateService: DeliveryTemplateService,
    private readonly telegramSender: TelegramSenderService,
    private readonly emailSender: EmailSenderService,
  ) {}

  async processPostPurchaseDelivery(payload: DeliveryPayloadDto) {
    const delivery = await this.ensureDeliveryRecord(payload.orderRef, payload.customerEmail);

    const adminMessage = this.templateService.buildAdminTelegramMessage(payload, 'DELIVERY_STARTED');
    const telegramNotified = await this.telegramSender.sendAdminMessage(adminMessage, payload.orderRef, payload.vpnLink);

    const rendered = this.templateService.renderEmail(payload);

    try {
      await this.emailSender.sendDeliveryEmail(payload.customerEmail, rendered.subject, rendered.body);

      await this.prisma.delivery.update({
        where: { id: delivery.id },
        data: {
          telegram_notified: telegramNotified,
          email_sent: true,
          sent_at: new Date(),
          delivery_mode: 'EMAIL',
          notes: JSON.stringify({
            orderRef: payload.orderRef,
            language: rendered.language,
            status: 'EMAIL_SENT',
            vpnLink: payload.vpnLink,
            updatedAt: new Date().toISOString(),
          }),
        },
      });

      await this.telegramSender.sendDeliveryEvent(payload.orderRef, 'EMAIL_SENT');

      return {
        success: true,
        orderRef: payload.orderRef,
        telegramNotified,
        emailSent: true,
      };
    } catch (error) {
      const message = (error as Error).message;

      await this.prisma.delivery.update({
        where: { id: delivery.id },
        data: {
          telegram_notified: telegramNotified,
          email_sent: false,
          delivery_mode: 'EMAIL',
          notes: JSON.stringify({
            orderRef: payload.orderRef,
            language: rendered.language,
            status: 'EMAIL_FAILED',
            error: message,
            updatedAt: new Date().toISOString(),
          }),
        },
      });

      await this.telegramSender.sendDeliveryEvent(payload.orderRef, 'EMAIL_FAILED', message);

      return {
        success: false,
        orderRef: payload.orderRef,
        telegramNotified,
        emailSent: false,
        error: message,
      };
    }
  }

  async resendDeliveryEmail(orderRef: string, language?: DeliveryLanguage) {
    const order = await this.prisma.order.findUnique({
      where: { order_ref: orderRef },
      include: {
        customer: true,
        plan: true,
        assignments: {
          include: { inventory_item: true },
        },
      },
    });

    if (!order || !order.customer?.email || order.assignments.length === 0) {
      return { success: false, error: 'Order, customer email, or assigned vpn link not found' };
    }

    const payload: DeliveryPayloadDto = {
      orderRef,
      customerEmail: order.customer.email,
      customerPhone: order.customer.phone || undefined,
      planCode: order.plan.code,
      planLabel: order.plan.name,
      vpnLink: order.assignments[0].inventory_item.raw_config,
      expiryLabel: order.plan.duration_label,
      customerLanguage: language || 'ru',
    };

    return this.processPostPurchaseDelivery(payload);
  }

  async getDeliveryStatus(orderRef: string) {
    const order = await this.prisma.order.findUnique({
      where: { order_ref: orderRef },
      include: {
        deliveries: {
          orderBy: { sent_at: 'desc' },
          take: 1,
        },
      },
    });

    if (!order) {
      return { success: false, error: 'Order not found' };
    }

    const latestDelivery = order.deliveries[0] || null;

    return {
      success: true,
      orderRef,
      delivery: latestDelivery,
    };
  }

  async sendManualPaymentReviewEmail(params: {
    to: string;
    orderRef: string;
    planName: string;
    approved: boolean;
    reason?: string;
  }) {
    const subject = params.approved
      ? `SWIMVPN+ payment approved: ${params.orderRef}`
      : `SWIMVPN+ payment update: ${params.orderRef}`;

    const body = params.approved
      ? [
          'Hello,',
          '',
          `Your payment for ${params.planName} has been approved.`,
          'Your VPN access will be delivered shortly.',
          '',
          `Order: ${params.orderRef}`,
          '',
          'SWIMVPN+ Team',
        ].join('\n')
      : [
          'Hello,',
          '',
          `We reviewed your payment proof for ${params.planName}.`,
          'We could not confirm the transfer at this time.',
          params.reason ? `Reason: ${params.reason}` : null,
          '',
          `Order: ${params.orderRef}`,
          'Please contact support if you believe this is a mistake.',
          '',
          'SWIMVPN+ Team',
        ]
          .filter(Boolean)
          .join('\n');

    await this.emailSender.sendTextEmail(params.to, subject, body);

    return {
      success: true,
      orderRef: params.orderRef,
      approved: params.approved,
    };
  }

  private async ensureDeliveryRecord(orderRef: string, customerEmail: string) {
    const order = await this.prisma.order.findUnique({
      where: { order_ref: orderRef },
      include: { deliveries: true },
    });

    if (!order) {
      throw new Error(`Order ${orderRef} not found`);
    }

    if (order.deliveries.length > 0) {
      return order.deliveries[0];
    }

    return this.prisma.delivery.create({
      data: {
        order_id: order.id,
        customer_email: customerEmail,
        delivery_mode: 'EMAIL',
      },
    });
  }
}
