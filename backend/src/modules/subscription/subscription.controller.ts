import { Controller, Post, Body } from '@nestjs/common';
import { SubscriptionService } from './subscription.service';
import { ImportSubscriptionDto } from './dto/import-subscription.dto';
import { ActivateCodeDto } from './dto/activate-code.dto';

@Controller('api/v1/subscription')
export class SubscriptionController {
  constructor(private readonly subscriptionService: SubscriptionService) {}

  @Post('import')
  async importUrl(@Body() dto: ImportSubscriptionDto) {
    // Mode principal : Le client colle l'URL reçue, le backend l'enregistre et active l'accès
    return this.subscriptionService.importSubscriptionUrl(dto);
  }

  @Post('activate-code')
  async activateCode(@Body() dto: ActivateCodeDto) {
    // Mode secondaire : Le client entre un code SWIM-XXXX-XXXX
    return this.subscriptionService.activateCode(dto);
  }
}
