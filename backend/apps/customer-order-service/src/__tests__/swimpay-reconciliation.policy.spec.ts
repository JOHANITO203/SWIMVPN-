import {
  DEFAULT_SWIMPAY_RECONCILE_INTERVAL_MS,
  MIN_SWIMPAY_RECONCILE_INTERVAL_MS,
  DEFAULT_SWIMPAY_PENDING_MAX_AGE_MS,
  SWIMPAY_ABANDON_NOT_FOUND_AFTER_MS,
  mapSwimPayStatusToAction,
  parseSwimPayOrderId,
  resolveSwimPayReconcileIntervalMs,
  resolveSwimPayPendingMaxAgeMs,
  shouldAbandonSwimPayOrder,
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

// Pending max-age resolver
assert(
  resolveSwimPayPendingMaxAgeMs(undefined) === DEFAULT_SWIMPAY_PENDING_MAX_AGE_MS,
  'missing max-age uses default',
);
assert(
  resolveSwimPayPendingMaxAgeMs('0') === DEFAULT_SWIMPAY_PENDING_MAX_AGE_MS,
  'zero max-age falls back to default',
);
assert(
  resolveSwimPayPendingMaxAgeMs('1000') === SWIMPAY_ABANDON_NOT_FOUND_AFTER_MS,
  'too-small max-age clamps to the not-found window',
);
assert(
  resolveSwimPayPendingMaxAgeMs(String(3 * 60 * 60 * 1000)) === 3 * 60 * 60 * 1000,
  'valid max-age preserved',
);

// Abandon decision
const NOW = 1_000_000_000_000;
const minsAgo = (m: number) => new Date(NOW - m * 60 * 1000);
const MAX = DEFAULT_SWIMPAY_PENDING_MAX_AGE_MS;
assert(
  shouldAbandonSwimPayOrder({ createdAt: minsAgo(10), notFound: true, now: NOW, maxAgeMs: MAX }) === false,
  'recent not-found order waits (no premature abandon)',
);
assert(
  shouldAbandonSwimPayOrder({ createdAt: minsAgo(45), notFound: true, now: NOW, maxAgeMs: MAX }) === true,
  'aged not-found order is abandoned',
);
assert(
  shouldAbandonSwimPayOrder({ createdAt: minsAgo(45), notFound: false, now: NOW, maxAgeMs: MAX }) === false,
  'transient-error order is not abandoned before the hard cap',
);
assert(
  shouldAbandonSwimPayOrder({ createdAt: minsAgo(60 * 25), notFound: false, now: NOW, maxAgeMs: MAX }) === true,
  'order past the hard age cap is abandoned',
);
assert(
  shouldAbandonSwimPayOrder({ createdAt: new Date(NOW + 60_000), notFound: true, now: NOW, maxAgeMs: MAX }) === false,
  'future-dated order (clock skew) is never abandoned',
);

console.log('swimpay reconciliation policy tests passed');
