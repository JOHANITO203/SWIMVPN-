import { Injectable, Inject, Logger, OnModuleDestroy, OnModuleInit } from '@nestjs/common';
import { ClientProxy } from '@nestjs/microservices';
import { PrismaService } from '@app/database';
import { ImportConfigsDto, ImportTrialConfigsDto } from '@app/contracts/inventory.dto';
import * as crypto from 'crypto';
import {
  DEFAULT_SUPPLIER_DEVICE_LIMIT,
  SwimVpnProfile,
} from '@app/contracts';
import { firstValueFrom } from 'rxjs';
import {
  AssignmentAccessStatus,
  ConfigEventType,
  InventoryHealthStatus,
  InventoryStatus,
  OrderStatus,
  PlanCategory,
  Prisma,
  TrialCampaignStatus,
  TrialConfigStatus,
  TrialGrantStatus,
} from '@prisma/client';
import { canAllocateSupplierConfig } from './supplier-capacity.policy';
import {
  resolveFulfillmentRetryIntervalMs,
  resolveInventoryHealthcheckIntervalMs,
} from './inventory-health-scheduler.policy';
import { ResupplyOrchestrator } from './resupply-orchestrator';
import {
  parseStockThresholds,
  resolveThreshold,
  parsePositiveIntEnv,
  computeStockForecast,
  DEFAULT_VELOCITY_WINDOW_DAYS,
  DEFAULT_TARGET_DAYS_COVER,
  DEFAULT_FORECAST_ALERT_DAYS,
} from './stock-intelligence.policy';

type ConfigEventInput = {
  configScope: 'PAID' | 'TRIAL';
  configId: string;
  folderCode?: string | null;
  eventType: ConfigEventType;
  payload: Record<string, unknown>;
};

@Injectable()
export class InventoryService implements OnModuleInit, OnModuleDestroy {
  private static readonly DEFAULT_SOURCE_QUOTA_GB = 1000n;
  private static readonly DEFAULT_MAX_USERS_PER_CONFIG = 5;
  private static readonly TRIAL_QUOTA_LABEL = 'UNLIMITED';
  private static readonly TRIAL_DURATION_LABEL = '3 Days';
  private readonly logger = new Logger(InventoryService.name);
  private healthcheckTimer?: NodeJS.Timeout;
  private fulfillmentRetryTimer?: NodeJS.Timeout;
  private fulfillmentRetryInFlight = false;

  // Proactive stock intelligence (read-only analytics; thresholds/forecast/reorder).
  private readonly stockThresholds = parseStockThresholds(process.env.STOCK_THRESHOLDS);
  private readonly stockVelocityWindowDays = parsePositiveIntEnv(
    process.env.STOCK_VELOCITY_WINDOW_DAYS,
    DEFAULT_VELOCITY_WINDOW_DAYS,
  );
  private readonly stockTargetDaysCover = parsePositiveIntEnv(
    process.env.STOCK_TARGET_DAYS_COVER,
    DEFAULT_TARGET_DAYS_COVER,
  );
  private readonly stockForecastAlertDays = parsePositiveIntEnv(
    process.env.STOCK_FORECAST_ALERT_DAYS,
    DEFAULT_FORECAST_ALERT_DAYS,
  );
  private readonly stockAlertedAt = new Map<string, number>(); // category → last forecast-alert ms (dedup)

  constructor(
    private readonly prisma: PrismaService,
    @Inject('VPN_CONFIG_SERVICE') private readonly vpnClient: ClientProxy,
    @Inject('ADMIN_SERVICE') private readonly adminClient: ClientProxy,
    @Inject('NOTIFICATION_SERVICE') private readonly notificationClient: ClientProxy,
  ) {}

  onModuleInit() {
    const intervalMs = resolveInventoryHealthcheckIntervalMs(
      process.env.INVENTORY_HEALTHCHECK_INTERVAL_MS,
    );
    if (intervalMs) {
      this.logger.log(`Inventory healthcheck scheduler enabled every ${intervalMs}ms`);
      this.healthcheckTimer = setInterval(() => {
        void this.runScheduledHealthCheck();
      }, intervalMs);
      this.healthcheckTimer.unref?.();
    } else {
      this.logger.log('Inventory healthcheck scheduler disabled');
    }

    // Auto-retry orders stuck in PENDING_FULFILLMENT so capacity that frees up later (new import
    // or a revoked assignment) is picked up without a manual admin /retry.
    const retryIntervalMs = resolveFulfillmentRetryIntervalMs(
      process.env.INVENTORY_FULFILLMENT_RETRY_INTERVAL_MS,
    );
    if (retryIntervalMs) {
      this.logger.log(`Fulfillment retry scheduler enabled every ${retryIntervalMs}ms`);
      this.fulfillmentRetryTimer = setInterval(() => {
        void this.runScheduledFulfillmentRetry();
      }, retryIntervalMs);
      this.fulfillmentRetryTimer.unref?.();
    } else {
      this.logger.log('Fulfillment retry scheduler disabled');
    }
  }

  onModuleDestroy() {
    if (this.healthcheckTimer) {
      clearInterval(this.healthcheckTimer);
      this.healthcheckTimer = undefined;
    }
    if (this.fulfillmentRetryTimer) {
      clearInterval(this.fulfillmentRetryTimer);
      this.fulfillmentRetryTimer = undefined;
    }
  }

  // Periodic sweep: re-attempt every order stuck in PENDING_FULFILLMENT. fulfillOrder() is
  // idempotent — it delivers as soon as inventory capacity exists and otherwise leaves the order
  // pending. Bounded batch + an in-flight guard so overlapping ticks never stack.
  private async runScheduledFulfillmentRetry() {
    if (this.fulfillmentRetryInFlight) {
      return;
    }
    this.fulfillmentRetryInFlight = true;
    try {
      const stuck = await this.prisma.order.findMany({
        where: { status: OrderStatus.PENDING_FULFILLMENT },
        orderBy: { created_at: 'asc' },
        take: 25,
        select: { id: true, order_ref: true },
      });
      if (stuck.length === 0) {
        return;
      }
      let fulfilled = 0;
      for (const order of stuck) {
        try {
          const result = await this.fulfillOrder(order.id);
          if (result?.pendingFulfillment === false) {
            fulfilled += 1;
          }
        } catch (error) {
          this.logger.warn(
            `Fulfillment retry failed for ${order.order_ref}: ${(error as Error).message}`,
          );
        }
      }
      this.logger.log(
        `Fulfillment retry sweep: ${stuck.length} pending, ${fulfilled} fulfilled`,
      );
    } catch (error) {
      this.logger.error('Scheduled fulfillment retry failed', error as Error);
    } finally {
      this.fulfillmentRetryInFlight = false;
    }
  }

  async importConfigs(data: ImportConfigsDto) {
    const results = [];

    for (const raw of data.configs) {
      const supplierResource: {
        rawConfig: string;
        parsedProfile: SwimVpnProfile;
        metadata: {
          providerName?: string;
          trafficUsedBytes?: number;
          trafficTotalBytes?: number;
          expiresAt?: string;
          connectedDevices?: number;
          deviceLimit?: number;
        };
      } = await firstValueFrom(
        this.vpnClient.send({ cmd: 'process_supplier_resource' }, { rawConfig: raw }),
      );
      const profile = supplierResource.parsedProfile;

      if (profile.validationState === 'VALID') {
        const supplierExpiresAt = data.supplierExpiresAt
          ? new Date(data.supplierExpiresAt)
          : supplierResource.metadata.expiresAt
            ? new Date(supplierResource.metadata.expiresAt)
            : null;
        const configIdentity = await this.buildConfigFolderIdentity({
          rawConfig: supplierResource.rawConfig,
          prefix: data.category,
          protocol: profile.protocol,
          hasSupplierExpiry: !!supplierExpiresAt,
        });
        const sourceQuotaBytes =
          typeof supplierResource.metadata.trafficTotalBytes === 'number'
            ? BigInt(supplierResource.metadata.trafficTotalBytes)
            : this.toBytesFromGb(
                BigInt(data.sourceQuotaGb ?? Number(InventoryService.DEFAULT_SOURCE_QUOTA_GB)),
              );
        const sourceUsedBytes =
          typeof supplierResource.metadata.trafficUsedBytes === 'number'
            ? BigInt(supplierResource.metadata.trafficUsedBytes)
            : 0n;
        const item = await this.prisma.inventoryItem.create({
          data: {
            category: data.category,
            raw_config: supplierResource.rawConfig,
            config_type: profile.protocol,
            display_protocol: profile.protocol,
            batch_name: data.batchName,
            status: InventoryStatus.AVAILABLE,
            // One config = one client: health only reflects supplier expiry / source exhaustion.
            health_status:
              supplierExpiresAt && supplierExpiresAt.getTime() <= Date.now()
                ? InventoryHealthStatus.EXPIRED
                : this.isSourceExhausted(sourceQuotaBytes, sourceUsedBytes)
                  ? InventoryHealthStatus.FULL
                : InventoryHealthStatus.HEALTHY,
            source_quota_bytes: sourceQuotaBytes,
            source_used_bytes: sourceUsedBytes,
            max_customer_allocations:
              data.maxUsersPerConfig ?? InventoryService.DEFAULT_MAX_USERS_PER_CONFIG,
            // Resale columns are vestigial (one config = one client). Kept at 1/0 for schema
            // non-null compatibility until a later migration drops them.
            max_resale_slots: 1,
            used_resale_slots: 0,
            sale_priority_score: 0,
            supplier_expires_at: supplierExpiresAt,
            supplier_provider_name:
              data.supplierProviderName?.trim() ||
              supplierResource.metadata.providerName?.trim() ||
              null,
            supplier_device_limit:
              data.supplierDeviceLimit ??
              supplierResource.metadata.deviceLimit ??
              data.maxUsersPerConfig ??
              DEFAULT_SUPPLIER_DEVICE_LIMIT,
            config_fingerprint: configIdentity.fingerprint,
            folder_code: configIdentity.folderCode,
            admin_label: configIdentity.adminLabel,
            node_count: configIdentity.nodeCount,
            countries_preview: configIdentity.countriesPreview as any,
            admin_preview_json: configIdentity.adminPreview as any,
          },
        });
        await this.safeRecordConfigEvent(this.prisma, {
          configScope: 'PAID',
          configId: item.id,
          folderCode: configIdentity.folderCode,
          eventType: ConfigEventType.CONFIG_IMPORTED,
          payload: {
            folderCode: configIdentity.folderCode,
            category: data.category,
            configType: item.config_type,
            nodeCount: configIdentity.nodeCount,
            countriesPreview: configIdentity.countriesPreview,
            importedAt: item.imported_at?.toISOString?.() ?? new Date().toISOString(),
          },
        });
        results.push({
          id: item.id,
          status: 'IMPORTED',
          configType: item.config_type,
          displayProtocol: item.display_protocol,
          sourceQuotaBytes: item.source_quota_bytes?.toString() ?? null,
          sourceUsedBytes: item.source_used_bytes?.toString() ?? null,
          supplierExpiresAt: item.supplier_expires_at?.toISOString() ?? null,
          supplierProviderName: item.supplier_provider_name,
          supplierDeviceLimit: item.supplier_device_limit,
          usedResaleSlots: item.used_resale_slots,
          maxResaleSlots: item.max_resale_slots,
          healthStatus: item.health_status,
        });
      } else {
        results.push({ config: raw, status: 'FAILED', reason: profile.errorMessage });
      }
    }

    return {
      importedCount: results.filter((result) => result.status === 'IMPORTED').length,
      details: results,
    };
  }

