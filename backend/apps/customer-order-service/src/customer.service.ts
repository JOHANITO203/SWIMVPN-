import { Injectable, Inject } from '@nestjs/common';
import { ClientProxy, RpcException } from '@nestjs/microservices';
import { PrismaService } from '@app/database';
import { firstValueFrom } from 'rxjs';
import { AssignmentAccessStatus, OrderStatus } from '@prisma/client';
import {
  StartTrialDto,
  CreateOrderDto,
  BootstrapAccessDto,
  ActivateTrialDto,
  CreateCheckoutDto,
  getPublicPlanName,
  getPlanSlotCount,
  ReportUsageDto,
} from '@app/contracts';
import { CryptoPayService } from './crypto-pay.service';

@Injectable()
export class CustomerService {
  private static readonly TRIAL_DURATION_MS = 3 * 24 * 60 * 60 * 1000;
  private static readonly TRIAL_QUOTA_LABEL = '5 GB';

  constructor(
    private readonly prisma: PrismaService,
    @Inject('INVENTORY_SERVICE') private readonly inventoryClient: ClientProxy,
    @Inject('VPN_CONFIG_SERVICE') private readonly vpnConfigClient: ClientProxy,
    private readonly cryptoPayService: CryptoPayService,
  ) {}

  async bootstrapAccess(data: BootstrapAccessDto) {
    const customer = await this.findOrCreateCustomerByDevice(data.deviceId);
    const profile = await this.getProfile(customer.public_id);
    const hasActiveAccess = profile.status === 'ACTIVE' && !!profile.subscriptionUrl;

    return {
      userNumber: customer.public_id,
      email: customer.email,
      phone: customer.phone,
      trialEligible: await this.isTrialEligible(customer, customer.email, customer.phone),
      profileCompletionRequired: !customer.email || !customer.phone,
      hasActiveAccess,
      profile: hasActiveAccess ? profile : null,
    };
  }

  async createOrder(data: CreateOrderDto) {
    const checkout = await this.preparePendingOrder({
      email: data.email,
      phone: data.phone,
      planId: data.planId,
    });

    return checkout.order;
  }

  async createCheckout(data: CreateCheckoutDto) {
    try {
      const checkout = await this.preparePendingOrder({
        email: data.email,
        phone: data.phone,
        planId: data.planId,
      });

      if (data.paymentMethod === 'CRYPTO') {
        const invoice = await this.cryptoPayService.createInvoice({
          amountRub: checkout.plan.price_rub.toString(),
          orderRef: checkout.order.order_ref,
          planLabel: `${checkout.plan.name} - ${checkout.plan.duration_label} - ${checkout.plan.quota_label}`,
          asset: data.cryptoAsset,
        });

        await this.prisma.order.update({
          where: { id: checkout.order.id },
          data: {
            payment_ref: `CRYPTO_INVOICE:${invoice.invoice_id}`,
          },
        });

        return {
          orderRef: checkout.order.order_ref,
          status: checkout.order.status,
          amountRub: checkout.plan.price_rub.toString(),
          paymentMethod: 'CRYPTO',
          redirectUrl: invoice.bot_invoice_url || invoice.mini_app_invoice_url || invoice.web_app_invoice_url || null,
          message: 'Crypto invoice created',
        };
      }

      const paymentBotUsername = process.env.PAYMENT_BOT_USERNAME?.trim();
      if (!paymentBotUsername) {
        this.fail('Manual payment bot username is not configured');
      }

      await this.prisma.order.update({
        where: { id: checkout.order.id },
        data: {
          payment_ref: 'CARD_MANUAL:INIT',
        },
      });

      const cleanUsername = paymentBotUsername.replace(/^@/, '');
      return {
        orderRef: checkout.order.order_ref,
        status: checkout.order.status,
        amountRub: checkout.plan.price_rub.toString(),
        paymentMethod: 'CARD_MANUAL',
        redirectUrl: `https://t.me/${cleanUsername}?start=card_${checkout.order.order_ref}`,
        message: 'Continue payment in Telegram',
      };
    } catch (error: any) {
      this.fail(this.extractErrorMessage(error));
    }
  }

