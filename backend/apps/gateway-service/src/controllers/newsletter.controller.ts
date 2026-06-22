import {
  Body,
  Controller,
  Get,
  Inject,
  Post,
  Query,
  Res,
  ServiceUnavailableException,
} from '@nestjs/common';
import { ClientProxy } from '@nestjs/microservices';
import { EmailOptInDto } from '@app/contracts';
import { firstValueFrom } from 'rxjs';

@Controller()
export class NewsletterController {
  constructor(
    @Inject('NOTIFICATION_SERVICE') private readonly notificationClient: ClientProxy,
  ) {}

  @Post('newsletter/subscribe')
  async subscribe(@Body() data: EmailOptInDto) {
    try {
      return await firstValueFrom(this.notificationClient.send({ cmd: 'subscribe_email' }, data));
    } catch (error: any) {
      const message =
        (typeof error?.error === 'string' && error.error) ||
        (typeof error?.message === 'string' && error.message) ||
        'Newsletter service unavailable';
      throw new ServiceUnavailableException(message);
    }
  }

  // Link clicked from the confirmation email → confirm, then redirect to the landing.
  @Get('newsletter/confirm')
  async confirm(@Query('token') token: string, @Res() res: any) {
    const landing = process.env.PUBLIC_LANDING_URL || 'https://app.swimvpn.pro';
    try {
      const result: any = await firstValueFrom(
        this.notificationClient.send({ cmd: 'confirm_subscription' }, { token: token || '' }),
      );
      const ok = result?.status === 'confirmed' || result?.status === 'already_confirmed';
      return res.redirect(302, `${landing}/?subscribed=${ok ? '1' : '0'}`);
    } catch {
      return res.redirect(302, `${landing}/?subscribed=0`);
    }
  }
}
