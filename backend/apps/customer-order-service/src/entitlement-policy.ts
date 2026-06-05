export const TRIAL_DURATION_MS = 3 * 24 * 60 * 60 * 1000;
export const PLAN_DURATION_MS: Record<string, number> = {
  WEEK: 7 * 24 * 60 * 60 * 1000,
  MONTH: 30 * 24 * 60 * 60 * 1000,
  QUARTER: 90 * 24 * 60 * 60 * 1000,
};

/** Quota VENDU en GB. Trial ou plan illimité (quota_gb null/0) -> 0. */
export function resolveSoldQuotaGb(quotaGb: number | null | undefined, isTrial: boolean): number {
  if (isTrial) return 0;
  return quotaGb && quotaGb > 0 ? quotaGb : 0;
}

/** Expiration VENDUE = fulfilled_at + durée du forfait (ISO). null si pas encore fulfilled. */
export function soldExpiryIso(fulfilledAt: Date | null, planCode: string, isTrial: boolean): string | null {
  if (!fulfilledAt) return null;
  const durationMs = isTrial ? TRIAL_DURATION_MS : (PLAN_DURATION_MS[planCode] ?? 0);
  return new Date(fulfilledAt.getTime() + durationMs).toISOString();
}
