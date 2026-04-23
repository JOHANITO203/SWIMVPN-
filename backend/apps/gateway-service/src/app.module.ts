import { Module } from '@nestjs/common';
import { ClientsModule, Transport } from '@nestjs/microservices';
import { StoreController } from './controllers/store.controller';
import { CustomerController } from './controllers/customer.controller';
import { AdminController } from './controllers/admin.controller';
import { AccessController } from './controllers/access.controller';
import { PaymentsController } from './controllers/payments.controller';

@Module({
  imports: [
    ClientsModule.register([
      {
        name: 'STORE_SERVICE',
        transport: Transport.TCP,
        options: { host: process.env.STORE_SERVICE_HOST || '127.0.0.1', port: 3005 },
      },
      {
        name: 'CUSTOMER_SERVICE',
        transport: Transport.TCP,
        options: { host: process.env.CUSTOMER_SERVICE_HOST || '127.0.0.1', port: 3001 },
      },
      {
        name: 'INVENTORY_SERVICE',
        transport: Transport.TCP,
        options: { host: process.env.INVENTORY_SERVICE_HOST || '127.0.0.1', port: 3002 },
      },
      {
        name: 'ADMIN_SERVICE',
        transport: Transport.TCP,
        options: { host: process.env.ADMIN_SERVICE_HOST || '127.0.0.1', port: 3003 },
      },
      {
        name: 'VPN_CONFIG_SERVICE',
        transport: Transport.TCP,
        options: { host: process.env.VPN_CONFIG_SERVICE_HOST || '127.0.0.1', port: 3004 },
      },
    ]),
  ],
  controllers: [StoreController, CustomerController, AdminController, AccessController, PaymentsController],
  providers: [],
})
export class AppModule {}
