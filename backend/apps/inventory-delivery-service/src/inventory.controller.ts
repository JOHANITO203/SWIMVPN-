import { Controller } from '@nestjs/common';
import { MessagePattern, Payload } from '@nestjs/microservices';
import { InventoryService } from './inventory.service';
import { ImportConfigsDto, FulfillOrderDto, RecordUsageDto } from '@app/contracts/inventory.dto';

@Controller()
export class InventoryController {
  constructor(private readonly inventoryService: InventoryService) {}

  @MessagePattern({ cmd: 'import_configs' })
  async importConfigs(@Payload() data: ImportConfigsDto) {
    return this.inventoryService.importConfigs(data);
  }

  @MessagePattern({ cmd: 'fulfill_order' })
  async fulfillOrder(@Payload() data: FulfillOrderDto) {
    return this.inventoryService.fulfillOrder(data.orderId);
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
}
