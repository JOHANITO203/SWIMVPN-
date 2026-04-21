import { Controller, Get, Inject } from '@nestjs/common';
import { ClientProxy } from '@nestjs/microservices';

@Controller('api/v1/store')
export class StoreController {
  constructor(@Inject('STORE_SERVICE') private readonly storeClient: ClientProxy) {}

  @Get('plans')
  getPlans() {
    return this.storeClient.send({ cmd: 'get_active_plans' }, {});
  }
}
