import { Injectable, Inject, UnauthorizedException } from '@nestjs/common';
import { ClientProxy } from '@nestjs/microservices';
import { JwtService } from '@nestjs/jwt';
import { PrismaService } from '@app/database';
import {
  DEFAULT_RESALE_SLOT_CAP,
  AdminLoginDto,
  CreatePlanDto,
  getPlanSlotCount,
  MoveAssignmentDto,
  RevokeAssignmentDto,
  RetryFulfillmentDto,
  TriggerImportDto,
  UpdateInventoryHealthDto,
} from '@app/contracts';
import * as bcrypt from 'bcryptjs';
import { firstValueFrom } from 'rxjs';

@Injectable()
export class AdminService {
  constructor(
    private readonly prisma: PrismaService,
    private readonly jwtService: JwtService,
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

    const token = this.jwtService.sign({
      sub: admin.id,
      username: admin.username,
      role: admin.role,
    });

    await this.prisma.adminSession.updateMany({
      where: {
        admin_id: admin.id,
        revoked_at: null,
      },
      data: {
        revoked_at: new Date(),
      },
    });

    await this.prisma.adminSession.create({
      data: {
        admin_id: admin.id,
        refresh_token_hash: token,
        expires_at: new Date(Date.now() + 7 * 24 * 60 * 60 * 1000),
      },
    });

    return {
      adminId: admin.id,
      username: admin.username,
      role: admin.role,
      token,
    };
  }

  async validateToken(token: string) {
    try {
      const payload = this.jwtService.verify(token);
      const admin = await this.prisma.admin.findUnique({
        where: { id: payload.sub },
      });

      if (!admin || !admin.active) return null;

      const session = await this.prisma.adminSession.findFirst({
        where: {
          admin_id: admin.id,
          refresh_token_hash: token,
          revoked_at: null,
          expires_at: { gt: new Date() },
        },
      });

      if (!session) return null;

      return {
        id: admin.id,
        username: admin.username,
        role: admin.role,
      };
    } catch (e) {
      return null;
    }
  }

  async logout(token: string) {
    const result = await this.prisma.adminSession.updateMany({
      where: {
        refresh_token_hash: token,
        revoked_at: null,
      },
      data: {
        revoked_at: new Date(),
      },
    });

    return { success: result.count > 0 };
  }

  async createPlan(data: CreatePlanDto) {
    return this.prisma.plan.create({
      data: {
        code: data.code,
        name: data.name,
        duration_label: data.duration_label,
        quota_label: data.quota_label,
        slot_count: data.slot_count ?? getPlanSlotCount(data.code),
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
        sourceQuotaGb: data.sourceQuotaGb,
        maxUsersPerConfig: data.maxUsersPerConfig,
        maxResaleSlots: data.maxResaleSlots,
        supplierExpiresAt: data.supplierExpiresAt,
        supplierProviderName: data.supplierProviderName,
        supplierDeviceLimit: data.supplierDeviceLimit,
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
          sourceQuotaGb: data.sourceQuotaGb ?? 1000,
          maxUsersPerConfig: data.maxUsersPerConfig ?? 5,
          maxResaleSlots: data.maxResaleSlots ?? DEFAULT_RESALE_SLOT_CAP,
          supplierExpiresAt: data.supplierExpiresAt ?? null,
          supplierProviderName: data.supplierProviderName ?? null,
          supplierDeviceLimit: data.supplierDeviceLimit ?? null,
          count: data.configs.length,
          result,
        } as any,
      },
    });

    return result;
  }

  async listInventoryOverview() {
    return firstValueFrom(this.inventoryClient.send({ cmd: 'list_inventory_overview' }, {}));
  }

  async updateInventoryHealth(data: UpdateInventoryHealthDto) {
    return firstValueFrom(
      this.inventoryClient.send({ cmd: 'update_inventory_health' }, data),
    );
  }

  async revokeAssignment(data: RevokeAssignmentDto) {
    return firstValueFrom(this.inventoryClient.send({ cmd: 'revoke_assignment' }, data));
  }

  async moveAssignment(data: MoveAssignmentDto) {
    return firstValueFrom(this.inventoryClient.send({ cmd: 'move_assignment' }, data));
  }

  async retryFulfillment(data: RetryFulfillmentDto) {
    const result = await firstValueFrom(
      this.inventoryClient.send({ cmd: 'fulfill_order' }, { orderId: data.orderId }),
    );

    await this.prisma.adminEvent.create({
      data: {
        admin_id: data.adminId,
        event_type: 'FULFILLMENT_RETRY_TRIGGERED',
        entity_type: 'ORDER',
        entity_id: data.orderId,
        payload_json: {
          orderId: data.orderId,
          result,
          retriedAt: new Date().toISOString(),
        } as any,
      },
    });

    return result;
  }
}
