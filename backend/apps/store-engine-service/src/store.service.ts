import { Injectable } from '@nestjs/common';
import { PrismaService } from '@app/database';

@Injectable()
export class StoreService {
  constructor(private readonly prisma: PrismaService) {}

  async getActivePlans() {
    return this.prisma.plan.findMany({
      where: { active: true },
      orderBy: { display_order: 'asc' },
    });
  }

  async getServers(userNumber: string) {
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
