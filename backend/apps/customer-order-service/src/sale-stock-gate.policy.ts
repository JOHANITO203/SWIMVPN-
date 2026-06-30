/**
 * Sale stock gate — decides whether to BLOCK a checkout because there is no deliverable inventory for
 * the requested plan. [available] is the count of configs still allocatable for this plan (returned by
 * the inventory service's check_category_availability). Block only on a confirmed zero (or invalid)
 * count; a positive count means at least one config can serve the order. Pure and deterministic, so the
 * rule is testable without a database or network.
 */
export function shouldBlockSaleForStock(available: number): boolean {
  return !Number.isFinite(available) || available <= 0;
}
