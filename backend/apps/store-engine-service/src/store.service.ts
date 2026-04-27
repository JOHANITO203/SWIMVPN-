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

  async getServers(userNumber: string) {
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
            },
            plan: true,
          },
        },
      },
    });

    if (!customer) {
      return [];
    }

    const latestOrder = customer.orders[0];
    const activeAssignment = latestOrder?.assignments[0];

    // If there is no active assignment or the access is expired, block premium servers
    if (!latestOrder || !activeAssignment || activeAssignment.access_status !== 'ACTIVE') {
      return [];
    }

    // Check expiration dynamically
    const isTrial = latestOrder.order_ref.startsWith('TRIAL-') || latestOrder.payment_ref === 'TRIAL:3D';
    let isExpired = false;

    if (latestOrder.fulfilled_at) {
      const durationMs = isTrial
        ? 3 * 24 * 60 * 60 * 1000
        : (latestOrder.plan.code === 'MONTH' ? 30 : latestOrder.plan.code === 'QUARTER' ? 90 : 7) * 24 * 60 * 60 * 1000;

      if (latestOrder.fulfilled_at.getTime() + durationMs < Date.now()) {
        isExpired = true;
      }
    }

    if (isExpired) {
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
}
