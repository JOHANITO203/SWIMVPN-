import { Module } from '@nestjs/common';
import { ClientsModule, Transport } from '@nestjs/microservices';
import { DatabaseModule } from '@app/database';
import { StoreController } from './store.controller';
import { StoreService } from './store.service';

@Module({
  imports: [
    DatabaseModule,
    ClientsModule.register([
      {
        name: 'VPN_CONFIG_SERVICE',
        transport: Transport.TCP,
        options: {
          host: process.env.VPN_CONFIG_SERVICE_HOST || '127.0.0.1',
          port: 3004,
        },
      },
    ]),
  ],
  controllers: [StoreController],
  providers: [StoreService],
})
export class AppModule {}
