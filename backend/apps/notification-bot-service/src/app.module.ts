import { Module } from '@nestjs/common';
import { ConfigModule } from '@nestjs/config';
import { ClientsModule, Transport } from '@nestjs/microservices';
import { DatabaseModule } from '@app/database';
import { NotificationController } from './notification.controller';
import { NotificationService } from './notification.service';
import { TelegramSenderService } from './telegram-sender.service';
import { EmailSenderService } from './email-sender.service';
import { DeliveryTemplateService } from './templates/delivery-template.service';
import { TelegramCommandService } from './telegram-command.service';

@Module({
  imports: [
    ConfigModule.forRoot({
      isGlobal: true,
      envFilePath: '.env',
    }),
    DatabaseModule,
    ClientsModule.register([
      {
        name: 'CUSTOMER_SERVICE',
        transport: Transport.TCP,
        options: {
          host: process.env.CUSTOMER_SERVICE_HOST || '127.0.0.1',
          port: 3001,
        },
      },
    ]),
  ],
  controllers: [NotificationController],
  providers: [NotificationService, TelegramSenderService, EmailSenderService, DeliveryTemplateService, TelegramCommandService],
})
export class AppModule {}