  async fulfillOrder(orderId: string) {
    const postCommitEffects: Array<() => void> = [];
    const result = await this.prisma.$transaction(async (tx) => {
      const order = await tx.order.findUnique({
        where: { id: orderId },
        include: {
          plan: true,
          customer: true,
          assignments: {
            include: {
              inventory_item: true,
            },
            orderBy: { assigned_at: 'desc' },
          },
        },
      });

      if (
        !order ||
        (order.status !== OrderStatus.PENDING &&
          order.status !== OrderStatus.PAID &&
          order.status !== OrderStatus.PENDING_FULFILLMENT &&
          order.status !== OrderStatus.FULFILLED)
      ) {
        throw new Error('Order not found or not in fulfillable state');
      }

      const existingActiveAssignment = order.assignments.find(
        (assignment) =>
          assignment.access_status === AssignmentAccessStatus.ACTIVE &&
          !!assignment.inventory_item_id,
      );

      if (existingActiveAssignment) {
        if (
          this.shouldSendPostPurchaseDelivery(order) &&
          order.customer?.email &&
          existingActiveAssignment.inventory_item?.raw_config
        ) {
          postCommitEffects.push(() => {
            this.notificationClient.emit('process_post_purchase_delivery', {
              orderRef: order.order_ref,
              customerEmail: order.customer.email,
              customerPhone: order.customer.phone || undefined,
              planCode: order.plan.code,
              planLabel: order.plan.name,
              vpnLink: existingActiveAssignment.inventory_item.raw_config,
              expiryLabel:
                existingActiveAssignment.inventory_item.supplier_expires_at?.toISOString() ||
                order.plan.duration_label,
              customerLanguage: 'ru',
            });
          });
        }

        if (!this.isTrialOrder(order)) {
          await this.revokeReplacedActiveAssignments(tx, {
            customerId: order.customer_id,
            newAssignmentId: existingActiveAssignment.id,
            newOrderRef: order.order_ref,
            replacementOrderCreatedAt: order.created_at,
          });
        }

        return {
          success: true,
          orderId: order.id,
          orderStatus: order.status,
          assignmentStatus: existingActiveAssignment.access_status,
          pendingFulfillment: false,
        };
      }

      const requiredSlots = this.getRequiredSlots(order.plan.code, this.isTrialOrder(order));
      const pendingAssignment =
        order.assignments.find(
          (assignment) =>
            assignment.access_status === AssignmentAccessStatus.PENDING &&
            !assignment.revoked_at,
        ) ||
        (await tx.orderAssignment.create({
          data: {
            order_id: order.id,
            customer_id: order.customer_id,
            access_status: AssignmentAccessStatus.PENDING,
            fallback_offer_title: order.plan.name,
            fallback_duration_label: this.getEffectiveDurationLabel(order),
            fallback_quota_label: this.getEffectiveQuotaLabel(order),
            slot_count: requiredSlots,
          },
        }));

      // One config = one client: only ever pick a FREE (AVAILABLE) config, never co-sell an
      // ASSIGNED one. Prefer the config whose supplier expiry is soonest (burn short-lived stock
      // first, less waste), then oldest imported. SKIP LOCKED + LIMIT 1 keeps concurrent
      // fulfillments from grabbing the same row.
      const candidateIds = await tx.$queryRaw<Array<{ id: string }>>(Prisma.sql`
        SELECT "id"
        FROM "InventoryItem"
        WHERE "category" = ${order.plan.code}::"PlanCategory"
          AND "health_status" = 'HEALTHY'::"InventoryHealthStatus"
          AND "status" = 'AVAILABLE'::"InventoryStatus"
          AND ("supplier_expires_at" IS NULL OR "supplier_expires_at" > NOW())
          AND (
            "source_quota_bytes" IS NULL
            OR "source_quota_bytes" <= 0
            OR "source_used_bytes" < "source_quota_bytes"
          )
        ORDER BY
          "supplier_expires_at" ASC NULLS LAST,
          "imported_at" ASC
        LIMIT 1
        FOR UPDATE SKIP LOCKED
      `);

      const candidateId = candidateIds[0]?.id;
      if (!candidateId) {
        await tx.order.update({
          where: { id: order.id },
          data: {
            status: OrderStatus.PENDING_FULFILLMENT,
            paid_at: order.paid_at ?? (order.status === OrderStatus.PENDING ? null : new Date()),
          },
        });

        await this.ensureDeliveryRecord(tx, order.id, 'Awaiting supplier capacity');
        await tx.adminEvent.create({
          data: {
            event_type: 'FULFILLMENT_PENDING_NO_CAPACITY',
            entity_type: 'ORDER',
            entity_id: order.order_ref,
            payload_json: {
              orderRef: order.order_ref,
              planCode: order.plan.code,
              requiredSlots,
              createdAt: new Date().toISOString(),
            } as any,
          },
        });

        postCommitEffects.push(() => {
          this.adminClient.emit('fulfillment_pending_alert', {
            orderRef: order.order_ref,
            planCode: order.plan.code,
            requiredSlots,
          });
        });

        return {
          success: true,
          orderId: order.id,
          orderStatus: OrderStatus.PENDING_FULFILLMENT,
          assignmentStatus: AssignmentAccessStatus.PENDING,
          pendingFulfillment: true,
        };
      }

      const inventoryItem = await tx.inventoryItem.findUniqueOrThrow({
        where: { id: candidateId },
      });

      // Burn the config to this single client. It leaves the AVAILABLE pool for good; its health
      // only reflects expiry/source exhaustion now (no slot-fill concept anymore).
      await tx.inventoryItem.update({
        where: { id: inventoryItem.id },
        data: {
          health_status: this.computeHealthStatus({
            currentHealth: inventoryItem.health_status,
            supplierExpiresAt: inventoryItem.supplier_expires_at,
            sourceQuotaBytes: inventoryItem.source_quota_bytes,
            sourceUsedBytes: inventoryItem.source_used_bytes,
          }),
          status: InventoryStatus.ASSIGNED,
          assigned_order_id: order.id,
          assigned_customer_id: order.customer_id,
          assigned_at: new Date(),
        },
      });

      const updatedAssignment = await tx.orderAssignment.update({
        where: { id: pendingAssignment.id },
        data: {
          inventory_item_id: inventoryItem.id,
          access_status: AssignmentAccessStatus.ACTIVE,
          slot_count: requiredSlots,
          expires_at: inventoryItem.supplier_expires_at,
          status_reason: null,
        },
      });

      const updatedOrder = await tx.order.update({
        where: { id: order.id },
        data: {
          status: OrderStatus.FULFILLED,
          paid_at: order.paid_at ?? new Date(),
          fulfilled_at: new Date(),
        },
      });

      if (!this.isTrialOrder(order)) {
        await this.revokeReplacedActiveAssignments(tx, {
          customerId: order.customer_id,
          newAssignmentId: updatedAssignment.id,
          newOrderRef: order.order_ref,
          replacementOrderCreatedAt: order.created_at,
        });
      }

      await this.ensureDeliveryRecord(tx, order.id, null);

      await tx.adminEvent.create({
        data: {
          event_type: 'CONFIG_ASSIGNED',
          entity_type: 'ORDER',
          entity_id: order.order_ref,
          payload_json: {
            orderRef: order.order_ref,
            inventoryItemId: inventoryItem.id,
            slotCount: requiredSlots,
            assignedAt: new Date().toISOString(),
          } as any,
        },
      });
      const configAssignedAt = new Date().toISOString();
      postCommitEffects.push(() => {
        void this.safeRecordConfigEvent(this.prisma, {
          configScope: 'PAID',
          configId: inventoryItem.id,
          folderCode: inventoryItem.folder_code,
          eventType: ConfigEventType.CONFIG_ASSIGNED,
          payload: {
            folderCode: inventoryItem.folder_code,
            orderRef: order.order_ref,
            planCode: order.plan.code,
            slotCount: requiredSlots,
            assignedAt: configAssignedAt,
          },
        });
      });

      postCommitEffects.push(() => {
        this.adminClient.emit('order_fulfilled', {
          orderId: updatedOrder.id,
          orderRef: updatedOrder.order_ref,
          amount: updatedOrder.amount_rub,
          planCode: order.plan.code,
        });
      });

      postCommitEffects.push(() => {
        void this.checkStockAndNotify(order.plan.code).catch((error) => {
          this.logger.error('Post-fulfillment stock check failed', error as Error);
        });
      });

      if (this.shouldSendPostPurchaseDelivery(order) && order.customer?.email) {
        postCommitEffects.push(() => {
          this.notificationClient.emit('process_post_purchase_delivery', {
            orderRef: updatedOrder.order_ref,
            customerEmail: order.customer.email,
            customerPhone: order.customer.phone || undefined,
            planCode: order.plan.code,
            planLabel: order.plan.name,
            vpnLink: inventoryItem.raw_config,
            expiryLabel: inventoryItem.supplier_expires_at?.toISOString() || order.plan.duration_label,
            customerLanguage: 'ru',
          });
        });
      }

      return {
        success: true,
        orderId: updatedOrder.id,
        orderStatus: updatedOrder.status,
        assignmentStatus: updatedAssignment.access_status,
        pendingFulfillment: false,
        itemProtocol: inventoryItem.display_protocol,
      };
    });

    postCommitEffects.forEach((effect) => {
      try {
        effect();
      } catch (error) {
        this.logger.error('Post-fulfillment side effect failed', error as Error);
      }
    });

    return result;
  }

