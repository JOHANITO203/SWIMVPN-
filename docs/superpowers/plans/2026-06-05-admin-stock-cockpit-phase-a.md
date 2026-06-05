# Admin Stock Cockpit — Phase A (continuity reallocation engine) Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** When a client's assigned supplier config will die (date or source-quota) before the SOLD promise, automatically re-assign that client to another healthy in-stock config of the same plan — drawing only from existing stock, logging every move, and alerting (audit) when no stock can cover.

**Architecture:** A pure, testable `EntitlementContinuityPolicy` (risk verdict + candidate selection) plus a `ResupplyOrchestrator` pass grafted into the existing 30-min healthcheck scheduler in `inventory-delivery-service`. Plan-duration logic is extracted to the shared `@app/contracts` lib so the sold-expiry computation has a SINGLE source of truth (shared with `customer-order-service`).

**Tech Stack:** NestJS monorepo (`backend/apps/*`, `backend/libs/contracts` = `@app/contracts`), Prisma, ts-node policy specs chained in `package.json` `test:policy`.

**Scope:** Phase A only. OUT: bot push of alerts + per-plan stock view (Phase B), cockpit UX (Phase C). The orchestrator emits an audit `AdminEvent` and a `continuity_alert` microservice event now; the bot CONSUMING it is Phase B.

**Machine note:** RAM-constrained. Tests = targeted `ts-node` specs. Only `npm run lint` + `nest build inventory-delivery-service` for build checks; never assembleRelease here.

---

### Task 0: Branch

- [ ] **Step 1: Create the working branch**

Run (from `c:/Users/Lenovo/StudioProjects/SWIMVPN-`):
```bash
git checkout main
git checkout -b feat/stock-cockpit-phase-a
```

---

### Task 1: Shared plan-duration helper (single source of truth)

**Why:** `getDurationMsFromOrder` is private in `customer.service.ts`. The orchestrator needs the same durations. Duplicating durations already caused a real bug (premium-quota). Extract once to `@app/contracts`, then both services use it.

**Files:**
- Create: `backend/libs/contracts/src/plan-duration.ts`
- Modify: `backend/libs/contracts/src/index.ts` (barrel export — confirm the real barrel file name by reading `backend/libs/contracts/src/`)
- Modify: `backend/apps/customer-order-service/src/customer.service.ts` (rewire `getDurationMsFromOrder` to delegate)
- Test: `backend/apps/inventory-delivery-service/src/__tests__/plan-duration.policy.spec.ts`

- [ ] **Step 1: Read the real barrel + confirm `@app/contracts` export style**

Read `backend/libs/contracts/src/index.ts` (or the directory) to see how helpers are exported (e.g., `export * from './supplier-capacity'`). Match that style.

- [ ] **Step 2: Write the failing test** `plan-duration.policy.spec.ts`

```typescript
import { PLAN_DURATION_MS, TRIAL_DURATION_MS, planDurationMs } from '@app/contracts';

function assert(condition: boolean, message: string) {
  if (!condition) throw new Error(message);
}

assert(PLAN_DURATION_MS.WEEK === 7 * 24 * 60 * 60 * 1000, 'WEEK = 7d');
assert(PLAN_DURATION_MS.MONTH === 30 * 24 * 60 * 60 * 1000, 'MONTH = 30d');
assert(PLAN_DURATION_MS.QUARTER === 90 * 24 * 60 * 60 * 1000, 'QUARTER = 90d');
assert(TRIAL_DURATION_MS === 3 * 24 * 60 * 60 * 1000, 'TRIAL = 3d');

assert(planDurationMs('MONTH', false) === PLAN_DURATION_MS.MONTH, 'MONTH paid');
assert(planDurationMs('QUARTER', false) === PLAN_DURATION_MS.QUARTER, 'QUARTER paid');
assert(planDurationMs('WEEK', false) === PLAN_DURATION_MS.WEEK, 'WEEK paid');
// Unknown plan code falls back to WEEK (matches the legacy switch `default`).
assert(planDurationMs('NOPE', false) === PLAN_DURATION_MS.WEEK, 'unknown -> WEEK');
assert(planDurationMs('MONTH', true) === TRIAL_DURATION_MS, 'trial overrides to TRIAL');

console.log('plan-duration.policy.spec.ts passed');
```