  async startTrial(data: StartTrialDto) {
    const customer = await this.findOrCreateCustomerByDevice(data.deviceId);
    if (!customer.email || !customer.phone) {
      return {
        userNumber: customer.public_id,
        email: customer.email,
        phone: customer.phone,
        accessType: 'NONE',
        offerCode: null,
        status: 'PROFILE_INCOMPLETE',
        trialStartedAt: null,
        trialExpiresAt: null,
        subscriptionExpiresAt: null,
        subscriptionUrl: null,
        devicesAllowed: 1,
        dataLimitGB: 0,
        dataUsedBytes: '0',
        profileCompletionRequired: true,
        trialEligible: await this.isTrialEligible(customer, customer.email, customer.phone),
      };
    }

    return this.activateTrial({
      userNumber: customer.public_id,
      email: customer.email,
      phone: customer.phone,
    });
  }

  async activateTrial(data: ActivateTrialDto) {
    const normalizedEmail = data.email.trim().toLowerCase();
    const normalizedPhone = this.normalizePhone(data.phone);

    if (!normalizedPhone) {
      throw new Error('Phone is required');
    }

    const customer = await this.prisma.customer.findUnique({
      where: { public_id: data.userNumber },
    });

    if (!customer) {
      throw new Error('Customer not found');
    }

    const trialEligible = await this.isTrialEligible(customer, normalizedEmail, normalizedPhone);
    if (!trialEligible) {
      throw new Error('Trial already used for this device or contact data');
    }

    await this.prisma.customer.update({
      where: { id: customer.id },
      data: {
        email: normalizedEmail,
        phone: normalizedPhone,
      },
    });

    const trialPlan = await this.prisma.plan.findFirst({
      where: { code: 'WEEK' },
    });

    if (!trialPlan) {
      throw new Error('Trial plan not configured in database');
    }

    const order = await this.prisma.order.create({
      data: {
        order_ref: `TRIAL-${customer.public_id}-${Date.now()}`,
        customer_id: customer.id,
        plan_id: trialPlan.id,
        amount_rub: 0,
        status: OrderStatus.PENDING,
        payment_ref: 'TRIAL:3D',
      },
    });

    try {
      await firstValueFrom(
        this.inventoryClient.send({ cmd: 'fulfill_order' }, { orderId: order.id }),
      );
    } catch (e) {
      console.error('Fulfillment failed during trial activation:', e);
    }

    return this.getProfile(customer.public_id);
  }

