import 'reflect-metadata';
import { strict as assert } from 'assert';
import { METHOD_METADATA, PATH_METADATA } from '@nestjs/common/constants';
import { RequestMethod } from '@nestjs/common';
import { of } from 'rxjs';
import {
  PaymentsController,
  SwimPayWebhookAliasController,
} from '../controllers/payments.controller';
import { SWIMPAY_LEGACY_WEBHOOK_ROUTE } from '../swimpay-route-compat';

async function main() {
  assert.equal(Reflect.getMetadata(PATH_METADATA, PaymentsController), 'payments');
  assert.equal(
    Reflect.getMetadata(PATH_METADATA, PaymentsController.prototype.handleSwimPayWebhook),
    'swimpay/webhook',
  );
  assert.equal(
    Reflect.getMetadata(METHOD_METADATA, PaymentsController.prototype.handleSwimPayWebhook),
    RequestMethod.POST,
  );
  assert.equal(
    Reflect.getMetadata(PATH_METADATA, PaymentsController.prototype.handleSwimPayReturn),
    'swimpay/return',
  );
  assert.equal(
    Reflect.getMetadata(METHOD_METADATA, PaymentsController.prototype.handleSwimPayReturn),
    RequestMethod.GET,
  );

  assert.equal(Reflect.getMetadata(PATH_METADATA, SwimPayWebhookAliasController), 'webhooks');
  assert.equal(
    Reflect.getMetadata(PATH_METADATA, SwimPayWebhookAliasController.prototype.handleSwimPayWebhookAlias),
    'swimpay',
  );
  assert.equal(
    Reflect.getMetadata(METHOD_METADATA, SwimPayWebhookAliasController.prototype.handleSwimPayWebhookAlias),
    RequestMethod.POST,
  );
  assert.deepEqual(SWIMPAY_LEGACY_WEBHOOK_ROUTE, {
    path: 'webhooks/swimpay',
    method: RequestMethod.POST,
  });

  const forwards: any[] = [];
  const customerClient = {
    send: (pattern: any, payload: any) => {
      forwards.push({ pattern, payload });
      return of({ received: true });
    },
  };
  const rawBody = Buffer.from('{"id":"evt_route"}');
  const headers = {
    'swimpay-event-id': 'evt_route',
    'swimpay-timestamp': '2026-05-13T12:00:00.000Z',
    'swimpay-signature': 'sha256=test',
  };

  new PaymentsController(customerClient as any).handleSwimPayWebhook(
    { rawBody } as any,
    { id: 'parsed_body_should_not_win' },
    headers,
  );
  new SwimPayWebhookAliasController(customerClient as any).handleSwimPayWebhookAlias(
    { rawBody } as any,
    { id: 'parsed_body_should_not_win' },
    headers,
  );

  assert.equal(forwards.length, 2);
  for (const forward of forwards) {
    assert.deepEqual(forward.pattern, { cmd: 'handle_swimpay_webhook' });
    assert.equal(forward.payload.rawBody, '{"id":"evt_route"}');
    assert.equal(forward.payload.headers, headers);
  }

  console.log('swim-pay-gateway-routes.spec.ts passed');
}

main().catch((error) => {
  console.error(error);
  process.exit(1);
});