- [ ] **Step 3: Run it, expect FAIL** (module not found)

Run (from `backend/`): `npx ts-node -r tsconfig-paths/register apps/inventory-delivery-service/src/__tests__/plan-duration.policy.spec.ts`
Expected: FAIL (`Cannot find module '@app/contracts'` export / `planDurationMs` undefined).

- [ ] **Step 4: Implement** `plan-duration.ts`

```typescript
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
```

- [ ] **Step 5: Export from the barrel**

Add to `backend/libs/contracts/src/index.ts` (matching the existing export style read in Step 1):
```typescript
export * from './plan-duration';
```

- [ ] **Step 6: Run the test, expect PASS**

Run: `npx ts-node -r tsconfig-paths/register apps/inventory-delivery-service/src/__tests__/plan-duration.policy.spec.ts`
Expected: `plan-duration.policy.spec.ts passed`.

- [ ] **Step 7: Rewire `customer.service.ts` to the shared helper (kill duplication)**

Read `getDurationMsFromOrder` (~lines 1965-1979) and `TRIAL_DURATION_MS` (~line 40). Replace the body of `getDurationMsFromOrder` to delegate, and remove the now-unused private `TRIAL_DURATION_MS` ONLY if nothing else references it (grep `TRIAL_DURATION_MS` in the file first; if other call sites exist, keep the constant but set it `= TRIAL_DURATION_MS` from contracts). Add `import { planDurationMs } from '@app/contracts';` near the other imports.
```typescript
private getDurationMsFromOrder(planCode: string, isTrialOrder: boolean) {
  return planDurationMs(planCode, isTrialOrder);
}
```

- [ ] **Step 8: Verify customer-order-service still green + build**

Run (from `backend/`):
```bash
npx ts-node -r tsconfig-paths/register apps/customer-order-service/src/__tests__/backend-security.policy.spec.ts
npm run lint
```
Expected: `backend security policy tests passed` (trial/sub expiry durations unchanged) + tsc 0 errors.

- [ ] **Step 9: Chain the new spec into `test:policy` + commit**

In `backend/package.json`, append to the `test:policy` chain (after the last `&&`):
` && ts-node -r tsconfig-paths/register apps/inventory-delivery-service/src/__tests__/plan-duration.policy.spec.ts`
```bash
git add backend/libs/contracts/src/plan-duration.ts backend/libs/contracts/src/index.ts backend/apps/customer-order-service/src/customer.service.ts backend/apps/inventory-delivery-service/src/__tests__/plan-duration.policy.spec.ts backend/package.json
git commit -m "feat(contracts): shared plan-duration helper (single source of truth)"
```

---

### Task 2: `EntitlementContinuityPolicy` — pure risk + candidate selection (TDD)

**Files:**
- Create: `backend/apps/inventory-delivery-service/src/entitlement-continuity.policy.ts`
- Test: `backend/apps/inventory-delivery-service/src/__tests__/entitlement-continuity.policy.spec.ts`

- [ ] **Step 1: Write the failing test**

