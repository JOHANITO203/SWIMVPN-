import { Controller } from '@nestjs/common';
import { MessagePattern, Payload } from '@nestjs/microservices';
import { ConfigPipelineResult, VpnConfigService } from './vpn-config.service';
import { ParseConfigDto, SwimVpnProfile } from '@app/contracts';

@Controller()
export class VpnConfigController {
  constructor(private readonly vpnConfigService: VpnConfigService) {}

  @MessagePattern({ cmd: 'parse_config' })
  async parseConfig(@Payload() data: ParseConfigDto): Promise<SwimVpnProfile> {
    return this.vpnConfigService.parse(data.rawConfig);
  }

  @MessagePattern({ cmd: 'process_config_pipeline' })
  async processConfigPipeline(@Payload() data: ParseConfigDto): Promise<ConfigPipelineResult> {
    return this.vpnConfigService.processPipeline(data.rawConfig);
  }

  @MessagePattern({ cmd: 'check_health' })
  async checkHealth(@Payload() data: { rawConfig: string }) {
    return this.vpnConfigService.checkHealth(data.rawConfig);
  }
}
