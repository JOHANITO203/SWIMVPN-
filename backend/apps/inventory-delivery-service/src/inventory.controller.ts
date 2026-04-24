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
}