```typescript
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
// DATE: config expires before sold promise.
assert(
  isAssignmentAtRisk({
    soldExpiryMs: now + 10 * day, soldQuotaBytes: 0,
    configSupplierExpiresAtMs: now + 3 * day,
    configSourceQuotaRemainingBytes: null, clientConsumedBytes: 0, nowMs: now,
  }).reasons.includes('DATE'),
  'date risk when config dies before promise',
);
// No DATE risk when config outlives promise.
assert(
  !isAssignmentAtRisk({
    soldExpiryMs: now + 3 * day, soldQuotaBytes: 0,
    configSupplierExpiresAtMs: now + 10 * day,
    configSourceQuotaRemainingBytes: null, clientConsumedBytes: 0, nowMs: now,
  }).atRisk,
  'no date risk when config outlives promise',
);
// QUOTA: config source remaining < client remaining sold need.
assert(
  isAssignmentAtRisk({
    soldExpiryMs: now + 30 * day, soldQuotaBytes: 50 * GB,
    configSupplierExpiresAtMs: null,
    configSourceQuotaRemainingBytes: 5 * GB, clientConsumedBytes: 10 * GB, nowMs: now,
  }).reasons.includes('QUOTA'),
  'quota risk: 5GB left on config < 40GB still owed',
);
// No QUOTA risk when sold quota is unlimited (0).
assert(
  !isAssignmentAtRisk({
    soldExpiryMs: now + 30 * day, soldQuotaBytes: 0,
    configSupplierExpiresAtMs: null,
    configSourceQuotaRemainingBytes: 1, clientConsumedBytes: 0, nowMs: now,
  }).atRisk,
  'unlimited sold quota => no quota risk',
);
// Fail-safe: missing data => not at risk (never move blindly).
assert(
  !isAssignmentAtRisk({
    soldExpiryMs: now + 30 * day, soldQuotaBytes: 50 * GB,
    configSupplierExpiresAtMs: null,
    configSourceQuotaRemainingBytes: null, clientConsumedBytes: 0, nowMs: now,
  }).atRisk,
  'null config data => not at risk',
);
// Client already consumed sold quota => no quota risk (nothing left to protect).
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
  // wrong category
  { id: 'c1', category: 'MONTH', healthStatus: 'HEALTHY', usedResaleSlots: 0, maxResaleSlots: 2, supplierExpiresAtMs: now + 60 * day, sourceQuotaRemainingBytes: 100 * GB, salePriorityScore: 100 },
  // not healthy
  { id: 'c2', category: 'WEEK', healthStatus: 'DEGRADED', usedResaleSlots: 0, maxResaleSlots: 2, supplierExpiresAtMs: now + 60 * day, sourceQuotaRemainingBytes: 100 * GB, salePriorityScore: 100 },
  // full slots
  { id: 'c3', category: 'WEEK', healthStatus: 'HEALTHY', usedResaleSlots: 2, maxResaleSlots: 2, supplierExpiresAtMs: now + 60 * day, sourceQuotaRemainingBytes: 100 * GB, salePriorityScore: 100 },
  // dies before promise (date)
  { id: 'c4', category: 'WEEK', healthStatus: 'HEALTHY', usedResaleSlots: 0, maxResaleSlots: 2, supplierExpiresAtMs: now + 5 * day, sourceQuotaRemainingBytes: 100 * GB, salePriorityScore: 100 },
  // not enough source quota (needs 40GB, has 10GB)
  { id: 'c5', category: 'WEEK', healthStatus: 'HEALTHY', usedResaleSlots: 0, maxResaleSlots: 2, supplierExpiresAtMs: now + 60 * day, sourceQuotaRemainingBytes: 10 * GB, salePriorityScore: 100 },
  // valid, lower score
  { id: 'c6', category: 'WEEK', healthStatus: 'HEALTHY', usedResaleSlots: 1, maxResaleSlots: 2, supplierExpiresAtMs: now + 60 * day, sourceQuotaRemainingBytes: 100 * GB, salePriorityScore: 10 },
  // valid, higher score => should win
  { id: 'c7', category: 'WEEK', healthStatus: 'HEALTHY', usedResaleSlots: 0, maxResaleSlots: 2, supplierExpiresAtMs: now + 60 * day, sourceQuotaRemainingBytes: 100 * GB, salePriorityScore: 90 },
];
assert(selectReallocationCandidate(base, candidates, now)?.id === 'c7', 'picks valid candidate with best score');
assert(selectReallocationCandidate(base, [candidates[0], candidates[1], candidates[3]], now) === null, 'null when none qualify');

console.log('entitlement-continuity.policy.spec.ts passed');
```

