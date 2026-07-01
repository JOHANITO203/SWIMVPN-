import {
  isAssignmentAtRisk,
  selectReallocationCandidate,
  type ContinuityCandidate,
} from '../entitlement-continuity.policy';

function assert(condition: boolean, message: string) {
  if (!condition) throw new Error(message);
}

const GB = 1024 * 1024 * 1024;
const now = 1_000_000_000_000;
const day = 24 * 60 * 60 * 1000;

// --- isAssignmentAtRisk ---
assert(
  isAssignmentAtRisk({
    soldExpiryMs: now + 10 * day, soldQuotaBytes: 0,
    configSupplierExpiresAtMs: now + 3 * day,
    configSourceQuotaRemainingBytes: null, clientConsumedBytes: 0, nowMs: now,
  }).reasons.includes('DATE'),
  'date risk when config dies before promise',
);
assert(
  !isAssignmentAtRisk({
    soldExpiryMs: now + 3 * day, soldQuotaBytes: 0,
    configSupplierExpiresAtMs: now + 10 * day,
    configSourceQuotaRemainingBytes: null, clientConsumedBytes: 0, nowMs: now,
  }).atRisk,
  'no date risk when config outlives promise',
);
assert(
  isAssignmentAtRisk({
    soldExpiryMs: now + 30 * day, soldQuotaBytes: 50 * GB,
    configSupplierExpiresAtMs: null,
    configSourceQuotaRemainingBytes: 5 * GB, clientConsumedBytes: 10 * GB, nowMs: now,
  }).reasons.includes('QUOTA'),
  'quota risk: 5GB left on config < 40GB still owed',
);
assert(
  !isAssignmentAtRisk({
    soldExpiryMs: now + 30 * day, soldQuotaBytes: 0,
    configSupplierExpiresAtMs: null,
    configSourceQuotaRemainingBytes: 1, clientConsumedBytes: 0, nowMs: now,
  }).atRisk,
  'unlimited sold quota => no quota risk',
);
assert(
  !isAssignmentAtRisk({
    soldExpiryMs: now + 30 * day, soldQuotaBytes: 50 * GB,
    configSupplierExpiresAtMs: null,
    configSourceQuotaRemainingBytes: null, clientConsumedBytes: 0, nowMs: now,
  }).atRisk,
  'null config data => not at risk',
);
assert(
  !isAssignmentAtRisk({
    soldExpiryMs: now + 30 * day, soldQuotaBytes: 50 * GB,
    configSupplierExpiresAtMs: null,
    configSourceQuotaRemainingBytes: 0, clientConsumedBytes: 50 * GB, nowMs: now,
  }).atRisk,
  'client already used sold quota => no quota risk',
);

// --- selectReallocationCandidate ---
const base = { soldExpiryMs: now + 20 * day, soldQuotaBytes: 50 * GB, clientConsumedBytes: 10 * GB, category: 'WEEK' as const };
// One config = one client: only FREE (AVAILABLE) healthy configs that cover date+quota qualify;
// among those the winner is the one with the latest supplier expiry (most margin).
const candidates: ContinuityCandidate[] = [
  { id: 'c1', category: 'MONTH', healthStatus: 'HEALTHY', status: 'AVAILABLE', supplierExpiresAtMs: now + 60 * day, sourceQuotaRemainingBytes: 100 * GB },
  { id: 'c2', category: 'WEEK', healthStatus: 'DEGRADED', status: 'AVAILABLE', supplierExpiresAtMs: now + 60 * day, sourceQuotaRemainingBytes: 100 * GB },
  { id: 'c3', category: 'WEEK', healthStatus: 'HEALTHY', status: 'ASSIGNED', supplierExpiresAtMs: now + 90 * day, sourceQuotaRemainingBytes: 100 * GB },
  { id: 'c4', category: 'WEEK', healthStatus: 'HEALTHY', status: 'AVAILABLE', supplierExpiresAtMs: now + 5 * day, sourceQuotaRemainingBytes: 100 * GB },
  { id: 'c5', category: 'WEEK', healthStatus: 'HEALTHY', status: 'AVAILABLE', supplierExpiresAtMs: now + 60 * day, sourceQuotaRemainingBytes: 10 * GB },
  { id: 'c6', category: 'WEEK', healthStatus: 'HEALTHY', status: 'AVAILABLE', supplierExpiresAtMs: now + 40 * day, sourceQuotaRemainingBytes: 100 * GB },
  { id: 'c7', category: 'WEEK', healthStatus: 'HEALTHY', status: 'AVAILABLE', supplierExpiresAtMs: now + 60 * day, sourceQuotaRemainingBytes: 100 * GB },
];
assert(selectReallocationCandidate(base, candidates, now)?.id === 'c7', 'picks the free covering config with the latest expiry');
assert(selectReallocationCandidate(base, [candidates[0], candidates[1], candidates[3]], now) === null, 'null when none qualify (wrong cat / degraded / date-fail)');

console.log('entitlement-continuity.policy.spec.ts passed');
