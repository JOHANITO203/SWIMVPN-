import { ResupplyOrchestrator } from '../resupply-orchestrator';

function assert(condition: boolean, message: string) {
  if (!condition) throw new Error(message);
}
const GB = 1024 * 1024 * 1024;
const now = Date.now();
const day = 24 * 60 * 60 * 1000;

function makeDeps(overrides: any = {}) {
  const writes: any = { assignmentUpdates: [], itemUpdates: [], events: [], emitted: [], created: [] };
  const prisma: any = {
    orderAssignment: {
      findMany: async () => overrides.assignments ?? [],
      update: async (a: any) => { writes.assignmentUpdates.push(a); return a; },
      create: async (a: any) => { writes.created.push(a); return a; },
    },
    inventoryItem: {
      findMany: async () => overrides.candidates ?? [],
      findUnique: async ({ where }: any) => (overrides.winnerFresh !== undefined ? overrides.winnerFresh : (overrides.candidates ?? []).find((c: any) => c.id === where.id) ?? null),
      update: async (a: any) => { writes.itemUpdates.push(a); return a; },
    },
    adminEvent: { create: async (a: any) => { writes.events.push(a); return a; } },
    $transaction: async (fn: any) => fn(prisma),
  };
  const adminClient = { emit: (ev: string, payload: any) => writes.emitted.push({ ev, payload }) };
  return { prisma, adminClient, writes };
}