- [ ] **Step 2: Run it, expect FAIL** (module/functions undefined)

Run (from `backend/`): `npx ts-node -r tsconfig-paths/register apps/inventory-delivery-service/src/__tests__/entitlement-continuity.policy.spec.ts`
Expected: FAIL.

- [ ] **Step 3: Implement** `entitlement-continuity.policy.ts`

```typescript
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
  usedResaleSlots: number;
  maxResaleSlots: number;
  supplierExpiresAtMs: number | null;
  sourceQuotaRemainingBytes: number | null;
  salePriorityScore: number;
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
  if (c.usedResaleSlots >= c.maxResaleSlots) return false;

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

/** Best covering candidate: highest salePriorityScore, tie-broken by latest expiry (most margin). */
export function selectReallocationCandidate(
  ctx: SelectionContext,
  candidates: ContinuityCandidate[],
  _nowMs: number,
): ContinuityCandidate | null {
  const eligible = candidates.filter((c) => candidateCovers(ctx, c));
  if (eligible.length === 0) return null;
  return eligible.reduce((best, c) => {
    if (c.salePriorityScore !== best.salePriorityScore) {
      return c.salePriorityScore > best.salePriorityScore ? c : best;
    }
    const cExp = c.supplierExpiresAtMs ?? Number.POSITIVE_INFINITY;
    const bExp = best.supplierExpiresAtMs ?? Number.POSITIVE_INFINITY;
    return cExp > bExp ? c : best;
  });
}
```

- [ ] **Step 4: Run the test, expect PASS**

Run: `npx ts-node -r tsconfig-paths/register apps/inventory-delivery-service/src/__tests__/entitlement-continuity.policy.spec.ts`
Expected: `entitlement-continuity.policy.spec.ts passed`.

- [ ] **Step 5: Chain into `test:policy` + commit**

Append to `test:policy` in `backend/package.json`:
` && ts-node -r tsconfig-paths/register apps/inventory-delivery-service/src/__tests__/entitlement-continuity.policy.spec.ts`
```bash
git add backend/apps/inventory-delivery-service/src/entitlement-continuity.policy.ts backend/apps/inventory-delivery-service/src/__tests__/entitlement-continuity.policy.spec.ts backend/package.json
git commit -m "feat(inventory): pure entitlement-continuity policy (risk + candidate selection)"
```

---

### Task 3: `ResupplyOrchestrator` — reallocation pass (service)

**Files:**
- Create: `backend/apps/inventory-delivery-service/src/resupply-orchestrator.ts`
- Test: `backend/apps/inventory-delivery-service/src/__tests__/resupply-orchestrator.policy.spec.ts`

**READ FIRST (do not invent — mirror these):**
- `inventory.service.ts` `fulfillOrder()` (~268-520): how an `OrderAssignment` is CREATED (fields set), how `used_resale_slots` is incremented (`nextUsedSlots`), and the `tx.adminEvent.create` shape (~483-497).
- `revokeReplacedActiveAssignments` (~619): the established pattern for revoking a replaced assignment (`access_status: 'REVOKED'`, `revoked_at`, `status_reason`, and slot decrement on the old item).
- `checkStockAndNotify` (~1616-1635): the `this.adminClient.emit('low_stock_alert', {...})` mechanism (ClientProxy) — reuse for the new alert.

- [ ] **Step 1: Write the failing test (mock-prisma, mirrors `backend-security.policy.spec.ts` style)**

