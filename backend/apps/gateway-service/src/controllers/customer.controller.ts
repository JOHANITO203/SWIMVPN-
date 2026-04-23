import { Controller, Post, Body, Inject, Get, Param } from '@nestjs/common';
import { ClientProxy } from '@nestjs/microservices';
import { CreateCheckoutDto, CreateOrderDto } from '@app/contracts';

@Controller('orders')
export class CustomerController {
  constructor(@Inject('CUSTOMER_SERVICE') private readonly customerClient: ClientProxy) {}

  @Post()
  createOrder(@Body() data: CreateOrderDto) {
    return this.customerClient.send({ cmd: 'create_order' }, data);
  }

  @Post('checkout')
  createCheckout(@Body() data: CreateCheckoutDto) {
    return this.customerClient.send({ cmd: 'create_checkout' }, data);
  }
}
