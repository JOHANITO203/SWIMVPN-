import { Module } from '@nestjs/common';
import { VpnConfigController } from './vpn-config.controller';
import { VpnConfigService } from './vpn-config.service';

@Module({
  imports: [],
  controllers: [VpnConfigController],
  providers: [VpnConfigService],
})
export class AppModule {}
