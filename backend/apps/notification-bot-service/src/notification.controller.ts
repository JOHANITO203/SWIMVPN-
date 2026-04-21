import { Controller } from '@nestjs/common';
import { EventPattern, MessagePattern, Payload } from '@nestjs/microservices';
import { NotificationService } from './notification.service';
import { DeliveryPayloadDto, OrderRefDto } from './dto/delivery-payload.dto';

@Controller()
export class NotificationController {
  constructor(private readonly notificationService: NotificationService) {}

  @MessagePattern({ cmd: 'process_post_purchase_delivery' })
  processPostPurchaseDelivery(@Payload() payload: DeliveryPayloadDto) {
    return this.notificationService.processPostPurchaseDelivery(payload);
  }

  @EventPattern('process_post_purchase_delivery')
  processPostPurchaseDeliveryEvent(@Payload() payload: DeliveryPayloadDto) {
    return this.notificationService.processPostPurchaseDelivery(payload);
  }

  @MessagePattern({ cmd: 'resend_delivery_email' })
  resendDeliveryEmail(@Payload() payload: OrderRefDto) {
    return this.notificationService.resendDeliveryEmail(payload.orderRef, payload.language);
  }

  @MessagePattern({ cmd: 'get_delivery_status' })
  getDeliveryStatus(@Payload() payload: OrderRefDto) {
    return this.notificationService.getDeliveryStatus(payload.orderRef);
  }
}
