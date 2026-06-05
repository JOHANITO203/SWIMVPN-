/** Quota VENDU en GB. Trial ou plan illimité (quota_gb null/0) -> 0. */
export function resolveSoldQuotaGb(quotaGb: number | null | undefined, isTrial: boolean): number {
  if (isTrial) return 0;
  return quotaGb && quotaGb > 0 ? quotaGb : 0;
}
