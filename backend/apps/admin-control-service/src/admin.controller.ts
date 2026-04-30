import { Controller } from '@nestjs/common';
import { EventPattern, MessagePattern, Payload } from '@nestjs/microservices';
import { AdminService } from './admin.service';
import { AdminBotService } from './admin-bot.service';
import {
  AdminLoginDto,
  CreatePlanDto,
  MoveAssignmentDto,
  RevokeAssignmentDto,
  RetryFulfillmentDto,
  TriggerImportDto,
  UpdateInventoryHealthDto,
} from '@app/contracts';

@Controller()
export class AdminController {
  constructor(
    private readonly adminService: AdminService,
    private readonly adminBotService: AdminBotService,
  ) {}

  @EventPattern('low_stock_alert')
  async handleLowStock(@Payload() data: { category: string; remaining: number }) {
    await this.adminBotService.sendAdminAlert(
      `LOW STOCK ALERT\nCategory: ${data.category}\nRemaining: ${data.remaining} configs.`,
    );
  }

  @EventPattern('order_fulfilled')
  async handleOrderFulfilled(
    @Payload() data: { orderId: string; orderRef: string; amount: number; planCode: string },
  ) {
    await this.adminService.recordOrderRevenue({
      orderRef: data.orderRef,
      amount: data.amount,
      planCode: data.planCode,
    });

    await this.adminBotService.sendAdminAlert(
      `NEW SALE\nRef: ${data.orderRef}\nPlan: ${data.planCode}\nAmount: ${data.amount} RUB`,
    );
  }

  @EventPattern('fulfillment_pending_alert')
  async handleFulfillmentPending(
    @Payload() data: { orderRef: string; planCode: string; requiredSlots: number },
  ) {
    await this.adminBotService.sendAdminAlert(
      `FULFILLMENT PENDING\nRef: ${data.orderRef}\nPlan: ${data.planCode}\nSlots needed: ${data.requiredSlots}`,
    );
  }

  @MessagePattern({ cmd: 'admin_login' })
  async login(@Payload() data: AdminLoginDto) {
    return this.adminService.login(data);
  }

  @MessagePattern({ cmd: 'validate_admin_token' })
  async validateToken(@Payload() data: { token: string }) {
    return this.adminService.validateToken(data.token);
  }

  @MessagePattern({ cmd: 'admin_logout' })
  async logout(@Payload() data: { token: string }) {
    return this.adminService.logout(data.token);
  }

  @MessagePattern({ cmd: 'create_plan' })
  async createPlan(@Payload() data: CreatePlanDto) {
    return this.adminService.createPlan(data);
  }

  @MessagePattern({ cmd: 'get_plans' })
  async getPlans() {
    return this.adminService.getPlans();
  }

  @MessagePattern({ cmd: 'trigger_import' })
  async triggerImport(@Payload() data: TriggerImportDto) {
    return this.adminService.triggerImport(data);
  }

  @MessagePattern({ cmd: 'list_inventory_overview' })
  async listInventoryOverview() {
    return this.adminService.listInventoryOverview();
  }

  @MessagePattern({ cmd: 'update_inventory_health' })
  async updateInventoryHealth(@Payload() data: UpdateInventoryHealthDto) {
    return this.adminService.updateInventoryHealth(data);
  }

  @MessagePattern({ cmd: 'revoke_assignment' })
  async revokeAssignment(@Payload() data: RevokeAssignmentDto) {
    return this.adminService.revokeAssignment(data);
  }

  @MessagePattern({ cmd: 'move_assignment' })
  async moveAssignment(@Payload() data: MoveAssignmentDto) {
    return this.adminService.moveAssignment(data);
  }

  @MessagePattern({ cmd: 'retry_fulfillment' })
  async retryFulfillment(@Payload() data: RetryFulfillmentDto) {
    return this.adminService.retryFulfillment(data);
  }
}
