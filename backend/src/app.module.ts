import { Module } from '@nestjs/common';
import { PrismaService } from './prisma/prisma.service';

// Controllers
import { AccessController } from './modules/access/access.controller';
import { ServersController } from './modules/servers/servers.controller';
import { SubscriptionController } from './modules/subscription/subscription.controller';
import { PaymentController } from './modules/payment/payment.controller';
import { ContentController } from './modules/content/content.controller';

// Services
import { AccessService } from './modules/access/access.service';
import { ServersService } from './modules/servers/servers.service';
import { SubscriptionService } from './modules/subscription/subscription.service';
import { PaymentService } from './modules/payment/payment.service';
import { ContentService } from './modules/content/content.service';
import { EmailService } from './modules/email/email.service';

import { ThrottlerModule, ThrottlerGuard } from '@nestjs/throttler';
import { APP_GUARD } from '@nestjs/core';

@Module({
  imports: [
    ThrottlerModule.forRoot([{
      ttl: 60000, // 60 seconds
      limit: 10,  // limit each IP to 10 requests per ttl
    }]),
  ],
  controllers: [
    AccessController,
    ServersController,
    SubscriptionController,
    PaymentController,
    ContentController,
  ],
  providers: [
    PrismaService,
    AccessService,
    ServersService,
    SubscriptionService,
    PaymentService,
    ContentService,
    EmailService,
    {
      provide: APP_GUARD,
      useClass: ThrottlerGuard,
    },
  ],
})
export class AppModule {}
