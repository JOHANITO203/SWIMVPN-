import {
  DEFAULT_SWIMPAY_RECONCILE_INTERVAL_MS,
  MIN_SWIMPAY_RECONCILE_INTERVAL_MS,
  mapSwimPayStatusToAction,
  parseSwimPayOrderId,
  resolveSwimPayReconcileIntervalMs,
} from '../swimpay-reconciliation.policy';

function assert(condition: boolean, message: string) {
  if (!condition) {
    throw new Error(message);
  }
}

// Interval resolver
assert(
  resolveSwimPayReconcileIntervalMs(undefined) === DEFAULT_SWIMPAY_RECONCILE_INTERVAL_MS,
  'missing interval must use default',
);
assert(resolveSwimPayReconcileIntervalMs('0') === null, 'zero disables reconciliation');
assert(resolveSwimPayReconcileIntervalMs('disabled') === null, 'disabled disables reconciliation');
assert(
  resolveSwimPayReconcileIntervalMs('1000') === MIN_SWIMPAY_RECONCILE_INTERVAL_MS,
  'too-small interval clamps to minimum',
);
assert(resolveSwimPayReconcileIntervalMs('600000') === 600000, 'valid interval preserved');
assert(
  resolveSwimPayReconcileIntervalMs('nope') === DEFAULT_SWIMPAY_RECONCILE_INTERVAL_MS,
  'invalid interval falls back to default',
);

// payment_ref parsing
assert(
  parseSwimPayOrderId('SWIMPAY_SESSION:sess_123:ord_456') === 'ord_456',
  'parses the SwimPay order id (2nd segment)',
);
assert(parseSwimPayOrderId('CRYPTO_INVOICE:abc') === null, 'non-swimpay ref returns null');
assert(parseSwimPayOrderId('SWIMPAY_SESSION:onlyone') === null, 'malformed ref returns null');
assert(parseSwimPayOrderId(null) === null, 'null ref returns null');

// Status mapping
assert(mapSwimPayStatusToAction('manual_confirmed') === 'FULFILL', 'manual_confirmed fulfills');
assert(mapSwimPayStatusToAction('FULFILLED') === 'FULFILL', 'fulfilled (any case) fulfills');
assert(mapSwimPayStatusToAction('rejected') === 'FAIL', 'rejected fails');
assert(mapSwimPayStatusToAction('expired') === 'FAIL', 'expired fails');
assert(mapSwimPayStatusToAction('needs_review') === 'WAIT', 'in-progress status waits');
assert(mapSwimPayStatusToAction('') === 'WAIT', 'empty status waits');
assert(mapSwimPayStatusToAction(undefined) === 'WAIT', 'missing status waits');

console.log('swimpay reconciliation policy tests passed');
