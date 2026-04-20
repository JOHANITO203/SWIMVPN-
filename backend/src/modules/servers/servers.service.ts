import { Injectable, UnauthorizedException } from '@nestjs/common';
import { PrismaService } from '../../prisma/prisma.service';
import { AccessService } from '../access/access.service';

@Injectable()
export class ServersService {
  constructor(
    private prisma: PrismaService,
    private accessService: AccessService
  ) {}

  async getAvailableServers(userNumber: string) {
    const profile = await this.accessService.getAccessProfile(userNumber);

    if (profile.status === 'EXPIRED') {
       // Accès expiré : aucun serveur autorisé
      return [];
    }

    // Filtrage basé sur le plan (TRIAL vs PREMIUM)
    let planScopeFilter = ['TRIAL', 'PREMIUM'];

    if (profile.planType === 'TRIAL') {
      planScopeFilter = ['TRIAL'];
    }

    const servers = await this.prisma.serverNode.findMany({
      where: {
        isActive: true,
        planScope: {
          in: planScopeFilter as any
        }
      },
      select: {
        id: true,
        country: true,
        city: true,
        host: true,
        port: true,
        protocol: true,
        tags: true,
        planScope: true
      }
    });

    return servers;
  }
}
