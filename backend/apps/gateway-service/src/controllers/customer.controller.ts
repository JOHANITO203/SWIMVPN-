import { Controller, Post, Body, Inject, Get, Param } from '@nestjs/common';
import { ClientProxy } from '@nestjs/microservices';

@Controller('orders')
export class CustomerController {
  constructor(@Inject('CUSTOMER_SERVICE') private readonly customerClient: ClientProxy) {}

  @Post()
  createOrder(@Body() data: any) {
    return this.customerClient.send({ cmd: 'create_order' }, data);
  }
}
