import { Injectable, Inject, UnauthorizedException } from '@nestjs/common';
import { ClientProxy } from '@nestjs/microservices';
import { PrismaService } from '@app/database';
import { AdminLoginDto, CreatePlanDto, TriggerImportDto } from '@app/contracts';
import * as bcrypt from 'bcryptjs';
import { firstValueFrom } from 'rxjs';

@Injectable()
export class AdminService {
  constructor(
    private readonly prisma: PrismaService,
    @Inject('INVENTORY_SERVICE') private readonly inventoryClient: ClientProxy,
  ) {}

  async login(data: AdminLoginDto) {
    const admin = await this.prisma.admin.findUnique({
      where: { username: data.username },
    });

    if (!admin || !admin.active) {
      throw new UnauthorizedException('Invalid credentials');
    }

    const isMatch = await bcrypt.compare(data.password_plain, admin.password_hash);
    if (!isMatch) {
      throw new UnauthorizedException('Invalid credentials');
    }

    return {
      id: admin.id,
      username: admin.username,
      role: admin.role,
    };
  }

  async createPlan(data: CreatePlanDto) {
    return this.prisma.plan.create({
      data: {
        code: data.code,
        name: data.name,
        duration_label: data.duration_label,
        quota_label: data.quota_label,
        price_rub: data.price_rub,
      },
    });
  }

  async getPlans() {
    return this.prisma.plan.findMany({
      orderBy: { display_order: 'asc' },
    });
  }

  async triggerImport(data: TriggerImportDto) {
    // 1. Delegate to inventory service
    const result = await firstValueFrom(
      this.inventoryClient.send({ cmd: 'import_configs' }, {
        category: data.category,
        configs: data.configs,
        batchName: data.batchName,
      }),
    );

    // 2. Log admin event
    await this.prisma.adminEvent.create({
      data: {
        admin_id: data.adminId,
        event_type: 'CONFIG_IMPORTED',
        entity_type: 'INVENTORY',
        entity_id: 'BATCH',
        payload_json: {
          category: data.category,
          batchName: data.batchName,
          count: data.configs.length,
          result,
        } as any,
      },
    });

    return result;
  }
}
