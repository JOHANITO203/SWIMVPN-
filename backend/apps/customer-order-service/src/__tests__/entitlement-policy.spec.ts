import { strict as assert } from 'assert';
import { resolveSoldQuotaGb, soldExpiryIso, PLAN_DURATION_MS } from '../entitlement-policy';

function main() {
  assert.equal(resolveSoldQuotaGb(50, false), 50);
  assert.equal(resolveSoldQuotaGb(null, false), 0);
  assert.equal(resolveSoldQuotaGb(150, true), 0, 'trial has no measured quota');

  const fulfilled = new Date('2026-06-01T00:00:00.000Z');
  assert.equal(
    soldExpiryIso(fulfilled, 'WEEK', false),
    new Date(fulfilled.getTime() + PLAN_DURATION_MS.WEEK).toISOString(),
  );
  assert.equal(soldExpiryIso(null, 'MONTH', false), null, 'no fulfilled_at -> null');

  console.log('entitlement-policy.spec.ts passed');
}
main();