`resupply-orchestrator.policy.spec.ts` — construct the orchestrator with a fake prisma + fake adminClient, run one pass, assert the writes. Use this shape (adapt method/ctor names to the real class after Step 3):
```typescript
import { ResupplyOrchestrator } from '../resupply-orchestrator';

function assert(condition: boolean, message: string) {
  if (!condition) throw new Error(message);
}
const GB = 1024 * 1024 * 1024;
const now = Date.now();
const day = 24 * 60 * 60 * 1000;

function makeDeps(overrides: any = {}) {
  const writes: any = { assignmentUpdates: [], itemUpdates: [], events: [], emitted: [], created: [] };
  const prisma = {
    orderAssignment: {
      findMany: async () => overrides.assignments ?? [],
      update: async (a: any) => { writes.assignmentUpdates.push(a); return a; },
      create: async (a: any) => { writes.created.push(a); return a; },
    },
    inventoryItem: {
      findMany: async () => overrides.candidates ?? [],
      update: async (a: any) => { writes.itemUpdates.push(a); return a; },
    },
    adminEvent: { create: async (a: any) => { writes.events.push(a); return a; } },
    $transaction: async (fn: any) => fn(prisma),
  };
  const adminClient = { emit: (ev: string, payload: any) => writes.emitted.push({ ev, payload }) };
  return { prisma, adminClient, writes };
}

// At-risk assignment (config dies before promise) + a covering candidate => reallocates + audit event.
{
  const order = { fulfilled_at: new Date(now - 5 * day), plan: { code: 'WEEK', quota_gb: 0 } };
  const assignment = {
    id: 'a1', order_id: 'o1', customer_id: 'cust1', inventory_item_id: 'old1',
    access_status: 'ACTIVE', measured_used_bytes: 0n,
    order,
    inventory_item: { id: 'old1', category: 'WEEK', supplier_expires_at: new Date(now + 1 * day), source_quota_bytes: null, source_used_bytes: 0n, used_resale_slots: 1, max_resale_slots: 2 },
  };
  const candidate = { id: 'new1', category: 'WEEK', health_status: 'HEALTHY', used_resale_slots: 0, max_resale_slots: 2, supplier_expires_at: new Date(now + 30 * day), source_quota_bytes: null, source_used_bytes: 0n, sale_priority_score: 50 };
  const { prisma, adminClient, writes } = makeDeps({ assignments: [assignment], candidates: [candidate] });
  const orch = new ResupplyOrchestrator(prisma as any, adminClient as any);
  await orch.runReallocationPass();
  assert(writes.events.some((e: any) => e.data.event_type === 'ASSIGNMENT_REALLOCATED'), 'logs ASSIGNMENT_REALLOCATED');
  assert(writes.itemUpdates.length >= 2, 'adjusts both old and new item slots');
}

// At-risk but NO covering candidate => no reallocation, logs failure + emits alert.
{
  const order = { fulfilled_at: new Date(now - 5 * day), plan: { code: 'WEEK', quota_gb: 0 } };
  const assignment = {
    id: 'a2', order_id: 'o2', customer_id: 'cust2', inventory_item_id: 'old2',
    access_status: 'ACTIVE', measured_used_bytes: 0n, order,
    inventory_item: { id: 'old2', category: 'WEEK', supplier_expires_at: new Date(now + 1 * day), source_quota_bytes: null, source_used_bytes: 0n, used_resale_slots: 1, max_resale_slots: 2 },
  };
  const { prisma, adminClient, writes } = makeDeps({ assignments: [assignment], candidates: [] });
  const orch = new ResupplyOrchestrator(prisma as any, adminClient as any);
  await orch.runReallocationPass();
  assert(writes.events.some((e: any) => e.data.event_type === 'REALLOCATION_FAILED_NO_STOCK'), 'logs REALLOCATION_FAILED_NO_STOCK');
  assert(writes.emitted.some((m: any) => m.ev === 'continuity_alert'), 'emits continuity_alert');
  assert(writes.assignmentUpdates.length === 0 && writes.created.length === 0, 'does not touch the assignment');
}

// NOT at risk (config outlives promise) => no-op (idempotent).
{
  const order = { fulfilled_at: new Date(now - 5 * day), plan: { code: 'WEEK', quota_gb: 0 } };
  const assignment = {
    id: 'a3', order_id: 'o3', customer_id: 'cust3', inventory_item_id: 'ok3',
    access_status: 'ACTIVE', measured_used_bytes: 0n, order,
    inventory_item: { id: 'ok3', category: 'WEEK', supplier_expires_at: new Date(now + 30 * day), source_quota_bytes: null, source_used_bytes: 0n, used_resale_slots: 1, max_resale_slots: 2 },
  };
  const { prisma, adminClient, writes } = makeDeps({ assignments: [assignment], candidates: [] });
  const orch = new ResupplyOrchestrator(prisma as any, adminClient as any);
  await orch.runReallocationPass();
  assert(writes.events.length === 0 && writes.emitted.length === 0, 'no-op when healthy');
}

console.log('resupply-orchestrator.policy.spec.ts passed');
```

