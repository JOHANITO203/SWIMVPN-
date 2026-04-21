import { Controller } from '@nestjs/common';
import { MessagePattern, Payload, EventPattern } from '@nestjs/microservices';
import { AdminService } from './admin.service';
import { AdminBotService } from './admin-bot.service';
import { AdminLoginDto, CreatePlanDto, TriggerImportDto } from '@app/contracts';

@Controller()
export class AdminController {
  constructor(
    private readonly adminService: AdminService,
    private readonly adminBotService: AdminBotService,
  ) {}

  @EventPattern('low_stock_alert')
  async handleLowStock(@Payload() data: { category: string; remaining: number }) {
    await this.adminBotService.sendAdminAlert(
      `⚠️ *LOW STOCK ALERT*\nCategory: ${data.category}\nRemaining: ${data.remaining} configs.`
    );
  }

  @EventPattern('order_fulfilled')
  async handleOrderFulfilled(@Payload() data: { orderId: string; orderRef: string; amount: number; planCode: string }) {
    await this.adminBotService.sendAdminAlert(
      `✅ *NEW SALE*\nRef: \`${data.orderRef}\`\nPlan: ${data.planCode}\nAmount: ${data.amount} RUB`
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
}
