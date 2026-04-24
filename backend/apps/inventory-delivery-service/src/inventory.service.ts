import { Injectable, Inject } from '@nestjs/common';
import { ClientProxy } from '@nestjs/microservices';
import { PrismaService } from '@app/database';
import { ImportConfigsDto } from '@app/contracts/inventory.dto';
import { SwimVpnProfile } from '@app/contracts';
import { firstValueFrom } from 'rxjs';
import { InventoryStatus, OrderStatus } from '@prisma/client';

@Injectable()
export class InventoryService {
  private static readonly DEFAULT_SOURCE_QUOTA_GB = 1000n;
  private static readonly DEFAULT_MAX_USERS_PER_CONFIG = 5;

  constructor(
    private readonly prisma: PrismaService,
    @Inject('VPN_CONFIG_SERVICE') private readonly vpnClient: ClientProxy,
    @Inject('ADMIN_SERVICE') private readonly adminClient: ClientProxy,
    @Inject('NOTIFICATION_SERVICE') private readonly notificationClient: ClientProxy,
  ) {}

  async importConfigs(data: ImportConfigsDto) {
    const results = [];

    for (const raw of data.configs) {
      // 1. Ask engine to parse
      const profile: SwimVpnProfile = await firstValueFrom(
        this.vpnClient.send({ cmd: 'parse_config' }, { rawConfig: raw }),
      );

      if (profile.validationState === 'VALID') {
        // 2. Save to inventory
        const item = await this.prisma.inventoryItem.create({
          data: {
            category: data.category,
            raw_config: raw,
            config_type: profile.protocol,
            display_protocol: profile.protocol,
            batch_name: data.batchName,
            status: InventoryStatus.AVAILABLE,
            source_quota_bytes: this.toBytesFromGb(
              BigInt(data.sourceQuotaGb ?? Number(InventoryService.DEFAULT_SOURCE_QUOTA_GB))
            ),
            max_customer_allocations: data.maxUsersPerConfig ?? InventoryService.DEFAULT_MAX_USERS_PER_CONFIG,
          },
        });
        results.push({ id: item.id, status: 'IMPORTED' });
      } else {
        results.push({ config: raw, status: 'FAILED', reason: profile.errorMessage });
      }
    }

    return { importedCount: results.filter(r => r.status === 'IMPORTED').length, details: results };
  }

