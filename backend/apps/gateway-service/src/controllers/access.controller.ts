import { Controller, Post, Get, Body, Param, Inject, Headers } from '@nestjs/common';
import { ClientProxy } from '@nestjs/microservices';

@Controller('api/v1')
export class AccessController {
  constructor(
    @Inject('CUSTOMER_SERVICE') private readonly customerClient: ClientProxy,
    @Inject('VPN_CONFIG_SERVICE') private readonly vpnClient: ClientProxy,
    @Inject('STORE_SERVICE') private readonly storeClient: ClientProxy,
  ) {}

  @Post('access/trial')
  async startTrial(@Body() data: any) {
    // In a real scenario, this would check if deviceId already had a trial
    // For now, redirect to customer service or a specialized trial service
    return this.customerClient.send({ cmd: 'start_trial' }, data);
  }

  @Get('access/:userNumber')
  async getAccessProfile(@Param('userNumber') userNumber: String) {
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
