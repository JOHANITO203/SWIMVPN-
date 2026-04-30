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
