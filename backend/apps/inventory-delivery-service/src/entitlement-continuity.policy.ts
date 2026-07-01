export interface RiskInput {
  soldExpiryMs: number | null;
  soldQuotaBytes: number; // 0 = unlimited
  configSupplierExpiresAtMs: number | null;
  configSourceQuotaRemainingBytes: number | null;
  clientConsumedBytes: number;
  nowMs: number;
}

export type RiskReason = 'DATE' | 'QUOTA';
export interface RiskVerdict {
  atRisk: boolean;
  reasons: RiskReason[];
}

export interface ContinuityCandidate {
  id: string;
  category: string;
  healthStatus: string;
  status: string; // one config = one client: only AVAILABLE configs can take a reallocated client
  supplierExpiresAtMs: number | null;
  sourceQuotaRemainingBytes: number | null;
}

export interface SelectionContext {
  category: string;
  soldExpiryMs: number | null;
  soldQuotaBytes: number;
  clientConsumedBytes: number;
}

/** Sold need still owed to the client (bytes); <= 0 means nothing left to protect. */
function remainingSoldNeed(soldQuotaBytes: number, clientConsumedBytes: number): number {
  return soldQuotaBytes - clientConsumedBytes;
}

export function isAssignmentAtRisk(input: RiskInput): RiskVerdict {
  const reasons: RiskReason[] = [];

  // DATE: the assigned config expires before the sold promise. Unknown promise or
  // unknown config expiry => no date risk (never move blindly).
  if (
    input.soldExpiryMs !== null &&
    input.configSupplierExpiresAtMs !== null &&
    input.configSupplierExpiresAtMs < input.soldExpiryMs
  ) {
    reasons.push('DATE');
  }

  // QUOTA: only for metered plans (soldQuotaBytes > 0) with a known config source quota.
  // NOTE: configSourceQuotaRemainingBytes is the WHOLE config's supplier quota pool (shared by up
  // to max_resale_slots co-tenants), compared against ONE client's remaining need — an intentional
  // CONSERVATIVE approximation (it may over-reallocate, never under-protect).
  if (input.soldQuotaBytes > 0 && input.configSourceQuotaRemainingBytes !== null) {
    const stillOwed = remainingSoldNeed(input.soldQuotaBytes, input.clientConsumedBytes);
    if (stillOwed > 0 && input.configSourceQuotaRemainingBytes < stillOwed) {
      reasons.push('QUOTA');
    }
  }

  return { atRisk: reasons.length > 0, reasons };
}

/** True if a candidate config covers the remaining sold promise (date AND quota). */
function candidateCovers(ctx: SelectionContext, c: ContinuityCandidate): boolean {
  if (c.category !== ctx.category) return false;
  if (c.healthStatus !== 'HEALTHY') return false;
  if (c.status !== 'AVAILABLE') return false; // must be a free config (one config = one client)

  // Date coverage: null supplier expiry = no known death => covers.
  if (ctx.soldExpiryMs !== null && c.supplierExpiresAtMs !== null && c.supplierExpiresAtMs < ctx.soldExpiryMs) {
    return false;
  }

  // Quota coverage: only constrains metered plans. null source quota = unknown/unlimited => covers.
  if (ctx.soldQuotaBytes > 0 && c.sourceQuotaRemainingBytes !== null) {
    const stillOwed = remainingSoldNeed(ctx.soldQuotaBytes, ctx.clientConsumedBytes);
    if (stillOwed > 0 && c.sourceQuotaRemainingBytes < stillOwed) return false;
  }
  return true;
}

/** Best covering candidate: the free config with the latest supplier expiry (most margin). */
export function selectReallocationCandidate(
  ctx: SelectionContext,
  candidates: ContinuityCandidate[],
  _nowMs: number,
): ContinuityCandidate | null {
  const eligible = candidates.filter((c) => candidateCovers(ctx, c));
  if (eligible.length === 0) return null;
  return eligible.reduce((best, c) => {
    const cExp = c.supplierExpiresAtMs ?? Number.POSITIVE_INFINITY;
    const bExp = best.supplierExpiresAtMs ?? Number.POSITIVE_INFINITY;
    return cExp > bExp ? c : best;
  });
}
