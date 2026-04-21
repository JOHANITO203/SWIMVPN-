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

  @MessagePattern({ cmd: 'admin_login' })
  async login(@Payload() data: AdminLoginDto) {
    return this.adminService.login(data);
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
