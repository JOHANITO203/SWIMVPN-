import { AssignmentAccessStatus, InventoryHealthStatus, InventoryStatus, OrderStatus, PlanCategory } from '@prisma/client';
import { InventoryService } from '../inventory.service';
import { of } from 'rxjs';

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
  const trialConfigsCreated: unknown[] = [];
  const recoveredTrialAssignments: unknown[] = [];
  const recoveredTrialGrantUpdates: unknown[] = [];
  const recoveredTrialEvents: unknown[] = [];
  const recoveredTrialConfigLocks: unknown[] = [];
  const trialImportService = new InventoryService(
    {
      $transaction: async (callback: any) =>
        callback({
          trialCampaign: {
            findUnique: async () => ({
              id: 'trial-campaign-2026-05',
              code: 'trial-2026-05',
              duration_days: 3,
            }),
          },
          trialGrant: {
            findMany: async () => [
              {
                id: 'trial-grant-pending',
                customer_id: 'customer-trial-pending',
                customer: {
                  public_id: 'SW-TRIAL-PENDING',
                },
              },
            ],
            updateMany: async (args: unknown) => {
              recoveredTrialGrantUpdates.push(args);
              return { count: 1 };
            },
          },
          trialConfig: {
            findFirst: async () => ({
              id: 'trial-config-imported',
              supplier_expires_at: null,
            }),
            updateMany: async (args: unknown) => {
              recoveredTrialConfigLocks.push(args);
              return { count: 1 };
            },
          },
          trialAssignment: {
            create: async (args: unknown) => {
              recoveredTrialAssignments.push(args);
              return args;
            },
          },
          adminEvent: {
            create: async (args: unknown) => {
              recoveredTrialEvents.push(args);
              return args;
            },
          },
        }),
      trialCampaign: {
        findFirst: async () => ({
          id: 'trial-campaign-2026-05',
          code: 'trial-2026-05',
        }),
      },
      trialConfig: {
        create: async (args: any) => {
          trialConfigsCreated.push(args);
          return {
            id: 'trial-config-imported',
            config_type: args.data.config_type,
            display_protocol: args.data.display_protocol,
            supplier_expires_at: args.data.supplier_expires_at,
            supplier_provider_name: args.data.supplier_provider_name,
          };
        },
      },
    } as any,
    {
      send: () =>
        of({
          rawConfig: 'vless://trial-imported',
          parsedProfile: {
            validationState: 'VALID',
            protocol: 'VLESS',
          },
          metadata: {
            providerName: 'trial-provider',
          },
        }),
    } as any,
    { emit: () => undefined } as any,
    { emit: () => undefined } as any,
  );
  const trialImportResult = await trialImportService.importTrialConfigs({
    configs: ['vless://trial-imported'],
    batchName: 'trial-batch',
  });
  assert(trialConfigsCreated.length === 1, 'trial import should create one TrialConfig');
  assert(
    (trialConfigsCreated[0] as any).data.raw_config === 'vless://trial-imported',
    'trial import must preserve raw trial config',
  );
  assert(
    trialImportResult.details[0].status === 'IMPORTED',
    'valid trial config should be imported into Trial Store',
  );
  assert(
    trialImportResult.recoveredPendingCount === 1,
    'trial import should recover one pending trial grant when config capacity becomes available',
  );
  assert(
    recoveredTrialAssignments.length === 1 &&
      (recoveredTrialAssignments[0] as any).data.grant_id === 'trial-grant-pending',
    'trial import recovery should create an active TrialAssignment for pending grant',
  );
  assert(
    recoveredTrialGrantUpdates.some((args: any) =>
      args.where?.id === 'trial-grant-pending' &&
        args.where?.status === 'PENDING' &&
        args.data?.status === 'ACTIVE',
    ),
    'trial import recovery should atomically lock and activate the pending grant',
  );
  assert(
    recoveredTrialConfigLocks.some((args: any) =>
      args.where?.id === 'trial-config-imported' &&
        args.where?.status === 'AVAILABLE' &&
        args.data?.status === 'ASSIGNED',
    ),
    'trial import recovery should lock the trial config before assignment',
  );
  assert(
    recoveredTrialEvents.some((args: any) => args.data?.event_type === 'TRIAL_CONFIG_ASSIGNED'),
    'trial import recovery should emit a trial-specific assignment event',
  );

  const raceLostAssignments: unknown[] = [];
  const raceLostConfigLocks: unknown[] = [];
  const raceLostConfigReleases: unknown[] = [];
  const raceLostService = new InventoryService(
    {
      $transaction: async (callback: any) =>
        callback({
          trialCampaign: {
            findUnique: async () => ({
              id: 'trial-campaign-2026-05',
              code: 'trial-2026-05',
              duration_days: 3,
            }),
          },
          trialGrant: {
            findMany: async () => [
              {
                id: 'trial-grant-race-lost',
                customer_id: 'customer-trial-race-lost',
                customer: {
                  public_id: 'SW-TRIAL-RACE-LOST',
                },
              },
            ],
            updateMany: async () => ({ count: 0 }),
          },
          trialConfig: {
            findFirst: async () => ({
              id: 'trial-config-race-lost',
              supplier_expires_at: null,
            }),
            updateMany: async (args: any) => {
              if (args.data?.status === 'ASSIGNED') {
                raceLostConfigLocks.push(args);
                return { count: 1 };
              }
              raceLostConfigReleases.push(args);
              return { count: 1 };
            },
          },
          trialAssignment: {
            create: async (args: unknown) => {
              raceLostAssignments.push(args);
              return args;
            },
          },
          adminEvent: {
            create: async () => undefined,
          },
        }),
      trialCampaign: {
        findFirst: async () => ({
          id: 'trial-campaign-2026-05',
          code: 'trial-2026-05',
        }),
      },
      trialConfig: {
        create: async (args: any) => ({
          id: 'trial-config-race-lost',
          config_type: args.data.config_type,
          display_protocol: args.data.display_protocol,
          supplier_expires_at: args.data.supplier_expires_at,
          supplier_provider_name: args.data.supplier_provider_name,
        }),
      },
    } as any,
    {
      send: () =>
        of({
          rawConfig: 'vless://trial-race-lost',
          parsedProfile: {
            validationState: 'VALID',
            protocol: 'VLESS',
          },
          metadata: {},
        }),
    } as any,
    { emit: () => undefined } as any,
    { emit: () => undefined } as any,
  );
  const raceLostResult = await raceLostService.importTrialConfigs({
    configs: ['vless://trial-race-lost'],
  });
  assert(raceLostConfigLocks.length === 1, 'race-lost recovery should still attempt to lock one config');
  assert(
    raceLostAssignments.length === 0 &&
      raceLostResult.recoveredPendingCount === 0,
    'recovery must not create an assignment when the pending grant lock is lost',
  );
  assert(
    raceLostConfigReleases.some((args: any) =>
      args.where?.id === 'trial-config-race-lost' &&
        args.where?.status === 'ASSIGNED' &&
        args.data?.status === 'AVAILABLE',
    ),
    'recovery should release a config lock when the pending grant lock is lost',
  );

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

  const retryDeliveryEvents: any[] = [];
  const retryReplacementUpdates: Array<{ model: string; where?: any; data?: any }> = [];
  const retryReplacementEvents: any[] = [];
  const retryDeliveryService = new InventoryService(
    {
      $transaction: async (fn: any) =>
        fn({
          order: {
            findUnique: async () => ({
              id: 'order-delivery-retry',
              order_ref: 'ORD-DELIVERY-RETRY',
              status: OrderStatus.FULFILLED,
              paid_at: new Date('2026-05-17T22:53:00.000Z'),
              payment_ref: 'SWIMPAY_CONFIRMED:session:event',
              customer_id: 'customer-delivery-retry',
              created_at: new Date('2026-05-17T22:53:00.000Z'),
              plan: {
                code: PlanCategory.WEEK,
                name: 'Basic',
                duration_label: '1 week',
                quota_label: '50 GB',
              },
              customer: {
                email: 'buyer@example.com',
                phone: '+79990001122',
              },
              assignments: [
                {
                  id: 'assignment-delivery-retry',
                  access_status: AssignmentAccessStatus.ACTIVE,
                  inventory_item_id: 'inventory-delivery-retry',
                  inventory_item: {
                    raw_config: 'vless://uuid@example.com:443?security=tls#Basic',
                    supplier_expires_at: new Date('2026-06-17T22:53:00.000Z'),
                    display_protocol: 'VLESS',
                  },
                },
              ],
            }),
          },
          orderAssignment: {
            findMany: async ({ where }: any) => {
              assert(
                where?.customer_id === 'customer-delivery-retry' &&
                  where?.access_status === AssignmentAccessStatus.ACTIVE &&
                  where?.id?.not === 'assignment-delivery-retry' &&
                  where?.order?.created_at?.lt?.toISOString() === '2026-05-17T22:53:00.000Z',
                'retry fulfillment with an existing active paid assignment must query only older active assignments for replacement cleanup',
              );
              return [
                {
                  id: 'assignment-delivery-old',
                  inventory_item_id: 'inventory-delivery-old',
                  order: { order_ref: 'ORD-DELIVERY-OLD' },
                },
              ];
            },
            update: async ({ where, data }: any) => {
              retryReplacementUpdates.push({ model: 'orderAssignment', where, data });
              return { id: where.id, ...data };
            },
            aggregate: async () => ({
              _sum: {
                slot_count: 0,
                measured_used_bytes: 0n,
              },
            }),
          },
          inventoryItem: {
            findUniqueOrThrow: async ({ where }: any) => ({
              id: where.id,
              health_status: InventoryHealthStatus.HEALTHY,
              source_quota_bytes: null,
              source_used_bytes: 0n,
              supplier_expires_at: null,
              max_resale_slots: 2,
            }),
            update: async ({ where, data }: any) => {
              retryReplacementUpdates.push({ model: 'inventoryItem', where, data });
              return { id: where.id, ...data };
            },
          },
          adminEvent: {
            create: async ({ data }: any) => {
              retryReplacementEvents.push(data);
              return data;
            },
          },
        }),
    } as any,
    { send: () => ({}) } as any,
    { emit: () => undefined } as any,
    {
      emit: (event: string, payload: any) => {
        retryDeliveryEvents.push({ event, payload });
      },
    } as any,
  );

  const retryResult = await retryDeliveryService.fulfillOrder('order-delivery-retry');

  assert(retryResult.success === true, 'retry fulfillment with active assignment should succeed');
  assert(
    retryDeliveryEvents.some((entry) => entry.event === 'process_post_purchase_delivery'),
    'retry fulfillment with active assignment must re-emit post-purchase delivery',
  );
  assert(
    retryReplacementUpdates.some(
      (entry) =>
        entry.model === 'orderAssignment' &&
        entry.data?.access_status === AssignmentAccessStatus.REVOKED &&
        entry.where?.id === 'assignment-delivery-old',
    ),
    'retry fulfillment with an existing active paid assignment must revoke older active assignments',
  );
  assert(
    retryReplacementEvents.some((entry) => entry.event_type === 'CUSTOMER_ACCESS_REPLACED'),
    'retry fulfillment replacement cleanup must be audited',
  );

  const replacementUpdates: Array<{ model: string; where?: any; data?: any }> = [];
  const replacementEvents: any[] = [];
  let replacementFindManyWhere: any = null;
  const replacementService = createService({
    inventoryItem: {
      findMany: async () => [],
    },
    $transaction: async (fn: any) =>
      fn({
        order: {
          findUnique: async () => ({
            id: 'order-upgrade-new',
            order_ref: 'ORD-UPGRADE-NEW',
            status: OrderStatus.PAID,
            paid_at: new Date('2026-05-19T10:00:00.000Z'),
            payment_ref: 'SWIMPAY_CONFIRMED:session:event-new',
            customer_id: 'customer-upgrade',
            created_at: new Date('2026-05-19T10:00:00.000Z'),
            plan: {
              code: PlanCategory.QUARTER,
              name: 'Platinum',
              duration_label: '3 months',
              quota_label: '150 GB',
            },
            customer: {
              email: 'upgrade@example.com',
              phone: '+79990002233',
            },
            assignments: [],
          }),
          update: async ({ where, data }: any) => {
            replacementUpdates.push({ model: 'order', where, data });
            return {
              id: where.id,
              order_ref: 'ORD-UPGRADE-NEW',
              status: data.status,
              amount_rub: 1500,
            };
          },
        },
        orderAssignment: {
          create: async () => ({
            id: 'assignment-upgrade-new-pending',
            access_status: AssignmentAccessStatus.PENDING,
          }),
          update: async ({ where, data }: any) => {
            replacementUpdates.push({ model: 'orderAssignment', where, data });
            return {
              id: where.id,
              access_status: data.access_status,
              inventory_item_id: data.inventory_item_id,
              slot_count: data.slot_count,
            };
          },
          findMany: async ({ where }: any) => {
            replacementFindManyWhere = where;
            return [
              {
                id: 'assignment-basic-old',
                inventory_item_id: 'inventory-basic-old',
                slot_count: 1,
                order: { order_ref: 'ORD-BASIC-OLD' },
              },
              {
                id: 'assignment-premium-old',
                inventory_item_id: 'inventory-premium-old',
                slot_count: 1,
                order: { order_ref: 'ORD-PREMIUM-OLD' },
              },
            ];
          },
          aggregate: async ({ where }: any) => ({
            _sum: {
              slot_count: where?.inventory_item_id === 'inventory-platinum-new' ? 1 : 0,
              measured_used_bytes: 0n,
            },
          }),
        },
        inventoryItem: {
          findUniqueOrThrow: async ({ where }: any) => {
            if (where.id === 'inventory-platinum-new') {
              return {
                id: 'inventory-platinum-new',
                raw_config: 'vless://uuid@platinum.example:443#Platinum',
                display_protocol: 'VLESS',
                used_resale_slots: 0,
                max_resale_slots: 2,
                health_status: InventoryHealthStatus.HEALTHY,
                source_quota_bytes: 1000n * gb,
                source_used_bytes: 0n,
                supplier_expires_at: new Date('2026-08-19T10:00:00.000Z'),
              };
            }

            return {
              id: where.id,
              used_resale_slots: 1,
              max_resale_slots: 2,
              health_status: InventoryHealthStatus.HEALTHY,
              source_quota_bytes: 1000n * gb,
              source_used_bytes: 0n,
              supplier_expires_at: null,
            };
          },
          update: async ({ where, data }: any) => {
            replacementUpdates.push({ model: 'inventoryItem', where, data });
            return { id: where.id, ...data };
          },
        },
        $queryRaw: async () => [{ id: 'inventory-platinum-new' }],
        delivery: {
          findFirst: async () => null,
          create: async ({ data }: any) => {
            replacementUpdates.push({ model: 'delivery', data });
            return data;
          },
        },
        adminEvent: {
          create: async ({ data }: any) => {
            replacementEvents.push(data);
            return data;
          },
        },
      }),
  });

  const replacementResult = await replacementService.fulfillOrder('order-upgrade-new');
  assert(replacementResult.success === true, 'upgrade fulfillment should succeed');
  const revokedReplacementAssignments = replacementUpdates.filter(
    (entry) =>
      entry.model === 'orderAssignment' &&
      entry.data?.access_status === AssignmentAccessStatus.REVOKED,
  );
  assert(
    revokedReplacementAssignments.length === 2,
    'successful upgrade/downgrade fulfillment must revoke all older active assignments for the customer',
  );
  assert(
    replacementEvents.some((event) => event.event_type === 'CUSTOMER_ACCESS_REPLACED'),
    'upgrade/downgrade replacement must be audited',
  );
  assert(
    replacementFindManyWhere?.customer_id === 'customer-upgrade' &&
      replacementFindManyWhere?.access_status === AssignmentAccessStatus.ACTIVE &&
      replacementFindManyWhere?.id?.not === 'assignment-upgrade-new-pending' &&
      replacementFindManyWhere?.order?.created_at?.lt?.toISOString() === '2026-05-19T10:00:00.000Z',
    'upgrade/downgrade replacement must query only older active assignments for the same customer',
  );

  const trialReplacementUpdates: Array<{ model: string; data?: any }> = [];
  const trialReplacementService = createService({
    inventoryItem: {
      findMany: async () => [],
    },
    $transaction: async (fn: any) =>
      fn({
        order: {
          findUnique: async () => ({
            id: 'order-trial-with-paid-active',
            order_ref: 'TRIAL-SW-TRIAL-WITH-PAID-1',
            status: OrderStatus.PAID,
            paid_at: null,
            payment_ref: 'TRIAL:3D',
            customer_id: 'customer-trial-with-paid',
            plan: {
              code: PlanCategory.WEEK,
              name: 'Trial',
              duration_label: '3 days',
              quota_label: 'UNLIMITED',
            },
            customer: {
              email: 'trial-with-paid@example.com',
              phone: '+79990003344',
            },
            assignments: [],
          }),
          update: async ({ data }: any) => ({
            id: 'order-trial-with-paid-active',
            order_ref: 'TRIAL-SW-TRIAL-WITH-PAID-1',
            status: data.status,
            amount_rub: 0,
          }),
        },
        orderAssignment: {
          create: async () => ({
            id: 'assignment-trial-new-pending',
            access_status: AssignmentAccessStatus.PENDING,
          }),
          update: async ({ data }: any) => {
            trialReplacementUpdates.push({ model: 'orderAssignment', data });
            return {
              id: 'assignment-trial-new-pending',
              access_status: data.access_status,
              inventory_item_id: data.inventory_item_id,
              slot_count: data.slot_count,
            };
          },
          findMany: async () => [
            {
              id: 'assignment-paid-old',
              inventory_item_id: 'inventory-paid-old',
              order: { order_ref: 'ORD-PAID-OLD' },
            },
          ],
          aggregate: async () => ({
            _sum: {
              slot_count: 1,
              measured_used_bytes: 0n,
            },
          }),
        },
        inventoryItem: {
          findUniqueOrThrow: async ({ where }: any) => ({
            id: where.id,
            raw_config: 'vless://uuid@trial.example:443#Trial',
            display_protocol: 'VLESS',
            used_resale_slots: 0,
            max_resale_slots: 2,
            health_status: InventoryHealthStatus.HEALTHY,
            source_quota_bytes: null,
            source_used_bytes: 0n,
            supplier_expires_at: new Date('2026-05-22T10:00:00.000Z'),
          }),
          update: async ({ data }: any) => {
            trialReplacementUpdates.push({ model: 'inventoryItem', data });
            return data;
          },
        },
        $queryRaw: async () => [{ id: 'inventory-trial-new' }],
        delivery: {
          findFirst: async () => null,
          create: async ({ data }: any) => data,
        },
        adminEvent: {
          create: async ({ data }: any) => data,
        },
      }),
  });

  const trialReplacementResult = await trialReplacementService.fulfillOrder('order-trial-with-paid-active');
  assert(trialReplacementResult.success === true, 'trial fulfillment should still succeed');
  assert(
    trialReplacementUpdates.every((entry) => entry.data?.access_status !== AssignmentAccessStatus.REVOKED),
    'trial fulfillment must not revoke an existing paid access',
  );

  const pendingNoCapacityUpdates: Array<{ model: string; data?: any }> = [];
  const pendingNoCapacityService = createService({
    $transaction: async (fn: any) =>
      fn({
        order: {
          findUnique: async () => ({
            id: 'order-upgrade-pending',
            order_ref: 'ORD-UPGRADE-PENDING',
            status: OrderStatus.PAID,
            paid_at: new Date('2026-05-19T10:00:00.000Z'),
            payment_ref: 'SWIMPAY_CONFIRMED:session:event-pending',
            customer_id: 'customer-upgrade',
            plan: {
              code: PlanCategory.QUARTER,
              name: 'Platinum',
              duration_label: '3 months',
              quota_label: '150 GB',
            },
            customer: {
              email: 'upgrade@example.com',
              phone: '+79990002233',
            },
            assignments: [],
          }),
          update: async ({ data }: any) => {
            pendingNoCapacityUpdates.push({ model: 'order', data });
            return data;
          },
        },
        orderAssignment: {
          create: async () => ({
            id: 'assignment-upgrade-pending',
            access_status: AssignmentAccessStatus.PENDING,
          }),
          update: async ({ data }: any) => {
            pendingNoCapacityUpdates.push({ model: 'orderAssignment', data });
            return data;
          },
        },
        $queryRaw: async () => [],
        delivery: {
          findFirst: async () => null,
          create: async ({ data }: any) => {
            pendingNoCapacityUpdates.push({ model: 'delivery', data });
            return data;
          },
        },
        adminEvent: {
          create: async ({ data }: any) => data,
        },
      }),
  });

  const pendingNoCapacityResult = await pendingNoCapacityService.fulfillOrder('order-upgrade-pending');
  assert(pendingNoCapacityResult.pendingFulfillment === true, 'no-capacity upgrade should remain pending');
  assert(
    pendingNoCapacityUpdates.every((entry) => entry.data?.access_status !== AssignmentAccessStatus.REVOKED),
    'pending upgrade/downgrade fulfillment must not revoke existing active access before replacement succeeds',
  );

  console.log('inventory service policy tests passed');
}

main();
