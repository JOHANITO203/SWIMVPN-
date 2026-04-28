import { Injectable } from '@nestjs/common';
import { PrismaService } from '@app/database';

@Injectable()
export class StoreService {
  constructor(private readonly prisma: PrismaService) {}

  async getActivePlans() {
    return this.prisma.plan.findMany({
      where: {
        active: true,
        price_rub: { gt: 0 },
      },
      orderBy: { display_order: 'asc' },
    });
  }

  async getServers(data: { userNumber: string; deviceId?: string }) {
    const userNumber = data.userNumber;
    const deviceId = data.deviceId?.trim();
    // SECURITY BOUNDARY: Verify entitlement before returning backend servers
    const customer = await this.prisma.customer.findUnique({
      where: { public_id: userNumber },
      include: {
        orders: {
          where: {
            status: { in: ['FULFILLED', 'PAID', 'PENDING_FULFILLMENT'] },
          },
          orderBy: { created_at: 'desc' },
          take: 1,
          include: {
            assignments: {
              where: { access_status: 'ACTIVE' },
              orderBy: { assigned_at: 'desc' },
              take: 1,
              include: {
                inventory_item: true,
              },
            },
            plan: true,
          },
        },
      },
    });

    if (!customer) {
      return [];
    }

    if (!deviceId || !customer.device_id || customer.device_id !== deviceId) {
      return [];
    }

    const latestOrder = customer.orders[0];
    const activeAssignment = latestOrder?.assignments[0];
    const inventoryItem = activeAssignment?.inventory_item;

    // If there is no active assignment or the access is expired, block premium servers
    if (
      !latestOrder ||
      !activeAssignment ||
      activeAssignment.access_status !== 'ACTIVE' ||
      !inventoryItem
    ) {
      return [];
    }

    if (
      inventoryItem.health_status === 'EXPIRED' ||
      inventoryItem.health_status === 'DISABLED' ||
      this.isSourceQuotaExceeded(inventoryItem.source_quota_bytes, inventoryItem.source_used_bytes)
    ) {
      return [];
    }

    // Check expiration dynamically
    const isTrial = latestOrder.order_ref.startsWith('TRIAL-') || latestOrder.payment_ref === 'TRIAL:3D';
    const providerExpiresAt = activeAssignment.expires_at;
    let orderExpiresAt: Date | null = null;

    if (latestOrder.fulfilled_at) {
      const durationMs = isTrial
        ? 3 * 24 * 60 * 60 * 1000
        : (latestOrder.plan.code === 'MONTH' ? 30 : latestOrder.plan.code === 'QUARTER' ? 90 : 7) * 24 * 60 * 60 * 1000;

      orderExpiresAt = new Date(latestOrder.fulfilled_at.getTime() + durationMs);
    }

    const expiresAt = this.pickEarlierDate(providerExpiresAt, orderExpiresAt);
    if (expiresAt && expiresAt.getTime() < Date.now()) {
      return [];
    }

    const servers = await this.prisma.server.findMany({
      where: { is_active: true },
    });

    return servers.map(s => ({
      id: s.id,
      country: s.name,
      city: s.name,
      host: s.host,
      port: 443,
      protocol: "VLESS",
      tags: ["LOW-LATENCY"],
      planScope: "PREMIUM",
      countryCode: s.country_code,
    }));
  }

  private isSourceQuotaExceeded(sourceQuotaBytes?: bigint | null, sourceUsedBytes?: bigint | null) {
    if (!sourceQuotaBytes || sourceQuotaBytes <= 0n) {
      return false;
    }

    return (sourceUsedBytes ?? 0n) >= sourceQuotaBytes;
  }

  private pickEarlierDate(first?: Date | null, second?: Date | null) {
    if (!first) {
      return second || null;
    }

    if (!second) {
      return first;
    }

    return first.getTime() <= second.getTime() ? first : second;
  }
}
