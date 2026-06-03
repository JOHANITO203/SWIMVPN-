import {
  DEFAULT_FULFILLMENT_RETRY_INTERVAL_MS,
  DEFAULT_INVENTORY_HEALTHCHECK_INTERVAL_MS,
  MIN_FULFILLMENT_RETRY_INTERVAL_MS,
  MIN_INVENTORY_HEALTHCHECK_INTERVAL_MS,
  resolveFulfillmentRetryIntervalMs,
  resolveInventoryHealthcheckIntervalMs,
} from '../inventory-health-scheduler.policy';

function assert(condition: boolean, message: string) {
  if (!condition) {
    throw new Error(message);
  }
}

assert(
  resolveInventoryHealthcheckIntervalMs(undefined) === DEFAULT_INVENTORY_HEALTHCHECK_INTERVAL_MS,
  'missing interval must use default',
);

assert(
  resolveInventoryHealthcheckIntervalMs('0') === null,
  'zero interval must disable scheduler',
);

assert(
  resolveInventoryHealthcheckIntervalMs('disabled') === null,
  'disabled interval must disable scheduler',
);

assert(
  resolveInventoryHealthcheckIntervalMs('1000') === MIN_INVENTORY_HEALTHCHECK_INTERVAL_MS,
  'too-small interval must be clamped to minimum',
);

assert(
  resolveInventoryHealthcheckIntervalMs('120000') === 120000,
  'valid interval must be preserved',
);

assert(
  resolveInventoryHealthcheckIntervalMs('not-a-number') === DEFAULT_INVENTORY_HEALTHCHECK_INTERVAL_MS,
  'invalid interval must fall back to default',
);

// Fulfillment retry scheduler resolver
assert(
  resolveFulfillmentRetryIntervalMs(undefined) === DEFAULT_FULFILLMENT_RETRY_INTERVAL_MS,
  'missing retry interval must use default',
);
assert(
  resolveFulfillmentRetryIntervalMs('0') === null,
  'zero retry interval must disable the sweep',
);
assert(
  resolveFulfillmentRetryIntervalMs('disabled') === null,
  'disabled retry interval must disable the sweep',
);
assert(
  resolveFulfillmentRetryIntervalMs('1000') === MIN_FULFILLMENT_RETRY_INTERVAL_MS,
  'too-small retry interval must clamp to minimum',
);
assert(
  resolveFulfillmentRetryIntervalMs('120000') === 120000,
  'valid retry interval must be preserved',
);
assert(
  resolveFulfillmentRetryIntervalMs('not-a-number') === DEFAULT_FULFILLMENT_RETRY_INTERVAL_MS,
  'invalid retry interval must fall back to default',
);

console.log('inventory health scheduler policy tests passed');