  private async revokeReplacedActiveAssignments(
    tx: Prisma.TransactionClient,
    data: {
      customerId: string;
      newAssignmentId: string;
      newOrderRef: string;
      replacementOrderCreatedAt: Date;
    },
  ) {
    const replacedAssignments = await tx.orderAssignment.findMany({
      where: {
        customer_id: data.customerId,
        access_status: AssignmentAccessStatus.ACTIVE,
        id: { not: data.newAssignmentId },
        order: {
          created_at: { lt: data.replacementOrderCreatedAt },
        },
      },
      include: {
        order: true,
      },
    });

    if (replacedAssignments.length === 0) {
      return;
    }

    const replacedAt = new Date();
    const inventoryItemIds = Array.from(
      new Set(
        replacedAssignments
          .map((assignment) => assignment.inventory_item_id)
          .filter((id): id is string => !!id),
      ),
    );

    for (const assignment of replacedAssignments) {
      await tx.orderAssignment.update({
        where: { id: assignment.id },
        data: {
          access_status: AssignmentAccessStatus.REVOKED,
          revoked_at: replacedAt,
          status_reason: 'REPLACED_BY_NEW_PURCHASE',
        },
      });
    }

    for (const inventoryItemId of inventoryItemIds) {
      await this.recalculateInventoryState(tx, inventoryItemId);
    }

    await tx.adminEvent.create({
      data: {
        event_type: 'CUSTOMER_ACCESS_REPLACED',
        entity_type: 'CUSTOMER',
        entity_id: data.customerId,
        payload_json: {
          customerId: data.customerId,
          newAssignmentId: data.newAssignmentId,
          newOrderRef: data.newOrderRef,
          replacedAssignments: replacedAssignments.map((assignment) => ({
            assignmentId: assignment.id,
            orderRef: assignment.order.order_ref,
            inventoryItemId: assignment.inventory_item_id,
          })),
          replacedAt: replacedAt.toISOString(),
          slotPolicy: 'new fulfillment succeeded before older active assignments were revoked',
        } as any,
      },
    });
  }

  async recordAssignmentUsage(data: { orderRef: string; measuredUsedBytes: string }) {
    const measuredUsedBytes = this.parseBytesInput(data.measuredUsedBytes);

    return this.prisma.$transaction(async (tx) => {
      const order = await tx.order.findUnique({
        where: { order_ref: data.orderRef },
        include: {
          plan: true,
          assignments: {
            where: {
              access_status: AssignmentAccessStatus.ACTIVE,
            },
            include: {
              inventory_item: true,
            },
            orderBy: { assigned_at: 'desc' },
            take: 1,
          },
        },
      });

      if (!order) {
        throw new Error('Order not found');
      }

      const assignment = order.assignments[0];
      if (!assignment || !assignment.inventory_item) {
        throw new Error('Active assignment not found for order');
      }

      const nextMeasuredUsedBytes = this.maxBigInt(
        assignment.measured_used_bytes ?? 0n,
        measuredUsedBytes,
      );

      await tx.orderAssignment.update({
        where: { id: assignment.id },
        data: {
          measured_used_bytes: nextMeasuredUsedBytes,
          last_measured_at: new Date(),
        },
      });

      const sourceUsedBytes = await this.computeMonotoneSourceUsedBytes(
        tx,
        assignment.inventory_item_id!,
        assignment.inventory_item.source_used_bytes,
      );
      const sourceExhausted = this.isSourceExhausted(
        assignment.inventory_item.source_quota_bytes,
        sourceUsedBytes,
      );

      await tx.inventoryItem.update({
        where: { id: assignment.inventory_item_id! },
        data: {
          source_used_bytes: sourceUsedBytes,
          health_status: this.computeHealthStatus({
            currentHealth: assignment.inventory_item.health_status,
            supplierExpiresAt: assignment.inventory_item.supplier_expires_at,
            sourceQuotaBytes: assignment.inventory_item.source_quota_bytes,
            sourceUsedBytes,
          }),
        },
      });

      const planQuotaExceeded =
        !this.isTrialOrder(order) &&
        this.isPlanQuotaExceeded(this.getEffectiveQuotaLabel(order), nextMeasuredUsedBytes);

      if (planQuotaExceeded) {
        await tx.orderAssignment.update({
          where: { id: assignment.id },
          data: {
            access_status: AssignmentAccessStatus.EXPIRED,
            expires_at: new Date(),
            status_reason: 'PLAN_QUOTA_EXHAUSTED',
          },
        });

        if (sourceExhausted) {
          await tx.orderAssignment.updateMany({
            where: {
              inventory_item_id: assignment.inventory_item_id!,
              access_status: AssignmentAccessStatus.ACTIVE,
            },
            data: {
              access_status: AssignmentAccessStatus.EXPIRED,
              expires_at: new Date(),
              status_reason: 'SOURCE_QUOTA_EXHAUSTED',
            },
          });
        }

        await this.recalculateInventoryState(tx, assignment.inventory_item_id!);

        await tx.adminEvent.create({
          data: {
            event_type: 'PLAN_QUOTA_EXHAUSTED',
            entity_type: 'ORDER',
            entity_id: order.order_ref,
            payload_json: {
              orderRef: order.order_ref,
              assignmentId: assignment.id,
              inventoryItemId: assignment.inventory_item_id,
              measuredUsedBytes: nextMeasuredUsedBytes.toString(),
              quotaLabel: this.getEffectiveQuotaLabel(order),
              updatedAt: new Date().toISOString(),
            } as any,
          },
        });

        return {
          success: true,
          orderRef: order.order_ref,
          measuredUsedBytes: nextMeasuredUsedBytes.toString(),
          sourceUsedBytes: sourceUsedBytes.toString(),
          sourceExhausted,
          planQuotaExceeded: true,
        };
      }

      if (sourceExhausted) {
        await tx.orderAssignment.updateMany({
          where: {
            inventory_item_id: assignment.inventory_item_id!,
            access_status: AssignmentAccessStatus.ACTIVE,
          },
          data: {
            access_status: AssignmentAccessStatus.EXPIRED,
            expires_at: new Date(),
            status_reason: 'SOURCE_QUOTA_EXHAUSTED',
          },
        });
        await this.recalculateInventoryState(tx, assignment.inventory_item_id!);
      }

      await tx.adminEvent.create({
        data: {
          event_type: sourceExhausted ? 'SOURCE_QUOTA_EXHAUSTED' : 'SLOTS_USAGE_UPDATED',
          entity_type: sourceExhausted ? 'INVENTORY' : 'ORDER',
          entity_id: sourceExhausted ? assignment.inventory_item_id! : order.order_ref,
          payload_json: {
            orderRef: order.order_ref,
            inventoryItemId: assignment.inventory_item_id,
            measuredUsedBytes: nextMeasuredUsedBytes.toString(),
            sourceUsedBytes: sourceUsedBytes.toString(),
            sourceQuotaBytes: assignment.inventory_item.source_quota_bytes?.toString() ?? null,
            updatedAt: new Date().toISOString(),
          } as any,
        },
      });

      return {
        success: true,
        orderRef: order.order_ref,
        measuredUsedBytes: nextMeasuredUsedBytes.toString(),
        sourceUsedBytes: sourceUsedBytes.toString(),
        sourceExhausted,
      };
    });
  }

