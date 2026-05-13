import { Body, Controller, Get, Headers, Inject, Post, Query, Req } from '@nestjs/common';
import { ClientProxy } from '@nestjs/microservices';
import { Request } from 'express';

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

  @Post('swimpay/webhook')
  handleSwimPayWebhook(
    @Req() request: Request & { rawBody?: Buffer },
    @Body() body: Record<string, unknown>,
    @Headers() headers: Record<string, string | string[] | number | undefined>,
  ) {
    return forwardSwimPayWebhook(this.customerClient, request, body, headers);
  }

  @Get('swimpay/return')
  handleSwimPayReturn(@Query('orderRef') orderRef?: string) {
    return {
      received: true,
      orderRef: orderRef || null,
      message: 'Return received. Payment is confirmed only after the signed SwimPay webhook.',
    };
  }
}

@Controller('webhooks')
export class SwimPayWebhookAliasController {
  constructor(@Inject('CUSTOMER_SERVICE') private readonly customerClient: ClientProxy) {}

  @Post('swimpay')
  handleSwimPayWebhookAlias(
    @Req() request: Request & { rawBody?: Buffer },
    @Body() body: Record<string, unknown>,
    @Headers() headers: Record<string, string | string[] | number | undefined>,
  ) {
    return forwardSwimPayWebhook(this.customerClient, request, body, headers);
  }
}

function forwardSwimPayWebhook(
  customerClient: ClientProxy,
  request: Request & { rawBody?: Buffer },
  body: Record<string, unknown>,
  headers: Record<string, string | string[] | number | undefined>,
) {
  const rawBody = request.rawBody?.toString('utf8') || JSON.stringify(body || {});
  return customerClient.send(
    { cmd: 'handle_swimpay_webhook' },
    { rawBody, headers },
  );
}
