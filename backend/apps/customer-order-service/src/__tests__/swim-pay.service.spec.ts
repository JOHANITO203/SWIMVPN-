import { strict as assert } from 'assert';
import { createHmac } from 'crypto';
import { SwimPayService } from '../swim-pay.service';

function signWebhook(secret: string, timestamp: string, rawBody: string) {
  return `sha256=${createHmac('sha256', secret).update(`${timestamp}.${rawBody}`).digest('hex')}`;
}

async function main() {
  const requests: Array<{ url: string; init: RequestInit }> = [];
  const originalFetch = global.fetch;
  global.fetch = (async (url: string, init: RequestInit) => {
    requests.push({ url, init });
    return Response.json({
      order_id: 'swp_order_1',
      payment_session_id: 'swp_session_1',
      checkout_url: 'https://staging.swimpay.pro/checkout/swp_session_1',
      status: 'payment_session_created',
      expires_at: '2026-05-10T00:00:00.000Z',
    });
  }) as any;

  try {
    const service = new SwimPayService({
      get: (key: string, fallback?: string) =>
        ({
          SWIMPAY_SECRET_KEY: 'sk_test_server_only',
          SWIMPAY_WEBHOOK_SECRET: 'whsec_test',
          SWIMPAY_API_BASE_URL: 'https://staging.swimpay.pro',
          SWIMPAY_RETURN_BASE_URL: 'https://api.swimvpn.pro/api/v1/payments/swimpay/return',
          SWIMPAY_WEBHOOK_URL: 'https://api.swimvpn.pro/api/v1/payments/swimpay/webhook',
        })[key] ?? fallback,
    } as any);

    const checkout = await service.createCheckout({
      orderRef: 'ORD-SWIMPAY-1',
      amountRub: '425.00',
      planLabel: 'Premium - 1 Month - 100 GB',
      customerPhone: '+79990001122',
    });

    assert.equal(checkout.orderId, 'swp_order_1');
    assert.equal(checkout.paymentSessionId, 'swp_session_1');
    assert.equal(checkout.checkoutUrl, 'https://staging.swimpay.pro/checkout/swp_session_1');
    assert.equal(requests.length, 1, 'checkout creation must call SwimPay once');

    const request = requests[0];
    assert.equal(request.url, 'https://staging.swimpay.pro/v1/orders');
    assert.equal(request.init.method, 'POST');
    assert.equal((request.init.headers as Record<string, string>).Authorization, 'Bearer sk_test_server_only');
    assert.equal((request.init.headers as Record<string, string>)['Idempotency-Key'], 'ORD-SWIMPAY-1');

    const body = JSON.parse(String(request.init.body));
    assert.equal(body.external_id, 'ORD-SWIMPAY-1');
    assert.deepEqual(body.amount, { value: '425.00', currency: 'RUB' });
    assert.equal(body.return_url, 'https://api.swimvpn.pro/api/v1/payments/swimpay/return?orderRef=ORD-SWIMPAY-1');
    assert.equal(body.webhook_url, 'https://api.swimvpn.pro/api/v1/payments/swimpay/webhook');
    assert(!String(request.init.body).match(/cvv|cvc|card|notification_text|raw/iu), 'unsafe fields must not be sent');

    const confirmedRaw = JSON.stringify({
      id: 'evt_confirmed_1',
      type: 'payment.confirmed',
      created_at: '2026-05-09T10:00:00.000Z',
      data: {
        external_id: 'ORD-SWIMPAY-1',
        order_id: 'swp_order_1',
        payment_session_id: 'swp_session_1',
        amount: { value: '425.00', currency: 'RUB' },
        confirmation_type: 'notification_signal',
        official_bank_confirmation: false,
        decision: 'manual_confirmed',
      },
    });
    const timestamp = new Date().toISOString();
    const confirmed = service.verifyWebhook(confirmedRaw, {
      'SwimPay-Event-Id': 'evt_confirmed_1',
      'SwimPay-Timestamp': timestamp,
      'SwimPay-Signature': signWebhook('whsec_test', timestamp, confirmedRaw),
    });
    assert.equal(confirmed.type, 'payment.confirmed');
    assert.equal(confirmed.data.externalOrderId, 'ORD-SWIMPAY-1');
    assert.equal(confirmed.data.officialBankConfirmation, false);

    assert.throws(
      () =>
        service.verifyWebhook(confirmedRaw, {
          'SwimPay-Event-Id': 'evt_confirmed_1',
          'SwimPay-Timestamp': timestamp,
          'SwimPay-Signature': 'sha256=invalid',
        }),
      /Invalid SwimPay webhook signature/,
      'invalid SwimPay signatures must be rejected',
    );

    const internalRaw = JSON.stringify({
      id: 'evt_internal_1',
      type: 'payment.signal_detected',
      created_at: '2026-05-09T10:00:00.000Z',
      data: {
        order_id: 'swp_order_1',
        payment_session_id: 'swp_session_1',
        amount_minor: 42500,
        currency: 'RUB',
        official_bank_confirmation: false,
      },
    });
    assert.throws(
      () =>
        service.verifyWebhook(internalRaw, {
          'SwimPay-Event-Id': 'evt_internal_1',
          'SwimPay-Timestamp': timestamp,
          'SwimPay-Signature': signWebhook('whsec_test', timestamp, internalRaw),
        }),
      /Unsupported public webhook event type/,
      'internal signal events must never fulfill orders',
    );

    const officialRaw = confirmedRaw.replace('"official_bank_confirmation":false', '"official_bank_confirmation":true');
    assert.throws(
      () =>
        service.verifyWebhook(officialRaw, {
          'SwimPay-Event-Id': 'evt_confirmed_1',
          'SwimPay-Timestamp': timestamp,
          'SwimPay-Signature': signWebhook('whsec_test', timestamp, officialRaw),
        }),
      /officialBankConfirmation must be false/,
      'SwimPay V1 must not claim official bank confirmation',
    );
  } finally {
    global.fetch = originalFetch;
  }

  console.log('swim-pay.service.spec.ts passed');
}

main().catch((error) => {
  console.error(error);
  process.exit(1);
});
