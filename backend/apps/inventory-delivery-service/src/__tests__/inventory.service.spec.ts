import { AssignmentAccessStatus, InventoryHealthStatus, InventoryStatus, PlanCategory } from '@prisma/client';
import { InventoryService } from '../inventory.service';

function assert(condition: boolean, message: string) {
  if (!condition) {
    throw new Error(message);
  }
}

function createService(prisma: any) {
  return new InventoryService(
    prisma,
    { send: () => ({}) } as any,
    { emit: () => undefined } as any,
    { emit: () => undefined } as any,
  ) as any;
}

async function assertRejects(fn: () => Promise<unknown>, expectedMessage: string) {
  try {
    await fn();
  } catch (error) {
    assert(
      error instanceof Error && error.message === expectedMessage,
      `expected "${expectedMessage}", got "${error instanceof Error ? error.message : String(error)}"`,
    );
    return;
  }

  throw new Error(`expected rejection "${expectedMessage}"`);
}

const gb = 1024n * 1024n * 1024n;

async function main() {
  const healthService = createService({});
  const health = healthService.computeHealthStatus({
    currentHealth: InventoryHealthStatus.HEALTHY,
    supplierExpiresAt: null,
    usedResaleSlots: 1,
    maxResaleSlots: 2,
    sourceQuotaBytes: 10n,
    sourceUsedBytes: 10n,
  });

  assert(health === InventoryHealthStatus.FULL, 'source-exhausted configs must be FULL');

  for (const accessStatus of [
    AssignmentAccessStatus.REVOKED,
    AssignmentAccessStatus.EXPIRED,
    AssignmentAccessStatus.FAILED,
  ]) {
    const tx = {
      orderAssignment: {
        findUnique: async () => ({
          id: `assignment-${accessStatus}`,
          access_status: accessStatus,
          slot_count: 1,
          inventory_item_id: 'source-item',
          order: {
            order_ref: 'ORDER-1',
            plan: { code: PlanCategory.MONTH },
          },
        }),
        update: async () => {
          throw new Error('terminal assignment must not be updated');
        },
      },
      inventoryItem: {
        findUnique: async () => {
          throw new Error('target inventory must not be loaded for terminal assignments');
        },
      },
    };
    const service = createService({ $transaction: async (fn: any) => fn(tx) });

    await assertRejects(
      () => service.moveAssignment({ assignmentId: `assignment-${accessStatus}`, targetInventoryItemId: 'target-item' }),
      'Terminal assignment cannot be moved or reactivated',
    );
  }

  const moveQuotaTx = {
    orderAssignment: {
      findUnique: async () => ({
        id: 'assignment-source-quota',
        access_status: AssignmentAccessStatus.ACTIVE,
        measured_used_bytes: 20n * gb,
        slot_count: 1,
        inventory_item_id: 'source-item',
        order: {
          order_ref: 'ORDER-MOVE',
          plan: { code: PlanCategory.MONTH },
        },
      }),
    },
    inventoryItem: {
      findUnique: async () => ({
        id: 'target-nearly-full',
        status: InventoryStatus.AVAILABLE,
        health_status: InventoryHealthStatus.HEALTHY,
        used_resale_slots: 0,
        max_resale_slots: 2,
        source_used_bytes: 90n * gb,
        source_quota_bytes: 100n * gb,
        supplier_expires_at: null,
      }),
      update: async () => {
        throw new Error('source-exhausting move must not update target inventory');
      },
    },
  };
  const moveQuotaService = createService({ $transaction: async (fn: any) => fn(moveQuotaTx) });

  await assertRejects(
    () => moveQuotaService.moveAssignment({
      assignmentId: 'assignment-source-quota',
      targetInventoryItemId: 'target-nearly-full',
    }),
    'Target inventory item has no remaining resale capacity',
  );

  const assignmentId = 'assignment-monotone';
  const inventoryItemId = 'inventory-1';
  const existingMeasured = 60n * gb;
  const incomingMeasured = 10n * gb;
  const updates: Array<{ model: string; data: any }> = [];

  const tx = {
    order: {
      findUnique: async () => ({
        id: 'order-1',
        order_ref: 'ORDER-MONOTONE',
        payment_ref: 'PAYMENT-1',
        plan: { quota_label: '50 GB' },
        assignments: [
          {
            id: assignmentId,
            access_status: AssignmentAccessStatus.ACTIVE,
            inventory_item_id: inventoryItemId,
            measured_used_bytes: existingMeasured,
            inventory_item: {
              id: inventoryItemId,
              health_status: InventoryHealthStatus.HEALTHY,
              source_quota_bytes: 100n * gb,
              source_used_bytes: 55n * gb,
              supplier_expires_at: null,
              used_resale_slots: 1,
              max_resale_slots: 2,
            },
          },
        ],
      }),
    },
    orderAssignment: {
      update: async ({ data }: any) => {
        updates.push({ model: 'orderAssignment', data });
        return { id: assignmentId, ...data };
      },
      aggregate: async () => ({
        _sum: {
          measured_used_bytes: existingMeasured,
          slot_count: 0,
        },
      }),
    },
    inventoryItem: {
      update: async ({ data }: any) => {
        updates.push({ model: 'inventoryItem', data });
        return { id: inventoryItemId, ...data };
      },
      findUniqueOrThrow: async () => ({
        id: inventoryItemId,
        health_status: InventoryHealthStatus.HEALTHY,
        source_quota_bytes: 100n * gb,
        source_used_bytes: existingMeasured,
        supplier_expires_at: null,
        max_resale_slots: 2,
      }),
    },
    adminEvent: {
      create: async () => ({}),
    },
  };
  const usageService = createService({ $transaction: async (fn: any) => fn(tx) });

  const result = await usageService.recordAssignmentUsage({
    orderRef: 'ORDER-MONOTONE',
    measuredUsedBytes: incomingMeasured.toString(),
  });

  const measuredUpdate = updates.find((entry) => entry.model === 'orderAssignment')?.data;
  const sourceUpdate = updates.find((entry) => entry.model === 'inventoryItem')?.data;

  assert(
    measuredUpdate?.measured_used_bytes === existingMeasured,
    'assignment measured usage must not decrease when a lower measurement arrives',
  );
  assert(
    sourceUpdate?.source_used_bytes === existingMeasured,
    'source usage must be updated from monotone assignment usage before plan expiration',
  );
  assert(
    result.measuredUsedBytes === existingMeasured.toString(),
    'response must report monotone measured usage',
  );
  assert(result.planQuotaExceeded === true, 'plan quota must still expire from preserved usage');

  console.log('inventory service policy tests passed');
}

main();