  async importTrialConfigs(data: ImportTrialConfigsDto) {
    const campaignCode = data.campaignCode?.trim() || 'trial-2026-05';
    const now = new Date();
    const campaign = await this.prisma.trialCampaign.findFirst({
      where: {
        code: campaignCode,
        status: TrialCampaignStatus.ACTIVE,
        starts_at: { lte: now },
        ends_at: { gte: now },
      },
    });

    if (!campaign) {
      throw new Error('Active trial campaign not found');
    }

    const results = [];
    for (const raw of data.configs) {
      const supplierResource: {
        rawConfig: string;
        parsedProfile: SwimVpnProfile;
        metadata: {
          providerName?: string;
          expiresAt?: string;
        };
      } = await firstValueFrom(
        this.vpnClient.send({ cmd: 'process_supplier_resource' }, { rawConfig: raw }),
      );
      const profile = supplierResource.parsedProfile;

      if (profile.validationState !== 'VALID') {
        results.push({
          status: 'REJECTED',
          reason: (profile as any).errorMessage || 'Invalid config',
        });
        continue;
      }

      const supplierExpiresAt = data.supplierExpiresAt
        ? new Date(data.supplierExpiresAt)
        : supplierResource.metadata.expiresAt
          ? new Date(supplierResource.metadata.expiresAt)
          : null;
      const status =
        supplierExpiresAt && supplierExpiresAt.getTime() <= Date.now()
          ? TrialConfigStatus.DEAD
          : TrialConfigStatus.AVAILABLE;
      const configIdentity = await this.buildConfigFolderIdentity({
        rawConfig: supplierResource.rawConfig,
        prefix: 'TRIAL',
        protocol: profile.protocol,
        hasSupplierExpiry: !!supplierExpiresAt,
      });

      const item = await this.prisma.trialConfig.create({
        data: {
          campaign_id: campaign.id,
          raw_config: supplierResource.rawConfig,
          config_type: profile.protocol,
          display_protocol: profile.protocol,
          batch_name: data.batchName,
          status,
          supplier_expires_at: supplierExpiresAt,
          supplier_provider_name:
            data.supplierProviderName?.trim() ||
            supplierResource.metadata.providerName?.trim() ||
            null,
          config_fingerprint: configIdentity.fingerprint,
          folder_code: configIdentity.folderCode,
          admin_label: configIdentity.adminLabel,
          node_count: configIdentity.nodeCount,
          countries_preview: configIdentity.countriesPreview as any,
          admin_preview_json: configIdentity.adminPreview as any,
        },
      });
      await this.safeRecordConfigEvent(this.prisma, {
        configScope: 'TRIAL',
        configId: item.id,
        folderCode: configIdentity.folderCode,
        eventType: ConfigEventType.TRIAL_CONFIG_IMPORTED,
        payload: {
          folderCode: configIdentity.folderCode,
          campaignCode,
          configType: item.config_type,
          nodeCount: configIdentity.nodeCount,
          countriesPreview: configIdentity.countriesPreview,
          importedAt: item.imported_at?.toISOString?.() ?? new Date().toISOString(),
        },
      });

      results.push({
        id: item.id,
        status: status === TrialConfigStatus.AVAILABLE ? 'IMPORTED' : 'IMPORTED_DEAD',
        campaignCode,
        configType: item.config_type,
        displayProtocol: item.display_protocol,
        supplierExpiresAt: item.supplier_expires_at?.toISOString() ?? null,
        supplierProviderName: item.supplier_provider_name,
      });
    }

    const recoveredAssignments = await this.assignPendingTrialGrants(campaign.id, campaign.code);

    return {
      importedCount: results.filter((result) => result.status === 'IMPORTED').length,
      recoveredPendingCount: recoveredAssignments.length,
      details: results,
      recoveredAssignments,
    };
  }

  private async assignPendingTrialGrants(campaignId: string, campaignCode: string) {
    const now = new Date();

    const result = await this.prisma.$transaction(async (tx) => {
      const campaign = await tx.trialCampaign.findUnique({
        where: { id: campaignId },
      });

      if (!campaign) {
        return { recoveredAssignments: [], configEvents: [] as ConfigEventInput[] };
      }

      const pendingGrants = await tx.trialGrant.findMany({
        where: {
          campaign_id: campaignId,
          status: TrialGrantStatus.PENDING,
          OR: [
            { expires_at: null },
            { expires_at: { gt: now } },
          ],
        },
        orderBy: { started_at: 'asc' },
        include: { customer: true },
      });
      const recoveredAssignments = [];
      const configEvents: ConfigEventInput[] = [];

      for (const grant of pendingGrants) {
        const candidates = await tx.trialConfig.findMany({
          where: {
            campaign_id: campaignId,
            status: { in: [TrialConfigStatus.AVAILABLE, TrialConfigStatus.ASSIGNED] },
            max_device_assignments: { gt: 0 },
            OR: [
              { supplier_expires_at: null },
              { supplier_expires_at: { gt: now } },
            ],
          },
          orderBy: { imported_at: 'asc' },
          take: 25,
        });
        const candidate = candidates.find(
          (config) => config.used_device_assignments < config.max_device_assignments,
        );

        if (!candidate) {
          break;
        }

        const lockedConfig = await tx.trialConfig.updateMany({
          where: {
            id: candidate.id,
            status: { in: [TrialConfigStatus.AVAILABLE, TrialConfigStatus.ASSIGNED] },
            used_device_assignments: { lt: candidate.max_device_assignments },
          },
          data: {
            status: TrialConfigStatus.ASSIGNED,
            assigned_at: now,
            used_device_assignments: { increment: 1 },
          },
        });

        if (lockedConfig.count !== 1) {
          continue;
        }

        const trialExpiresAt = new Date(
          now.getTime() + Math.max(campaign.duration_days, 1) * 24 * 60 * 60 * 1000,
        );
        const expiresAt = this.pickEarlierDate(trialExpiresAt, candidate.supplier_expires_at);

        const lockedGrant = await tx.trialGrant.updateMany({
          where: {
            id: grant.id,
            status: TrialGrantStatus.PENDING,
          },
          data: {
            status: TrialGrantStatus.ACTIVE,
            assigned_at: now,
            expires_at: expiresAt,
            status_reason: null,
          },
        });

        if (lockedGrant.count !== 1) {
          await tx.trialConfig.updateMany({
            where: {
              id: candidate.id,
              status: TrialConfigStatus.ASSIGNED,
            },
            data: {
              status:
                candidate.used_device_assignments > 0
                  ? TrialConfigStatus.ASSIGNED
                  : TrialConfigStatus.AVAILABLE,
              assigned_at: candidate.used_device_assignments > 0 ? candidate.assigned_at : null,
              used_device_assignments: { decrement: 1 },
            },
          });
          continue;
        }

        await tx.trialAssignment.create({
          data: {
            grant_id: grant.id,
            trial_config_id: candidate.id,
            customer_id: grant.customer_id,
            status: TrialGrantStatus.ACTIVE,
            assigned_at: now,
            expires_at: expiresAt,
          },
        });

        await tx.adminEvent.create({
          data: {
            event_type: 'TRIAL_CONFIG_ASSIGNED',
            entity_type: 'TRIAL_GRANT',
            entity_id: grant.id,
            payload_json: {
              userNumber: grant.customer.public_id,
              campaignCode,
              grantId: grant.id,
              trialConfigId: candidate.id,
              recoveredFromPending: true,
              expiresAt: expiresAt.toISOString(),
              assignedAt: now.toISOString(),
            } as any,
          },
        });
        configEvents.push({
          configScope: 'TRIAL',
          configId: candidate.id,
          folderCode: candidate.folder_code,
          eventType: ConfigEventType.TRIAL_CONFIG_ASSIGNED,
          payload: {
            folderCode: candidate.folder_code,
            campaignCode,
            grantId: grant.id,
            userNumber: grant.customer.public_id,
            expiresAt: expiresAt.toISOString(),
            assignedAt: now.toISOString(),
          },
        });

        recoveredAssignments.push({
          grantId: grant.id,
          userNumber: grant.customer.public_id,
          trialConfigId: candidate.id,
          expiresAt: expiresAt.toISOString(),
        });
      }

      return { recoveredAssignments, configEvents };
    });

    for (const event of result.configEvents) {
      await this.safeRecordConfigEvent(this.prisma, event);
    }

    return result.recoveredAssignments;
  }