  async fulfillOrder(orderId: string) {
    return this.prisma.$transaction(async (tx) => {
      // 1. Get order details
      const order = await tx.order.findUnique({
        where: { id: orderId },
        include: { plan: true, customer: true },
      });

      if (!order || (order.status !== OrderStatus.PENDING && order.status !== OrderStatus.PAID)) {
        throw new Error('Order not found or not in fulfillable state');
      }

      // 2. Find an available config matching the plan category
      const inventoryItems = await tx.inventoryItem.findMany({
        where: {
          category: order.plan.code,
          status: { in: [InventoryStatus.AVAILABLE, InventoryStatus.ASSIGNED] },
        },
        include: {
          assignments: {
            select: {
              customer_id: true,
            },
          },
        },
        orderBy: { imported_at: 'asc' },
      });

      const inventoryItem = inventoryItems.find((item) =>
        this.canAllocateInventoryItem(item, order.customer_id),
      );

      if (!inventoryItem) {
        throw new Error('No available inventory for this plan');
      }

      // 3. Atomically assign and update statuses
      await tx.inventoryItem.update({
        where: { id: inventoryItem.id },
        data: {
          status: InventoryStatus.ASSIGNED,
          assigned_order_id: inventoryItem.assigned_order_id ?? order.id,
          assigned_customer_id: inventoryItem.assigned_customer_id ?? order.customer_id,
          assigned_at: inventoryItem.assigned_at ?? new Date(),
        },
      });

      await tx.orderAssignment.create({
        data: {
          order_id: order.id,
          inventory_item_id: inventoryItem.id,
          customer_id: order.customer_id,
          fallback_offer_title: order.plan.name,
          fallback_duration_label: order.plan.duration_label,
          fallback_quota_label: order.plan.quota_label,
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

      // 4. Create delivery record
      await tx.delivery.create({
        data: {
          order_id: order.id,
          delivery_mode: 'APP_ONLY',
        },
      });

      // 5. Notify Admin of fulfillment and low stock
      this.adminClient.emit('order_fulfilled', {
        orderId: updatedOrder.id,
        orderRef: updatedOrder.order_ref,
        amount: updatedOrder.amount_rub,
        planCode: order.plan.code,
      });

      this.checkStockAndNotify(tx, order.plan.code);

      const shouldSendPostPurchaseDelivery =
        order.status === OrderStatus.PAID || order.paid_at !== null || !!order.payment_ref;

      if (shouldSendPostPurchaseDelivery && order.customer?.email) {
        this.notificationClient.emit('process_post_purchase_delivery', {
          orderRef: updatedOrder.order_ref,
          customerEmail: order.customer.email,
          customerPhone: order.customer.phone || undefined,
          planCode: order.plan.code,
          planLabel: order.plan.name,
          vpnLink: inventoryItem.raw_config,
          expiryLabel: order.plan.duration_label,
        });
      }

      return { success: true, orderId: updatedOrder.id, itemProtocol: inventoryItem.display_protocol };
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
      if (!assignment) {
        throw new Error('Assignment not found for order');
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
        },
        _sum: {
          measured_used_bytes: true,
        },
      });

      const sourceUsedBytes = aggregate._sum.measured_used_bytes ?? 0n;
      const quotaExceeded = this.getPlanQuotaBytes(order.plan.quota_label) > 0n &&
        measuredUsedBytes >= this.getPlanQuotaBytes(order.plan.quota_label);
      const sourceExhausted = this.isSourceExhausted(
        assignment.inventory_item.source_quota_bytes,
        sourceUsedBytes,
      );

      await tx.inventoryItem.update({
        where: { id: assignment.inventory_item_id },
        data: {
          source_used_bytes: sourceUsedBytes,
          status: sourceExhausted ? InventoryStatus.DEAD : assignment.inventory_item.status,
        },
      });

      if (quotaExceeded || sourceExhausted) {
        await tx.adminEvent.create({
          data: {
            event_type: quotaExceeded ? 'ORDER_QUOTA_EXHAUSTED' : 'SOURCE_QUOTA_EXHAUSTED',
            entity_type: quotaExceeded ? 'ORDER' : 'INVENTORY',
            entity_id: quotaExceeded ? order.order_ref : assignment.inventory_item_id,
            payload_json: {
              orderRef: order.order_ref,
              inventoryItemId: assignment.inventory_item_id,
              measuredUsedBytes: measuredUsedBytes.toString(),
              sourceUsedBytes: sourceUsedBytes.toString(),
              sourceQuotaBytes: assignment.inventory_item.source_quota_bytes?.toString() ?? null,
              planQuotaBytes: this.getPlanQuotaBytes(order.plan.quota_label).toString(),
              updatedAt: new Date().toISOString(),
            } as any,
          },
        });
      }

      return {
        success: true,
        orderRef: order.order_ref,
        measuredUsedBytes: measuredUsedBytes.toString(),
        sourceUsedBytes: sourceUsedBytes.toString(),
        quotaExceeded,
        sourceExhausted,
      };
    });
  }

  private async checkStockAndNotify(tx: any, category: any) {
    const items = await tx.inventoryItem.findMany({
      where: {
        category,
        status: { in: [InventoryStatus.AVAILABLE, InventoryStatus.ASSIGNED] },
      },
      include: {
        assignments: {
          select: {
            customer_id: true,
          },
        },
      },
    });
    const count = items.filter((item: any) => this.hasRemainingCapacity(item)).length;

    if (count < 5) {
      this.adminClient.emit('low_stock_alert', { category, remaining: count });
    }
  }

  async runHealthCheck() {
    const items = await this.prisma.inventoryItem.findMany({
      where: { status: { in: [InventoryStatus.AVAILABLE, InventoryStatus.ASSIGNED] } },
    });

    const results = { alive: 0, dead: 0, checked: items.length };

    for (const item of items) {
      const health: { alive: boolean } = await firstValueFrom(
        this.vpnClient.send({ cmd: 'check_health' }, { rawConfig: item.raw_config }),
      );

      if (!health.alive) {
        await this.prisma.inventoryItem.update({
          where: { id: item.id },
          data: { status: InventoryStatus.DEAD },
        });
        results.dead++;
      } else {
        results.alive++;
      }
    }

    return results;
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

  private getPlanQuotaBytes(quotaLabel: string) {
    const match = quotaLabel.match(/(\d+(?:[.,]\d+)?)/);
    if (!match) {
      return 0n;
    }

    const normalized = match[1].replace(',', '.');
    const parsed = Number.parseFloat(normalized);
    if (!Number.isFinite(parsed) || parsed <= 0) {
      return 0n;
    }

    return BigInt(Math.round(parsed * 1024 * 1024 * 1024));
  }

  private isSourceExhausted(sourceQuotaBytes?: bigint | null, sourceUsedBytes?: bigint | null) {
    if (!sourceQuotaBytes || sourceQuotaBytes <= 0n) {
      return false;
    }

    return (sourceUsedBytes ?? 0n) >= sourceQuotaBytes;
  }

  private distinctCustomerCount(item: { assignments: Array<{ customer_id: string }> }) {
    return new Set(item.assignments.map((assignment) => assignment.customer_id)).size;
  }

  private hasRemainingCapacity(item: {
    assignments: Array<{ customer_id: string }>;
    source_quota_bytes?: bigint | null;
    source_used_bytes?: bigint | null;
    max_customer_allocations: number;
  }) {
    if (this.isSourceExhausted(item.source_quota_bytes, item.source_used_bytes)) {
      return false;
    }

    return this.distinctCustomerCount(item) < item.max_customer_allocations;
  }

  private canAllocateInventoryItem(
    item: {
      assignments: Array<{ customer_id: string }>;
      source_quota_bytes?: bigint | null;
      source_used_bytes?: bigint | null;
      max_customer_allocations: number;
    },
    customerId: string,
  ) {
    if (this.isSourceExhausted(item.source_quota_bytes, item.source_used_bytes)) {
      return false;
    }

    const distinctCustomers = new Set(item.assignments.map((assignment) => assignment.customer_id));
    if (distinctCustomers.has(customerId)) {
      return true;
    }

    return distinctCustomers.size < item.max_customer_allocations;
  }
}
