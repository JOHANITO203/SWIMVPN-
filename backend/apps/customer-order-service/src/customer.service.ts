import { Injectable, Inject } from '@nestjs/common';
import { ClientProxy, RpcException } from '@nestjs/microservices';
import { PrismaService } from '@app/database';
import { firstValueFrom } from 'rxjs';
import { AssignmentAccessStatus, InventoryHealthStatus, OrderStatus } from '@prisma/client';
import {
  StartTrialDto,
  CreateOrderDto,
  BootstrapAccessDto,
  ActivateTrialDto,
  CompleteProfileDto,
  CancelCurrentSubscriptionDto,
  CreateCheckoutDto,
  getPlanDeviceAllowance,
  getPublicPlanName,
  ReportUsageDto,
} from '@app/contracts';
import { CryptoPayService } from './crypto-pay.service';
import {
  looksLikeTelegramBotToken,
  normalizeConfiguredPaymentBotUsername,
  selectManualPaymentBotToken,
} from './payment-bot-routing';

@Injectable()
export class CustomerService {
  private static readonly TRIAL_DURATION_MS = 3 * 24 * 60 * 60 * 1000;
  private static readonly TRIAL_QUOTA_LABEL = 'UNLIMITED';

  constructor(
    private readonly prisma: PrismaService,
    @Inject('INVENTORY_SERVICE') private readonly inventoryClient: ClientProxy,
    @Inject('VPN_CONFIG_SERVICE') private readonly vpnConfigClient: ClientProxy,
    private readonly cryptoPayService: CryptoPayService,
  ) {}

  async bootstrapAccess(data: BootstrapAccessDto) {
    const customer = await this.findOrCreateCustomerByDevice(data.deviceId);
    const profile = await this.getProfile(customer.public_id);
    const hasActiveAccess = this.isPremiumAllowed(profile.entitlementState) && !!profile.subscriptionUrl;

    return {
      userNumber: customer.public_id,
      email: customer.email,
      phone: customer.phone,
      trialEligible: await this.isTrialEligible(customer, customer.email, customer.phone),
      profileCompletionRequired: !customer.email || !customer.phone,
      hasActiveAccess,
      profile: profile, // ALWAYS return profile to allow freemium app shell access
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

      const paymentBotUsername = await this.resolvePaymentBotUsername();
      if (!paymentBotUsername) {
        this.fail('Manual payment bot is not configured');
      }

      await this.prisma.order.update({
        where: { id: checkout.order.id },
        data: {
          payment_ref: 'CARD_MANUAL:INIT',
        },
      });

      return {
        orderRef: checkout.order.order_ref,
        status: checkout.order.status,
        amountRub: checkout.plan.price_rub.toString(),
        paymentMethod: 'CARD_MANUAL',
        redirectUrl: `https://t.me/${paymentBotUsername}?start=card_${checkout.order.order_ref}`,
        message: 'Continue payment in Telegram',
      };
    } catch (error: any) {
      this.fail(this.extractErrorMessage(error));
    }
  }

  async startTrial(data: StartTrialDto) {
    const customer = await this.findOrCreateCustomerByDevice(data.deviceId);
    if (!customer.email || !customer.phone) {
      return this.getProfile(customer.public_id);
    }

    return this.activateTrial({
      userNumber: customer.public_id,
      deviceId: data.deviceId,
      email: customer.email,
      phone: customer.phone,
    });
  }

