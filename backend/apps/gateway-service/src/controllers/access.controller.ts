import { Controller, Post, Get, Body, Param, Inject, Headers, ServiceUnavailableException } from '@nestjs/common';
import { ClientProxy } from '@nestjs/microservices';
import * as net from 'net';

@Controller()
export class AccessController {
  constructor(
    @Inject('CUSTOMER_SERVICE') private readonly customerClient: ClientProxy,
    @Inject('STORE_SERVICE') private readonly storeClient: ClientProxy,
  ) {}

  @Get('health')
  async health() {
    const targets = [
      { host: process.env.CUSTOMER_SERVICE_HOST || '127.0.0.1', port: 3001, name: 'customer-order-service' },
      { host: process.env.INVENTORY_SERVICE_HOST || '127.0.0.1', port: 3002, name: 'inventory-delivery-service' },
      { host: process.env.ADMIN_SERVICE_HOST || '127.0.0.1', port: 3003, name: 'admin-control-service' },
      { host: process.env.VPN_CONFIG_SERVICE_HOST || '127.0.0.1', port: 3004, name: 'vpn-config-engine-service' },
      { host: process.env.STORE_SERVICE_HOST || '127.0.0.1', port: 3005, name: 'store-engine-service' },
    ];

    const checks = await Promise.all(
      targets.map((target) => this.checkTcp(target.host, target.port).then((ok) => ({ ...target, ok }))),
    );

    const failed = checks.filter((c) => !c.ok);
    if (failed.length > 0) {
      throw new ServiceUnavailableException({
        status: 'degraded',
        failedDependencies: failed.map((f) => f.name),
      });
    }

    return {
      status: 'ok',
      service: 'gateway-service',
      dependencies: checks.map((c) => ({ service: c.name, status: c.ok ? 'up' : 'down' })),
    };
  }

  @Post('access/bootstrap')
  async bootstrapAccess(@Body() data: any) {
    return this.customerClient.send({ cmd: 'bootstrap_access' }, data);
  }

  @Post('access/trial')
  async startTrial(@Body() data: any) {
    return this.customerClient.send({ cmd: 'start_trial' }, data);
  }

  @Post('access/trial/activate')
  async activateTrial(@Body() data: any) {
    return this.customerClient.send({ cmd: 'activate_trial' }, data);
  }

  @Get('access/:userNumber')
  async getAccessProfile(@Param('userNumber') userNumber: string) {
    return this.customerClient.send({ cmd: 'get_profile' }, { userNumber });
  }

  @Post('subscription/import')
  async importSubscription(@Body() data: any) {
    return this.customerClient.send({ cmd: 'import_subscription' }, data);
  }

  @Post('subscription/resolve-crypt')
  async resolveCryptSubscription(@Body() data: any) {
    return this.customerClient.send({ cmd: 'resolve_crypt_subscription' }, data);
  }

  @Post('subscription/activate-code')
  async activateCode(@Body() data: any) {
    return this.customerClient.send({ cmd: 'activate_code' }, data);
  }

  @Post('subscription/usage')
  async reportUsage(@Body() data: any) {
    return this.customerClient.send({ cmd: 'report_usage' }, data);
  }

  @Get('servers')
  async getServers(@Headers('x-user-number') userNumber: string) {
    return this.storeClient.send({ cmd: 'get_servers' }, { userNumber });
  }

  private checkTcp(host: string, port: number): Promise<boolean> {
    return new Promise((resolve) => {
      const socket = new net.Socket();
      let settled = false;
      const done = (result: boolean) => {
        if (settled) return;
        settled = true;
        socket.destroy();
        resolve(result);
      };

      socket.setTimeout(2000);
      socket.on('connect', () => done(true));
      socket.on('error', () => done(false));
      socket.on('timeout', () => done(false));
      socket.connect(port, host);
    });
  }
}
