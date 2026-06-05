import { strict as assert } from 'assert';
import { resolveSoldQuotaGb } from '../entitlement-policy';

function main() {
  assert.equal(resolveSoldQuotaGb(50, false), 50);
  assert.equal(resolveSoldQuotaGb(null, false), 0);
  assert.equal(resolveSoldQuotaGb(150, true), 0, 'trial has no measured quota');

  console.log('entitlement-policy.spec.ts passed');
}
main();
