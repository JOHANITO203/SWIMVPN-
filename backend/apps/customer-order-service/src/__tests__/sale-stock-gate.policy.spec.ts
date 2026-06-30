import { strict as assert } from 'assert';
import { shouldBlockSaleForStock } from '../sale-stock-gate.policy';

function main() {
  assert.equal(shouldBlockSaleForStock(0), true, 'zero stock blocks the sale');
  assert.equal(shouldBlockSaleForStock(1), false, 'one available config allows the sale');
  assert.equal(shouldBlockSaleForStock(5), false, 'positive stock allows the sale');
  assert.equal(shouldBlockSaleForStock(-1), true, 'negative count is treated as no stock');
  assert.equal(shouldBlockSaleForStock(NaN), true, 'invalid availability blocks (fail-safe)');

  console.log('sale-stock-gate.policy.spec.ts passed');
}
main();