- [ ] **Step 2: Run it, expect FAIL** (class undefined)

Run: `npx ts-node -r tsconfig-paths/register apps/inventory-delivery-service/src/__tests__/resupply-orchestrator.policy.spec.ts`
Expected: FAIL.

- [ ] **Step 3: Implement** `resupply-orchestrator.ts`

Compute sold promise via `@app/contracts.planDurationMs` (Task 1) + `plan.quota_gb`. Use the pure policy (Task 2). Mirror the real prisma write patterns you read. Skeleton (align field writes with `fulfillOrder` + `revokeReplacedActiveAssignments`):
```typescript
import { planDurationMs } from '@app/contracts';
import { isAssignmentAtRisk, selectReallocationCandidate, type ContinuityCandidate, type SelectionContext } from './entitlement-continuity.policy';

const GB = 1024 * 1024 * 1024;
const MAX_REALLOCATIONS_PER_PASS = 50; // anti-runaway guard

export class ResupplyOrchestrator {
  constructor(private readonly prisma: any, private readonly adminClient: any) {}

  async runReallocationPass(): Promise<{ checked: number; reallocated: number; failed: number }> {
    const nowMs = Date.now();
    const assignments = await this.prisma.orderAssignment.findMany({
      where: { access_status: 'ACTIVE' },
      include: { order: { include: { plan: true } }, inventory_item: true },
    });
    let reallocated = 0;
    let failed = 0;
    for (const a of assignments) {
      if (reallocated >= MAX_REALLOCATIONS_PER_PASS) break;
      const item = a.inventory_item;
      if (!item || !a.order?.fulfilled_at) continue;

      const soldExpiryMs = a.order.fulfilled_at.getTime() + planDurationMs(a.order.plan.code, false);
      const soldQuotaBytes = (a.order.plan.quota_gb ?? 0) > 0 ? a.order.plan.quota_gb * GB : 0;
      const configRemaining = item.source_quota_bytes != null
        ? Number(item.source_quota_bytes) - Number(item.source_used_bytes ?? 0n)
        : null;

      const verdict = isAssignmentAtRisk({
        soldExpiryMs,
        soldQuotaBytes,
        configSupplierExpiresAtMs: item.supplier_expires_at ? item.supplier_expires_at.getTime() : null,
        configSourceQuotaRemainingBytes: configRemaining,
        clientConsumedBytes: Number(a.measured_used_bytes ?? 0n),
        nowMs,
      });
      if (!verdict.atRisk) continue;

      const ctx: SelectionContext = {
        category: item.category,
        soldExpiryMs,
        soldQuotaBytes,
        clientConsumedBytes: Number(a.measured_used_bytes ?? 0n),
      };
      const rawCandidates = await this.prisma.inventoryItem.findMany({
        where: { category: item.category, health_status: 'HEALTHY', status: { in: ['AVAILABLE', 'ASSIGNED'] } },
      });
      const candidates: ContinuityCandidate[] = rawCandidates
        .filter((c: any) => c.id !== item.id)
        .map((c: any) => ({
          id: c.id, category: c.category, healthStatus: c.health_status,
          usedResaleSlots: c.used_resale_slots, maxResaleSlots: c.max_resale_slots,
          supplierExpiresAtMs: c.supplier_expires_at ? c.supplier_expires_at.getTime() : null,
          sourceQuotaRemainingBytes: c.source_quota_bytes != null ? Number(c.source_quota_bytes) - Number(c.source_used_bytes ?? 0n) : null,
          salePriorityScore: c.sale_priority_score,
        }));
      const winner = selectReallocationCandidate(ctx, candidates, nowMs);

      if (!winner) {
        failed++;
        await this.prisma.adminEvent.create({ data: {
          event_type: 'REALLOCATION_FAILED_NO_STOCK', entity_type: 'INVENTORY', entity_id: item.id,
          payload_json: { assignmentId: a.id, category: item.category, reasons: verdict.reasons, soldExpiryMs } as any,
        }});
        this.adminClient.emit('continuity_alert', { kind: 'NO_STOCK', category: item.category, assignmentId: a.id, reasons: verdict.reasons });
        continue;
      }

      // Reallocate atomically: repoint assignment to winner, move resale slots, audit.
      await this.prisma.$transaction(async (tx: any) => {
        await tx.orderAssignment.update({
          where: { id: a.id },
          data: { inventory_item_id: winner.id, expires_at: winner.supplierExpiresAtMs ? new Date(winner.supplierExpiresAtMs) : null, status_reason: 'REALLOCATED' },
        });
        await tx.inventoryItem.update({ where: { id: winner.id }, data: { used_resale_slots: { increment: 1 } } });
        await tx.inventoryItem.update({ where: { id: item.id }, data: { used_resale_slots: { decrement: 1 } } });
        await tx.adminEvent.create({ data: {
          event_type: 'ASSIGNMENT_REALLOCATED', entity_type: 'ORDER', entity_id: a.order_id,
          payload_json: { assignmentId: a.id, fromItemId: item.id, toItemId: winner.id, reasons: verdict.reasons } as any,
        }});
      });
      reallocated++;
    }
    return { checked: assignments.length, reallocated, failed };
  }
}
```
> NOTE on repoint vs revoke+create: this skeleton REPOINTS the same `OrderAssignment` (preserves `measured_used_bytes` + order linkage; the client gets the new node on next profile fetch). If the codebase's established pattern (`revokeReplacedActiveAssignments`) instead REVOKES the old and CREATES a new ACTIVE assignment, follow THAT pattern (revoke old: `access_status:'REVOKED'`, `revoked_at`, decrement old slot; create new carrying `measured_used_bytes`), and update the test's assertions accordingly (the test already tolerates either via `assignmentUpdates`/`created`). Pick the one matching the existing code; state which in the implementation report.

