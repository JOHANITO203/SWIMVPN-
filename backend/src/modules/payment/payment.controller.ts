import { Controller, Post, Body, HttpCode } from '@nestjs/common';
import { PaymentService } from './payment.service';
import { CreatePurchaseDto } from './dto/create-purchase.dto';

@Controller('api/v1/payment')
export class PaymentController {
  constructor(private readonly paymentService: PaymentService) {}

  @Post('create')
  async createPurchase(@Body() dto: CreatePurchaseDto) {
    return this.paymentService.createPurchase(dto);
  }

  @Post('webhook')
  @HttpCode(200) // Les webhooks s'attendent généralement à un 200 OK
  async handleWebhook(@Body() payload: any) {
    // Dans la réalité, on valide le payload avec la signature du provider de paiement.
    return this.paymentService.handleWebhook(payload);
  }
}
