import { Injectable, Inject } from '@nestjs/common';
import { ClientProxy } from '@nestjs/microservices';
import { PrismaService } from '@app/database';
import { ImportConfigsDto } from '@app/contracts/inventory.dto';
import { SwimVpnProfile } from '@app/contracts';
import { firstValueFrom } from 'rxjs';
import { InventoryStatus, OrderStatus } from '@prisma/client';

@Injectable()
export class InventoryService {
  constructor(
    private readonly prisma: PrismaService,
    @Inject('VPN_CONFIG_SERVICE') private readonly vpnClient: ClientProxy,
    @Inject('ADMIN_SERVICE') private readonly adminClient: ClientProxy,
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
        include: { plan: true },
      });

      if (!order || (order.status !== OrderStatus.PENDING && order.status !== OrderStatus.PAID)) {
        throw new Error('Order not found or not in fulfillable state');
      }

      // 2. Find an available config matching the plan category
      const inventoryItem = await tx.inventoryItem.findFirst({
        where: {
          category: order.plan.code,
          status: InventoryStatus.AVAILABLE,
        },
      });

      if (!inventoryItem) {
        throw new Error('No available inventory for this plan');
      }

      // 3. Atomically assign and update statuses
      await tx.inventoryItem.update({
        where: { id: inventoryItem.id },
        data: {
          status: InventoryStatus.ASSIGNED,
          assigned_order_id: order.id,
          assigned_customer_id: order.customer_id,
          assigned_at: new Date(),
        },
      });

      await tx.orderAssignment.create({
        data: {
          order_id: order.id,
          inventory_item_id: inventoryItem.id,
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

      return { success: true, orderId: updatedOrder.id, itemProtocol: inventoryItem.display_protocol };
    });
  }

  private async checkStockAndNotify(tx: any, category: any) {
    const count = await tx.inventoryItem.count({
      where: { category, status: InventoryStatus.AVAILABLE }
    });

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
}
