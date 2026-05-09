import { Module } from '@nestjs/common';
import { ConfigModule } from '@nestjs/config';
import { ClientsModule, Transport } from '@nestjs/microservices';
import { DatabaseModule } from '@app/database';
import { CustomerController } from './customer.controller';
import { CustomerService } from './customer.service';
import { CryptoPayService } from './crypto-pay.service';
import { SwimPayService } from './swim-pay.service';

@Module({
  imports: [
    ConfigModule.forRoot({
      isGlobal: true,
      envFilePath: '.env',
    }),
    DatabaseModule,
    ClientsModule.register([
      {
        name: 'INVENTORY_SERVICE',
        transport: Transport.TCP,
        options: {
          host: process.env.INVENTORY_SERVICE_HOST || '127.0.0.1',
          port: 3002
        },
      },
      {
        name: 'VPN_CONFIG_SERVICE',
        transport: Transport.TCP,
        options: {
          host: process.env.VPN_CONFIG_SERVICE_HOST || '127.0.0.1',
          port: 3004
        },
      },
    ]),
  ],
  controllers: [CustomerController],
  providers: [CustomerService, CryptoPayService, SwimPayService],
  exports: [CustomerService],
})
export class AppModule {}