  async getProfile(userNumber: string) {
    const customer = await this.prisma.customer.findUnique({
      where: { public_id: userNumber },
      include: {
        orders: {
          where: {
            status: {
              in: [OrderStatus.FULFILLED, OrderStatus.PAID, OrderStatus.PENDING_FULFILLMENT],
            },
          },
          orderBy: { created_at: 'desc' },
          take: 1,
          include: {
            plan: true,
            assignments: {
              orderBy: { assigned_at: 'desc' },
              include: {
                inventory_item: true,
              },
            },
          },
        },
      },
    });

    if (!customer) {
      throw new Error('Customer not found');
    }

    const latestOrder = customer.orders[0];
    const assignment = latestOrder?.assignments.find(
      (item) =>
        item.access_status === AssignmentAccessStatus.ACTIVE ||
        item.access_status === AssignmentAccessStatus.PENDING,
    );
    const inventoryItem = assignment?.inventory_item;
    const isTrialOrder = this.isTrialOrder(latestOrder);
    const accessType = latestOrder ? (isTrialOrder ? 'TRIAL' : 'PAID') : 'NONE';
    const offerCode = latestOrder && !isTrialOrder ? latestOrder.plan.code : null;
    const planDisplayName =
      latestOrder && !isTrialOrder ? getPublicPlanName(latestOrder.plan.code) : null;
    const fulfillmentStatus = latestOrder
      ? latestOrder.status === OrderStatus.FULFILLED
        ? 'DELIVERED'
        : 'PENDING_FULFILLMENT'
      : 'NONE';
    const subscriptionExpiresAt = isTrialOrder
      ? this.calculateSubscriptionExpiresAt(latestOrder, isTrialOrder)
      : inventoryItem?.supplier_expires_at?.toISOString() || null;
    const trialExpiresAt = isTrialOrder ? subscriptionExpiresAt : null;
    const quotaLabel = this.getEffectiveQuotaLabel(latestOrder, assignment?.fallback_quota_label);
    const status = this.resolveProfileStatus({
      hasCompletedProfile: !!customer.email && !!customer.phone,
      hasOrder: !!latestOrder,
      fulfillmentStatus,
      subscriptionExpiresAt,
      quotaExceeded: isTrialOrder
        ? this.isPlanQuotaExceeded(quotaLabel, assignment?.measured_used_bytes)
        : false,
      sourceExhausted: this.isSourceQuotaExceeded(
        inventoryItem?.source_quota_bytes,
        inventoryItem?.source_used_bytes,
      ),
    });
    return {
      userNumber: customer.public_id,
      email: customer.email,
      phone: customer.phone,
      accessType,
      offerCode,
      planDisplayName,
      planType: accessType,
      status,
      trialStartedAt: latestOrder?.created_at.toISOString() || null,
      trialExpiresAt,
      subscriptionExpiresAt,
      subscriptionUrl: inventoryItem?.raw_config || null,
      devicesAllowed: latestOrder
        ? isTrialOrder
          ? 1
          : getPlanSlotCount(latestOrder.plan.code)
        : 0,
      fulfillmentStatus,
      dataLimitGB: inventoryItem?.source_quota_bytes
        ? this.bytesToGb(inventoryItem.source_quota_bytes)
        : isTrialOrder && latestOrder
          ? this.parseQuotaLabelToGb(quotaLabel)
          : 0,
      dataUsedBytes: inventoryItem?.source_used_bytes?.toString() || '0',
      supplierProviderName: inventoryItem?.supplier_provider_name || null,
      supplierExpiresAt: inventoryItem?.supplier_expires_at?.toISOString() || null,
      profileCompletionRequired: !customer.email || !customer.phone,
      trialEligible: await this.isTrialEligible(customer, customer.email, customer.phone),
    };
  }

  async importSubscription(data: { userNumber: string; subscriptionUrl: string }) {
    const customer = await this.prisma.customer.findUnique({
      where: { public_id: data.userNumber },
    });

    if (!customer) {
      throw new Error('Customer not found');
    }

    // In MVP, "import" means manually setting the raw_config for the customer's current valid assignment.
    // Or creating a manual order/assignment if one doesn't exist.
    // For now, let's find their latest fulfilled order and update the raw_config.

    const latestOrder = await this.prisma.order.findFirst({
      where: {
        customer_id: customer.id,
        status: OrderStatus.FULFILLED,
      },
      orderBy: { created_at: 'desc' },
      include: { assignments: true },
    });

    const linkedAssignment = latestOrder?.assignments.find((assignment) => !!assignment.inventory_item_id);

    if (latestOrder && linkedAssignment?.inventory_item_id) {
      await this.prisma.inventoryItem.update({
        where: { id: linkedAssignment.inventory_item_id },
        data: { raw_config: data.subscriptionUrl },
      });
    } else {
      // If no order exists, this is a "manual import" which might need a different handling in the future.
      // For now, we'll just log it or return the existing profile.
      console.log(`Manual config import for ${customer.public_id}: ${data.subscriptionUrl}`);
    }

    return this.getProfile(customer.public_id);
  }

  async resolveCryptSubscription(data: { userNumber: string; deviceId: string; encryptedLink: string }) {
    const customer = await this.prisma.customer.findUnique({
      where: { public_id: data.userNumber },
    });

    if (!customer) {
      throw new Error('Customer not found');
    }

    if (!customer.device_id || customer.device_id !== data.deviceId) {
      throw new Error('Device is not authorized for this customer');
    }

    const profile = await this.getProfile(customer.public_id);
    if (profile.status !== 'ACTIVE') {
      throw new Error('Active access is required to resolve encrypted subscription payloads');
    }

    const encryptedLink = data.encryptedLink?.trim();
    if (!encryptedLink?.toLowerCase().startsWith('swimvpn://crypt1/')) {
      throw new Error('Unsupported encrypted subscription format');
    }

    return firstValueFrom(
      this.vpnConfigClient.send(
        { cmd: 'resolve_swim_crypt_import' },
        { encryptedLink },
      ),
    );
  }


