import { strict as assert } from 'assert';
import { of } from 'rxjs';
import { CustomerService } from '../customer.service';
import { SwimPayPublicWebhookEvent } from '../swim-pay.service';

async function main() {
  const orders = new Map<string, any>();
  const adminEvents: any[] = [];
  const fulfilledOrderIds: string[] = [];
  const swimPayRequests: any[] = [];
  let currentWebhookEvent: SwimPayPublicWebhookEvent = createSwimPayEvent(
    'evt_1',
    'payment.confirmed',
    'ORD-SWIMPAY-1',
    'manual_confirmed',
  );

  const prisma = {
    plan: {
      findUnique: async () => ({
        id: 'plan-month',
        code: 'MONTH',
        name: 'Premium',
        duration_label: '1 month',
        quota_label: '100 GB',
        price_rub: { toString: () => '425.00' },
        active: true,
      }),
    },
    customer: {
      findUnique: async ({ where }: any) => {
        if (where.public_id === 'SW-PAID') {
          return {
            id: 'customer-1',
            public_id: 'SW-PAID',
            device_id: 'device-1',
            email: 'buyer@example.com',
            phone: '+79990001122',
          };
        }
        return null;
      },
      update: async ({ data }: any) => ({
        id: 'customer-1',
        public_id: 'SW-PAID',
        device_id: 'device-1',
        email: data.email,
        phone: data.phone,
      }),
    },
    order: {
      create: async ({ data }: any) => {
        const order = {
          id: 'order-1',
          order_ref: 'ORD-SWIMPAY-1',
          customer_id: data.customer_id,
          plan_id: data.plan_id,
          amount_rub: data.amount_rub,
          status: data.status,
          payment_ref: null,
          paid_at: null,
        };
        orders.set(order.order_ref, order);
        return order;
      },
      update: async ({ where, data }: any) => {
        const order = [...orders.values()].find((item) => item.id === where.id || item.order_ref === where.order_ref);
        Object.assign(order, data);
        return order;
      },
      findUnique: async ({ where }: any) => {
        if (where.order_ref) return orders.get(where.order_ref) || null;
        return [...orders.values()].find((item) => item.id === where.id) || null;
      },
    },
    adminEvent: {
      findFirst: async ({ where }: any) =>
        adminEvents.find((event) => event.event_type === where.event_type && event.entity_id === where.entity_id) || null,
      create: async ({ data }: any) => {
        adminEvents.push(data);
        return data;
      },
    },
  };

  const inventoryClient = {
    send: (_pattern: any, payload: any) => {
      fulfilledOrderIds.push(payload.orderId);
      return of({ success: true, orderId: payload.orderId });
    },
  };

  const swimPayService = {
    isConfigured: () => true,
    createCheckout: async (params: any) => {
      swimPayRequests.push(params);
      return {
        orderId: 'swp_order_1',
        paymentSessionId: 'swp_session_1',
        checkoutUrl: 'https://staging.swimpay.pro/checkout/swp_session_1',
        status: 'payment_session_created',
        expiresAt: '2026-05-10T00:00:00.000Z',
      };
    },
    verifyWebhook: () => currentWebhookEvent,
  };

  const service = new CustomerService(
    prisma as any,
    inventoryClient as any,
    {} as any,
    {} as any,
    swimPayService as any,
  );

  const checkout = await service.createCheckout({
    userNumber: 'SW-PAID',
    deviceId: 'device-1',
    email: 'buyer@example.com',
    phone: '+79990001122',
    planId: 'plan-month',
    paymentMethod: 'SWIMPAY',
  });

  assert.equal(checkout.paymentMethod, 'SWIMPAY');
  assert.equal(checkout.redirectUrl, 'https://staging.swimpay.pro/checkout/swp_session_1');
  assert.equal(swimPayRequests[0].orderRef, 'ORD-SWIMPAY-1');
  assert.equal(orders.get('ORD-SWIMPAY-1').payment_ref, 'SWIMPAY_SESSION:swp_session_1:swp_order_1');

  currentWebhookEvent = {
    ...currentWebhookEvent,
    id: 'evt_forged_mismatch',
    data: {
      ...currentWebhookEvent.data,
      orderId: 'swp_attacker_order',
      paymentSessionId: 'swp_attacker_session',
    },
  };
  const forgedWebhookResult = await service.handleSwimPayWebhook({
    rawBody: '{}',
    headers: {},
  });

  assert.equal(forgedWebhookResult.received, true);
  assert.equal(forgedWebhookResult.ignored, true);
  assert.equal(orders.get('ORD-SWIMPAY-1').status, 'PENDING');
  assert.equal(orders.get('ORD-SWIMPAY-1').payment_ref, 'SWIMPAY_SESSION:swp_session_1:swp_order_1');
  assert.deepEqual(fulfilledOrderIds, [], 'mismatched SwimPay webhook must not fulfill the order');

  currentWebhookEvent = {
    ...createSwimPayEvent(
      'evt_micro_adjusted',
      'payment.confirmed',
      'ORD-SWIMPAY-1',
      'manual_confirmed',
      42501,
    ),
  };
  const microAdjustedWebhookResult = await service.handleSwimPayWebhook({
    rawBody: '{}',
    headers: {},
  });

  assert.equal(microAdjustedWebhookResult.received, true);
  assert.equal(microAdjustedWebhookResult.success, true);
  assert.equal(orders.get('ORD-SWIMPAY-1').status, 'PAID');
  assert.deepEqual(fulfilledOrderIds, ['order-1'], 'higher SwimPay anti-collision amount must fulfill');

  orders.get('ORD-SWIMPAY-1').status = 'PENDING';
  orders.get('ORD-SWIMPAY-1').payment_ref = 'SWIMPAY_SESSION:swp_session_1:swp_order_1';
  orders.get('ORD-SWIMPAY-1').paid_at = null;
  fulfilledOrderIds.length = 0;

  currentWebhookEvent = {
    ...createSwimPayEvent(
      'evt_underpaid',
      'payment.confirmed',
      'ORD-SWIMPAY-1',
      'manual_confirmed',
      42499,
    ),
  };
  const underpaidWebhookResult = await service.handleSwimPayWebhook({
    rawBody: '{}',
    headers: {},
  });

  assert.equal(underpaidWebhookResult.received, true);
  assert.equal(underpaidWebhookResult.ignored, true);
  assert.equal(orders.get('ORD-SWIMPAY-1').status, 'PENDING');
  assert.deepEqual(fulfilledOrderIds, [], 'lower SwimPay amount must not fulfill');

  currentWebhookEvent = createSwimPayEvent(
    'evt_1',
    'payment.confirmed',
    'ORD-SWIMPAY-1',
    'manual_confirmed',
  );
  const webhookResult = await service.handleSwimPayWebhook({
    rawBody: '{}',
    headers: {},
  });

  assert.equal(webhookResult.received, true);
  assert.equal(webhookResult.success, true);
  assert.equal(orders.get('ORD-SWIMPAY-1').status, 'PAID');
  assert.equal(orders.get('ORD-SWIMPAY-1').payment_ref, 'SWIMPAY_CONFIRMED:swp_session_1:evt_1');
  assert.deepEqual(fulfilledOrderIds, ['order-1']);

  const duplicateResult = await service.handleSwimPayWebhook({
    rawBody: '{}',
    headers: {},
  });
  assert.equal(duplicateResult.duplicate, true, 'duplicate SwimPay events must be idempotent');
  assert.deepEqual(fulfilledOrderIds, ['order-1'], 'duplicate SwimPay events must not fulfill twice');

  orders.set('ORD-SWIMPAY-REJECTED', createPendingOrder('order-rejected', 'ORD-SWIMPAY-REJECTED'));
  currentWebhookEvent = createSwimPayEvent(
    'evt_rejected',
    'payment.rejected',
    'ORD-SWIMPAY-REJECTED',
    'manual_rejected',
  );
  const rejectedResult = await service.handleSwimPayWebhook({
    rawBody: '{}',
    headers: {},
  });

  assert.equal(rejectedResult.received, true);
  assert.equal(rejectedResult.terminal, true);
  assert.equal(rejectedResult.type, 'payment.rejected');
  assert.equal(orders.get('ORD-SWIMPAY-REJECTED').status, 'FAILED');
  assert.equal(
    orders.get('ORD-SWIMPAY-REJECTED').payment_ref,
    'SWIMPAY_REJECTED:swp_session_1:evt_rejected',
  );
  assert.deepEqual(fulfilledOrderIds, ['order-1'], 'rejected SwimPay events must not fulfill');

  orders.set('ORD-SWIMPAY-EXPIRED', createPendingOrder('order-expired', 'ORD-SWIMPAY-EXPIRED'));
  currentWebhookEvent = createSwimPayEvent(
    'evt_expired',
    'payment.expired',
    'ORD-SWIMPAY-EXPIRED',
    'expired',
  );
  const expiredResult = await service.handleSwimPayWebhook({
    rawBody: '{}',
    headers: {},
  });

  assert.equal(expiredResult.received, true);
  assert.equal(expiredResult.terminal, true);
  assert.equal(expiredResult.type, 'payment.expired');
  assert.equal(orders.get('ORD-SWIMPAY-EXPIRED').status, 'FAILED');
  assert.equal(
    orders.get('ORD-SWIMPAY-EXPIRED').payment_ref,
    'SWIMPAY_EXPIRED:swp_session_1:evt_expired',
  );
  assert.deepEqual(fulfilledOrderIds, ['order-1'], 'expired SwimPay events must not fulfill');

  console.log('swim-pay-checkout.policy.spec.ts passed');
}

function createPendingOrder(id: string, orderRef: string) {
  return {
    id,
    order_ref: orderRef,
    customer_id: 'customer-1',
    plan_id: 'plan-month',
    amount_rub: '425.00',
    status: 'PENDING',
    payment_ref: 'SWIMPAY_SESSION:swp_session_1:swp_order_1',
    paid_at: null,
  };
}

function createSwimPayEvent(
  id: string,
  type: SwimPayPublicWebhookEvent['type'],
  externalOrderId: string,
  decision: NonNullable<SwimPayPublicWebhookEvent['data']['decision']>,
  amountMinor = 42500,
): SwimPayPublicWebhookEvent {
  return {
    id,
    type,
    createdAt: '2026-05-09T10:00:00.000Z',
    data: {
      externalOrderId,
      orderId: 'swp_order_1',
      paymentSessionId: 'swp_session_1',
      amountMinor,
      currency: 'RUB',
      confirmationType: 'notification_signal',
      officialBankConfirmation: false,
      decision,
    },
  };
}

main().catch((error) => {
  console.error(error);
  process.exit(1);
});