  async listInventoryOverview() {
    const items = await this.prisma.inventoryItem.findMany({
      orderBy: { imported_at: 'asc' },
      include: {
        assignments: {
          orderBy: { assigned_at: 'desc' },
          include: {
            customer: {
              select: {
                public_id: true,
                email: true,
                phone: true,
              },
            },
            order: {
              select: {
                order_ref: true,
                status: true,
              },
            },
          },
        },
      },
    });

    return items.map((item) => ({
      id: item.id,
      category: item.category,
      batchName: item.batch_name,
      displayProtocol: item.display_protocol,
      inventoryStatus: item.status,
      healthStatus: item.health_status,
      usedResaleSlots: item.used_resale_slots,
      maxResaleSlots: item.max_resale_slots,
      salePriorityScore: item.sale_priority_score,
      sourceUsedBytes: item.source_used_bytes.toString(),
      sourceQuotaBytes: item.source_quota_bytes?.toString() ?? null,
      supplierExpiresAt: item.supplier_expires_at?.toISOString() ?? null,
      supplierProviderName: item.supplier_provider_name,
      supplierDeviceLimit: item.supplier_device_limit,
      configFingerprint: item.config_fingerprint,
      folderCode: item.folder_code,
      adminLabel: item.admin_label,
      nodeCount: item.node_count,
      countriesPreview: item.countries_preview,
      adminPreview: item.admin_preview_json,
      assignments: item.assignments.map((assignment) => ({
        id: assignment.id,
        orderRef: assignment.order.order_ref,
        customerPublicId: assignment.customer.public_id,
        customerEmail: assignment.customer.email,
        customerPhone: assignment.customer.phone,
        accessStatus: assignment.access_status,
        slotCount: assignment.slot_count,
        assignedAt: assignment.assigned_at.toISOString(),
        expiresAt: assignment.expires_at?.toISOString() ?? null,
        revokedAt: assignment.revoked_at?.toISOString() ?? null,
      })),
    }));
  }

  async listTrialInventoryOverview() {
    const items = await this.prisma.trialConfig.findMany({
      orderBy: { imported_at: 'asc' },
      include: {
        campaign: {
          select: {
            code: true,
            title: true,
            status: true,
          },
        },
        assignments: {
          orderBy: { assigned_at: 'desc' },
          include: {
            customer: {
              select: {
                public_id: true,
                email: true,
                phone: true,
              },
            },
          },
        },
      },
    });

    return items.map((item) => ({
      id: item.id,
      campaignCode: item.campaign.code,
      campaignTitle: item.campaign.title,
      campaignStatus: item.campaign.status,
      batchName: item.batch_name,
      displayProtocol: item.display_protocol,
      status: item.status,
      usedDeviceAssignments: item.used_device_assignments,
      maxDeviceAssignments: item.max_device_assignments,
      supplierExpiresAt: item.supplier_expires_at?.toISOString() ?? null,
      supplierProviderName: item.supplier_provider_name,
      configFingerprint: item.config_fingerprint,
      folderCode: item.folder_code,
      adminLabel: item.admin_label,
      nodeCount: item.node_count,
      countriesPreview: item.countries_preview,
      adminPreview: item.admin_preview_json,
      assignments: item.assignments.map((assignment) => ({
        id: assignment.id,
        customerPublicId: assignment.customer.public_id,
        customerEmail: assignment.customer.email,
        customerPhone: assignment.customer.phone,
        assignedAt: assignment.assigned_at.toISOString(),
        expiresAt: assignment.expires_at?.toISOString() ?? null,
      })),
    }));
  }

  async getInventoryStats() {
    const paidStats = await this.prisma.inventoryItem.groupBy({
      by: ['category', 'status'],
      _count: { _all: true },
    });

    const trialStats = await this.prisma.trialConfig.groupBy({
      by: ['status'],
      _count: { _all: true },
    });

    const categories: PlanCategory[] = [PlanCategory.WEEK, PlanCategory.MONTH, PlanCategory.QUARTER];
    const forecast = await Promise.all(
      categories.map(async (category) => {
        const f = await this.buildStockForecast(category);
        return {
          category: f.category,
          available: f.available,
          threshold: f.threshold,
          dailyRate: Number(f.dailyRate.toFixed(2)),
          daysOfStock: Number.isFinite(f.daysOfStock) ? Number(f.daysOfStock.toFixed(1)) : null,
          reorderQty: f.reorderQty,
        };
      }),
    );

    return {
      paid: paidStats.map((s) => ({
        category: s.category,
        status: s.status,
        count: s._count._all,
      })),
      trial: trialStats.map((s) => ({
        status: s.status,
        count: s._count._all,
      })),
      forecast,
    };
  }

  async clearAvailableConfigs(category: PlanCategory) {
    const result = await this.prisma.inventoryItem.deleteMany({
      where: {
        category,
        status: InventoryStatus.AVAILABLE,
        used_resale_slots: 0,
      },
    });

    this.logger.log(`Cleared ${result.count} available configs for category ${category}`);
    return { count: result.count };
  }

  async clearAvailableTrialConfigs() {
    const result = await this.prisma.trialConfig.deleteMany({
      where: {
        status: TrialConfigStatus.AVAILABLE,
        used_device_assignments: 0,
      },
    });

    this.logger.log(`Cleared ${result.count} available trial configs`);
    return { count: result.count };
  }

  async updateInventoryHealth(data: {
    inventoryItemId: string;
    healthStatus: InventoryHealthStatus;
    reason?: string | null;
    adminId?: string | null;
  }) {
    return this.prisma.$transaction(async (tx) => {
      const item = await tx.inventoryItem.update({
        where: { id: data.inventoryItemId },
        data: {
          health_status: data.healthStatus,
        },
      });

      const reason = data.reason || `ADMIN_MARKED_${data.healthStatus}`;
      let affectedAssignments = 0;

      if (data.healthStatus === InventoryHealthStatus.EXPIRED) {
        const result = await tx.orderAssignment.updateMany({
          where: {
            inventory_item_id: item.id,
            access_status: AssignmentAccessStatus.ACTIVE,
          },
          data: {
            access_status: AssignmentAccessStatus.EXPIRED,
            expires_at: new Date(),
            status_reason: reason,
          },
        });
        affectedAssignments = result.count;
      }

      if (data.healthStatus === InventoryHealthStatus.DISABLED) {
        const result = await tx.orderAssignment.updateMany({
          where: {
            inventory_item_id: item.id,
            access_status: AssignmentAccessStatus.ACTIVE,
          },
          data: {
            access_status: AssignmentAccessStatus.REVOKED,
            revoked_at: new Date(),
            status_reason: reason,
          },
        });
        affectedAssignments = result.count;
      }

      if (
        data.healthStatus === InventoryHealthStatus.EXPIRED ||
        data.healthStatus === InventoryHealthStatus.DISABLED
      ) {
        await this.recalculateInventoryState(tx, item.id);
        await tx.inventoryItem.update({
          where: { id: item.id },
          data: { health_status: data.healthStatus },
        });
      }

      await tx.adminEvent.create({
        data: {
          admin_id: data.adminId ?? undefined,
          event_type: 'CONFIG_HEALTH_UPDATED',
          entity_type: 'INVENTORY',
          entity_id: item.id,
          payload_json: {
            inventoryItemId: item.id,
            healthStatus: data.healthStatus,
            reason,
            affectedAssignments,
            updatedAt: new Date().toISOString(),
          } as any,
        },
      });

      return {
        success: true,
        inventoryItemId: item.id,
        healthStatus: data.healthStatus,
        affectedAssignments,
      };
    });
  }

  async revokeAssignment(data: { assignmentId: string; reason?: string; adminId?: string | null }) {
    return this.prisma.$transaction(async (tx) => {
      const assignment = await tx.orderAssignment.findUnique({
        where: { id: data.assignmentId },
        include: {
          inventory_item: true,
          order: true,
        },
      });

      if (!assignment) {
        throw new Error('Assignment not found');
      }

      if (assignment.access_status === AssignmentAccessStatus.REVOKED) {
        return { success: true, alreadyRevoked: true };
      }

      await tx.orderAssignment.update({
        where: { id: assignment.id },
        data: {
          access_status: AssignmentAccessStatus.REVOKED,
          revoked_at: new Date(),
          status_reason: data.reason || 'ADMIN_REVOKED',
        },
      });

      if (assignment.inventory_item_id) {
        await this.recalculateInventoryState(tx, assignment.inventory_item_id);
      }

      await tx.adminEvent.create({
        data: {
          admin_id: data.adminId ?? undefined,
          event_type: 'ASSIGNMENT_REVOKED',
          entity_type: 'ORDER_ASSIGNMENT',
          entity_id: assignment.id,
          payload_json: {
            assignmentId: assignment.id,
            orderRef: assignment.order.order_ref,
            inventoryItemId: assignment.inventory_item_id,
            reason: data.reason || null,
            revokedAt: new Date().toISOString(),
          } as any,
        },
      });

      return { success: true, assignmentId: assignment.id };
    });
  }