  async reportUsage(data: ReportUsageDto) {
    const measuredUsedBytes = data.measuredUsedBytes?.trim();
    if (!measuredUsedBytes) {
      throw new Error('Measured usage is required');
    }

    const customer = await this.prisma.customer.findUnique({
      where: { public_id: data.userNumber },
      include: {
        orders: {
          where: { status: OrderStatus.FULFILLED },
          orderBy: { created_at: 'desc' },
          take: 1,
        },
      },
    });

    if (!customer) {
      throw new Error('Customer not found');
    }

    const latestOrder = customer.orders[0];
    if (!latestOrder) {
      return { success: true, ignored: true, reason: 'NO_FULFILLED_ORDER' };
    }

    await firstValueFrom(
      this.inventoryClient.send(
        { cmd: 'record_assignment_usage' },
        {
          orderRef: latestOrder.order_ref,
          measuredUsedBytes,
        },
      ),
    );

    return this.getProfile(customer.public_id);
  }

  async activateCode(data: { userNumber: string; code: string }) {
    // 1. Find the coupon/code in the database
    // For MVP, we'll assume any code starting with "SWIM-" is valid for a MONTH plan
    if (!data.code.startsWith('SWIM-')) {
      throw new Error('Invalid coupon code');
    }

    const customer = await this.prisma.customer.findUnique({
      where: { public_id: data.userNumber },
    });

    if (!customer) throw new Error('Customer not found');

    const plan = await this.prisma.plan.findFirst({
      where: { code: 'MONTH' },
    });

    if (!plan) throw new Error('Plan not found');

    // 2. Create a PAID order
    const order = await this.prisma.order.create({
      data: {
        order_ref: `CODE-${data.code}-${Date.now()}`,
        customer_id: customer.id,
        plan_id: plan.id,
        amount_rub: 0,
        status: OrderStatus.PAID,
        payment_ref: `COUPON:${data.code}`,
      },
    });

    // 3. Fulfill
    try {
      await firstValueFrom(
        this.inventoryClient.send({ cmd: 'fulfill_order' }, { orderId: order.id }),
      );
    } catch (e) {
      console.error('Fulfillment failed during code activation:', e);
    }

    return this.getProfile(customer.public_id);
  }

  async handleStripeWebhook(data: any) {
    // Basic Stripe Webhook Logic
    // In production: verify signature
    if (data.type === 'checkout.session.completed') {
      const session = data.data.object;
      const orderRef = session.client_reference_id;
      return this.fulfillOrderByRef(orderRef, session.id);
    }
    return { received: true };
  }

  async handleYookassaWebhook(data: any) {
    // Basic YooKassa Webhook Logic
    if (data.event === 'payment.succeeded') {
      const payment = data.object;
      const orderRef = payment.metadata?.order_ref;
      return this.fulfillOrderByRef(orderRef, payment.id);
    }
    return { received: true };
  }

  async handleCryptoWebhook(data: { body: any; signature?: string | string[] }) {
    if (!this.cryptoPayService.isConfigured()) {
      throw new Error('Crypto Pay API is not configured');
    }

    if (!this.cryptoPayService.verifyWebhook(data.body, data.signature)) {
      throw new Error('Invalid Crypto Pay webhook signature');
    }

    if (data.body?.update_type !== 'invoice_paid') {
      return { received: true, ignored: true };
    }

    const payload = data.body?.payload || {};
    const orderRef =
      typeof payload?.payload === 'string' && payload.payload.trim().length > 0
        ? payload.payload.trim()
        : undefined;

    const invoiceId = payload?.invoice_id;
    const paidAsset = payload?.paid_asset || payload?.asset || 'UNKNOWN';
    const paidAmount = payload?.paid_amount || payload?.amount || 'UNKNOWN';

    const result = await this.fulfillOrderByRef(
      orderRef,
      `CRYPTO_PAID:${invoiceId}:${paidAsset}:${paidAmount}`,
    );

    return {
      received: true,
      ...result,
    };
  }

