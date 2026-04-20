import { Injectable } from '@nestjs/common';
import { PrismaService } from '../../prisma/prisma.service';
import { StartTrialDto } from './dto/start-trial.dto';

@Injectable()
export class AccessService {
  constructor(private prisma: PrismaService) {}

  async startTrial(dto: StartTrialDto) {
    // Vérifier si un compte existe pour ce device
    const existing = await this.prisma.userAccess.findUnique({
      where: { deviceId: dto.deviceId },
    });

    if (existing) {
      return this.formatAccessResponse(existing);
    }

    // Créer un nouveau compte Trial
    const trialDurationDays = 7;
    const now = new Date();
    const trialExpiresAt = new Date(now.getTime() + trialDurationDays * 24 * 60 * 60 * 1000);

    // Générer un userNumber court (8 chiffres)
    const userNumber = Math.floor(10000000 + Math.random() * 90000000).toString();

    const newAccess = await this.prisma.userAccess.create({
      data: {
        deviceId: dto.deviceId,
        userNumber,
        planType: 'TRIAL',
        trialExpiresAt,
      },
    });

    return this.formatAccessResponse(newAccess);
  }

  async getAccessProfile(userNumber: string) {
    const access = await this.prisma.userAccess.findUnique({
      where: { userNumber },
    });

    if (!access) throw new Error('Access not found');
    return this.formatAccessResponse(access);
  }

  private formatAccessResponse(access: any) {
    const now = new Date();
    let status = 'EXPIRED';
    let activePlan = access.planType;

    // Règle métier : Priorité Premium > Trial > Expired
    if (access.subscriptionExpiresAt && access.subscriptionExpiresAt > now) {
      status = 'ACTIVE';
      activePlan = 'PREMIUM';
    } else if (access.trialExpiresAt > now) {
      status = 'ACTIVE';
      activePlan = 'TRIAL';
    }

    return {
      userNumber: access.userNumber,
      email: access.email,
      planType: activePlan,
      status: status,
      trialStartedAt: access.trialStartedAt,
      trialExpiresAt: access.trialExpiresAt,
      subscriptionExpiresAt: access.subscriptionExpiresAt,
      subscriptionUrl: status === 'ACTIVE' ? access.subscriptionUrl : null,
      devicesAllowed: access.devicesAllowed,
      dataLimitGB: access.dataLimitGB,
      dataUsedBytes: access.dataUsedBytes.toString() // BigInt to string for JSON
    };
  }
}
