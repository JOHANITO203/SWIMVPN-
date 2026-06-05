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
      if (soldExpiryMs < nowMs) continue; // sold promise already over — nothing to protect
      const soldQuotaBytes = (a.order.plan.quota_gb ?? 0) > 0 ? a.order.plan.quota_gb * GB : 0;
      const configRemaining = item.source_quota_bytes != null
        ? Number(item.source_quota_bytes) - Number(item.source_used_bytes ?? 0n)
        : null;
      const clientConsumed = Number(a.measured_used_bytes ?? 0n);

      const verdict = isAssignmentAtRisk({
        soldExpiryMs,
        soldQuotaBytes,
        configSupplierExpiresAtMs: item.supplier_expires_at ? item.supplier_expires_at.getTime() : null,
        configSourceQuotaRemainingBytes: configRemaining,
        clientConsumedBytes: clientConsumed,
        nowMs,
      });
      if (!verdict.atRisk) continue;

      const ctx: SelectionContext = { category: item.category, soldExpiryMs, soldQuotaBytes, clientConsumedBytes: clientConsumed };
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

      const didRealloc = await this.prisma.$transaction(async (tx: any) => {
        const fresh = await tx.inventoryItem.findUnique({ where: { id: winner.id } });
        if (!fresh || fresh.used_resale_slots >= fresh.max_resale_slots) {
          return false; // winner filled up since selection (race) — skip, retry next pass
        }
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
        return true;
      });
      if (didRealloc) reallocated++;
    }
    return { checked: assignments.length, reallocated, failed };
  }
}