  async approveManualCardPayment(data: { orderRef: string; paymentRef: string; proofEventId?: string }) {
    const result = await this.fulfillOrderByRef(data.orderRef, data.paymentRef);

    if (data.proofEventId) {
      await this.prisma.adminEvent.create({
        data: {
          event_type: 'CARD_PAYMENT_APPROVED',
          entity_type: 'ORDER',
          entity_id: data.orderRef,
          payload_json: {
            orderRef: data.orderRef,
            paymentRef: data.paymentRef,
            proofEventId: data.proofEventId,
            processedAt: new Date().toISOString(),
          } as any,
        },
      });
    }

    return result;
  }

  async rejectManualCardPayment(data: { orderRef: string; reason?: string }) {
    const order = await this.prisma.order.findUnique({
      where: { order_ref: data.orderRef },
      include: {
        customer: true,
        plan: true,
      },
    });

    if (!order) {
      return { success: false, error: 'Order not found' };
    }

    if (order.status !== OrderStatus.PENDING) {
      return {
        success: true,
        alreadyProcessed: true,
        orderRef: data.orderRef,
        customerEmail: order.customer.email,
        planName: order.plan.name,
      };
    }

    await this.prisma.order.update({
      where: { id: order.id },
      data: {
        status: OrderStatus.FAILED,
        payment_ref: `CARD_MANUAL:REJECTED:${Date.now()}`,
      },
    });

    await this.prisma.adminEvent.create({
      data: {
        event_type: 'CARD_PAYMENT_REJECTED',
        entity_type: 'ORDER',
        entity_id: order.order_ref,
        payload_json: {
          orderRef: order.order_ref,
          reason: data.reason || null,
          processedAt: new Date().toISOString(),
        } as any,
      },
    });

    return {
      success: true,
      orderRef: order.order_ref,
      customerEmail: order.customer.email,
      planName: order.plan.name,
      rejected: true,
    };
  }

  private async fulfillOrderByRef(orderRef: string, paymentRef: string) {
    if (!orderRef) return { success: false, error: 'No order ref' };

    const order = await this.prisma.order.findUnique({
      where: { order_ref: orderRef },
    });

    if (!order) return { success: false, error: 'Order not found' };
    if (
      order.status !== OrderStatus.PENDING &&
      order.status !== OrderStatus.PENDING_FULFILLMENT
    ) {
      return { success: true, alreadyProcessed: true };
    }

    await this.prisma.order.update({
      where: { id: order.id },
      data: {
        status: OrderStatus.PAID,
        paid_at: order.paid_at ?? new Date(),
        payment_ref: paymentRef || order.payment_ref,
      },
    });

    try {
      return await firstValueFrom(
        this.inventoryClient.send({ cmd: 'fulfill_order' }, { orderId: order.id }),
      );
    } catch (e) {
      console.error(`Fulfillment failed for order ${order.id}:`, e);
      return { success: false, error: 'Fulfillment triggered but failed' };
    }
  }

  private async preparePendingOrder(data: { email?: string; phone?: string; planId: string }) {
    const plan = await this.prisma.plan.findUnique({
      where: { id: data.planId },
    });

    if (!plan || !plan.active) {
      this.fail('Plan not found or inactive');
    }

    const normalizedEmail = data.email?.trim().toLowerCase() || undefined;
    const normalizedPhone = this.normalizePhone(data.phone) || undefined;

    if (!normalizedEmail) {
      this.fail('Payment contact email is required before checkout');
    }

    const customer = await this.findOrCreateCustomerByContact(normalizedEmail, normalizedPhone);
    const orderRef = `ORD-${Date.now()}-${Math.floor(Math.random() * 1000)}`;
    const order = await this.prisma.order.create({
      data: {
        order_ref: orderRef,
        customer_id: customer.id,
        plan_id: plan.id,
        amount_rub: plan.price_rub,
        status: OrderStatus.PENDING,
      },
    });

    return {
      customer,
      plan,
      order,
    };
  }

