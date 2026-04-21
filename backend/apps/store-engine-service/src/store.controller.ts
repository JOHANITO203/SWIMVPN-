import { Controller } from '@nestjs/common';
import { MessagePattern } from '@nestjs/microservices';
import { StoreService } from './store.service';

@Controller()
export class StoreController {
  constructor(private readonly storeService: StoreService) {}

  @MessagePattern({ cmd: 'get_active_plans' })
  async getActivePlans() {
    return this.storeService.getActivePlans();
  }

  @MessagePattern({ cmd: 'get_servers' })
  async getServers(data: any) {
    return this.storeService.getServers(data.userNumber);
  }
}