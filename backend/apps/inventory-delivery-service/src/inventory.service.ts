import { Injectable, Inject } from '@nestjs/common';
import { ClientProxy } from '@nestjs/microservices';
import { PrismaService } from '@app/database';
import { ImportConfigsDto } from '@app/contracts/inventory.dto';
import {
  DEFAULT_RESALE_SLOT_CAP,
  DEFAULT_SUPPLIER_DEVICE_LIMIT,
  getPlanSlotCount,
  SwimVpnProfile,
} from '@app/contracts';
import { firstValueFrom } from 'rxjs';
import {
  AssignmentAccessStatus,
  InventoryHealthStatus,
  InventoryStatus,
  OrderStatus,
  PlanCategory,
  Prisma,
} from '@prisma/client';
import { canAllocateSupplierConfig } from './supplier-capacity.policy';

@Injectable()
export class InventoryService {
  private static readonly DEFAULT_SOURCE_QUOTA_GB = 1000n;
  private static readonly DEFAULT_MAX_USERS_PER_CONFIG = 5;
  private static readonly TRIAL_QUOTA_LABEL = 'UNLIMITED';
  private static readonly TRIAL_DURATION_LABEL = '3 Days';

  constructor(
    private readonly prisma: PrismaService,
    @Inject('VPN_CONFIG_SERVICE') private readonly vpnClient: ClientProxy,
    @Inject('ADMIN_SERVICE') private readonly adminClient: ClientProxy,
    @Inject('NOTIFICATION_SERVICE') private readonly notificationClient: ClientProxy,
  ) {}

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
        const usedResaleSlots = Math.min(
          supplierResource.metadata.connectedDevices ?? 0,
          data.maxResaleSlots ?? DEFAULT_RESALE_SLOT_CAP,
        );
        const maxResaleSlots = data.maxResaleSlots ?? DEFAULT_RESALE_SLOT_CAP;
        const item = await this.prisma.inventoryItem.create({
          data: {
            category: data.category,
            raw_config: supplierResource.rawConfig,
            config_type: profile.protocol,
            display_protocol: profile.protocol,
            batch_name: data.batchName,
            status: InventoryStatus.AVAILABLE,
            health_status:
              supplierExpiresAt && supplierExpiresAt.getTime() <= Date.now()
                ? InventoryHealthStatus.EXPIRED
                : usedResaleSlots >= maxResaleSlots
                  ? InventoryHealthStatus.FULL
                : InventoryHealthStatus.HEALTHY,
            source_quota_bytes: sourceQuotaBytes,
            source_used_bytes: sourceUsedBytes,
            max_customer_allocations:
              data.maxUsersPerConfig ?? InventoryService.DEFAULT_MAX_USERS_PER_CONFIG,
            max_resale_slots: maxResaleSlots,
            used_resale_slots: usedResaleSlots,
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
          },
        });
        results.push({ id: item.id, status: 'IMPORTED' });
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
    return this.prisma.$transaction(async (tx) => {
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
          order.status !== OrderStatus.PENDING_FULFILLMENT)
      ) {
        throw new Error('Order not found or not in fulfillable state');
      }

      const existingActiveAssignment = order.assignments.find(
        (assignment) =>
          assignment.access_status === AssignmentAccessStatus.ACTIVE &&
          !!assignment.inventory_item_id,
      );

