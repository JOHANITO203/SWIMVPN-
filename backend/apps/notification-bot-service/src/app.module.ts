import { Module } from '@nestjs/common';
import { ConfigModule } from '@nestjs/config';
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
  ],
  controllers: [NotificationController],
  providers: [NotificationService, TelegramSenderService, EmailSenderService, DeliveryTemplateService, TelegramCommandService],
})
export class AppModule {}
