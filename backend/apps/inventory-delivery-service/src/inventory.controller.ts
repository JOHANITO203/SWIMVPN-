import { Controller } from '@nestjs/common';
import { MessagePattern, Payload } from '@nestjs/microservices';
import { InventoryService } from './inventory.service';
import { ImportConfigsDto, FulfillOrderDto } from '@app/contracts/inventory.dto';

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

  @MessagePattern({ cmd: 'trigger_health_check' })
  async triggerHealthCheck() {
    return this.inventoryService.runHealthCheck();
  }
}
