import { Controller } from '@nestjs/common';
import { MessagePattern, Payload } from '@nestjs/microservices';
import { InventoryService } from './inventory.service';
import {
  ImportConfigsDto,
  ImportTrialConfigsDto,
  FulfillOrderDto,
  RecordUsageDto,
} from '@app/contracts/inventory.dto';
import { PlanCategory } from '@prisma/client';

@Controller()
export class InventoryController {
  constructor(private readonly inventoryService: InventoryService) {}

  @MessagePattern({ cmd: 'import_configs' })
  async importConfigs(@Payload() data: ImportConfigsDto) {
    return this.inventoryService.importConfigs(data);
  }

  @MessagePattern({ cmd: 'import_trial_configs' })
  async importTrialConfigs(@Payload() data: ImportTrialConfigsDto) {
    return this.inventoryService.importTrialConfigs(data);
  }

  @MessagePattern({ cmd: 'fulfill_order' })
  async fulfillOrder(@Payload() data: FulfillOrderDto) {
    try {
      return await this.inventoryService.fulfillOrder(data.orderId);
    } catch (error) {
      return {
        success: false,
        error: this.extractErrorMessage(error),
      };
    }
  }

  @MessagePattern({ cmd: 'record_assignment_usage' })
  async recordAssignmentUsage(@Payload() data: RecordUsageDto) {
    return this.inventoryService.recordAssignmentUsage(data);
  }

  @MessagePattern({ cmd: 'trigger_health_check' })
  async triggerHealthCheck() {
    return this.inventoryService.runHealthCheck();
  }

  @MessagePattern({ cmd: 'list_inventory_overview' })
  async listInventoryOverview() {
    return this.inventoryService.listInventoryOverview();
  }

  @MessagePattern({ cmd: 'list_trial_inventory_overview' })
  async listTrialInventoryOverview() {
    return this.inventoryService.listTrialInventoryOverview();
  }

  @MessagePattern({ cmd: 'get_inventory_stats' })
  async getInventoryStats() {
    return this.inventoryService.getInventoryStats();
  }

  // Sale gate: how many configs are still allocatable for a paid order of this category.
  @MessagePattern({ cmd: 'check_category_availability' })
  async checkCategoryAvailability(@Payload() data: { category: PlanCategory }) {
    return this.inventoryService.getCategoryAvailability(data.category);
  }

  @MessagePattern({ cmd: 'clear_available_configs' })
  async clearAvailableConfigs(@Payload() data: { category: any }) {
    return this.inventoryService.clearAvailableConfigs(data.category);
  }

  @MessagePattern({ cmd: 'clear_available_trial_configs' })
  async clearAvailableTrialConfigs() {
    return this.inventoryService.clearAvailableTrialConfigs();
  }

  @MessagePattern({ cmd: 'update_inventory_health' })
  async updateInventoryHealth(
    @Payload() data: {
      inventoryItemId: string;
      healthStatus: 'HEALTHY' | 'DEGRADED' | 'FULL' | 'EXPIRED' | 'DISABLED';
      reason?: string | null;
      adminId?: string | null;
    },
  ) {
    return this.inventoryService.updateInventoryHealth(data as any);
  }

  @MessagePattern({ cmd: 'revoke_assignment' })
  async revokeAssignment(
    @Payload() data: { assignmentId: string; reason?: string; adminId?: string | null },
  ) {
    return this.inventoryService.revokeAssignment(data);
  }

  @MessagePattern({ cmd: 'move_assignment' })
  async moveAssignment(
    @Payload()
    data: { assignmentId: string; targetInventoryItemId: string; adminId?: string | null },
  ) {
    return this.inventoryService.moveAssignment(data);
  }

  private extractErrorMessage(error: unknown) {
    if (error instanceof Error && error.message.trim().length > 0) {
      return error.message;
    }

    if (
      typeof error === 'object' &&
      error !== null &&
      'message' in error &&
      typeof (error as { message?: unknown }).message === 'string' &&
      (error as { message: string }).message.trim().length > 0
    ) {
      return (error as { message: string }).message;
    }

    return 'Inventory fulfillment failed';
  }
}