  async moveAssignment(data: {
    assignmentId: string;
    targetInventoryItemId: string;
    adminId?: string | null;
  }) {
    return this.prisma.$transaction(async (tx) => {
      const assignment = await tx.orderAssignment.findUnique({
        where: { id: data.assignmentId },
        include: {
          order: {
            include: { plan: true },
          },
        },
      });

      if (!assignment) {
        throw new Error('Assignment not found');
      }

      if (
        assignment.access_status === AssignmentAccessStatus.REVOKED ||
        assignment.access_status === AssignmentAccessStatus.EXPIRED ||
        assignment.access_status === AssignmentAccessStatus.FAILED
      ) {
        throw new Error('Terminal assignment cannot be moved or reactivated');
      }

      const target = await tx.inventoryItem.findUnique({
        where: { id: data.targetInventoryItemId },
      });

      if (!target) {
        throw new Error('Target inventory item not found');
      }

      const projectedTargetSourceUsedBytes = this.projectMovedAssignmentSourceUsedBytes(
        target.source_used_bytes,
        assignment.measured_used_bytes,
      );

      // One config = one client: the target must be a FREE, HEALTHY config (not already assigned),
      // and its supplier source must still cover the moved usage.
      if (
        !canAllocateSupplierConfig({
          healthStatus: target.health_status,
          status: target.status,
        }) ||
        this.isSourceExhausted(target.source_quota_bytes, projectedTargetSourceUsedBytes)
      ) {
        throw new Error('Target inventory item is not a free, healthy config');
      }

      const previousInventoryItemId = assignment.inventory_item_id;

      await tx.orderAssignment.update({
        where: { id: assignment.id },
        data: {
          inventory_item_id: target.id,
          access_status: AssignmentAccessStatus.ACTIVE,
          expires_at: target.supplier_expires_at,
          status_reason: 'ADMIN_MOVED',
        },
      });

      await tx.inventoryItem.update({
        where: { id: target.id },
        data: {
          source_used_bytes: projectedTargetSourceUsedBytes,
          health_status: this.computeHealthStatus({
            currentHealth: target.health_status,
            supplierExpiresAt: target.supplier_expires_at,
            sourceQuotaBytes: target.source_quota_bytes,
            sourceUsedBytes: projectedTargetSourceUsedBytes,
          }),
          status: InventoryStatus.ASSIGNED,
        },
      });

      // The config the client just left is burned (never re-sold).
      if (previousInventoryItemId) {
        await this.recalculateInventoryState(tx, previousInventoryItemId);
      }

      await tx.adminEvent.create({
        data: {
          admin_id: data.adminId ?? undefined,
          event_type: 'ASSIGNMENT_MOVED',
          entity_type: 'ORDER_ASSIGNMENT',
          entity_id: assignment.id,
          payload_json: {
            assignmentId: assignment.id,
            orderRef: assignment.order.order_ref,
            fromInventoryItemId: previousInventoryItemId,
            toInventoryItemId: target.id,
            movedAt: new Date().toISOString(),
          } as any,
        },
      });

      return { success: true, assignmentId: assignment.id, inventoryItemId: target.id };
    });
  }

  async runHealthCheck() {
    const items = await this.prisma.inventoryItem.findMany({
      where: { health_status: { not: InventoryHealthStatus.DISABLED } },
    });

    const results = { healthy: 0, degraded: 0, checked: items.length };

    for (const item of items) {
      if (item.supplier_expires_at && item.supplier_expires_at.getTime() <= Date.now()) {
        await this.expireInventoryItem(item.id, 'SUPPLIER_EXPIRED');
        results.degraded++;
        continue;
      }

      const health: { alive: boolean } = await firstValueFrom(
        this.vpnClient.send({ cmd: 'check_health' }, { rawConfig: item.raw_config }),
      );

      if (!health.alive) {
        await this.prisma.inventoryItem.update({
          where: { id: item.id },
          data: { health_status: InventoryHealthStatus.DEGRADED },
        });
        results.degraded++;
      } else {
        await this.prisma.inventoryItem.update({
          where: { id: item.id },
          data: {
            health_status: this.computeHealthStatus({
              currentHealth: item.health_status,
              supplierExpiresAt: item.supplier_expires_at,
              sourceQuotaBytes: item.source_quota_bytes,
              sourceUsedBytes: item.source_used_bytes,
            }),
          },
        });
        results.healthy++;
      }
    }

    return results;
  }

  private async runScheduledHealthCheck() {
    try {
      const result = await this.runHealthCheck();
      if (process.env.CONTINUITY_REALLOCATION_ENABLED !== 'false') {
        try {
          const orchestrator = new ResupplyOrchestrator(this.prisma, this.adminClient);
          const realloc = await orchestrator.runReallocationPass();
          this.logger.log(
            `Continuity reallocation: checked=${realloc.checked} reallocated=${realloc.reallocated} failed=${realloc.failed}`,
          );
        } catch (reallocError) {
          this.logger.error('Continuity reallocation pass failed', reallocError as Error);
        }
      }
      await this.runStockForecastPass();
      const purged = await this.purgeExpiredUnsoldConfigs();
      this.logger.log(
        `Scheduled inventory healthcheck completed: checked=${result.checked} healthy=${result.healthy} degraded=${result.degraded} purged=${purged.deleted}`,
      );
    } catch (error) {
      this.logger.error('Scheduled inventory healthcheck failed', error as Error);
    }
  }

  /**
   * Auto-purge expired stock, cleanly. Only NEVER-SOLD configs (no OrderAssignment ever) that are
   * supplier-expired / EXPIRED / DEAD are hard-deleted — no order references them, so deletion is
   * FK-safe and cannot break order traceability. Sold-then-expired configs keep their row (EXPIRED)
   * for auditability. The ConfigEvent/AdminEvent journal is keyed by string id and survives the
   * row deletion, so the audit trail stays intact. Bounded batch per pass.
   */
  async purgeExpiredUnsoldConfigs(): Promise<{ deleted: number }> {
    const now = new Date();
    const stale = await this.prisma.inventoryItem.findMany({
      where: {
        assignments: { none: {} },
        OR: [
          { supplier_expires_at: { lte: now } },
          { health_status: InventoryHealthStatus.EXPIRED },
          { status: InventoryStatus.DEAD },
        ],
      },
      select: { id: true, category: true, folder_code: true },
      take: 200,
    });

    if (stale.length === 0) {
      return { deleted: 0 };
    }

    const ids = stale.map((item) => item.id);
    const result = await this.prisma.inventoryItem.deleteMany({ where: { id: { in: ids } } });

    await this.prisma.adminEvent.create({
      data: {
        event_type: 'EXPIRED_CONFIGS_PURGED',
        entity_type: 'INVENTORY',
        entity_id: 'auto-purge',
        payload_json: {
          deleted: result.count,
          folderCodes: stale.map((item) => item.folder_code).filter(Boolean),
          purgedAt: now.toISOString(),
        } as any,
      },
    });

    this.logger.log(`Auto-purged ${result.count} expired unsold config(s)`);
    return { deleted: result.count };
  }

  /**
   * Backfill `supplier_expires_at` for stock imported BEFORE the parser learned to read expiry.
   * Re-parses each config with a NULL expiry via the vpn-config engine (which now reads the
   * `Subscription-Userinfo` header + broad date formats). Configs whose date is already past are
   * expired via the existing machinery, then unsold expired configs are purged. Raw links with no
   * recoverable date are left untouched (counted as unresolved). Bounded + idempotent (NULL-only).
   */
  async backfillSupplierExpiries(limit = 100): Promise<{
    scanned: number;
    updated: number;
    expiredNow: number;
    purgedDeleted: number;
    unresolved: number;
  }> {
    const boundedLimit = Math.max(1, Math.min(Math.floor(limit) || 100, 500));
    const targets = await this.prisma.inventoryItem.findMany({
      where: {
        supplier_expires_at: null,
        health_status: { not: InventoryHealthStatus.DISABLED },
      },
      orderBy: { imported_at: 'asc' },
      take: boundedLimit,
      select: { id: true, raw_config: true },
    });

    let updated = 0;
    let expiredNow = 0;
    let unresolved = 0;

    for (const item of targets) {
      let expiresAt: Date | null = null;
      try {
        const parsed: { metadata?: { expiresAt?: string } } = await firstValueFrom(
          this.vpnClient.send({ cmd: 'process_supplier_resource' }, { rawConfig: item.raw_config }),
        );
        if (parsed?.metadata?.expiresAt) {
          const candidate = new Date(parsed.metadata.expiresAt);
          if (!Number.isNaN(candidate.getTime())) {
            expiresAt = candidate;
          }
        }
      } catch (error) {
        this.logger.warn(`Backfill parse failed for ${item.id}: ${(error as Error).message}`);
        unresolved += 1;
        continue;
      }

      if (!expiresAt) {
        unresolved += 1;
        continue;
      }

      await this.prisma.inventoryItem.update({
        where: { id: item.id },
        data: { supplier_expires_at: expiresAt },
      });
      updated += 1;

      // Already expired → run the existing expire path (marks EXPIRED + expires ACTIVE assignments).
      if (expiresAt.getTime() <= Date.now()) {
        await this.expireInventoryItem(item.id, 'BACKFILL_SUPPLIER_EXPIRED');
        expiredNow += 1;
      }
    }

    const purged = await this.purgeExpiredUnsoldConfigs();

    await this.prisma.adminEvent.create({
      data: {
        event_type: 'SUPPLIER_EXPIRY_BACKFILL',
        entity_type: 'INVENTORY',
        entity_id: 'backfill',
        payload_json: {
          scanned: targets.length,
          updated,
          expiredNow,
          purgedDeleted: purged.deleted,
          unresolved,
          ranAt: new Date().toISOString(),
        } as any,
      },
    });

    this.logger.log(
      `Supplier expiry backfill: scanned=${targets.length} updated=${updated} expiredNow=${expiredNow} purged=${purged.deleted} unresolved=${unresolved}`,
    );

    return {
      scanned: targets.length,
      updated,
      expiredNow,
      purgedDeleted: purged.deleted,
      unresolved,
    };
  }