      if (existingActiveAssignment) {
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

      const candidateIds = await tx.$queryRaw<Array<{ id: string }>>(Prisma.sql`
        SELECT "id"
        FROM "InventoryItem"
        WHERE "category" = ${order.plan.code}::"PlanCategory"
          AND "health_status" = 'HEALTHY'::"InventoryHealthStatus"
          AND "status" IN ('AVAILABLE'::"InventoryStatus", 'ASSIGNED'::"InventoryStatus")
          AND "used_resale_slots" + ${requiredSlots} <= "max_resale_slots"
        ORDER BY "imported_at" ASC
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

        this.adminClient.emit('fulfillment_pending_alert', {
          orderRef: order.order_ref,
          planCode: order.plan.code,
          requiredSlots,
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

      const nextUsedSlots = inventoryItem.used_resale_slots + requiredSlots;
      const nextHealth =
        nextUsedSlots >= inventoryItem.max_resale_slots
          ? InventoryHealthStatus.FULL
          : InventoryHealthStatus.HEALTHY;

      await tx.inventoryItem.update({
        where: { id: inventoryItem.id },
        data: {
          used_resale_slots: nextUsedSlots,
          health_status: nextHealth,
          status: InventoryStatus.ASSIGNED,
          assigned_order_id: inventoryItem.assigned_order_id ?? order.id,
          assigned_customer_id: inventoryItem.assigned_customer_id ?? order.customer_id,
          assigned_at: inventoryItem.assigned_at ?? new Date(),
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
            usedResaleSlots: nextUsedSlots,
            maxResaleSlots: inventoryItem.max_resale_slots,
            assignedAt: new Date().toISOString(),
          } as any,
        },
      });

      this.adminClient.emit('order_fulfilled', {
        orderId: updatedOrder.id,
        orderRef: updatedOrder.order_ref,
        amount: updatedOrder.amount_rub,
        planCode: order.plan.code,
      });

      this.checkStockAndNotify(tx, order.plan.code);

      const shouldSendPostPurchaseDelivery =
        order.status === OrderStatus.PAID ||
        order.status === OrderStatus.PENDING_FULFILLMENT ||
        order.paid_at !== null ||
        !!order.payment_ref;

      if (shouldSendPostPurchaseDelivery && order.customer?.email) {
        this.notificationClient.emit('process_post_purchase_delivery', {
          orderRef: updatedOrder.order_ref,
          customerEmail: order.customer.email,
          customerPhone: order.customer.phone || undefined,
          planCode: order.plan.code,
          planLabel: order.plan.name,
          vpnLink: inventoryItem.raw_config,
          expiryLabel: inventoryItem.supplier_expires_at?.toISOString() || order.plan.duration_label,
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

      await tx.orderAssignment.update({
        where: { id: assignment.id },
        data: {
          measured_used_bytes: measuredUsedBytes,
          last_measured_at: new Date(),
        },
      });

      const aggregate = await tx.orderAssignment.aggregate({
        where: {
          inventory_item_id: assignment.inventory_item_id,
          access_status: AssignmentAccessStatus.ACTIVE,
        },
        _sum: {
          measured_used_bytes: true,
        },
      });

      const sourceUsedBytes = aggregate._sum.measured_used_bytes ?? 0n;
      const sourceExhausted = this.isSourceExhausted(
        assignment.inventory_item.source_quota_bytes,
        sourceUsedBytes,
      );

      await tx.inventoryItem.update({
        where: { id: assignment.inventory_item_id! },
        data: {
          source_used_bytes: sourceUsedBytes,
          health_status: sourceExhausted
            ? InventoryHealthStatus.FULL
            : assignment.inventory_item.health_status,
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

      await tx.adminEvent.create({
        data: {
          event_type: sourceExhausted ? 'SOURCE_QUOTA_EXHAUSTED' : 'SLOTS_USAGE_UPDATED',
          entity_type: sourceExhausted ? 'INVENTORY' : 'ORDER',
          entity_id: sourceExhausted ? assignment.inventory_item_id! : order.order_ref,
          payload_json: {
            orderRef: order.order_ref,
            inventoryItemId: assignment.inventory_item_id,
            measuredUsedBytes: measuredUsedBytes.toString(),
            sourceUsedBytes: sourceUsedBytes.toString(),
            sourceQuotaBytes: assignment.inventory_item.source_quota_bytes?.toString() ?? null,
            updatedAt: new Date().toISOString(),
          } as any,
        },
      });

      return {
        success: true,
        orderRef: order.order_ref,
        measuredUsedBytes: measuredUsedBytes.toString(),
        sourceUsedBytes: sourceUsedBytes.toString(),
        sourceExhausted,
      };
    });
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
      sourceUsedBytes: item.source_used_bytes.toString(),
      sourceQuotaBytes: item.source_quota_bytes?.toString() ?? null,
      supplierExpiresAt: item.supplier_expires_at?.toISOString() ?? null,
      supplierProviderName: item.supplier_provider_name,
      supplierDeviceLimit: item.supplier_device_limit,
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

  async updateInventoryHealth(data: {
    inventoryItemId: string;
    healthStatus: InventoryHealthStatus;
    adminId?: string | null;
  }) {
    const item = await this.prisma.inventoryItem.update({
      where: { id: data.inventoryItemId },
      data: {
        health_status: data.healthStatus,
      },
    });

    await this.prisma.adminEvent.create({
      data: {
        admin_id: data.adminId ?? undefined,
        event_type: 'CONFIG_HEALTH_UPDATED',
        entity_type: 'INVENTORY',
        entity_id: item.id,
        payload_json: {
          inventoryItemId: item.id,
          healthStatus: data.healthStatus,
          updatedAt: new Date().toISOString(),
        } as any,
      },
    });

    return { success: true, inventoryItemId: item.id, healthStatus: item.health_status };
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

      const target = await tx.inventoryItem.findUnique({
        where: { id: data.targetInventoryItemId },
      });

      if (!target) {
        throw new Error('Target inventory item not found');
      }

      if (
        !canAllocateSupplierConfig({
          healthStatus: target.health_status,
          usedResaleSlots: target.used_resale_slots,
          maxResaleSlots: target.max_resale_slots,
          requiredSlots: assignment.slot_count,
        })
      ) {
        throw new Error('Target inventory item has no remaining resale capacity');
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
          used_resale_slots: { increment: assignment.slot_count },
          health_status:
            target.used_resale_slots + assignment.slot_count >= target.max_resale_slots
              ? InventoryHealthStatus.FULL
              : InventoryHealthStatus.HEALTHY,
          status: InventoryStatus.ASSIGNED,
        },
      });

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
            health_status:
              item.used_resale_slots >= item.max_resale_slots
                ? InventoryHealthStatus.FULL
                : InventoryHealthStatus.HEALTHY,
          },
        });
        results.healthy++;
      }
    }

    return results;
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

  private async checkStockAndNotify(tx: Prisma.TransactionClient, category: PlanCategory) {
    const items = await tx.inventoryItem.findMany({
      where: {
        category,
        health_status: InventoryHealthStatus.HEALTHY,
      },
    });
    const count = items.filter((item) =>
      canAllocateSupplierConfig({
        healthStatus: item.health_status,
        usedResaleSlots: item.used_resale_slots,
        maxResaleSlots: item.max_resale_slots,
        requiredSlots: 1,
      }),
    ).length;

    if (count < 5) {
      this.adminClient.emit('low_stock_alert', { category, remaining: count });
    }
  }

  private getRequiredSlots(planCode: PlanCategory, isTrialOrder: boolean) {
    if (isTrialOrder) {
      return 1;
    }

    return getPlanSlotCount(planCode);
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

  private parseBytesInput(value: string) {
    if (!/^\d+$/.test(value.trim())) {
      throw new Error('measuredUsedBytes must be an unsigned integer string');
    }

    return BigInt(value.trim());
  }

  private isSourceExhausted(sourceQuotaBytes?: bigint | null, sourceUsedBytes?: bigint | null) {
    if (!sourceQuotaBytes || sourceQuotaBytes <= 0n) {
      return false;
    }

    return (sourceUsedBytes ?? 0n) >= sourceQuotaBytes;
  }

  private async recalculateInventoryState(tx: Prisma.TransactionClient, inventoryItemId: string) {
    const assignmentAggregate = await tx.orderAssignment.aggregate({
      where: {
        inventory_item_id: inventoryItemId,
        access_status: AssignmentAccessStatus.ACTIVE,
      },
      _sum: {
        slot_count: true,
      },
    });

    const inventoryItem = await tx.inventoryItem.findUniqueOrThrow({
      where: { id: inventoryItemId },
    });

    const usedSlots = assignmentAggregate._sum.slot_count ?? 0;
    const nextHealth = this.computeHealthStatus({
      currentHealth: inventoryItem.health_status,
      supplierExpiresAt: inventoryItem.supplier_expires_at,
      usedResaleSlots: usedSlots,
      maxResaleSlots: inventoryItem.max_resale_slots,
    });

    await tx.inventoryItem.update({
      where: { id: inventoryItemId },
      data: {
        used_resale_slots: usedSlots,
        health_status: nextHealth,
        status: usedSlots > 0 ? InventoryStatus.ASSIGNED : InventoryStatus.AVAILABLE,
      },
    });
  }

  private computeHealthStatus(input: {
    currentHealth: InventoryHealthStatus;
    supplierExpiresAt: Date | null;
    usedResaleSlots: number;
    maxResaleSlots: number;
  }) {
    if (input.currentHealth === InventoryHealthStatus.DISABLED) {
      return InventoryHealthStatus.DISABLED;
    }

    if (input.supplierExpiresAt && input.supplierExpiresAt.getTime() <= Date.now()) {
      return InventoryHealthStatus.EXPIRED;
    }

    if (input.usedResaleSlots >= input.maxResaleSlots) {
      return InventoryHealthStatus.FULL;
    }

    return InventoryHealthStatus.HEALTHY;
  }
}
