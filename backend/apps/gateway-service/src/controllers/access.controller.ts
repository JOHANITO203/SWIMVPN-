import {
  BadRequestException,
  ConflictException,
  Controller,
  ForbiddenException,
  Get,
  Body,
  Param,
  Inject,
  Headers,
  Post,
  ServiceUnavailableException,
} from '@nestjs/common';
import { ClientProxy } from '@nestjs/microservices';
import * as net from 'net';
import { firstValueFrom } from 'rxjs';

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
    return this.sendCustomer({ cmd: 'bootstrap_access' }, data);
  }

  @Post('access/trial')
  async startTrial(@Body() data: any) {
    return this.sendCustomer({ cmd: 'start_trial' }, data);
  }

  @Post('access/trial/activate')
  async activateTrial(@Body() data: any) {
    return this.sendCustomer({ cmd: 'activate_trial' }, data);
  }

  @Post('access/profile/complete')
  async completeProfile(@Body() data: any) {
    return this.sendCustomer({ cmd: 'complete_profile' }, data);
  }

  @Get('access/:userNumber')
  async getAccessProfile(@Param('userNumber') userNumber: string) {
    return this.customerClient.send(
      { cmd: 'get_profile' },
      { userNumber, exposeRuntimeConfig: false },
    );
  }

  @Post('subscription/import')
  async importSubscription(@Body() data: any) {
    return this.sendCustomer({ cmd: 'import_subscription' }, data);
  }

  @Post('subscription/resolve-crypt')
  async resolveCryptSubscription(@Body() data: any) {
    return this.sendCustomer({ cmd: 'resolve_crypt_subscription' }, data);
  }

  @Post('subscription/activate-code')
  async activateCode(@Body() data: any) {
    return this.sendCustomer({ cmd: 'activate_code' }, data);
  }

  @Post('subscription/usage')
  async reportUsage(@Body() data: any) {
    return this.sendCustomer({ cmd: 'report_usage' }, data);
  }

  @Get('servers')
  async getServers(
    @Headers('x-user-number') userNumber: string,
    @Headers('x-device-id') deviceId?: string,
  ) {
    return this.storeClient.send({ cmd: 'get_servers' }, { userNumber, deviceId });
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

  private async sendCustomer(pattern: Record<string, string>, data: any) {
    try {
      return await firstValueFrom(this.customerClient.send(pattern, data));
    } catch (error: any) {
      const message = this.extractErrorMessage(error);
      const normalized = message.toLowerCase();

      if (normalized.includes('trial already used')) {
        throw new ConflictException(message);
      }

      if (normalized.includes('not authorized')) {
        throw new ForbiddenException(message);
      }

      if (
        normalized.includes('required') ||
        normalized.includes('not found') ||
        normalized.includes('invalid') ||
        normalized.includes('unsupported') ||
        normalized.includes('should not be empty') ||
        normalized.includes('must be')
      ) {
        throw new BadRequestException(message);
      }

      throw new ServiceUnavailableException(message || 'Access service unavailable');
    }
  }

  private extractErrorMessage(error: any) {
    if (typeof error?.error === 'string' && error.error.trim().length > 0) {
      return error.error;
    }

    if (typeof error?.response?.message === 'string' && error.response.message.trim().length > 0) {
      return error.response.message;
    }

    if (Array.isArray(error?.response?.message) && error.response.message.length > 0) {
      return error.response.message.join(', ');
    }

    if (typeof error?.message === 'string' && error.message.trim().length > 0) {
      return error.message;
    }

    if (typeof error?.error?.message === 'string' && error.error.message.trim().length > 0) {
      return error.error.message;
    }

    if (Array.isArray(error?.error?.message) && error.error.message.length > 0) {
      return error.error.message.join(', ');
    }

    return 'Access service unavailable';
  }
}