  async activateTrial(data: ActivateTrialDto) {
    const normalizedDeviceId = this.normalizeDeviceId(data.deviceId);
    const normalizedEmail = data.email.trim().toLowerCase();
    const normalizedPhone = this.normalizePhone(data.phone);

    if (!normalizedPhone) {
      this.fail('Phone is required');
    }

    const customer = await this.prisma.customer.findUnique({
      where: { public_id: data.userNumber },
    });

    if (!customer) {
      this.fail('Customer not found');
    }

    if (!customer.device_id || customer.device_id !== normalizedDeviceId) {
      this.fail('Device is not authorized for trial activation');
    }

    await this.prisma.customer.update({
      where: { id: customer.id },
      data: {
        email: normalizedEmail,
        phone: normalizedPhone,
      },
    });

    const trialEligible = await this.isTrialEligible(customer, normalizedEmail, normalizedPhone);
    if (!trialEligible) {
      this.fail('Trial already used for this device or contact data');
    }

    const trialPlan = await this.prisma.plan.findFirst({
      where: { code: 'WEEK' },
    });

    if (!trialPlan) {
      this.fail('Trial plan not configured in database');
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

  async completeProfile(data: CompleteProfileDto) {
    const normalizedDeviceId = this.normalizeDeviceId(data.deviceId);
    const normalizedEmail = data.email?.trim().toLowerCase() || undefined;
    const normalizedPhone = this.normalizePhone(data.phone);

    if (normalizedEmail && !this.looksLikeEmail(normalizedEmail)) {
      this.fail('Valid email is required');
    }

    const customer = await this.prisma.customer.findUnique({
      where: { public_id: data.userNumber },
    });

    if (!customer) {
      this.fail('Customer not found');
    }

    if (!customer.device_id || customer.device_id !== normalizedDeviceId) {
      this.fail('Device is not authorized for profile completion');
    }

    if (normalizedEmail || normalizedPhone) {
      await this.prisma.customer.update({
        where: { id: customer.id },
        data: {
          email: normalizedEmail ?? customer.email,
          phone: normalizedPhone ?? customer.phone,
        },
      });
    }

    return this.getProfile(customer.public_id, { exposeRuntimeConfig: false });
  }

  async cancelCurrentSubscription(data: CancelCurrentSubscriptionDto) {
    const normalizedDeviceId = this.normalizeDeviceId(data.deviceId);
    const rawReason = typeof data.reason === 'string' ? data.reason.trim() : '';
    const reason = rawReason.slice(0, 120) || 'CUSTOMER_CANCELLED';

    const customer = await this.prisma.customer.findUnique({
      where: { public_id: data.userNumber },
      include: {
        orders: {
          where: {
            status: {
              in: [OrderStatus.FULFILLED, OrderStatus.PAID, OrderStatus.PENDING_FULFILLMENT],
            },
          },
          orderBy: { created_at: 'desc' },
          take: 10,
          include: {
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
      this.fail('Customer not found');
    }

    if (!customer.device_id || customer.device_id !== normalizedDeviceId) {
      this.fail('Device is not authorized for cancellation');
    }

    const activeOrder = customer.orders.find((order) =>
      order.assignments.some((assignment) => assignment.access_status === AssignmentAccessStatus.ACTIVE),
    );
    const activeAssignment = activeOrder?.assignments.find(
      (assignment) => assignment.access_status === AssignmentAccessStatus.ACTIVE,
    );

    if (!activeOrder || !activeAssignment) {
      const pendingOrder = customer.orders.find(
        (order) =>
          order.status === OrderStatus.PAID ||
          order.status === OrderStatus.PENDING_FULFILLMENT,
      );

      if (pendingOrder) {
        await this.prisma.order.update({
          where: { id: pendingOrder.id },
          data: { status: OrderStatus.CANCELLED },
        });

        await this.prisma.adminEvent.create({
          data: {
            event_type: 'CUSTOMER_PENDING_ORDER_CANCELLED',
            entity_type: 'ORDER',
            entity_id: pendingOrder.order_ref,
            payload_json: {
              userNumber: customer.public_id,
              orderRef: pendingOrder.order_ref,
              previousStatus: pendingOrder.status,
              requestedReason: reason,
              cancelledAt: new Date().toISOString(),
              slotPolicy: 'no active assignment existed; no resale slot was consumed',
            } as any,
          },
        }).catch(() => undefined);

        return this.getProfile(customer.public_id, { exposeRuntimeConfig: false });
      }

      await this.prisma.adminEvent.create({
        data: {
          event_type: 'CUSTOMER_SUBSCRIPTION_CANCEL_SKIPPED',
          entity_type: 'CUSTOMER',
          entity_id: customer.public_id,
          payload_json: {
            reason: 'NO_ACTIVE_ASSIGNMENT',
            requestedReason: reason,
            requestedAt: new Date().toISOString(),
          } as any,
        },
      }).catch(() => undefined);

      return this.getProfile(customer.public_id, { exposeRuntimeConfig: false });
    }

    await firstValueFrom(
      this.inventoryClient.send(
        { cmd: 'revoke_assignment' },
        {
          assignmentId: activeAssignment.id,
          reason,
          adminId: null,
        },
      ),
    );

    await this.prisma.adminEvent.create({
      data: {
        event_type: 'CUSTOMER_SUBSCRIPTION_CANCELLED',
        entity_type: 'ORDER_ASSIGNMENT',
        entity_id: activeAssignment.id,
        payload_json: {
          userNumber: customer.public_id,
          orderRef: activeOrder.order_ref,
          inventoryItemId: activeAssignment.inventory_item_id,
          reason,
          cancelledAt: new Date().toISOString(),
          slotPolicy: 'assignment revoked through inventory service; resale capacity recalculated',
        } as any,
      },
    });

    return this.getProfile(customer.public_id, { exposeRuntimeConfig: false });
  }

  async getProfile(userNumber: string, options: { exposeRuntimeConfig?: boolean } = {}) {
    const exposeRuntimeConfig = options.exposeRuntimeConfig ?? true;
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
          take: 10,
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

    const trialEligible = await this.isTrialEligible(customer, customer.email, customer.phone);
    const latestOrder = customer.orders.find((order) =>
      order.status === OrderStatus.PAID ||
      order.status === OrderStatus.PENDING_FULFILLMENT ||
      order.assignments.some(
        (item) =>
          item.access_status === AssignmentAccessStatus.ACTIVE ||
          item.access_status === AssignmentAccessStatus.PENDING,
      ),
    );
    const activeAssignment = latestOrder?.assignments.find(
      (item) =>
        item.access_status === AssignmentAccessStatus.ACTIVE,
    );
    const pendingAssignment = latestOrder?.assignments.find(
      (item) => item.access_status === AssignmentAccessStatus.PENDING,
    );
    const assignment = activeAssignment ?? pendingAssignment;
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
    const orderExpiresAt = latestOrder
      ? this.calculateSubscriptionExpiresAt(latestOrder, isTrialOrder)
      : null;
    const providerExpiresAt =
      activeAssignment?.expires_at?.toISOString() ||
      inventoryItem?.supplier_expires_at?.toISOString() ||
      null;
    const subscriptionExpiresAt = isTrialOrder
      ? this.pickEarlierIsoDate(orderExpiresAt, providerExpiresAt)
      : this.pickEarlierIsoDate(providerExpiresAt, orderExpiresAt);
    const trialExpiresAt = isTrialOrder ? subscriptionExpiresAt : null;
    const measuredDataLimitGb =
      latestOrder && !isTrialOrder
        ? this.parseQuotaLabelToGb(latestOrder.plan.quota_label || '')
        : 0;
    const measuredDataUsedBytes = activeAssignment?.measured_used_bytes?.toString() || '0';
    const entitlementState = this.resolveEntitlementState({
      hasCompletedProfile: !!customer.email && !!customer.phone,
      hasOrder: !!latestOrder,
      trialEligible,
      fulfillmentStatus,
      accessType,
      hasActiveAssignment: !!activeAssignment?.inventory_item_id,
      subscriptionExpiresAt,
      quotaExceeded: latestOrder && !isTrialOrder
        ? this.isPlanQuotaExceeded(latestOrder.plan.quota_label || '', activeAssignment?.measured_used_bytes)
        : false,
      inventoryHealthStatus: inventoryItem?.health_status,
      sourceExhausted: this.isSourceQuotaExceeded(
        inventoryItem?.source_quota_bytes,
        inventoryItem?.source_used_bytes,
      ),
    });
    const status = this.toLegacyProfileStatus(entitlementState);
    return {
      userNumber: customer.public_id,
      email: customer.email,
      phone: customer.phone,
      accessType,
      offerCode,
      planDisplayName,
      planType: accessType,
      status,
      entitlementState,
      trialStartedAt: latestOrder?.created_at.toISOString() || null,
      trialExpiresAt,
      subscriptionExpiresAt,
      subscriptionUrl: exposeRuntimeConfig && this.isPremiumAllowed(entitlementState)
        ? (inventoryItem?.raw_config || null)
        : null,
      devicesAllowed: latestOrder ? getPlanDeviceAllowance(latestOrder.plan.code) : 0,
      fulfillmentStatus,
      dataLimitGB: isTrialOrder ? 0 : measuredDataLimitGb,
      dataUsedBytes: isTrialOrder ? '0' : measuredDataUsedBytes,
      supplierProviderName: inventoryItem?.supplier_provider_name || null,
      supplierExpiresAt: inventoryItem?.supplier_expires_at?.toISOString() || null,
      profileCompletionRequired: !customer.email || !customer.phone,
      trialEligible,
    };
  }

  async importSubscription(data: { userNumber: string; subscriptionUrl: string }) {
    const customer = await this.prisma.customer.findUnique({
      where: { public_id: data.userNumber },
    });

    if (!customer) {
      throw new Error('Customer not found');
    }

    console.log(`Local config import acknowledged for ${customer.public_id}; backend inventory unchanged.`);

    return this.getProfile(customer.public_id, { exposeRuntimeConfig: false });
  }

  async resolveCryptSubscription(data: { userNumber: string; deviceId: string; encryptedLink: string }) {
    const normalizedDeviceId = this.normalizeDeviceId(data.deviceId);
    const customer = await this.prisma.customer.findUnique({
      where: { public_id: data.userNumber },
    });

    if (!customer) {
      throw new Error('Customer not found');
    }

    if (!customer.device_id || customer.device_id !== normalizedDeviceId) {
      throw new Error('Device is not authorized for this customer');
    }

    const profile = await this.getProfile(customer.public_id);
    if (!this.isPremiumAllowed(profile.entitlementState)) {
      throw new Error('Active access is required to resolve encrypted subscription payloads');
    }

    const encryptedLink = data.encryptedLink?.trim();
    if (!encryptedLink?.toLowerCase().startsWith('swimvpn://crypt1/')) {
      throw new Error('Unsupported encrypted subscription format');
    }

    const resolved = await firstValueFrom(
      this.vpnConfigClient.send(
        { cmd: 'resolve_swim_crypt_import' },
        { encryptedLink },
      ),
    );

    if (resolved.rawConfig?.trim() !== profile.subscriptionUrl?.trim()) {
      throw new Error('Encrypted subscription payload is not assigned to this customer');
    }

    return resolved;
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
          take: 10,
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

    const normalizedDeviceId = this.normalizeDeviceId(data.deviceId);
    if (!customer.device_id || customer.device_id !== normalizedDeviceId) {
      throw new Error('Device is not authorized for usage reporting');
    }

    const now = Date.now();
    const bestActiveOrder =
      customer.orders.find((order) => {
        const activeAssignment = order.assignments.find(
          (item) => item.access_status === AssignmentAccessStatus.ACTIVE,
        );
        const inventoryItem = activeAssignment?.inventory_item;
        if (!activeAssignment || !inventoryItem) {
          return false;
        }

        const inventoryUnavailable =
          inventoryItem.health_status === InventoryHealthStatus.EXPIRED ||
          inventoryItem.health_status === InventoryHealthStatus.DISABLED;
        if (
          inventoryUnavailable ||
          this.isSourceQuotaExceeded(inventoryItem.source_quota_bytes, inventoryItem.source_used_bytes)
        ) {
          return false;
        }

        const isTrialOrder = this.isTrialOrder(order);
        const orderExpiresAt = this.calculateSubscriptionExpiresAt(order, isTrialOrder);
        const providerExpiresAt =
          activeAssignment.expires_at?.toISOString() ||
          inventoryItem.supplier_expires_at?.toISOString() ||
          null;
        const subscriptionExpiresAt = isTrialOrder
          ? this.pickEarlierIsoDate(orderExpiresAt, providerExpiresAt)
          : this.pickEarlierIsoDate(providerExpiresAt, orderExpiresAt);

        return !subscriptionExpiresAt || new Date(subscriptionExpiresAt).getTime() >= now;
      }) || null;
    const latestOrder = bestActiveOrder;
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
    await this.prisma.adminEvent.create({
      data: {
        event_type: 'ACTIVATION_CODE_REJECTED',
        entity_type: 'CUSTOMER',
        entity_id: data.userNumber || 'UNKNOWN',
        payload_json: {
          reason: 'ACTIVATION_CODES_DISABLED',
          codePrefix: data.code?.slice(0, 8) || null,
          rejectedAt: new Date().toISOString(),
        } as any,
      },
    }).catch(() => undefined);

    this.fail('Activation codes are disabled until managed coupons are configured');
  }

  async handleStripeWebhook(_data: any) {
    this.fail('Stripe webhook fulfillment is disabled until signature verification is configured');
  }

  async handleYookassaWebhook(_data: any) {
    this.fail('YooKassa webhook fulfillment is disabled until signature verification is configured');
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

    if (data.proofEventId && result?.success) {
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
        success: false,
        alreadyProcessed: true,
        currentStatus: order.status,
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
      order.status !== OrderStatus.PENDING_FULFILLMENT &&
      order.status !== OrderStatus.PAID
    ) {
      return {
        success: order.status === OrderStatus.FULFILLED,
        alreadyProcessed: true,
        currentStatus: order.status,
      };
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
      const fulfillmentResult = await firstValueFrom(
        this.inventoryClient.send({ cmd: 'fulfill_order' }, { orderId: order.id }),
      );
      if (fulfillmentResult?.success === false) {
        const errorMessage = fulfillmentResult.error || 'Inventory fulfillment failed';
        await this.auditFulfillmentFailure(order, paymentRef, errorMessage);
        await this.markOrderPendingFulfillment(order.id);
        return {
          success: true,
          paymentApproved: true,
          pendingFulfillment: true,
          orderStatus: OrderStatus.PENDING_FULFILLMENT,
          fulfillmentError: errorMessage,
          error: `Fulfillment pending: ${errorMessage}`,
        };
      }

      return fulfillmentResult;
    } catch (e) {
      const errorMessage = this.extractErrorMessage(e, 'Fulfillment failed');
      console.error(`Fulfillment failed for order ${order.id}: ${errorMessage}`, e);

      await this.auditFulfillmentFailure(order, paymentRef, errorMessage);
      await this.markOrderPendingFulfillment(order.id);

      return {
        success: true,
        paymentApproved: true,
        pendingFulfillment: true,
        orderStatus: OrderStatus.PENDING_FULFILLMENT,
        fulfillmentError: errorMessage,
        error: `Fulfillment pending: ${errorMessage}`,
      };
    }
  }

  private async markOrderPendingFulfillment(orderId: string) {
    await this.prisma.order.update({
      where: { id: orderId },
      data: { status: OrderStatus.PENDING_FULFILLMENT },
    }).catch(() => undefined);
  }

  private async auditFulfillmentFailure(
    order: { id: string; order_ref: string },
    paymentRef: string,
    errorMessage: string,
  ) {
    await this.prisma.adminEvent.create({
      data: {
        event_type: 'FULFILLMENT_FAILED',
        entity_type: 'ORDER',
        entity_id: order.order_ref,
        payload_json: {
          orderRef: order.order_ref,
          orderId: order.id,
          paymentRef,
          error: errorMessage,
          failedAt: new Date().toISOString(),
        } as any,
      },
    }).catch(() => undefined);
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
    const normalizedDeviceId = this.normalizeDeviceId(deviceId);
    const existing = await this.prisma.customer.findUnique({
      where: { device_id: normalizedDeviceId },
    });

    if (existing) {
      return existing;
    }

    return this.prisma.customer.create({
      data: {
        device_id: normalizedDeviceId,
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

  private normalizeDeviceId(deviceId?: string) {
    const normalized = deviceId?.trim();
    if (!normalized || ['unknown_device_id', 'unknown', 'null'].includes(normalized.toLowerCase())) {
      this.fail('Valid device id is required');
    }

    return normalized;
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

  private resolveEntitlementState(params: {
    hasCompletedProfile: boolean;
    hasOrder: boolean;
    trialEligible: boolean;
    fulfillmentStatus: string;
    accessType: string;
    hasActiveAssignment: boolean;
    subscriptionExpiresAt: string | null;
    quotaExceeded: boolean;
    inventoryHealthStatus?: InventoryHealthStatus | null;
    sourceExhausted: boolean;
  }) {
    if (!params.hasCompletedProfile) {
      return 'PROFILE_INCOMPLETE';
    }

    if (!params.hasOrder) {
      return params.trialEligible ? 'TRIAL_AVAILABLE' : 'FREEMIUM';
    }

    if (params.fulfillmentStatus === 'PENDING_FULFILLMENT') {
      return 'PENDING_FULFILLMENT';
    }

    const expiredState =
      params.accessType === 'TRIAL' ? 'EXPIRED_TRIAL' : 'EXPIRED_SUBSCRIPTION';
    const activeState =
      params.accessType === 'TRIAL' ? 'ACTIVE_TRIAL' : 'ACTIVE_SUBSCRIPTION';

    const inventoryUnavailable =
      params.inventoryHealthStatus === InventoryHealthStatus.EXPIRED ||
      params.inventoryHealthStatus === InventoryHealthStatus.DISABLED;

    if (
      !params.hasActiveAssignment ||
      params.quotaExceeded ||
      params.sourceExhausted ||
      inventoryUnavailable
    ) {
      return expiredState;
    }

    if (
      params.subscriptionExpiresAt &&
      new Date(params.subscriptionExpiresAt).getTime() < Date.now()
    ) {
      return expiredState;
    }

    return activeState;
  }

  private toLegacyProfileStatus(entitlementState: string) {
    if (this.isPremiumAllowed(entitlementState)) {
      return 'ACTIVE';
    }

    if (
      entitlementState === 'EXPIRED_TRIAL' ||
      entitlementState === 'EXPIRED_SUBSCRIPTION'
    ) {
      return 'EXPIRED';
    }

    return entitlementState;
  }

  private isPremiumAllowed(entitlementState: string) {
    return entitlementState === 'ACTIVE_TRIAL' || entitlementState === 'ACTIVE_SUBSCRIPTION';
  }

  private pickEarlierIsoDate(first?: string | null, second?: string | null) {
    if (!first) {
      return second || null;
    }

    if (!second) {
      return first;
    }

    return new Date(first).getTime() <= new Date(second).getTime() ? first : second;
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

  private async resolvePaymentBotUsername() {
    const configuredUsername = normalizeConfiguredPaymentBotUsername(process.env.PAYMENT_BOT_USERNAME);
    if (configuredUsername) {
      return configuredUsername;
    }

    const configuredValue = process.env.PAYMENT_BOT_USERNAME?.trim();
    if (configuredValue && looksLikeTelegramBotToken(configuredValue)) {
      const resolvedUsername = await this.fetchTelegramBotUsername(configuredValue);
      if (resolvedUsername) {
        return resolvedUsername;
      }
    }

    const commandBotToken = selectManualPaymentBotToken({
      paymentBotToken: process.env.PAYMENT_BOT_TOKEN,
      notificationBotToken: process.env.NOTIFICATION_BOT_TOKEN,
    });
    if (commandBotToken) {
      const resolvedUsername = await this.fetchTelegramBotUsername(commandBotToken);
      if (resolvedUsername) {
        return resolvedUsername;
      }
    }

    return null;
  }

  private looksLikeEmail(value: string) {
    return /^[^\s@]+@[^\s@]+\.[^\s@]+$/.test(value);
  }

  private async fetchTelegramBotUsername(token: string) {
    try {
      const response = await fetch(`https://api.telegram.org/bot${token}/getMe`);
      const json = await response.json().catch(() => ({}));
      const username = json?.result?.username;

      if (!response.ok || !json?.ok || typeof username !== 'string' || username.trim().length === 0) {
        return null;
      }

      return username.trim().replace(/^@/, '');
    } catch {
      return null;
    }
  }

  private extractErrorMessage(error: any, fallback = 'Unable to create checkout') {
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

    if (typeof error?.response?.message === 'string' && error.response.message.trim().length > 0) {
      return error.response.message;
    }

    if (Array.isArray(error?.response?.message) && error.response.message.length > 0) {
      return error.response.message.join(', ');
    }

    return fallback;
  }

  private fail(message: string): never {
    throw new RpcException({ message });
  }
}