  private async findOrCreateCustomerByContact(email?: string, phone?: string) {
    const orConditions = [
      email ? { email } : null,
      phone ? { phone } : null,
    ].filter(Boolean) as Array<{ email?: string; phone?: string }>;

    let customer = orConditions.length
      ? await this.prisma.customer.findFirst({
          where: {
            OR: orConditions,
          },
        })
      : null;

    if (!customer) {
      customer = await this.prisma.customer.create({
        data: {
          public_id: await this.generatePublicUserNumber(),
          email,
          phone,
        },
      });
      return customer;
    }

    if (customer.email !== email || customer.phone !== phone) {
      customer = await this.prisma.customer.update({
        where: { id: customer.id },
        data: {
          email,
          phone,
        },
      });
    }

    return customer;
  }

  private async findOrCreateCustomerByDevice(deviceId: string) {
    const existing = await this.prisma.customer.findUnique({
      where: { device_id: deviceId },
    });

    if (existing) {
      return existing;
    }

    return this.prisma.customer.create({
      data: {
        device_id: deviceId,
        public_id: await this.generatePublicUserNumber(),
      },
    });
  }

  private async generatePublicUserNumber() {
    for (let attempt = 0; attempt < 10; attempt += 1) {
      const suffix = Math.random().toString(36).slice(2, 8).toUpperCase();
      const candidate = `SW-${suffix}`;
      const existing = await this.prisma.customer.findUnique({
        where: { public_id: candidate },
      });

      if (!existing) {
        return candidate;
      }
    }

    throw new Error('Unable to generate unique public user number');
  }


  private normalizePhone(phone?: string) {
    if (!phone) {
      return '';
    }

    const normalized = phone.replace(/[^\d+]/g, '');
    return normalized.trim();
  }

  private async isTrialEligible(customer: { id: string; device_id: string | null }, email?: string | null, phone?: string | null) {
    const existingCustomerTrial = await this.prisma.order.findFirst({
      where: {
        customer_id: customer.id,
        OR: [
          { payment_ref: 'TRIAL:3D' },
          { order_ref: { startsWith: 'TRIAL-' } },
        ],
      },
    });

    if (existingCustomerTrial) {
      return false;
    }

    const normalizedEmail = email?.trim().toLowerCase();
    const normalizedPhone = this.normalizePhone(phone || undefined);
    const customerConditions: Array<{ device_id?: string; email?: string; phone?: string }> = [];

    if (customer.device_id) {
      customerConditions.push({ device_id: customer.device_id });
    }
    if (normalizedEmail) {
      customerConditions.push({ email: normalizedEmail });
    }
    if (normalizedPhone) {
      customerConditions.push({ phone: normalizedPhone });
    }

    if (customerConditions.length === 0) {
      return true;
    }

    const existingTrial = await this.prisma.order.findFirst({
      where: {
        AND: [
          {
            OR: [
              { payment_ref: 'TRIAL:3D' },
              { order_ref: { startsWith: 'TRIAL-' } },
            ],
          },
          { customer_id: { not: customer.id } },
          {
            customer: {
              OR: customerConditions,
            },
          },
        ],
      },
    });

    return !existingTrial;
  }

  private getDurationMsFromOrder(planCode: string, isTrialOrder: boolean) {
    if (isTrialOrder) {
      return CustomerService.TRIAL_DURATION_MS;
    }

    switch (planCode) {
      case 'MONTH':
        return 30 * 24 * 60 * 60 * 1000;
      case 'QUARTER':
        return 90 * 24 * 60 * 60 * 1000;
      case 'WEEK':
      default:
        return 7 * 24 * 60 * 60 * 1000;
    }
  }

  private calculateSubscriptionExpiresAt(
    latestOrder:
      | {
          fulfilled_at: Date | null;
          plan: { code: string };
        }
      | undefined,
    isTrialOrder: boolean,
  ) {
    if (!latestOrder?.fulfilled_at) {
      return null;
    }

    return new Date(
      latestOrder.fulfilled_at.getTime() +
        this.getDurationMsFromOrder(latestOrder.plan.code, isTrialOrder),
    ).toISOString();
  }

