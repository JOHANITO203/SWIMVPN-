// Reconciliation safety net for SwimPay orders whose confirmation webhook never arrived.
// We poll SwimPay's GET /v1/orders/:id for old PENDING orders and act on the returned status.
// fulfillOrderByRef() is idempotent + webhook-deduped, so polling can never double-fulfill.

export const DEFAULT_SWIMPAY_RECONCILE_INTERVAL_MS = 5 * 60 * 1000;
export const MIN_SWIMPAY_RECONCILE_INTERVAL_MS = 60 * 1000;
// Only reconcile orders older than this, so we never race the normal (faster) webhook path.
export const SWIMPAY_RECONCILE_MIN_ORDER_AGE_MS = 5 * 60 * 1000;

export type SwimPayReconcileAction = 'FULFILL' | 'FAIL' | 'WAIT';

// Conservative: only act on terminal, unambiguous statuses; anything else waits for the webhook.
const CONFIRMED_STATUSES = new Set(['manual_confirmed', 'fulfilled', 'confirmed', 'paid']);
const FAILED_STATUSES = new Set(['rejected', 'expired', 'cancelled', 'canceled', 'failed']);

export function resolveSwimPayReconcileIntervalMs(rawValue?: string | null) {
  const normalized = rawValue?.trim().toLowerCase();
  if (!normalized) {
    return DEFAULT_SWIMPAY_RECONCILE_INTERVAL_MS;
  }

  if (normalized === '0' || normalized === 'false' || normalized === 'disabled') {
    return null;
  }

  const parsed = Number(normalized);
  if (!Number.isFinite(parsed) || parsed < 0) {
    return DEFAULT_SWIMPAY_RECONCILE_INTERVAL_MS;
  }

  return Math.max(Math.floor(parsed), MIN_SWIMPAY_RECONCILE_INTERVAL_MS);
}

// payment_ref written at checkout is "SWIMPAY_SESSION:{paymentSessionId}:{swimpayOrderId}".
export function parseSwimPayOrderId(paymentRef?: string | null): string | null {
  if (!paymentRef) return null;
  const match = paymentRef.match(/^SWIMPAY_SESSION:([^:]+):([^:]+)$/);
  return match ? match[2] : null;
}

export function mapSwimPayStatusToAction(status?: string | null): SwimPayReconcileAction {
  const normalized = status?.trim().toLowerCase();
  if (!normalized) return 'WAIT';
  if (CONFIRMED_STATUSES.has(normalized)) return 'FULFILL';
  if (FAILED_STATUSES.has(normalized)) return 'FAIL';
  return 'WAIT';
}