async function main() {
  // 1) At-risk (config dies before promise) + covering candidate => reallocates + audit event.
  {
    const order = { fulfilled_at: new Date(now - 5 * day), plan: { code: 'WEEK', quota_gb: 0 } };
    const assignment = {
      id: 'a1', order_id: 'o1', customer_id: 'cust1', inventory_item_id: 'old1',
      access_status: 'ACTIVE', measured_used_bytes: 0n, order,
      inventory_item: { id: 'old1', category: 'WEEK', supplier_expires_at: new Date(now + 1 * day), source_quota_bytes: null, source_used_bytes: 0n, used_resale_slots: 1, max_resale_slots: 2 },
    };
    const candidate = { id: 'new1', category: 'WEEK', health_status: 'HEALTHY', used_resale_slots: 0, max_resale_slots: 2, supplier_expires_at: new Date(now + 30 * day), source_quota_bytes: null, source_used_bytes: 0n, sale_priority_score: 50 };
    const { prisma, adminClient, writes } = makeDeps({ assignments: [assignment], candidates: [candidate] });
    const orch = new ResupplyOrchestrator(prisma, adminClient);
    await orch.runReallocationPass();
    assert(writes.events.some((e: any) => e.data.event_type === 'ASSIGNMENT_REALLOCATED'), 'logs ASSIGNMENT_REALLOCATED');
    assert(writes.itemUpdates.length >= 2, 'adjusts both old and new item slots');
    assert(writes.emitted.some((m: any) => m.ev === 'continuity_pass_summary' && m.payload.reallocated === 1), 'emits per-pass summary with reallocated count');
  }

  // 2) At-risk but NO covering candidate => no reallocation, logs failure + emits alert.
  {
    const order = { fulfilled_at: new Date(now - 5 * day), plan: { code: 'WEEK', quota_gb: 0 } };
    const assignment = {
      id: 'a2', order_id: 'o2', customer_id: 'cust2', inventory_item_id: 'old2',
      access_status: 'ACTIVE', measured_used_bytes: 0n, order,
      inventory_item: { id: 'old2', category: 'WEEK', supplier_expires_at: new Date(now + 1 * day), source_quota_bytes: null, source_used_bytes: 0n, used_resale_slots: 1, max_resale_slots: 2 },
    };
    const { prisma, adminClient, writes } = makeDeps({ assignments: [assignment], candidates: [] });
    const orch = new ResupplyOrchestrator(prisma, adminClient);
    await orch.runReallocationPass();
    assert(writes.events.some((e: any) => e.data.event_type === 'REALLOCATION_FAILED_NO_STOCK'), 'logs REALLOCATION_FAILED_NO_STOCK');
    assert(writes.emitted.some((m: any) => m.ev === 'continuity_alert'), 'emits continuity_alert');
    assert(writes.assignmentUpdates.length === 0 && writes.created.length === 0, 'does not touch the assignment');
  }

  // 3) NOT at risk (config outlives promise) => no-op (idempotent).
  {
    const order = { fulfilled_at: new Date(now - 5 * day), plan: { code: 'WEEK', quota_gb: 0 } };
    const assignment = {
      id: 'a3', order_id: 'o3', customer_id: 'cust3', inventory_item_id: 'ok3',
      access_status: 'ACTIVE', measured_used_bytes: 0n, order,
      inventory_item: { id: 'ok3', category: 'WEEK', supplier_expires_at: new Date(now + 30 * day), source_quota_bytes: null, source_used_bytes: 0n, used_resale_slots: 1, max_resale_slots: 2 },
    };
    const { prisma, adminClient, writes } = makeDeps({ assignments: [assignment], candidates: [] });
    const orch = new ResupplyOrchestrator(prisma, adminClient);
    await orch.runReallocationPass();
    assert(writes.events.length === 0 && writes.emitted.length === 0, 'no-op when healthy');
    assert(!writes.emitted.some((m: any) => m.ev === 'continuity_pass_summary'), 'no summary when nothing happened');
  }

  // 4) At-risk + candidate passes selection but is FULL at tx-time (race guard) => no reallocation.
  {
    const order = { fulfilled_at: new Date(now - 5 * day), plan: { code: 'WEEK', quota_gb: 0 } };
    const assignment = {
      id: 'a4', order_id: 'o4', customer_id: 'cust4', inventory_item_id: 'old4',
      access_status: 'ACTIVE', measured_used_bytes: 0n, order,
      inventory_item: { id: 'old4', category: 'WEEK', supplier_expires_at: new Date(now + 1 * day), source_quota_bytes: null, source_used_bytes: 0n, used_resale_slots: 1, max_resale_slots: 2 },
    };
    // Candidate passes selection (usedResaleSlots: 0 < maxResaleSlots: 2) but winnerFresh is full
    const candidate = { id: 'new4', category: 'WEEK', health_status: 'HEALTHY', used_resale_slots: 0, max_resale_slots: 2, supplier_expires_at: new Date(now + 30 * day), source_quota_bytes: null, source_used_bytes: 0n, sale_priority_score: 50 };
    const winnerFresh = { id: 'new4', used_resale_slots: 2, max_resale_slots: 2 };
    const { prisma, adminClient, writes } = makeDeps({ assignments: [assignment], candidates: [candidate], winnerFresh });
    const orch = new ResupplyOrchestrator(prisma, adminClient);
    const result = await orch.runReallocationPass();
    assert(result.reallocated === 0, 'race guard: reallocated should be 0');
    assert(!writes.events.some((e: any) => e.data.event_type === 'ASSIGNMENT_REALLOCATED'), 'race guard: no ASSIGNMENT_REALLOCATED event');
    assert(writes.assignmentUpdates.length === 0, 'race guard: no assignment update');
    assert(writes.itemUpdates.length === 0, 'race guard: no item updates');
  }

  // 5) ACTIVE assignment whose sold promise already expired => skipped by soldExpiryMs < nowMs guard.
  {
    // fulfilled_at = 60 days ago, WEEK plan => soldExpiryMs = ~53 days ago (well in the past)
    const order = { fulfilled_at: new Date(now - 60 * day), plan: { code: 'WEEK', quota_gb: 0 } };
    const assignment = {
      id: 'a5', order_id: 'o5', customer_id: 'cust5', inventory_item_id: 'old5',
      access_status: 'ACTIVE', measured_used_bytes: 0n, order,
      inventory_item: { id: 'old5', category: 'WEEK', supplier_expires_at: new Date(now + 1 * day), source_quota_bytes: null, source_used_bytes: 0n, used_resale_slots: 1, max_resale_slots: 2 },
    };
    const { prisma, adminClient, writes } = makeDeps({ assignments: [assignment], candidates: [] });
    const orch = new ResupplyOrchestrator(prisma, adminClient);
    await orch.runReallocationPass();
    assert(writes.events.length === 0, 'expired promise: no events');
    assert(writes.emitted.length === 0, 'expired promise: no emits');
  }

  console.log('resupply-orchestrator.policy.spec.ts passed');
}

main();