  private isTrialOrder(
    order?:
      | {
          payment_ref?: string | null;
          order_ref: string;
        }
      | undefined,
  ) {
    return !!order && (
      order.payment_ref === 'TRIAL:3D' ||
      order.order_ref.startsWith('TRIAL-')
    );
  }

  private getEffectiveQuotaLabel(
    order?:
      | {
          payment_ref?: string | null;
          order_ref: string;
          plan?: { quota_label?: string | null };
        }
      | undefined,
    fallbackQuotaLabel?: string | null,
  ) {
    if (this.isTrialOrder(order)) {
      return CustomerService.TRIAL_QUOTA_LABEL;
    }

    return order?.plan?.quota_label || fallbackQuotaLabel || '';
  }

  private resolveProfileStatus(params: {
    hasCompletedProfile: boolean;
    hasOrder: boolean;
    fulfillmentStatus: string;
    subscriptionExpiresAt: string | null;
    quotaExceeded: boolean;
    sourceExhausted: boolean;
  }) {
    if (!params.hasCompletedProfile) {
      return 'PROFILE_INCOMPLETE';
    }

    if (!params.hasOrder) {
      return 'TRIAL_AVAILABLE';
    }

    if (params.fulfillmentStatus === 'PENDING_FULFILLMENT') {
      return 'PENDING_FULFILLMENT';
    }

    if (params.quotaExceeded || params.sourceExhausted) {
      return 'EXPIRED';
    }

    if (!params.subscriptionExpiresAt) {
      return 'ACTIVE';
    }

    return new Date(params.subscriptionExpiresAt).getTime() < Date.now() ? 'EXPIRED' : 'ACTIVE';
  }

  private parseQuotaLabelToGb(quotaLabel: string) {
    const match = quotaLabel.match(/(\d+(?:[.,]\d+)?)/);
    if (!match) {
      return 0;
    }

    const normalized = match[1].replace(',', '.');
    const parsed = Number.parseFloat(normalized);
    return Number.isFinite(parsed) ? parsed : 0;
  }

  private quotaLabelToBytes(quotaLabel: string) {
    const parsedGb = this.parseQuotaLabelToGb(quotaLabel);
    if (!Number.isFinite(parsedGb) || parsedGb <= 0) {
      return 0n;
    }

    return BigInt(Math.round(parsedGb * 1024 * 1024 * 1024));
  }

  private bytesToGb(valueBytes: bigint) {
    return Number(valueBytes) / (1024 * 1024 * 1024);
  }

  private isPlanQuotaExceeded(quotaLabel: string, measuredUsedBytes?: bigint | null) {
    const quotaBytes = this.quotaLabelToBytes(quotaLabel);
    if (quotaBytes <= 0n) {
      return false;
    }

    return (measuredUsedBytes ?? 0n) >= quotaBytes;
  }

  private isSourceQuotaExceeded(sourceQuotaBytes?: bigint | null, sourceUsedBytes?: bigint | null) {
    if (!sourceQuotaBytes || sourceQuotaBytes <= 0n) {
      return false;
    }

    return (sourceUsedBytes ?? 0n) >= sourceQuotaBytes;
  }

  private extractErrorMessage(error: any) {
    if (error instanceof RpcException) {
      const rpcError = error.getError();
      if (typeof rpcError === 'string' && rpcError.trim().length > 0) {
        return rpcError;
      }

      if (typeof rpcError === 'object' && rpcError && typeof (rpcError as any).message === 'string') {
        return (rpcError as any).message;
      }
    }

    if (typeof error?.message === 'string' && error.message.trim().length > 0) {
      return error.message;
    }

    if (typeof error?.error?.message === 'string' && error.error.message.trim().length > 0) {
      return error.error.message;
    }

    return 'Unable to create checkout';
  }

  private fail(message: string): never {
    throw new RpcException({ message });
  }
}
