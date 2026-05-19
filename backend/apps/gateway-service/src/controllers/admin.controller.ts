import { BadRequestException, Controller, Post, Get, Body, Inject, UseGuards, Req, Headers } from '@nestjs/common';
import { ClientProxy } from '@nestjs/microservices';
import {
  AdminLoginDto,
  CreatePlanDto,
  GenerateSwimCryptImportDto,
  MoveAssignmentDto,
  RetryFulfillmentDto,
  RevokeAssignmentDto,
  TriggerImportDto,
  TriggerTrialImportDto,
  UpdateInventoryHealthDto,
} from '@app/contracts';
import { AdminGuard } from '../admin.guard';

interface AdminRequest {
  admin: {
    id: string;
  };
}

type TriggerImportBody = Omit<TriggerImportDto, 'adminId'>;
type TriggerTrialImportBody = Omit<TriggerTrialImportDto, 'adminId'>;
type UpdateInventoryHealthBody = Omit<UpdateInventoryHealthDto, 'adminId'>;
type RevokeAssignmentBody = Omit<RevokeAssignmentDto, 'adminId'>;
type MoveAssignmentBody = Omit<MoveAssignmentDto, 'adminId'>;
type RetryFulfillmentBody = Omit<RetryFulfillmentDto, 'adminId'>;

@Controller('admin')
export class AdminController {
  constructor(
    @Inject('ADMIN_SERVICE') private readonly adminClient: ClientProxy,
    @Inject('VPN_CONFIG_SERVICE') private readonly vpnConfigClient: ClientProxy,
  ) {}

  @Post('login')
  login(@Body() data: AdminLoginDto) {
    return this.adminClient.send({ cmd: 'admin_login' }, data);
  }

  @UseGuards(AdminGuard)
  @Post('plans')
  createPlan(@Body() data: CreatePlanDto, @Req() req: AdminRequest) {
    return this.adminClient.send({ cmd: 'create_plan' }, { ...data, adminId: req.admin.id });
  }

  @UseGuards(AdminGuard)
  @Get('plans')
  getPlans() {
    return this.adminClient.send({ cmd: 'get_plans' }, {});
  }

  @UseGuards(AdminGuard)
  @Post('import')
  importConfigs(@Body() data: TriggerImportBody, @Req() req: AdminRequest) {
    return this.adminClient.send({ cmd: 'trigger_import' }, { ...data, adminId: req.admin.id });
  }

  @UseGuards(AdminGuard)
  @Post('trial/import')
  importTrialConfigs(@Body() data: TriggerTrialImportBody, @Req() req: AdminRequest) {
    this.assertTrialImportBody(data);
    return this.adminClient.send({ cmd: 'trigger_trial_import' }, { ...data, adminId: req.admin.id });
  }

  @UseGuards(AdminGuard)
  @Get('inventory')
  getInventoryOverview() {
    return this.adminClient.send({ cmd: 'list_inventory_overview' }, {});
  }

  @UseGuards(AdminGuard)
  @Post('inventory/health')
  updateInventoryHealth(@Body() data: UpdateInventoryHealthBody, @Req() req: AdminRequest) {
    return this.adminClient.send({ cmd: 'update_inventory_health' }, { ...data, adminId: req.admin.id });
  }

  @UseGuards(AdminGuard)
  @Post('assignments/revoke')
  revokeAssignment(@Body() data: RevokeAssignmentBody, @Req() req: AdminRequest) {
    return this.adminClient.send({ cmd: 'revoke_assignment' }, { ...data, adminId: req.admin.id });
  }

  @UseGuards(AdminGuard)
  @Post('assignments/move')
  moveAssignment(@Body() data: MoveAssignmentBody, @Req() req: AdminRequest) {
    return this.adminClient.send({ cmd: 'move_assignment' }, { ...data, adminId: req.admin.id });
  }

  @UseGuards(AdminGuard)
  @Post('orders/retry-fulfillment')
  retryFulfillment(@Body() data: RetryFulfillmentBody, @Req() req: AdminRequest) {
    return this.adminClient.send({ cmd: 'retry_fulfillment' }, { ...data, adminId: req.admin.id });
  }

  @UseGuards(AdminGuard)
  @Post('crypt-import')
  generateCryptImport(@Body() data: GenerateSwimCryptImportDto) {
    return this.vpnConfigClient.send({ cmd: 'generate_swim_crypt_import' }, data);
  }

  @UseGuards(AdminGuard)
  @Post('logout')
  logout(@Headers('authorization') authorization: string) {
    const token = authorization?.startsWith('Bearer ') ? authorization.split(' ')[1] : '';
    return this.adminClient.send({ cmd: 'admin_logout' }, { token });
  }

  private assertTrialImportBody(data: TriggerTrialImportBody) {
    const configs = Array.isArray(data?.configs) ? data.configs : [];
    const hasInvalidConfig = configs.some((item) => typeof item !== 'string' || item.trim().length === 0);
    if (configs.length === 0 || hasInvalidConfig) {
      throw new BadRequestException('configs must be a non-empty array of raw VPN config strings');
    }

    if (
      data.supplierExpiresAt !== undefined &&
      (
        typeof data.supplierExpiresAt !== 'string' ||
        Number.isNaN(new Date(data.supplierExpiresAt).getTime())
      )
    ) {
      throw new BadRequestException('supplierExpiresAt must be a valid ISO date');
    }
  }
}
