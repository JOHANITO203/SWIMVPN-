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
const candidates: ContinuityCandidate[] = [
  { id: 'c1', category: 'MONTH', healthStatus: 'HEALTHY', usedResaleSlots: 0, maxResaleSlots: 2, supplierExpiresAtMs: now + 60 * day, sourceQuotaRemainingBytes: 100 * GB, salePriorityScore: 100 },
  { id: 'c2', category: 'WEEK', healthStatus: 'DEGRADED', usedResaleSlots: 0, maxResaleSlots: 2, supplierExpiresAtMs: now + 60 * day, sourceQuotaRemainingBytes: 100 * GB, salePriorityScore: 100 },
  { id: 'c3', category: 'WEEK', healthStatus: 'HEALTHY', usedResaleSlots: 2, maxResaleSlots: 2, supplierExpiresAtMs: now + 60 * day, sourceQuotaRemainingBytes: 100 * GB, salePriorityScore: 100 },
  { id: 'c4', category: 'WEEK', healthStatus: 'HEALTHY', usedResaleSlots: 0, maxResaleSlots: 2, supplierExpiresAtMs: now + 5 * day, sourceQuotaRemainingBytes: 100 * GB, salePriorityScore: 100 },
  { id: 'c5', category: 'WEEK', healthStatus: 'HEALTHY', usedResaleSlots: 0, maxResaleSlots: 2, supplierExpiresAtMs: now + 60 * day, sourceQuotaRemainingBytes: 10 * GB, salePriorityScore: 100 },
  { id: 'c6', category: 'WEEK', healthStatus: 'HEALTHY', usedResaleSlots: 1, maxResaleSlots: 2, supplierExpiresAtMs: now + 60 * day, sourceQuotaRemainingBytes: 100 * GB, salePriorityScore: 10 },
  { id: 'c7', category: 'WEEK', healthStatus: 'HEALTHY', usedResaleSlots: 0, maxResaleSlots: 2, supplierExpiresAtMs: now + 60 * day, sourceQuotaRemainingBytes: 100 * GB, salePriorityScore: 90 },
];
assert(selectReallocationCandidate(base, candidates, now)?.id === 'c7', 'picks valid candidate with best score');
assert(selectReallocationCandidate(base, [candidates[0], candidates[1], candidates[3]], now) === null, 'null when none qualify');

console.log('entitlement-continuity.policy.spec.ts passed');
