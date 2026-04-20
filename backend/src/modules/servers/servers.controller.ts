import { Controller, Get, Headers, UnauthorizedException } from '@nestjs/common';
import { ServersService } from './servers.service';

@Controller('api/v1/servers')
export class ServersController {
  constructor(private readonly serversService: ServersService) {}

  @Get()
  async getServers(@Headers('x-user-number') userNumber: string) {
    if (!userNumber) {
      throw new UnauthorizedException('User number is required');
    }
    return this.serversService.getAvailableServers(userNumber);
  }
}
