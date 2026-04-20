import { Controller, Post, Get, Body, Param } from '@nestjs/common';
import { AccessService } from './access.service';
import { StartTrialDto } from './dto/start-trial.dto';

@Controller('api/v1/access')
export class AccessController {
  constructor(private readonly accessService: AccessService) {}

  @Post('trial')
  async startTrial(@Body() dto: StartTrialDto) {
    return this.accessService.startTrial(dto);
  }

  @Get(':userNumber')
  async getProfile(@Param('userNumber') userNumber: string) {
    return this.accessService.getAccessProfile(userNumber);
  }
}
