import { Controller } from '@nestjs/common';
import { MessagePattern, Payload } from '@nestjs/microservices';
import { ConfigPipelineResult, ResolvedSwimCryptImportResult, SwimCryptImportResult, VpnConfigService } from './vpn-config.service';
import { GenerateSwimCryptImportDto, ParseConfigDto, ResolveSwimCryptImportDto, SwimVpnProfile } from '@app/contracts';

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

  @MessagePattern({ cmd: 'generate_swim_crypt_import' })
  async generateSwimCryptImport(@Payload() data: GenerateSwimCryptImportDto): Promise<SwimCryptImportResult> {
    return this.vpnConfigService.generateSwimCryptImport(data);
  }

  @MessagePattern({ cmd: 'resolve_swim_crypt_import' })
  async resolveSwimCryptImport(@Payload() data: ResolveSwimCryptImportDto): Promise<ResolvedSwimCryptImportResult> {
    return this.vpnConfigService.resolveSwimCryptImport(data);
  }
}