  private async expireInventoryItem(inventoryItemId: string, reason: string) {
    await this.prisma.$transaction(async (tx) => {
      await tx.inventoryItem.update({
        where: { id: inventoryItemId },
        data: {
          health_status: InventoryHealthStatus.EXPIRED,
        },
      });

      await tx.orderAssignment.updateMany({
        where: {
          inventory_item_id: inventoryItemId,
          access_status: AssignmentAccessStatus.ACTIVE,
        },
        data: {
          access_status: AssignmentAccessStatus.EXPIRED,
          expires_at: new Date(),
          status_reason: reason,
        },
      });

      await tx.adminEvent.create({
        data: {
          event_type: 'SUPPLIER_CONFIG_EXPIRED',
          entity_type: 'INVENTORY',
          entity_id: inventoryItemId,
          payload_json: {
            inventoryItemId,
            reason,
            expiredAt: new Date().toISOString(),
          } as any,
        },
      });
    });
  }

  private async ensureDeliveryRecord(tx: Prisma.TransactionClient, orderId: string, notes: string | null) {
    const existing = await tx.delivery.findFirst({
      where: { order_id: orderId },
    });

    if (existing) {
      return tx.delivery.update({
        where: { id: existing.id },
        data: {
          notes,
        },
      });
    }

    return tx.delivery.create({
      data: {
        order_id: orderId,
        delivery_mode: 'APP_ONLY',
        notes,
      },
    });
  }

  private shouldSendPostPurchaseDelivery(order: {
    status: OrderStatus;
    paid_at?: Date | null;
    payment_ref?: string | null;
  }) {
    return (
      order.status === OrderStatus.PAID ||
      order.status === OrderStatus.PENDING_FULFILLMENT ||
      order.status === OrderStatus.FULFILLED ||
      order.paid_at !== null ||
      !!order.payment_ref
    );
  }

  /**
   * SINGLE SOURCE OF TRUTH for "how many configs can serve a NEW client of this category".
   * One config = one client: a config is allocatable iff it is FREE (AVAILABLE) + HEALTHY + not
   * supplier-expired + source not exhausted. Same predicate the fulfillment SELECT uses.
   */
  private async countAllocatable(category: PlanCategory): Promise<number> {
    const items = await this.prisma.inventoryItem.findMany({
      where: {
        category,
        status: InventoryStatus.AVAILABLE,
        health_status: InventoryHealthStatus.HEALTHY,
      },
    });
    const now = Date.now();
    return items.filter(
      (item) =>
        canAllocateSupplierConfig({ healthStatus: item.health_status, status: item.status }) &&
        !(item.supplier_expires_at && item.supplier_expires_at.getTime() <= now) &&
        !this.isSourceExhausted(item.source_quota_bytes, item.source_used_bytes),
    ).length;
  }

  /**
   * Sale-gate availability check: how many FREE configs can serve a NEW client of this category,
   * using the SINGLE allocatable predicate (countAllocatable). Called by the customer service at
   * checkout: 0 does NOT block the sale anymore — it flags the order as an honest backorder.
   */
  async getCategoryAvailability(
    category: PlanCategory,
  ): Promise<{ category: PlanCategory; available: number; requiredSlots: number }> {
    const available = await this.countAllocatable(category);
    return { category, available, requiredSlots: 1 };
  }

  // Post-fulfillment stock signal. Distinct events so the admin bot can shout the two states the
  // operator asked for: a soft low-stock warning, and a hard "stock épuisé" (0 configs left).
  private async checkStockAndNotify(category: PlanCategory) {
    const count = await this.countAllocatable(category);
    if (count <= 0) {
      this.adminClient.emit('stock_depleted', { category });
    } else if (count < resolveThreshold(category, this.stockThresholds)) {
      this.adminClient.emit('low_stock_alert', { category, remaining: count });
    }
  }

  /** Recent assignments (configs sold) for a category over the velocity window. */
  private async countRecentSales(category: PlanCategory, since: Date): Promise<number> {
    return this.prisma.orderAssignment.count({
      where: { assigned_at: { gte: since }, inventory_item: { category } },
    });
  }

  /** Proactive forecast for one category: available + velocity + days-of-stock + reorder. */
  private async buildStockForecast(category: PlanCategory) {
    const since = new Date(Date.now() - this.stockVelocityWindowDays * 86_400_000);
    const [available, soldCount] = await Promise.all([
      this.countAllocatable(category),
      this.countRecentSales(category, since),
    ]);
    return computeStockForecast({
      category,
      available,
      soldCount,
      windowDays: this.stockVelocityWindowDays,
      thresholds: this.stockThresholds,
      targetDaysCover: this.stockTargetDaysCover,
      forecastAlertDays: this.stockForecastAlertDays,
    });
  }

  // Scheduled proactive pass: forecast each plan and alert the admin BEFORE running out.
  // Read-only; dedup to at most one forecast alert per category per day.
  private async runStockForecastPass() {
    const categories: PlanCategory[] = [PlanCategory.WEEK, PlanCategory.MONTH, PlanCategory.QUARTER];
    const dedupMs = 24 * 60 * 60 * 1000;
    for (const category of categories) {
      try {
        const f = await this.buildStockForecast(category);
        if (!f.alert) continue;
        const last = this.stockAlertedAt.get(category) ?? 0;
        if (Date.now() - last < dedupMs) continue;
        this.stockAlertedAt.set(category, Date.now());
        this.adminClient.emit('stock_forecast_alert', {
          category: f.category,
          available: f.available,
          threshold: f.threshold,
          dailyRate: Number(f.dailyRate.toFixed(2)),
          daysOfStock: Number.isFinite(f.daysOfStock) ? Number(f.daysOfStock.toFixed(1)) : null,
          reorderQty: f.reorderQty,
        });
      } catch (error) {
        this.logger.error(`Stock forecast pass failed for ${category}`, error as Error);
      }
    }
  }

  // One config = one client. No resale/packing: every paid or trial order consumes exactly one
  // config, which is then burned (never co-sold). slot_count stays 1 purely as a schema vestige.
  private getRequiredSlots(_planCode: PlanCategory, _isTrialOrder: boolean) {
    return 1;
  }

  private getEffectiveQuotaLabel(order: { payment_ref?: string | null; order_ref: string; plan: { quota_label: string } }) {
    return this.isTrialOrder(order) ? InventoryService.TRIAL_QUOTA_LABEL : order.plan.quota_label;
  }

  private getEffectiveDurationLabel(order: {
    payment_ref?: string | null;
    order_ref: string;
    plan: { duration_label: string };
  }) {
    return this.isTrialOrder(order)
      ? InventoryService.TRIAL_DURATION_LABEL
      : order.plan.duration_label;
  }

  private isTrialOrder(order: { payment_ref?: string | null; order_ref: string }) {
    return order.payment_ref === 'TRIAL:3D' || order.order_ref.startsWith('TRIAL-');
  }

  private toBytesFromGb(valueGb: bigint) {
    return valueGb * 1024n * 1024n * 1024n;
  }

  private pickEarlierDate(first: Date, second?: Date | null) {
    if (!second) {
      return first;
    }

    return first.getTime() <= second.getTime() ? first : second;
  }

  private parseBytesInput(value: string) {
    if (!/^\d+$/.test(value.trim())) {
      throw new Error('measuredUsedBytes must be an unsigned integer string');
    }

    return BigInt(value.trim());
  }

  private parseQuotaLabelToGb(quotaLabel: string) {
    const match = quotaLabel.match(/(\d+(?:[.,]\d+)?)/);
    if (!match) {
      return 0;
    }

    const parsed = Number.parseFloat(match[1].replace(',', '.'));
    return Number.isFinite(parsed) ? parsed : 0;
  }

  private quotaLabelToBytes(quotaLabel: string) {
    const parsedGb = this.parseQuotaLabelToGb(quotaLabel);
    if (!Number.isFinite(parsedGb) || parsedGb <= 0) {
      return 0n;
    }

    return BigInt(Math.round(parsedGb * 1024 * 1024 * 1024));
  }

  private isPlanQuotaExceeded(quotaLabel: string, measuredUsedBytes?: bigint | null) {
    const quotaBytes = this.quotaLabelToBytes(quotaLabel);
    if (quotaBytes <= 0n) {
      return false;
    }

    return (measuredUsedBytes ?? 0n) >= quotaBytes;
  }

  private isSourceExhausted(sourceQuotaBytes?: bigint | null, sourceUsedBytes?: bigint | null) {
    if (!sourceQuotaBytes || sourceQuotaBytes <= 0n) {
      return false;
    }

    return (sourceUsedBytes ?? 0n) >= sourceQuotaBytes;
  }

  private maxBigInt(left: bigint, right: bigint) {
    return left > right ? left : right;
  }

