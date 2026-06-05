// Single source of truth for sold-plan durations. Mirrors the legacy
// CustomerService.getDurationMsFromOrder switch (WEEK is the default arm).
export const PLAN_DURATION_MS: Record<string, number> = {
  WEEK: 7 * 24 * 60 * 60 * 1000,
  MONTH: 30 * 24 * 60 * 60 * 1000,
  QUARTER: 90 * 24 * 60 * 60 * 1000,
};

export const TRIAL_DURATION_MS = 3 * 24 * 60 * 60 * 1000;

/** Sold duration for an order. Trial overrides the plan code. Unknown code => WEEK. */
export function planDurationMs(planCode: string, isTrial: boolean): number {
  if (isTrial) return TRIAL_DURATION_MS;
  return PLAN_DURATION_MS[planCode] ?? PLAN_DURATION_MS.WEEK;
}
