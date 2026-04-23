import { Module } from '@nestjs/common';
import { ClientsModule, Transport } from '@nestjs/microservices';
import { DatabaseModule } from '@app/database';
import { CustomerController } from './customer.controller';
import { CustomerService } from './customer.service';

@Module({
  imports: [
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
  providers: [CustomerService],
  exports: [CustomerService],
})
export class AppModule {}
