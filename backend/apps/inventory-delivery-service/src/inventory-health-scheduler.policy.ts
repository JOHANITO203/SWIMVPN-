export const DEFAULT_INVENTORY_HEALTHCHECK_INTERVAL_MS = 30 * 60 * 1000;
export const MIN_INVENTORY_HEALTHCHECK_INTERVAL_MS = 60 * 1000;

export function resolveInventoryHealthcheckIntervalMs(rawValue?: string | null) {
  const normalized = rawValue?.trim().toLowerCase();
  if (!normalized) {
    return DEFAULT_INVENTORY_HEALTHCHECK_INTERVAL_MS;
  }

  if (normalized === '0' || normalized === 'false' || normalized === 'disabled') {
    return null;
  }

  const parsed = Number(normalized);
  if (!Number.isFinite(parsed) || parsed < 0) {
    return DEFAULT_INVENTORY_HEALTHCHECK_INTERVAL_MS;
  }

  return Math.max(Math.floor(parsed), MIN_INVENTORY_HEALTHCHECK_INTERVAL_MS);
}

// Periodic sweep that re-attempts orders stuck in PENDING_FULFILLMENT (paid but no capacity at
// the time, or a transient inventory RPC failure). fulfillOrder() is idempotent, so retrying is
// safe: it fulfills as soon as capacity frees and is a no-op otherwise.
export const DEFAULT_FULFILLMENT_RETRY_INTERVAL_MS = 5 * 60 * 1000;
export const MIN_FULFILLMENT_RETRY_INTERVAL_MS = 30 * 1000;

export function resolveFulfillmentRetryIntervalMs(rawValue?: string | null) {
  const normalized = rawValue?.trim().toLowerCase();
  if (!normalized) {
    return DEFAULT_FULFILLMENT_RETRY_INTERVAL_MS;
  }

  if (normalized === '0' || normalized === 'false' || normalized === 'disabled') {
    return null;
  }

  const parsed = Number(normalized);
  if (!Number.isFinite(parsed) || parsed < 0) {
    return DEFAULT_FULFILLMENT_RETRY_INTERVAL_MS;
  }

  return Math.max(Math.floor(parsed), MIN_FULFILLMENT_RETRY_INTERVAL_MS);
}
