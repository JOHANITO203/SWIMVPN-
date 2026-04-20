import { Injectable, NotFoundException, BadRequestException } from '@nestjs/common';
import { PrismaService } from '../../prisma/prisma.service';
import { AccessService } from '../access/access.service';
import { ImportSubscriptionDto } from './dto/import-subscription.dto';
import { ActivateCodeDto } from './dto/activate-code.dto';

@Injectable()
export class SubscriptionService {
  constructor(
    private prisma: PrismaService,
    private accessService: AccessService
  ) {}

  async importSubscriptionUrl(dto: ImportSubscriptionDto) {
    const user = await this.prisma.userAccess.findUnique({
      where: { userNumber: dto.userNumber }
    });

    if (!user) {
      throw new NotFoundException('User not found');
    }

    // Le lien est un support technique d'accès (Cas A et Cas B).
    // On ne touche PAS au planType (TRIAL, PREMIUM, EXPIRED) ni à la date d'expiration.
    // L'activation commerciale se fait via activateCode ou paymentWebhook.

    // On valide le lien (si besoin, on peut faire une requête HTTP sur le lien pour extraire la config,
    // mais pour la V1, on le sauvegarde et on le retourne simplement).
    await this.prisma.userAccess.update({
      where: { userNumber: dto.userNumber },
      data: { subscriptionUrl: dto.subscriptionUrl }
    });

    // On retourne le profil, qui déterminera de lui-même si le statut est ACTIVE ou EXPIRED
    // en se basant UNIQUEMENT sur trialExpiresAt ou subscriptionExpiresAt.
    return this.accessService.getAccessProfile(dto.userNumber);
  }

  async activateCode(dto: ActivateCodeDto) {
    const user = await this.prisma.userAccess.findUnique({
      where: { userNumber: dto.userNumber }
    });

    if (!user) throw new NotFoundException('User not found');

    const code = await this.prisma.activationCode.findUnique({
      where: { code: dto.code },
      include: { plan: true }
    });

    if (!code) throw new NotFoundException('Invalid code');
    if (code.isUsed) throw new BadRequestException('Code already used');
    if (code.expiresAt && code.expiresAt < new Date()) throw new BadRequestException('Code expired');

    // Mettre à jour la date d'expiration
    const now = new Date();
    // Si l'utilisateur avait déjà un abo en cours, on l'étend. Sinon, on part d'aujourd'hui.
    const currentExpiration = user.subscriptionExpiresAt && user.subscriptionExpiresAt > now
      ? user.subscriptionExpiresAt
      : now;

    const newExpirationDate = new Date(currentExpiration.getTime() + code.plan.durationDays * 24 * 60 * 60 * 1000);

    // Transaction pour marquer le code comme utilisé et mettre à jour le compte
    await this.prisma.$transaction([
      this.prisma.activationCode.update({
        where: { id: code.id },
        data: {
          isUsed: true,
          usedAt: now,
          assignedToUserAccessId: user.id
        }
      }),
      this.prisma.userAccess.update({
        where: { id: user.id },
        data: {
          planType: 'PREMIUM',
          subscriptionExpiresAt: newExpirationDate,
          devicesAllowed: code.plan.devicesAllowed
        }
      })
    ]);

    return this.accessService.getAccessProfile(dto.userNumber);
  }
}
