import { Controller } from '@nestjs/common';
import { MessagePattern, Payload } from '@nestjs/microservices';
import { CustomerService } from './customer.service';
import { StartTrialDto, CreateOrderDto, BootstrapAccessDto, ActivateTrialDto } from '@app/contracts';

@Controller()
export class CustomerController {
  constructor(private readonly customerService: CustomerService) {}

  @MessagePattern({ cmd: 'create_order' })
  async createOrder(@Payload() data: CreateOrderDto) {
    return this.customerService.createOrder(data);
  }

  @MessagePattern({ cmd: 'bootstrap_access' })
  async bootstrapAccess(@Payload() data: BootstrapAccessDto) {
    return this.customerService.bootstrapAccess(data);
  }

  @MessagePattern({ cmd: 'start_trial' })
  async startTrial(@Payload() data: StartTrialDto) {
    return this.customerService.startTrial(data);
  }

  @MessagePattern({ cmd: 'activate_trial' })
  async activateTrial(@Payload() data: ActivateTrialDto) {
    return this.customerService.activateTrial(data);
  }

  @MessagePattern({ cmd: 'get_profile' })
  async getProfile(@Payload() data: { userNumber: string }) {
    return this.customerService.getProfile(data.userNumber);
  }

  @MessagePattern({ cmd: 'import_subscription' })
  async importSubscription(@Payload() data: { userNumber: string; subscriptionUrl: string }) {
    return this.customerService.importSubscription(data);
  }

  @MessagePattern({ cmd: 'activate_code' })
  async activateCode(@Payload() data: { userNumber: string; code: string }) {
    return this.customerService.activateCode(data);
  }

  @MessagePattern({ cmd: 'handle_stripe_webhook' })
  async handleStripeWebhook(@Payload() data: any) {
    return this.customerService.handleStripeWebhook(data);
  }

  @MessagePattern({ cmd: 'handle_yookassa_webhook' })
  async handleYookassaWebhook(@Payload() data: any) {
    return this.customerService.handleYookassaWebhook(data);
  }
}
