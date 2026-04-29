import { Controller } from '@nestjs/common';
import { MessagePattern, Payload } from '@nestjs/microservices';
import { CustomerService } from './customer.service';
import {
  StartTrialDto,
  CreateOrderDto,
  BootstrapAccessDto,
  ActivateTrialDto,
  CompleteProfileDto,
  CreateCheckoutDto,
  CryptoWebhookDto,
  ReportUsageDto,
} from '@app/contracts';

@Controller()
export class CustomerController {
  constructor(private readonly customerService: CustomerService) {}

  @MessagePattern({ cmd: 'create_order' })
  async createOrder(@Payload() data: CreateOrderDto) {
    return this.customerService.createOrder(data);
  }

  @MessagePattern({ cmd: 'create_checkout' })
  async createCheckout(@Payload() data: CreateCheckoutDto) {
    return this.customerService.createCheckout(data);
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

  @MessagePattern({ cmd: 'complete_profile' })
  async completeProfile(@Payload() data: CompleteProfileDto) {
    return this.customerService.completeProfile(data);
  }

  @MessagePattern({ cmd: 'get_profile' })
  async getProfile(@Payload() data: { userNumber: string; exposeRuntimeConfig?: boolean }) {
    return this.customerService.getProfile(data.userNumber, {
      exposeRuntimeConfig: data.exposeRuntimeConfig,
    });
  }

  @MessagePattern({ cmd: 'import_subscription' })
  async importSubscription(@Payload() data: { userNumber: string; subscriptionUrl: string }) {
    return this.customerService.importSubscription(data);
  }

  @MessagePattern({ cmd: 'resolve_crypt_subscription' })
  async resolveCryptSubscription(@Payload() data: { userNumber: string; deviceId: string; encryptedLink: string }) {
    return this.customerService.resolveCryptSubscription(data);
  }

  @MessagePattern({ cmd: 'activate_code' })
  async activateCode(@Payload() data: { userNumber: string; code: string }) {
    return this.customerService.activateCode(data);
  }

  @MessagePattern({ cmd: 'report_usage' })
  async reportUsage(@Payload() data: ReportUsageDto) {
    return this.customerService.reportUsage(data);
  }

  @MessagePattern({ cmd: 'handle_stripe_webhook' })
  async handleStripeWebhook(@Payload() data: any) {
    return this.customerService.handleStripeWebhook(data);
  }

  @MessagePattern({ cmd: 'handle_yookassa_webhook' })
  async handleYookassaWebhook(@Payload() data: any) {
    return this.customerService.handleYookassaWebhook(data);
  }

  @MessagePattern({ cmd: 'handle_crypto_webhook' })
  async handleCryptoWebhook(
    @Payload() data: { body: CryptoWebhookDto; signature?: string | string[] },
  ) {
    return this.customerService.handleCryptoWebhook(data);
  }

  @MessagePattern({ cmd: 'approve_manual_card_payment' })
  async approveManualCardPayment(
    @Payload() data: { orderRef: string; paymentRef: string; proofEventId?: string },
  ) {
    return this.customerService.approveManualCardPayment(data);
  }

  @MessagePattern({ cmd: 'reject_manual_card_payment' })
  async rejectManualCardPayment(@Payload() data: { orderRef: string; reason?: string }) {
    return this.customerService.rejectManualCardPayment(data);
  }
} 
