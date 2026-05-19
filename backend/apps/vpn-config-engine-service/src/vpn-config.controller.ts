import { Controller } from '@nestjs/common';
import { MessagePattern, Payload } from '@nestjs/microservices';
import {
  ConfigPipelineResult,
  ResolvedSwimCryptImportResult,
  SupplierResourceParseResult,
  ManagedRuntimeNode,
  SwimCryptImportResult,
  VpnConfigService,
} from './vpn-config.service';
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

  @MessagePattern({ cmd: 'process_supplier_resource' })
  async processSupplierResource(@Payload() data: ParseConfigDto): Promise<SupplierResourceParseResult> {
    return this.vpnConfigService.processSupplierResource(data.rawConfig);
  }

  @MessagePattern({ cmd: 'parse_managed_nodes' })
  async parseManagedNodes(@Payload() data: ParseConfigDto): Promise<ManagedRuntimeNode[]> {
    return this.vpnConfigService.parseManagedRuntimeNodes(data.rawConfig);
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