- [ ] **Step 4: Run the test, expect PASS** (adjust ctor/field names to the real class)

Run: `npx ts-node -r tsconfig-paths/register apps/inventory-delivery-service/src/__tests__/resupply-orchestrator.policy.spec.ts`
Expected: `resupply-orchestrator.policy.spec.ts passed`.

- [ ] **Step 5: Chain into `test:policy` + commit**

Append to `test:policy`:
` && ts-node -r tsconfig-paths/register apps/inventory-delivery-service/src/__tests__/resupply-orchestrator.policy.spec.ts`
```bash
git add backend/apps/inventory-delivery-service/src/resupply-orchestrator.ts backend/apps/inventory-delivery-service/src/__tests__/resupply-orchestrator.policy.spec.ts backend/package.json
git commit -m "feat(inventory): ResupplyOrchestrator reallocation pass (reallocate or alert)"
```

---

### Task 4: Graft the pass into the healthcheck scheduler

**Files:**
- Modify: `backend/apps/inventory-delivery-service/src/inventory.service.ts` (`runScheduledHealthCheck` ~1532-1541; wiring near `onModuleInit`/ctor)

- [ ] **Step 1: Read the real ctor + how `adminClient` (ClientProxy) and `prisma` are injected**

Read the `InventoryService` constructor + `onModuleInit` (~57-85) to see the exact injected names (`this.prisma`, `this.adminClient`, `this.logger`). Confirm `adminClient` is the same client used by `checkStockAndNotify`'s `emit`.