  private projectMovedAssignmentSourceUsedBytes(
    currentSourceUsedBytes?: bigint | null,
    assignmentMeasuredUsedBytes?: bigint | null,
  ) {
    return (currentSourceUsedBytes ?? 0n) + (assignmentMeasuredUsedBytes ?? 0n);
  }

  private async computeMonotoneSourceUsedBytes(
    tx: Prisma.TransactionClient,
    inventoryItemId: string,
    currentSourceUsedBytes?: bigint | null,
  ) {
    const aggregate = await tx.orderAssignment.aggregate({
      where: {
        inventory_item_id: inventoryItemId,
      },
      _sum: {
        measured_used_bytes: true,
      },
    });

    return this.maxBigInt(currentSourceUsedBytes ?? 0n, aggregate._sum.measured_used_bytes ?? 0n);
  }

  // One config = one client (burn-after-sale). Called after an assignment goes terminal
  // (revoked/expired/moved): if a live client remains the config stays ASSIGNED, otherwise it is
  // retired to DEAD — it never returns to the AVAILABLE pool. Auto-purge later deletes the row.
  private async recalculateInventoryState(tx: Prisma.TransactionClient, inventoryItemId: string) {
    const activeAssignmentCount = await tx.orderAssignment.count({
      where: {
        inventory_item_id: inventoryItemId,
        access_status: AssignmentAccessStatus.ACTIVE,
      },
    });

    const inventoryItem = await tx.inventoryItem.findUniqueOrThrow({
      where: { id: inventoryItemId },
    });

    const sourceUsedBytes = await this.computeMonotoneSourceUsedBytes(
      tx,
      inventoryItemId,
      inventoryItem.source_used_bytes,
    );
    const nextHealth = this.computeHealthStatus({
      currentHealth: inventoryItem.health_status,
      supplierExpiresAt: inventoryItem.supplier_expires_at,
      sourceQuotaBytes: inventoryItem.source_quota_bytes,
      sourceUsedBytes,
    });

    await tx.inventoryItem.update({
      where: { id: inventoryItemId },
      data: {
        source_used_bytes: sourceUsedBytes,
        health_status: nextHealth,
        status:
          activeAssignmentCount > 0 ? InventoryStatus.ASSIGNED : InventoryStatus.DEAD,
      },
    });
  }

  private async buildConfigFolderIdentity(input: {
    rawConfig: string;
    prefix: string;
    protocol: string;
    hasSupplierExpiry: boolean;
  }) {
    const normalizedRaw = input.rawConfig.trim();
    const fingerprint = crypto.createHash('sha256').update(normalizedRaw).digest('hex');
    const shortFingerprint = fingerprint.slice(0, 12).toUpperCase();
    const safePrefix = this.safeFolderToken(input.prefix);
    const safeProtocol = this.safeFolderToken(input.protocol);
    const folderCode = `${safePrefix}-${safeProtocol}-${shortFingerprint}`;
    const preview = await this.parseManagedNodesForPreview(normalizedRaw);
    const nodes = preview.nodes;
    const protocols = Array.from(
      new Set(nodes.map((node) => String(node.protocol || '').trim()).filter(Boolean)),
    ).slice(0, 6);
    const countriesPreview = this.extractCountriesPreview(nodes);

    return {
      fingerprint,
      folderCode,
      adminLabel: `${safePrefix} ${safeProtocol} ${shortFingerprint}`,
      nodeCount: nodes.length,
      countriesPreview,
      adminPreview: {
        folderCode,
        nodeCount: nodes.length,
        protocols,
        countriesPreview,
        previewStatus: preview.status,
        previewError: preview.error,
        hasSupplierExpiry: input.hasSupplierExpiry,
        importedSource: safePrefix,
      },
    };
  }

  private async parseManagedNodesForPreview(rawConfig: string) {
    try {
      const nodes = await firstValueFrom(
        this.vpnClient.send({ cmd: 'resolve_managed_nodes' }, { rawConfig }),
      );
      return {
        nodes: Array.isArray(nodes) ? nodes : [],
        status: 'PARSED',
        error: null,
      };
    } catch (error) {
      const message = error instanceof Error ? error.message : String(error);
      this.logger.warn(`Config folder preview unavailable: ${message}`);
      return {
        nodes: [],
        status: 'UNAVAILABLE',
        error: 'PARSER_UNAVAILABLE',
      };
    }
  }

  private extractCountriesPreview(nodes: any[]) {
    const countryAliases: Record<string, string> = {
      FR: 'France',
      FRA: 'France',
      DE: 'Germany',
      DEU: 'Germany',
      US: 'United States',
      USA: 'United States',
      UK: 'United Kingdom',
      GB: 'United Kingdom',
      GBR: 'United Kingdom',
      CA: 'Canada',
      CAN: 'Canada',
      JP: 'Japan',
      JPN: 'Japan',
      SG: 'Singapore',
      SGP: 'Singapore',
      RU: 'Russia',
      RUS: 'Russia',
      NL: 'Netherlands',
      NLD: 'Netherlands',
      TR: 'Turkey',
      TUR: 'Turkey',
      PL: 'Poland',
      POL: 'Poland',
      ES: 'Spain',
      ESP: 'Spain',
      IT: 'Italy',
      ITA: 'Italy',
      SE: 'Sweden',
      SWE: 'Sweden',
      FI: 'Finland',
      FIN: 'Finland',
      BR: 'Brazil',
      BRA: 'Brazil',
      IN: 'India',
      IND: 'India',
      KR: 'Korea',
      KOR: 'Korea',
    };
    const knownCountries = Array.from(new Set(Object.values(countryAliases)));
    const found = new Set<string>();

    for (const node of nodes) {
      const fields = [
        node?.countryName,
        node?.country,
        node?.countryCode,
        node?.location,
        node?.region,
        node?.displayName,
      ].map((value) => String(value || '').trim()).filter(Boolean);
      for (const field of fields) {
        const normalized = field.toUpperCase();
        const alias = countryAliases[normalized] ?? countryAliases[field];
        if (alias) {
          found.add(alias);
        }
        const flagCountry = this.countryFromFlagEmoji(field, countryAliases);
        if (flagCountry) {
          found.add(flagCountry);
        }
      }
      const label = fields.join(' ');
      for (const country of knownCountries) {
        if (label.toLowerCase().includes(country.toLowerCase())) {
          found.add(country);
        }
      }
    }

    return Array.from(found).slice(0, 8);
  }

  private countryFromFlagEmoji(value: string, countryAliases: Record<string, string>) {
    const chars = Array.from(value);
    for (let index = 0; index < chars.length - 1; index += 1) {
      const first = chars[index].codePointAt(0) ?? 0;
      const second = chars[index + 1].codePointAt(0) ?? 0;
      if (
        first >= 0x1f1e6 &&
        first <= 0x1f1ff &&
        second >= 0x1f1e6 &&
        second <= 0x1f1ff
      ) {
        const alpha2 = String.fromCharCode(first - 0x1f1e6 + 65, second - 0x1f1e6 + 65);
        const country = countryAliases[alpha2];
        if (country) {
          return country;
        }
      }
    }
    return null;
  }

  private safeFolderToken(value: string) {
    const token = value
      .trim()
      .toUpperCase()
      .replace(/[^A-Z0-9]+/g, '-')
      .replace(/^-+|-+$/g, '');
    return token || 'CONFIG';
  }

  private async recordConfigEvent(
    client: { configEvent?: { create: (args: any) => Promise<unknown> } },
    input: ConfigEventInput,
  ) {
    if (!client.configEvent?.create) {
      return;
    }

    await client.configEvent.create({
      data: {
        config_scope: input.configScope,
        config_id: input.configId,
        folder_code: input.folderCode ?? null,
        event_type: input.eventType,
        payload_json: input.payload as any,
      },
    });
  }

  private async safeRecordConfigEvent(
    client: { configEvent?: { create: (args: any) => Promise<unknown> } },
    input: ConfigEventInput,
  ) {
    try {
      await this.recordConfigEvent(client, input);
    } catch (error) {
      this.logger.warn(
        `Config journal write failed for ${input.configScope}:${input.configId}:${input.eventType}: ${
          error instanceof Error ? error.message : String(error)
        }`,
      );
    }
  }

  // Health of a config in the one-config-one-client model. No slot-fill concept: a config is
  // HEALTHY (sellable while AVAILABLE), or terminal for supplier expiry / source exhaustion, or
  // admin-DISABLED (sticky). FULL now means "supplier source quota exhausted".
  private computeHealthStatus(input: {
    currentHealth: InventoryHealthStatus;
    supplierExpiresAt: Date | null;
    sourceQuotaBytes?: bigint | null;
    sourceUsedBytes?: bigint | null;
  }) {
    if (input.currentHealth === InventoryHealthStatus.DISABLED) {
      return InventoryHealthStatus.DISABLED;
    }

    if (input.supplierExpiresAt && input.supplierExpiresAt.getTime() <= Date.now()) {
      return InventoryHealthStatus.EXPIRED;
    }

    if (this.isSourceExhausted(input.sourceQuotaBytes, input.sourceUsedBytes)) {
      return InventoryHealthStatus.FULL;
    }

    return InventoryHealthStatus.HEALTHY;
  }
}
