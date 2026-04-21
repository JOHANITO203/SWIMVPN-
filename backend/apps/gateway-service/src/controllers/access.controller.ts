import { Controller, Post, Get, Body, Param, Inject, Headers } from '@nestjs/common';
import { ClientProxy } from '@nestjs/microservices';

@Controller()
export class AccessController {
  constructor(
    @Inject('CUSTOMER_SERVICE') private readonly customerClient: ClientProxy,
    @Inject('STORE_SERVICE') private readonly storeClient: ClientProxy,
  ) {}

  @Get('health')
  health() {
    return { status: 'ok', service: 'gateway-service' };
  }

  @Post('access/trial')
  async startTrial(@Body() data: any) {
    return this.customerClient.send({ cmd: 'start_trial' }, data);
  }

  @Get('access/:userNumber')
  async getAccessProfile(@Param('userNumber') userNumber: string) {
    return this.customerClient.send({ cmd: 'get_profile' }, { userNumber });
  }

  @Post('subscription/import')
  async importSubscription(@Body() data: any) {
    return this.customerClient.send({ cmd: 'import_subscription' }, data);
  }

  @Post('subscription/activate-code')
  async activateCode(@Body() data: any) {
    return this.customerClient.send({ cmd: 'activate_code' }, data);
  }

  @Get('servers')
  async getServers(@Headers('x-user-number') userNumber: string) {
    return this.storeClient.send({ cmd: 'get_servers' }, { userNumber });
  }
}
