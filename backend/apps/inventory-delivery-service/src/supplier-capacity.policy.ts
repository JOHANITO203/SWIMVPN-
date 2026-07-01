export type SupplierAllocatableHealth =
  | 'HEALTHY'
  | 'DEGRADED'
  | 'FULL'
  | 'EXPIRED'
  | 'DISABLED';

export type SupplierAllocatableStatus =
  | 'AVAILABLE'
  | 'RESERVED'
  | 'ASSIGNED'
  | 'DEAD';

/**
 * One config = one client. A config can serve a new client iff it is HEALTHY and still FREE
 * (AVAILABLE). Once ASSIGNED/DEAD it is burned and never re-offered. Supplier-expiry and
 * source-exhaustion are checked by the caller (they need the live date/bytes).
 */
export function canAllocateSupplierConfig(input: {
  healthStatus: SupplierAllocatableHealth;
  status: SupplierAllocatableStatus;
}) {
  return input.healthStatus === 'HEALTHY' && input.status === 'AVAILABLE';
}
