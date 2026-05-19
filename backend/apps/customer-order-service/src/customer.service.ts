import { Injectable, Inject } from '@nestjs/common';
import { ClientProxy, RpcException } from '@nestjs/microservices';
import { PrismaService } from '@app/database';
import { firstValueFrom } from 'rxjs';
import {
  AssignmentAccessStatus,
  InventoryHealthStatus,
  OrderStatus,
  Prisma,
  TrialCampaignStatus,
  TrialConfigStatus,
  TrialGrantStatus,
} from '@prisma/client';
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
import { SwimPayService } from './swim-pay.service';
import {
  looksLikeTelegramBotToken,
  normalizeConfiguredPaymentBotUsername,
  selectManualPaymentBotToken,
} from './payment-bot-routing';

@Injectable()
export class CustomerService {
  private static readonly TRIAL_DURATION_MS = 3 * 24 * 60 * 60 * 1000;
  private static readonly TRIAL_QUOTA_LABEL = 'UNLIMITED';
  private static readonly ACTIVE_TRIAL_CAMPAIGN_CODE = 'trial-2026-05';

  constructor(
    private readonly prisma: PrismaService,
    @Inject('INVENTORY_SERVICE') private readonly inventoryClient: ClientProxy,
    @Inject('VPN_CONFIG_SERVICE') private readonly vpnConfigClient: ClientProxy,
    private readonly cryptoPayService: CryptoPayService,
    private readonly swimPayService?: SwimPayService,
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
        userNumber: data.userNumber,
        deviceId: data.deviceId,
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

      if (data.paymentMethod === 'SWIMPAY') {
        if (!this.swimPayService?.isConfigured()) {
          this.fail('SwimPay API is not configured');
        }

        const swimPayCheckout = await this.swimPayService.createCheckout({
          amountRub: checkout.plan.price_rub.toString(),
          orderRef: checkout.order.order_ref,
          planLabel: `${checkout.plan.name} - ${checkout.plan.duration_label} - ${checkout.plan.quota_label}`,
          customerPhone: checkout.customer.phone,
        });

        await this.prisma.order.update({
          where: { id: checkout.order.id },
          data: {
            payment_ref: `SWIMPAY_SESSION:${swimPayCheckout.paymentSessionId}:${swimPayCheckout.orderId}`,
          },
        });

        return {
          orderRef: checkout.order.order_ref,
          status: checkout.order.status,
          amountRub: checkout.plan.price_rub.toString(),
          paymentMethod: 'SWIMPAY',
          redirectUrl: swimPayCheckout.checkoutUrl,
          message: 'SwimPay checkout created',
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

    const currentProfile = await this.getProfile(customer.public_id, { exposeRuntimeConfig: false });
    if (currentProfile.entitlementState === 'ACTIVE_SUBSCRIPTION') {
      this.fail('Active subscription already exists');
    }
    if (await this.hasActivePaidAssignment(customer.id)) {
      this.fail('Active subscription already exists');
    }
    if (await this.hasPaidFulfillmentInProgress(customer.id)) {
      this.fail('Paid subscription fulfillment is already in progress');
    }

    if (!(await this.findActiveTrialCampaign())) {
      this.fail('Trial campaign is closed');
    }

    const trialEligible = await this.isTrialEligible(customer, normalizedEmail, normalizedPhone);
    if (!trialEligible) {
      this.fail('Trial already used for this device or contact data');
    }

    await this.prisma.customer.update({
      where: { id: customer.id },
      data: {
        email: normalizedEmail,
        phone: normalizedPhone,
      },
    });

    await this.activateTrialFromStore(customer.id, customer.public_id, {
      email: normalizedEmail,
      phone: normalizedPhone,
      deviceId: normalizedDeviceId,
    });

    return this.getProfile(customer.public_id);
  }

  private async activateTrialFromStore(
    customerId: string,
    userNumber: string,
    identity: { email?: string | null; phone?: string | null; deviceId?: string | null },
  ) {
    const now = new Date();

    return this.prisma.$transaction(async (tx) => {
      const campaign = await tx.trialCampaign.findFirst({
        where: {
          code: CustomerService.ACTIVE_TRIAL_CAMPAIGN_CODE,
          status: TrialCampaignStatus.ACTIVE,
          starts_at: { lte: now },
          ends_at: { gte: now },
        },
      });

      if (!campaign) {
        this.fail('Trial campaign is closed');
      }

      const existingGrant = await tx.trialGrant.findFirst({
        where: {
          customer_id: customerId,
          campaign_id: campaign.id,
        },
      });

      if (existingGrant) {
        this.fail('Trial already used for this campaign');
      }

      const grant = await tx.trialGrant.create({
        data: {
          customer_id: customerId,
          campaign_id: campaign.id,
          identity_email: identity.email?.trim().toLowerCase() || null,
          identity_phone: this.normalizePhone(identity.phone || undefined),
          identity_device_id: identity.deviceId?.trim() || null,
          status: TrialGrantStatus.PENDING,
          expires_at: campaign.ends_at,
          status_reason: 'AWAITING_TRIAL_CONFIG',
        },
      }).catch((error) => {
        if (this.isPrismaUniqueConstraintError(error)) {
          this.fail('Trial already used for this campaign');
        }

        throw error;
      });

      const candidate = await tx.trialConfig.findFirst({
        where: {
          campaign_id: campaign.id,
          status: TrialConfigStatus.AVAILABLE,
          OR: [
            { supplier_expires_at: null },
            { supplier_expires_at: { gt: now } },
          ],
        },
        orderBy: { imported_at: 'asc' },
      });

      if (!candidate) {
        await tx.adminEvent.create({
          data: {
            event_type: 'TRIAL_PENDING_NO_CAPACITY',
            entity_type: 'TRIAL_GRANT',
            entity_id: grant.id,
            payload_json: {
              userNumber,
              campaignCode: campaign.code,
              grantId: grant.id,
              createdAt: now.toISOString(),
            } as any,
          },
        });

        return grant;
      }

      const lockedConfig = await tx.trialConfig.updateMany({
        where: {
          id: candidate.id,
          status: TrialConfigStatus.AVAILABLE,
        },
        data: {
          status: TrialConfigStatus.ASSIGNED,
          assigned_at: now,
        },
      });

      if (lockedConfig.count !== 1) {
        await tx.adminEvent.create({
          data: {
            event_type: 'TRIAL_PENDING_NO_CAPACITY',
            entity_type: 'TRIAL_GRANT',
            entity_id: grant.id,
            payload_json: {
              userNumber,
              campaignCode: campaign.code,
              grantId: grant.id,
              reason: 'TRIAL_CONFIG_RACE_LOST',
              createdAt: now.toISOString(),
            } as any,
          },
        });

        return grant;
      }

      const campaignExpiresAt = new Date(
        now.getTime() + Math.max(campaign.duration_days, 1) * 24 * 60 * 60 * 1000,
      );
      const expiresAt = this.pickEarlierDate(campaignExpiresAt, candidate.supplier_expires_at);

      await tx.trialAssignment.create({
        data: {
          grant_id: grant.id,
          trial_config_id: candidate.id,
          customer_id: customerId,
          status: TrialGrantStatus.ACTIVE,
          assigned_at: now,
          expires_at: expiresAt,
        },
      });

      const activeGrant = await tx.trialGrant.update({
        where: { id: grant.id },
        data: {
          status: TrialGrantStatus.ACTIVE,
          assigned_at: now,
          expires_at: expiresAt,
          status_reason: null,
        },
      });

      await tx.adminEvent.create({
        data: {
          event_type: 'TRIAL_CONFIG_ASSIGNED',
          entity_type: 'TRIAL_GRANT',
          entity_id: activeGrant.id,
          payload_json: {
            userNumber,
            campaignCode: campaign.code,
            grantId: activeGrant.id,
            trialConfigId: candidate.id,
            expiresAt: expiresAt?.toISOString() || null,
            assignedAt: now.toISOString(),
          } as any,
        },
      });

      return activeGrant;
    });
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
              in: [
                OrderStatus.FULFILLED,
                OrderStatus.PAID,
                OrderStatus.PENDING_FULFILLMENT,
                OrderStatus.CANCELLED,
              ],
            },
          },
          orderBy: { created_at: 'desc' },
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

    const activeAssignmentRows =
      typeof (this.prisma as any).orderAssignment?.findMany === 'function'
        ? await this.prisma.orderAssignment.findMany({
            where: {
              customer_id: customer.id,
              access_status: AssignmentAccessStatus.ACTIVE,
            },
            orderBy: { assigned_at: 'desc' },
            include: {
              order: true,
              inventory_item: true,
            },
          })
        : customer.orders
            .flatMap((order) =>
              order.assignments
                .filter((assignment) => assignment.access_status === AssignmentAccessStatus.ACTIVE)
                .map((assignment) => ({ ...assignment, order })),
            );
    const activeAssignments = activeAssignmentRows.map((assignment) => ({
      order: assignment.order,
      assignment,
    }));
    const pendingOrders =
      typeof (this.prisma as any).order?.findMany === 'function'
        ? await this.prisma.order.findMany({
            where: {
              customer_id: customer.id,
              status: {
                in: [OrderStatus.PAID, OrderStatus.PENDING_FULFILLMENT],
              },
            },
            orderBy: { created_at: 'desc' },
          })
        : customer.orders.filter(
            (order) =>
              order.status === OrderStatus.PAID ||
              order.status === OrderStatus.PENDING_FULFILLMENT,
          );

    if (activeAssignments.length === 0) {
      if (pendingOrders.length > 0) {
        for (const pendingOrder of pendingOrders) {
          await this.prisma.order.update({
            where: { id: pendingOrder.id },
            data: { status: OrderStatus.CANCELLED },
          });
        }

        await this.prisma.adminEvent.create({
          data: {
            event_type: 'CUSTOMER_PENDING_ORDER_CANCELLED',
            entity_type: pendingOrders.length === 1 ? 'ORDER' : 'CUSTOMER',
            entity_id: pendingOrders.length === 1 ? pendingOrders[0].order_ref : customer.public_id,
            payload_json: {
              userNumber: customer.public_id,
              orderRefs: pendingOrders.map((order) => order.order_ref),
              previousStatuses: pendingOrders.map((order) => ({
                orderRef: order.order_ref,
                status: order.status,
              })),
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

    for (const item of activeAssignments) {
      await firstValueFrom(
        this.inventoryClient.send(
          { cmd: 'revoke_assignment' },
          {
            assignmentId: item.assignment.id,
            reason,
            adminId: null,
          },
        ),
      );
    }
    for (const pendingOrder of pendingOrders) {
      await this.prisma.order.update({
        where: { id: pendingOrder.id },
        data: { status: OrderStatus.CANCELLED },
      }).catch(() => undefined);
    }

    await this.prisma.adminEvent.create({
      data: {
        event_type: 'CUSTOMER_SUBSCRIPTION_CANCELLED',
        entity_type: activeAssignments.length === 1 ? 'ORDER_ASSIGNMENT' : 'CUSTOMER',
        entity_id: activeAssignments.length === 1 ? activeAssignments[0].assignment.id : customer.public_id,
        payload_json: {
          userNumber: customer.public_id,
          revokedAssignments: activeAssignments.map((item) => ({
            assignmentId: item.assignment.id,
            orderRef: item.order.order_ref,
            inventoryItemId: item.assignment.inventory_item_id,
          })),
          cancelledPendingOrderRefs: pendingOrders.map((order) => order.order_ref),
          reason,
          cancelledAt: new Date().toISOString(),
          slotPolicy: 'all active assignments revoked through inventory service; resale capacity recalculated',
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
    const directAssignmentRows =
      typeof (this.prisma as any).orderAssignment?.findMany === 'function'
        ? await this.prisma.orderAssignment.findMany({
            where: {
              customer_id: customer.id,
              access_status: {
                in: [
                  AssignmentAccessStatus.ACTIVE,
                  AssignmentAccessStatus.PENDING,
                  AssignmentAccessStatus.EXPIRED,
                ],
              },
              order: {
                status: {
                  in: [OrderStatus.FULFILLED, OrderStatus.PAID, OrderStatus.PENDING_FULFILLMENT],
                },
              },
            },
            orderBy: { assigned_at: 'desc' },
            include: {
              order: {
                include: { plan: true },
              },
              inventory_item: true,
            },
          })
        : [];
    const directAssignmentOrdersById = new Map<string, any>();
    for (const assignment of directAssignmentRows as any[]) {
      const { order, ...assignmentWithoutOrder } = assignment;
      if (!order?.id) {
        continue;
      }

      const existingOrder = directAssignmentOrdersById.get(order.id);
      if (existingOrder) {
        existingOrder.assignments.push(assignmentWithoutOrder);
      } else {
        directAssignmentOrdersById.set(order.id, {
          ...order,
          assignments: [assignmentWithoutOrder],
        });
      }
    }
    const directAssignmentOrders = Array.from(directAssignmentOrdersById.values());
    const directPendingOrders =
      typeof (this.prisma as any).order?.findMany === 'function'
        ? await this.prisma.order.findMany({
            where: {
              customer_id: customer.id,
              status: {
                in: [OrderStatus.PAID, OrderStatus.PENDING_FULFILLMENT],
              },
            },
            orderBy: { created_at: 'desc' },
            include: {
              plan: true,
              assignments: {
                orderBy: { assigned_at: 'desc' },
                include: { inventory_item: true },
              },
            },
          })
        : [];
    const ordersById = new Map<string, any>();
    for (const order of [...directAssignmentOrders, ...directPendingOrders, ...customer.orders]) {
      if (order?.id && !ordersById.has(order.id)) {
        ordersById.set(order.id, order);
      }
    }
    const relevantOrders = Array.from(ordersById.values());
    const activeOrders = relevantOrders.filter((order) =>
      order.assignments.some((item) => item.access_status === AssignmentAccessStatus.ACTIVE),
    );
    const latestPaidActiveOrder = activeOrders.find((order) => !this.isTrialOrder(order));
    const latestLegacyActiveTrialOrder = activeOrders.find((order) => this.isTrialOrder(order));
    const latestPendingOrder = relevantOrders.find((order) =>
      order.status === OrderStatus.PAID ||
      order.status === OrderStatus.PENDING_FULFILLMENT ||
      order.assignments.some((item) => item.access_status === AssignmentAccessStatus.PENDING),
    );
    const latestPaidPendingOrder = relevantOrders.find((order) =>
      !this.isTrialOrder(order) &&
      (
        order.status === OrderStatus.PAID ||
        order.status === OrderStatus.PENDING_FULFILLMENT ||
        order.assignments.some((item) => item.access_status === AssignmentAccessStatus.PENDING)
      ),
    );
    const hasPaidFulfillmentInProgress = !!latestPaidPendingOrder;
    const trialStoreProfile = await this.buildTrialStoreProfile(
      customer,
      trialEligible,
      exposeRuntimeConfig,
    );
    if (!latestPaidActiveOrder && trialStoreProfile?.priority === 'ACTIVE') {
      return this.finalizeTrialStoreProfile(
        trialStoreProfile.profile,
        hasPaidFulfillmentInProgress,
      );
    }

    const latestActiveOrder =
      latestPaidActiveOrder ||
      latestLegacyActiveTrialOrder;
    const latestExpiredOrder = relevantOrders.find((order) =>
      order.assignments.some((item) => item.access_status === AssignmentAccessStatus.EXPIRED),
    );
    const latestPaidExpiredOrder = relevantOrders.find((order) =>
      !this.isTrialOrder(order) &&
      order.assignments.some((item) => item.access_status === AssignmentAccessStatus.EXPIRED),
    );
    if (!latestActiveOrder && !latestPaidPendingOrder && !latestPaidExpiredOrder && trialStoreProfile) {
      return this.finalizeTrialStoreProfile(trialStoreProfile.profile, false);
    }

    const latestRelevantOrder = latestActiveOrder ?? latestPendingOrder ?? latestExpiredOrder;
    const activeAssignment = latestRelevantOrder?.assignments.find(
      (item) =>
        item.access_status === AssignmentAccessStatus.ACTIVE,
    );
    const pendingAssignment = latestRelevantOrder?.assignments.find(
      (item) => item.access_status === AssignmentAccessStatus.PENDING,
    );
    const expiredAssignment = latestRelevantOrder?.assignments.find(
      (item) => item.access_status === AssignmentAccessStatus.EXPIRED,
    );
    const assignment = activeAssignment ?? pendingAssignment ?? expiredAssignment;
    const isPendingOrderWithoutAssignment = !!latestRelevantOrder &&
      !assignment &&
      (
        latestRelevantOrder.status === OrderStatus.PAID ||
        latestRelevantOrder.status === OrderStatus.PENDING_FULFILLMENT
      );
    const latestOrder = latestRelevantOrder && (assignment || isPendingOrderWithoutAssignment)
      ? latestRelevantOrder
      : undefined;
    const hasPaidOrderHistory = relevantOrders.some((order) => !this.isTrialOrder(order));
    const effectiveTrialEligible = latestOrder ? trialEligible : trialEligible && !hasPaidOrderHistory;
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
    const orderExpiresAt = latestOrder && isTrialOrder
      ? this.calculateSubscriptionExpiresAt(latestOrder, isTrialOrder)
      : null;
    const providerExpiresAt =
      assignment?.expires_at?.toISOString() ||
      inventoryItem?.supplier_expires_at?.toISOString() ||
      null;
    const subscriptionExpiresAt = isTrialOrder
      ? this.pickEarlierIsoDate(orderExpiresAt, providerExpiresAt)
      : providerExpiresAt;
    const trialExpiresAt = isTrialOrder ? subscriptionExpiresAt : null;
    const measuredDataLimitGb =
      latestOrder && !isTrialOrder
        ? this.parseQuotaLabelToGb(latestOrder.plan.quota_label || '')
        : 0;
    const measuredDataUsedBytes = assignment?.measured_used_bytes?.toString() || '0';
    const entitlementState = this.resolveEntitlementState({
      hasCompletedProfile: !!customer.email && !!customer.phone,
      hasOrder: !!latestOrder,
      trialEligible: effectiveTrialEligible,
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
      fulfillmentStatus:
        hasPaidFulfillmentInProgress &&
        entitlementState === 'ACTIVE_TRIAL'
          ? 'PENDING_FULFILLMENT'
          : fulfillmentStatus,
      dataLimitGB: isTrialOrder ? 0 : measuredDataLimitGb,
      dataUsedBytes: isTrialOrder ? '0' : measuredDataUsedBytes,
      supplierProviderName: inventoryItem?.supplier_provider_name || null,
      supplierExpiresAt: inventoryItem?.supplier_expires_at?.toISOString() || null,
      profileCompletionRequired: !customer.email || !customer.phone,
      trialEligible: effectiveTrialEligible,
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
      throw new Error('PREMIUM_REQUIRED: Active access is required to resolve encrypted subscription payloads');
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
    const activeOrders =
      customer.orders.filter((order) => {
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
        const orderExpiresAt = isTrialOrder
          ? this.calculateSubscriptionExpiresAt(order, isTrialOrder)
          : null;
        const providerExpiresAt =
          activeAssignment.expires_at?.toISOString() ||
          inventoryItem.supplier_expires_at?.toISOString() ||
          null;
        const subscriptionExpiresAt = isTrialOrder
          ? this.pickEarlierIsoDate(orderExpiresAt, providerExpiresAt)
          : providerExpiresAt;

        return !subscriptionExpiresAt || new Date(subscriptionExpiresAt).getTime() >= now;
      });
    const bestActiveOrder =
      activeOrders.find((order) => !this.isTrialOrder(order)) ||
      activeOrders.find((order) => this.isTrialOrder(order)) ||
      null;
    const latestOrder = bestActiveOrder;
    if (!latestOrder) {
      return this.getProfile(customer.public_id);
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

  async handleSwimPayWebhook(data: { rawBody: string; headers: Record<string, string | string[] | number | undefined> }) {
    if (!this.swimPayService?.isConfigured()) {
      throw new Error('SwimPay API is not configured');
    }

    const event = this.swimPayService.verifyWebhook(data.rawBody, data.headers);
    const orderRef = event.data.externalOrderId;
    if (!orderRef) {
      return { received: true, ignored: true, error: 'Missing external order id' };
    }

    const existingEvent = await this.prisma.adminEvent.findFirst({
      where: {
        event_type: 'SWIMPAY_WEBHOOK_RECEIVED',
        entity_id: event.id,
      },
    }).catch(() => null);

    if (existingEvent) {
      return { received: true, duplicate: true, eventId: event.id };
    }

    const swimPayOrderCheck = await this.validateSwimPayWebhookOrder(orderRef, event);
    if (swimPayOrderCheck.valid === false) {
      return {
        received: true,
        eventId: event.id,
        ignored: true,
        error: swimPayOrderCheck.error,
      };
    }

    await this.prisma.adminEvent.create({
      data: {
        event_type: 'SWIMPAY_WEBHOOK_RECEIVED',
        entity_type: 'PAYMENT_WEBHOOK',
        entity_id: event.id,
        payload_json: {
          type: event.type,
          externalOrderId: event.data.externalOrderId || null,
          orderId: event.data.orderId,
          paymentSessionId: event.data.paymentSessionId,
          amountMinor: event.data.amountMinor,
          currency: event.data.currency,
          confirmationType: event.data.confirmationType || null,
          officialBankConfirmation: false,
          decision: event.data.decision || null,
          receivedAt: new Date().toISOString(),
        } as any,
      },
    });

    if (event.type === 'payment.confirmed') {
      const result = await this.fulfillOrderByRef(
        orderRef,
        `SWIMPAY_CONFIRMED:${event.data.paymentSessionId}:${event.id}`,
      );
      return {
        received: true,
        eventId: event.id,
        ...result,
      };
    }

    const order = swimPayOrderCheck.order;
    if (order?.status === OrderStatus.PENDING) {
      await this.prisma.order.update({
        where: { id: order.id },
        data: {
          status: OrderStatus.FAILED,
          payment_ref: `SWIMPAY_${event.type === 'payment.expired' ? 'EXPIRED' : 'REJECTED'}:${event.data.paymentSessionId}:${event.id}`,
        },
      });
    }

    return {
      received: true,
      eventId: event.id,
      terminal: true,
      type: event.type,
    };
  }

  private async validateSwimPayWebhookOrder(
    orderRef: string,
    event: ReturnType<SwimPayService['verifyWebhook']>,
  ): Promise<{ valid: true; order: any } | { valid: false; error: string }> {
    const order = await this.prisma.order.findUnique({
      where: { order_ref: orderRef },
    });

    if (!order) return { valid: false, error: 'Order not found' };

    const storedPayment = this.parseSwimPaySessionRef(order.payment_ref);
    if (!storedPayment) {
      return { valid: false, error: 'Order is not bound to a pending SwimPay session' };
    }

    if (storedPayment.paymentSessionId !== event.data.paymentSessionId || storedPayment.orderId !== event.data.orderId) {
      return { valid: false, error: 'SwimPay webhook session does not match order' };
    }

    if (event.data.amountMinor < this.orderAmountRubMinor(order.amount_rub)) {
      return { valid: false, error: 'SwimPay webhook amount is below order amount' };
    }

    if (event.data.currency.toUpperCase() !== 'RUB') {
      return { valid: false, error: 'SwimPay webhook currency does not match order' };
    }

    return { valid: true, order };
  }

  private parseSwimPaySessionRef(paymentRef?: string | null) {
    if (!paymentRef) return null;

    const match = paymentRef.match(/^SWIMPAY_SESSION:([^:]+):([^:]+)$/);
    if (!match) return null;

    return {
      paymentSessionId: match[1],
      orderId: match[2],
    };
  }

  private orderAmountRubMinor(amountRub: unknown) {
    const normalized = String(amountRub).replace(',', '.');
    if (!/^\d+(?:\.\d{1,2})?$/.test(normalized)) return Number.NaN;

    const [whole = '0', decimals = ''] = normalized.split('.');
    return Number(whole) * 100 + Number(decimals.padEnd(2, '0'));
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

  private async preparePendingOrder(data: {
    email?: string;
    phone?: string;
    planId: string;
    userNumber?: string;
    deviceId?: string;
  }) {
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

    const customer = await this.findOrCreateCheckoutCustomer({
      email: normalizedEmail,
      phone: normalizedPhone,
      userNumber: data.userNumber,
      deviceId: data.deviceId,
    });
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

  private async findOrCreateCheckoutCustomer(data: {
    email: string;
    phone?: string;
    userNumber?: string;
    deviceId?: string;
  }) {
    const userNumber = data.userNumber?.trim();
    const deviceId = this.normalizeOptionalDeviceId(data.deviceId);

    if (userNumber && !deviceId) {
      this.fail('Device is required for user-bound checkout');
    }

    if (userNumber) {
      const customer = await this.prisma.customer.findUnique({
        where: { public_id: userNumber },
      });

      if (customer) {
        if (!customer.device_id || customer.device_id !== deviceId) {
          this.fail('Device is not authorized for checkout');
        }

        return this.updateCheckoutCustomerContact(customer, data.email, data.phone);
      }
    }

    if (deviceId) {
      const customer = await this.prisma.customer.findUnique({
        where: { device_id: deviceId },
      });

      if (customer) {
        return this.updateCheckoutCustomerContact(customer, data.email, data.phone);
      }
    }

    return this.findOrCreateCustomerByContact(data.email, data.phone);
  }

  private async updateCheckoutCustomerContact(
    customer: { id: string; email?: string | null; phone?: string | null },
    email: string,
    phone?: string,
  ) {
    const nextPhone = phone ?? customer.phone ?? undefined;
    if (customer.email !== email || customer.phone !== nextPhone) {
      return this.prisma.customer.update({
        where: { id: customer.id },
        data: {
          email,
          phone: nextPhone,
        },
      });
    }

    return customer;
  }

  private normalizeDeviceId(deviceId?: string) {
    const normalized = deviceId?.trim();
    if (!normalized || ['unknown_device_id', 'unknown', 'null'].includes(normalized.toLowerCase())) {
      this.fail('Valid device id is required');
    }

    return normalized;
  }

  private normalizeOptionalDeviceId(deviceId?: string) {
    const normalized = deviceId?.trim();
    if (!normalized || ['unknown_device_id', 'unknown', 'null'].includes(normalized.toLowerCase())) {
      return undefined;
    }

    return normalized;
  }

  private finalizeTrialStoreProfile(profile: any, hasPaidFulfillmentInProgress: boolean) {
    const nextProfile = hasPaidFulfillmentInProgress
      ? { ...profile, fulfillmentStatus: 'PENDING_FULFILLMENT' }
      : profile;

    if (!nextProfile.profileCompletionRequired) {
      return nextProfile;
    }

    return {
      ...nextProfile,
      status: 'PROFILE_INCOMPLETE',
      entitlementState: 'PROFILE_INCOMPLETE',
      subscriptionUrl: null,
    };
  }

  private async buildTrialStoreProfile(
    customer: { id: string; public_id: string; email?: string | null; phone?: string | null },
    trialEligible: boolean,
    exposeRuntimeConfig: boolean,
  ): Promise<{ priority: 'ACTIVE' | 'PENDING' | 'EXPIRED'; profile: any } | null> {
    if (typeof (this.prisma as any).trialGrant?.findFirst !== 'function') {
      return null;
    }

    const grant = await (this.prisma as any).trialGrant.findFirst({
      where: {
        customer_id: customer.id,
        status: {
          in: [
            TrialGrantStatus.ACTIVE,
            TrialGrantStatus.PENDING,
            TrialGrantStatus.EXPIRED,
          ],
        },
      },
      orderBy: { started_at: 'desc' },
      include: {
        campaign: true,
        assignments: {
          orderBy: { assigned_at: 'desc' },
          include: { trial_config: true },
        },
      },
    }).catch((error) => {
      if (this.isPrismaSchemaNotReadyError(error)) {
        return null;
      }

      throw error;
    });

    if (!grant) {
      return null;
    }

    const assignment = grant.assignments?.find(
      (item) => item.status === TrialGrantStatus.ACTIVE && !item.revoked_at,
    );
    const trialConfig =
      assignment?.trial_config?.status === TrialConfigStatus.ASSIGNED
        ? assignment.trial_config
        : null;
    const now = Date.now();
    let expiresAt = grant.expires_at?.toISOString?.() || null;
    expiresAt = this.pickEarlierIsoDate(expiresAt, assignment?.expires_at?.toISOString?.() || null);
    expiresAt = this.pickEarlierIsoDate(expiresAt, trialConfig?.supplier_expires_at?.toISOString?.() || null);
    expiresAt = this.pickEarlierIsoDate(expiresAt, grant.campaign?.ends_at?.toISOString?.() || null);
    const isExpired = !!expiresAt && new Date(expiresAt).getTime() < now;
    if (isExpired && grant.status !== TrialGrantStatus.EXPIRED) {
      await this.persistExpiredTrialGrant(grant, customer.public_id, expiresAt);
    }

    const isActive = grant.status === TrialGrantStatus.ACTIVE && !!trialConfig && !isExpired;
    const isPending = !isExpired && grant.status === TrialGrantStatus.PENDING;
    const entitlementState = isActive
      ? 'ACTIVE_TRIAL'
      : isPending
        ? 'PENDING_FULFILLMENT'
        : 'EXPIRED_TRIAL';
    const priority = isActive ? 'ACTIVE' : isPending ? 'PENDING' : 'EXPIRED';

    return {
      priority,
      profile: {
        userNumber: customer.public_id,
        email: customer.email,
        phone: customer.phone,
        accessType: 'TRIAL',
        offerCode: null,
        planDisplayName: null,
        planType: 'TRIAL',
        status: this.toLegacyProfileStatus(entitlementState),
        entitlementState,
        trialStartedAt:
          grant.assigned_at?.toISOString?.() ||
          assignment?.assigned_at?.toISOString?.() ||
          grant.started_at?.toISOString?.() ||
          null,
        trialExpiresAt: expiresAt,
        subscriptionExpiresAt: expiresAt,
        subscriptionUrl: exposeRuntimeConfig && isActive ? trialConfig.raw_config : null,
        devicesAllowed: 1,
        fulfillmentStatus: isActive ? 'DELIVERED' : isPending ? 'PENDING_FULFILLMENT' : 'DELIVERED',
        dataLimitGB: 0,
        dataUsedBytes: '0',
        supplierProviderName: trialConfig?.supplier_provider_name || null,
        supplierExpiresAt: trialConfig?.supplier_expires_at?.toISOString?.() || null,
        profileCompletionRequired: !customer.email || !customer.phone,
        trialEligible: false,
      },
    };
  }

  private async persistExpiredTrialGrant(
    grant: { id: string },
    userNumber: string,
    expiresAt: string | null,
  ) {
    try {
      const expiredAt = new Date();
      const updateResult = await (this.prisma as any).trialGrant.updateMany({
        where: {
          id: grant.id,
          status: { in: [TrialGrantStatus.ACTIVE, TrialGrantStatus.PENDING] },
        },
        data: {
          status: TrialGrantStatus.EXPIRED,
          status_reason: 'TRIAL_EXPIRED',
        },
      });

      await (this.prisma as any).trialAssignment.updateMany({
        where: {
          grant_id: grant.id,
          status: { in: [TrialGrantStatus.ACTIVE, TrialGrantStatus.PENDING] },
        },
        data: {
          status: TrialGrantStatus.EXPIRED,
          status_reason: 'TRIAL_EXPIRED',
        },
      });

      if (updateResult?.count > 0) {
        await this.prisma.adminEvent.create({
          data: {
            event_type: 'TRIAL_EXPIRED',
            entity_type: 'TRIAL_GRANT',
            entity_id: grant.id,
            payload_json: {
              userNumber,
              grantId: grant.id,
              expiresAt,
              markedExpiredAt: expiredAt.toISOString(),
            } as any,
          },
        }).catch(() => undefined);
      }
    } catch (error) {
      if (!this.isPrismaSchemaNotReadyError(error)) {
        throw error;
      }
    }
  }

  private async isTrialEligible(customer: { id: string; device_id: string | null }, email?: string | null, phone?: string | null) {
    const campaign = await this.findActiveTrialCampaign();
    if (!campaign) {
      return false;
    }

    if (typeof (this.prisma as any).trialGrant?.findFirst === 'function') {
      const existingCustomerGrant = await (this.prisma as any).trialGrant.findFirst({
        where: {
          customer_id: customer.id,
          campaign_id: campaign.id,
        },
      }).catch((error) => {
        if (this.isPrismaSchemaNotReadyError(error)) {
          return null;
        }

        throw error;
      });

      if (existingCustomerGrant) {
        return false;
      }
    }

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

    const identityGrantConditions: Array<Record<string, string>> = [];
    if (normalizedEmail) {
      identityGrantConditions.push({ identity_email: normalizedEmail });
    }
    if (normalizedPhone) {
      identityGrantConditions.push({ identity_phone: normalizedPhone });
    }
    if (customer.device_id) {
      identityGrantConditions.push({ identity_device_id: customer.device_id });
    }

    if (
      identityGrantConditions.length > 0 &&
      typeof (this.prisma as any).trialGrant?.findFirst === 'function'
    ) {
      const existingIdentityGrant = await (this.prisma as any).trialGrant.findFirst({
        where: {
          campaign_id: campaign.id,
          customer_id: { not: customer.id },
          OR: identityGrantConditions,
        },
      }).catch((error) => {
        if (this.isPrismaSchemaNotReadyError(error)) {
          return null;
        }

        throw error;
      });

      if (existingIdentityGrant) {
        return false;
      }
    }

    if (typeof (this.prisma as any).trialGrant?.findFirst === 'function') {
      const existingGrant = await (this.prisma as any).trialGrant.findFirst({
        where: {
          campaign_id: campaign.id,
          customer_id: { not: customer.id },
          customer: {
            OR: customerConditions,
          },
        },
      }).catch((error) => {
        if (this.isPrismaSchemaNotReadyError(error)) {
          return null;
        }

        throw error;
      });

      if (existingGrant) {
        return false;
      }
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

  private async findActiveTrialCampaign() {
    if (typeof (this.prisma as any).trialCampaign?.findFirst !== 'function') {
      return null;
    }

    const now = new Date();
    return (this.prisma as any).trialCampaign.findFirst({
      where: {
        code: CustomerService.ACTIVE_TRIAL_CAMPAIGN_CODE,
        status: TrialCampaignStatus.ACTIVE,
        starts_at: { lte: now },
        ends_at: { gte: now },
      },
    }).catch((error) => {
      if (this.isPrismaSchemaNotReadyError(error)) {
        return null;
      }

      throw error;
    });
  }

  private isPrismaUniqueConstraintError(error: unknown) {
    return error instanceof Prisma.PrismaClientKnownRequestError && error.code === 'P2002';
  }

  private isPrismaSchemaNotReadyError(error: unknown) {
    return error instanceof Prisma.PrismaClientKnownRequestError &&
      (error.code === 'P2021' || error.code === 'P2022');
  }

  private async hasActivePaidAssignment(customerId: string) {
    if (typeof (this.prisma as any).orderAssignment?.findFirst !== 'function') {
      return false;
    }

    const activePaidAssignment = await this.prisma.orderAssignment.findFirst({
      where: {
        customer_id: customerId,
        access_status: AssignmentAccessStatus.ACTIVE,
        order: {
          NOT: {
            OR: [
              { payment_ref: 'TRIAL:3D' },
              { order_ref: { startsWith: 'TRIAL-' } },
            ],
          },
        },
      },
    });

    return !!activePaidAssignment;
  }

  private async hasPaidFulfillmentInProgress(customerId: string) {
    const pendingPaidOrder = await this.prisma.order.findFirst({
      where: {
        customer_id: customerId,
        status: {
          in: [OrderStatus.PAID, OrderStatus.PENDING_FULFILLMENT],
        },
        NOT: {
          OR: [
            { payment_ref: 'TRIAL:3D' },
            { order_ref: { startsWith: 'TRIAL-' } },
          ],
        },
      },
    });

    return !!pendingPaidOrder;
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

  private pickEarlierDate(first: Date, second?: Date | null) {
    if (!second) {
      return first;
    }

    return first.getTime() <= second.getTime() ? first : second;
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
