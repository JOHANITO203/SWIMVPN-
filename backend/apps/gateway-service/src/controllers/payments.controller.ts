import { Body, Controller, Headers, Inject, Post } from '@nestjs/common';
import { ClientProxy } from '@nestjs/microservices';

@Controller('payments')
export class PaymentsController {
  constructor(@Inject('CUSTOMER_SERVICE') private readonly customerClient: ClientProxy) {}

  @Post('crypto/webhook')
  handleCryptoWebhook(
    @Body() body: Record<string, unknown>,
    @Headers('crypto-pay-api-signature') signature?: string,
  ) {
    return this.customerClient.send(
      { cmd: 'handle_crypto_webhook' },
      { body, signature },
    );
  }
}
