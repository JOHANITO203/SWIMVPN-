import { Module } from '@nestjs/common';
import { ClientsModule, Transport } from '@nestjs/microservices';
import { DatabaseModule } from '@app/database';
import { InventoryController } from './inventory.controller';
import { InventoryService } from './inventory.service';

@Module({
  imports: [
    DatabaseModule,
    ClientsModule.register([
      {
        name: 'VPN_CONFIG_SERVICE',
        transport: Transport.TCP,
        options: {
          host: process.env.VPN_CONFIG_SERVICE_HOST || '127.0.0.1',
          port: 3004
        },
      },
      {
        name: 'ADMIN_SERVICE',
        transport: Transport.TCP,
        options: {
          host: process.env.ADMIN_SERVICE_HOST || '127.0.0.1',
          port: 3003
        },
      },
    ]),
  ],
  controllers: [InventoryController],
  providers: [InventoryService],
})
export class AppModule {}