- [ ] **Step 2: Instantiate the orchestrator + call it after the health pass**

In `runScheduledHealthCheck` (~1532), after `const result = await this.runHealthCheck();` and before the summary log, add a guarded reallocation pass:
```typescript
private async runScheduledHealthCheck() {
  try {
    const result = await this.runHealthCheck();
    if (process.env.CONTINUITY_REALLOCATION_ENABLED !== 'false') {
      try {
        const orchestrator = new ResupplyOrchestrator(this.prisma, this.adminClient);
        const realloc = await orchestrator.runReallocationPass();
        this.logger.log(
          `Continuity reallocation: checked=${realloc.checked} reallocated=${realloc.reallocated} failed=${realloc.failed}`,
        );
      } catch (reallocError) {
        this.logger.error('Continuity reallocation pass failed', reallocError as Error);
      }
    }
    this.logger.log(
      `Scheduled inventory healthcheck completed: checked=${result.checked} healthy=${result.healthy} degraded=${result.degraded}`,
    );
  } catch (error) {
    this.logger.error('Scheduled inventory healthcheck failed', error as Error);
  }
}
```
Add the import at the top: `import { ResupplyOrchestrator } from './resupply-orchestrator';`. (Default ON; `CONTINUITY_REALLOCATION_ENABLED=false` kill-switch. The reallocation pass NEVER throws into the healthcheck — its own try/catch.)

- [ ] **Step 3: Build check**

Run (from `backend/`): `npm run lint && npx nest build inventory-delivery-service`
Expected: tsc 0 errors + `compiled successfully`.

- [ ] **Step 4: Commit**

```bash
git add backend/apps/inventory-delivery-service/src/inventory.service.ts
git commit -m "feat(inventory): run continuity reallocation pass after scheduled healthcheck (kill-switch)"
```

---

### Task 5: Full verification (Phase A)

- [ ] **Step 1: Run the full policy chain**

Run (from `backend/`): `npm run test:policy`
Expected: all specs pass, including `plan-duration`, `entitlement-continuity`, `resupply-orchestrator` (and the pre-existing ones — chain stays green). If any pre-existing spec fails, STOP and report (do not mask).

- [ ] **Step 2: Build the host service**

Run: `npx nest build inventory-delivery-service` (and `npx nest build customer-order-service` since Task 1 touched it).
Expected: both compile.

- [ ] **Step 3: Report — STOP before merge**

Do NOT merge. Report: commits, what each does, test output, and the one open decision (repoint vs revoke+create — which the codebase pattern dictated). Merge to main+prod (triggers Dokploy redeploy of inventory-delivery-service + customer-order-service) requires user go-ahead. The engine runs silently (invisible to client) once deployed; Phase B will surface `continuity_alert` + reallocation events in the bot.

---

## Notes / sequencing
- Phase A is backend-only and **invisible to the client** (it just serves a healthier node on the next profile fetch). It deploys safely on its own.
- The `continuity_alert` emit + `AdminEvent` rows are written now but **consumed/surfaced in Phase B** (bot push + per-plan stock view).
- Kill-switch: `CONTINUITY_REALLOCATION_ENABLED=false` disables the pass without a redeploy of code (env only).
