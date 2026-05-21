import { BadRequestException, Controller, Post, Body, Inject, ServiceUnavailableException } from '@nestjs/common';
import { ClientProxy } from '@nestjs/microservices';
import { firstValueFrom } from 'rxjs';
import { CreateCheckoutDto, CreateOrderDto } from '@app/contracts';

@Controller('orders')
export class CustomerController {
  constructor(@Inject('CUSTOMER_SERVICE') private readonly customerClient: ClientProxy) {}

  @Post()
  async createOrder(@Body() data: CreateOrderDto) {
    return firstValueFrom(this.customerClient.send({ cmd: 'create_order' }, data));
  }

  @Post('checkout')
  async createCheckout(@Body() data: CreateCheckoutDto) {
    try {
      return await firstValueFrom(this.customerClient.send({ cmd: 'create_checkout' }, data));
    } catch (error: any) {
      const message = this.extractErrorMessage(error);
      if (message.includes('email is required') || message.includes('payment contact email')) {
        throw new BadRequestException(message);
      }
      if (
        message.includes('crypto pay api is not configured')
      ) {
        throw new ServiceUnavailableException(message);
      }
      throw new BadRequestException(message || 'Unable to create checkout');
    }
  }

  private extractErrorMessage(error: any) {
    if (typeof error?.error === 'string' && error.error.trim().length > 0) {
      return error.error;
    }

    if (typeof error?.response?.message === 'string' && error.response.message.trim().length > 0) {
      return error.response.message;
    }

    if (Array.isArray(error?.response?.message) && error.response.message.length > 0) {
      return error.response.message.join(', ');
    }

    if (typeof error?.message === 'string' && error.message.trim().length > 0) {
      return error.message;
    }

    if (typeof error?.error?.message === 'string' && error.error.message.trim().length > 0) {
      return error.error.message;
    }

    if (Array.isArray(error?.error?.message) && error.error.message.length > 0) {
      return error.error.message.join(', ');
    }

    return 'Unable to create checkout';
  }
}
