import { RpcException } from '@nestjs/microservices';
import { AssignmentAccessStatus, InventoryHealthStatus, Prisma } from '@prisma/client';
import { of, throwError } from 'rxjs';
import { CustomerService } from '../customer.service';

function assert(condition: boolean, message: string) {
  if (!condition) {
    throw new Error(message);
  }
}

async function assertRejectsWithRpcException(action: () => Promise<unknown>, message: string) {
  try {
    await action();
  } catch (error) {
    assert(error instanceof RpcException, `${message}: expected RpcException`);
    return;
  }

  throw new Error(`${message}: expected rejection`);
}

async function main() {
  const adminEvents: unknown[] = [];
  const service = new CustomerService(
    {
      adminEvent: {
        create: async (event: unknown) => {
          adminEvents.push(event);
          return event;
        },
      },
    } as any,
    {} as any,
    {} as any,
    {} as any,
  );

  await assertRejectsWithRpcException(
    () => service.activateCode({ userNumber: 'SW-TEST', code: 'SWIM-FREE-MONTH' }),
    'activation code endpoint must not grant hardcoded premium access',
  );

  assert(adminEvents.length === 1, 'rejected activation code attempts should be audited');

  assert(
    (service as any).resolveEntitlementState({
      hasCompletedProfile: true,
      hasOrder: true,
      trialEligible: false,
      fulfillmentStatus: 'DELIVERED',
      accessType: 'PAID',
      hasActiveAssignment: true,
      subscriptionExpiresAt: null,
      quotaExceeded: false,
      inventoryHealthStatus: InventoryHealthStatus.FULL,
      sourceExhausted: false,
    }) === 'ACTIVE_SUBSCRIPTION',
    'FULL inventory should block new sales without cutting existing active assignments',
  );

  assert(
    (service as any).resolveEntitlementState({
      hasCompletedProfile: true,
      hasOrder: true,
      trialEligible: false,
      fulfillmentStatus: 'DELIVERED',
      accessType: 'PAID',
      hasActiveAssignment: true,
      subscriptionExpiresAt: null,
      quotaExceeded: false,
      inventoryHealthStatus: InventoryHealthStatus.EXPIRED,
      sourceExhausted: false,
    }) === 'EXPIRED_SUBSCRIPTION',
    'expired inventory must not be treated as active premium access',
  );

  const activeOlderOrderService = new CustomerService(
    {
      customer: {
        findUnique: async () => ({
          id: 'customer-active-older',
          public_id: 'SW-ACTIVE-OLDER',
          device_id: 'device-active-older',
          email: 'active-older@example.com',
          phone: '79000000011',
          orders: [
            {
              id: 'order-new-revoked',
              order_ref: 'ORD-NEW-REVOKED',
              status: 'FULFILLED',
              plan: { code: 'WEEK', quota_label: '50 GB' },
              payment_ref: 'CARD_MANUAL:APPROVED',
              created_at: new Date('2026-05-02T00:00:00.000Z'),
              fulfilled_at: new Date('2026-05-02T00:00:00.000Z'),
              assignments: [
                {
                  id: 'assignment-new-revoked',
                  access_status: 'REVOKED',
                  inventory_item_id: 'inventory-new-revoked',
                  measured_used_bytes: 0n,
                  inventory_item: {
                    id: 'inventory-new-revoked',
                    raw_config: 'vless://revoked',
                    health_status: InventoryHealthStatus.HEALTHY,
                    supplier_expires_at: null,
                  },
                },
              ],
            },
            {
              id: 'order-old-active',
              order_ref: 'ORD-OLD-ACTIVE',
              status: 'FULFILLED',
              plan: { code: 'WEEK', quota_label: '50 GB' },
              payment_ref: 'CARD_MANUAL:APPROVED',
              created_at: new Date('2026-05-01T00:00:00.000Z'),
              fulfilled_at: new Date('2026-05-01T00:00:00.000Z'),
              assignments: [
                {
                  id: 'assignment-old-active',
                  access_status: AssignmentAccessStatus.ACTIVE,
                  inventory_item_id: 'inventory-old-active',
                  measured_used_bytes: 0n,
                  inventory_item: {
                    id: 'inventory-old-active',
                    raw_config: 'vless://active',
                    health_status: InventoryHealthStatus.HEALTHY,
                    supplier_expires_at: null,
                  },
                },
              ],
            },
          ],
        }),
      },
      order: {
        findFirst: async () => null,
      },
    } as any,
    {} as any,
    {} as any,
    {} as any,
  );
  const activeOlderProfile = await activeOlderOrderService.getProfile('SW-ACTIVE-OLDER');
  assert(
    activeOlderProfile.entitlementState === 'ACTIVE_SUBSCRIPTION',
    'a newer revoked order must not mask an older still-active paid assignment',
  );
  assert(
    activeOlderProfile.subscriptionUrl === 'vless://active',
    'profile must expose the active assignment runtime config, not the revoked one',
  );

  const deepProfileService = new CustomerService(
    {
      customer: {
        findUnique: async () => ({
          id: 'customer-deep-profile',
          public_id: 'SW-DEEP-PROFILE',
          device_id: 'device-deep-profile',
          email: 'deep-profile@example.com',
          phone: '79000000015',
          orders: [],
        }),
      },
      orderAssignment: {
        findMany: async ({ where }: any) => {
          assert(
            where?.customer_id === 'customer-deep-profile',
            'profile must query assignments directly by customer id',
          );
          return [
            {
              id: 'assignment-deep-profile',
              access_status: AssignmentAccessStatus.ACTIVE,
              inventory_item_id: 'inventory-deep-profile',
              measured_used_bytes: 0n,
              expires_at: null,
              order: {
                id: 'order-deep-profile',
                order_ref: 'ORD-DEEP-PROFILE',
                status: 'FULFILLED',
                payment_ref: 'CARD_MANUAL:APPROVED',
                created_at: new Date('2026-04-01T00:00:00.000Z'),
                fulfilled_at: new Date('2026-04-01T00:00:00.000Z'),
                plan: { code: 'MONTH', quota_label: '150 GB' },
              },
              inventory_item: {
                id: 'inventory-deep-profile',
                raw_config: 'vless://deep-active',
                health_status: InventoryHealthStatus.HEALTHY,
                source_quota_bytes: null,
                source_used_bytes: 0n,
                supplier_expires_at: null,
              },
            },
          ];
        },
      },
      order: {
        findFirst: async () => null,
        findMany: async () => [],
      },
    } as any,
    {} as any,
    {} as any,
    {} as any,
  );
  const deepProfile = await deepProfileService.getProfile('SW-DEEP-PROFILE');
  assert(
    deepProfile.entitlementState === 'ACTIVE_SUBSCRIPTION',
    'profile must expose active paid assignments even when they are outside the recent-order window',
  );
  assert(
    deepProfile.subscriptionUrl === 'vless://deep-active',
    'profile must expose runtime config for the direct active paid assignment',
  );

  const mergedAssignmentProfileService = new CustomerService(
    {
      customer: {
        findUnique: async () => ({
          id: 'customer-merged-assignment',
          public_id: 'SW-MERGED-ASSIGNMENT',
          device_id: 'device-merged-assignment',
          email: 'merged-assignment@example.com',
          phone: '79000000017',
          orders: [],
        }),
      },
      orderAssignment: {
        findMany: async () => [
          {
            id: 'assignment-same-order-expired',
            access_status: AssignmentAccessStatus.EXPIRED,
            inventory_item_id: 'inventory-expired',
            measured_used_bytes: 0n,
            expires_at: new Date('2026-05-01T00:00:00.000Z'),
            order: {
              id: 'order-same-id',
              order_ref: 'ORD-SAME-ID',
              status: 'FULFILLED',
              payment_ref: 'CARD_MANUAL:APPROVED',
              created_at: new Date('2026-04-01T00:00:00.000Z'),
              fulfilled_at: new Date('2026-04-01T00:00:00.000Z'),
              plan: { code: 'MONTH', quota_label: '150 GB' },
            },
            inventory_item: {
              id: 'inventory-expired',
              raw_config: 'vless://expired-same-order',
              health_status: InventoryHealthStatus.HEALTHY,
              source_quota_bytes: null,
              source_used_bytes: 0n,
              supplier_expires_at: null,
            },
          },
          {
            id: 'assignment-same-order-active',
            access_status: AssignmentAccessStatus.ACTIVE,
            inventory_item_id: 'inventory-active',
            measured_used_bytes: 0n,
            expires_at: null,
            order: {
              id: 'order-same-id',
              order_ref: 'ORD-SAME-ID',
              status: 'FULFILLED',
              payment_ref: 'CARD_MANUAL:APPROVED',
              created_at: new Date('2026-04-01T00:00:00.000Z'),
              fulfilled_at: new Date('2026-04-01T00:00:00.000Z'),
              plan: { code: 'MONTH', quota_label: '150 GB' },
            },
            inventory_item: {
              id: 'inventory-active',
              raw_config: 'vless://active-same-order',
              health_status: InventoryHealthStatus.HEALTHY,
              source_quota_bytes: null,
              source_used_bytes: 0n,
              supplier_expires_at: null,
            },
          },
        ],
      },
      order: {
        findFirst: async () => null,
        findMany: async () => [],
      },
    } as any,
    {} as any,
    {} as any,
    {} as any,
  );
  const mergedAssignmentProfile = await mergedAssignmentProfileService.getProfile('SW-MERGED-ASSIGNMENT');
  assert(
    mergedAssignmentProfile.entitlementState === 'ACTIVE_SUBSCRIPTION',
    'profile must merge all direct assignments for the same order before resolving entitlement',
  );
  assert(
    mergedAssignmentProfile.subscriptionUrl === 'vless://active-same-order',
    'profile must not lose the active assignment when another assignment on the same order appears first',
  );

  const paidOverTrialService = new CustomerService(
    {
      customer: {
        findUnique: async () => ({
          id: 'customer-paid-over-trial',
          public_id: 'SW-PAID-OVER-TRIAL',
          device_id: 'device-paid-over-trial',
          email: 'paid-over-trial@example.com',
          phone: '79000000016',
          orders: [
            {
              id: 'order-trial-new',
              order_ref: 'TRIAL-SW-PAID-OVER-TRIAL-1',
              status: 'FULFILLED',
              payment_ref: 'TRIAL:3D',
              created_at: new Date('2026-05-03T00:00:00.000Z'),
              fulfilled_at: new Date('2026-05-03T00:00:00.000Z'),
              plan: { code: 'WEEK', quota_label: 'UNLIMITED' },
              assignments: [
                {
                  id: 'assignment-trial-new',
                  access_status: AssignmentAccessStatus.ACTIVE,
                  inventory_item_id: 'inventory-trial-new',
                  measured_used_bytes: 0n,
                  expires_at: null,
                  inventory_item: {
                    id: 'inventory-trial-new',
                    raw_config: 'vless://trial-active',
                    health_status: InventoryHealthStatus.HEALTHY,
                    source_quota_bytes: null,
                    source_used_bytes: 0n,
                    supplier_expires_at: new Date('2026-05-06T00:00:00.000Z'),
                  },
                },
              ],
            },
            {
              id: 'order-paid-old',
              order_ref: 'ORD-PAID-OLD',
              status: 'FULFILLED',
              payment_ref: 'CARD_MANUAL:APPROVED',
              created_at: new Date('2026-05-01T00:00:00.000Z'),
              fulfilled_at: new Date('2026-05-01T00:00:00.000Z'),
              plan: { code: 'MONTH', quota_label: '150 GB' },
              assignments: [
                {
                  id: 'assignment-paid-old',
                  access_status: 'ACTIVE',
                  inventory_item_id: 'inventory-paid-old',
                  measured_used_bytes: 0n,
                  expires_at: null,
                  inventory_item: {
                    id: 'inventory-paid-old',
                    raw_config: 'vless://paid-active',
                    health_status: InventoryHealthStatus.HEALTHY,
                    source_quota_bytes: null,
                    source_used_bytes: 0n,
                    supplier_expires_at: null,
                  },
                },
              ],
            },
          ],
        }),
      },
      order: {
        findFirst: async () => ({ id: 'existing-trial' }),
      },
    } as any,
    {} as any,
    {} as any,
    {} as any,
  );
  const paidOverTrialProfile = await paidOverTrialService.getProfile('SW-PAID-OVER-TRIAL');
  assert(
    paidOverTrialProfile.entitlementState === 'ACTIVE_SUBSCRIPTION',
    'profile must prioritize active paid access over an active trial',
  );
  assert(
    paidOverTrialProfile.subscriptionUrl === 'vless://paid-active',
    'profile must expose paid runtime config when paid and trial are both active',
  );

  const activePlanQuotaService = new CustomerService(
    {
      customer: {
        findUnique: async () => ({
          id: 'customer-plan-quota',
          public_id: 'SW-PLAN-QUOTA',
          device_id: 'device-plan-quota',
          email: 'plan@example.com',
          phone: '79000000010',
          orders: [
            {
              id: 'order-plan-quota',
              order_ref: 'ORD-PLAN-QUOTA',
              status: 'FULFILLED',
              plan: {
                code: 'WEEK',
                quota_label: '50 GB',
              },
              payment_ref: 'CARD_MANUAL:APPROVED',
              created_at: new Date('2020-01-01T00:00:00.000Z'),
              fulfilled_at: new Date('2020-01-01T00:00:00.000Z'),
              assignments: [
                {
                  id: 'assignment-plan-quota',
                  access_status: 'ACTIVE',
                  inventory_item_id: 'inventory-plan-quota',
                  measured_used_bytes: 2n * 1024n * 1024n * 1024n,
                  inventory_item: {
                    id: 'inventory-plan-quota',
                    raw_config: 'https://wb.routerwb.ru/demo',
                    source_quota_bytes: 1000n * 1024n * 1024n * 1024n,
                    source_used_bytes: 7n * 1024n * 1024n * 1024n,
                    supplier_expires_at: new Date('2026-05-21T00:00:00.000Z'),
                    supplier_provider_name: 'Provider',
                    health_status: InventoryHealthStatus.HEALTHY,
                  },
                },
              ],
            },
          ],
        }),
      },
      order: {
        findFirst: async () => null,
      },
    } as any,
    {} as any,
    {} as any,
    {} as any,
  );

  const activePlanQuotaProfile = await activePlanQuotaService.getProfile('SW-PLAN-QUOTA');
  assert(
    activePlanQuotaProfile.entitlementState === 'ACTIVE_SUBSCRIPTION',
    'paid access time should be provider-managed, not expired by local plan duration',
  );
  assert(
    activePlanQuotaProfile.subscriptionExpiresAt === '2026-05-21T00:00:00.000Z',
    'paid access expiry should mirror supplier/assignment expiry when present',
  );
  assert(activePlanQuotaProfile.dataLimitGB === 50, 'customer-facing quota must come from the paid plan');
  assert(
    activePlanQuotaProfile.dataUsedBytes === (2n * 1024n * 1024n * 1024n).toString(),
    'customer-facing usage must come from the customer assignment, not supplier shared usage',
  );

  const cancellationEvents: unknown[] = [];
  const revokedAssignments: unknown[] = [];
  const cancellationService = new CustomerService(
    {
      customer: {
        findUnique: async () => ({
          id: 'customer-1',
          public_id: 'SW-1',
          device_id: 'device-1',
          email: 'user@example.com',
          phone: '79000000000',
          orders: [
            {
              id: 'order-1',
              order_ref: 'ORD-1',
              status: 'FULFILLED',
              plan: {
                code: 'WEEK',
              },
              payment_ref: 'CARD_MANUAL:APPROVED',
              created_at: new Date('2026-04-30T00:00:00.000Z'),
              fulfilled_at: new Date('2026-04-30T00:00:00.000Z'),
              assignments: [
                {
                  id: 'assignment-1',
                  access_status: 'ACTIVE',
                  inventory_item_id: 'inventory-1',
                  inventory_item: {
                    id: 'inventory-1',
                    source_quota_bytes: null,
                    source_used_bytes: 0n,
                    health_status: InventoryHealthStatus.HEALTHY,
                  },
                },
              ],
            },
          ],
        }),
      },
      order: {
        findFirst: async () => null,
      },
      adminEvent: {
        create: async (event: unknown) => {
          cancellationEvents.push(event);
          return event;
        },
      },
    } as any,
    {
      send: (pattern: unknown, payload: unknown) => {
        revokedAssignments.push({ pattern, payload });
        return of({ success: true });
      },
    } as any,
    {} as any,
    {} as any,
  );

  await (cancellationService as any).cancelCurrentSubscription({
    userNumber: 'SW-1',
    deviceId: 'device-1',
    reason: 'USER_REQUESTED_CANCEL',
  });

  assert(revokedAssignments.length === 1, 'customer cancellation must revoke through inventory service');
  const revokeRequest = revokedAssignments[0] as any;
  assert(
    revokeRequest.pattern?.cmd === 'revoke_assignment',
    'customer cancellation must use inventory revocation so resale slots are recalculated',
  );
  assert(
    revokeRequest.payload?.assignmentId === 'assignment-1',
    'customer cancellation must revoke the active assignment',
  );
  assert(cancellationEvents.length === 1, 'customer cancellation must be audited');

  const multiActiveRevocations: unknown[] = [];
  const multiActiveCancellationService = new CustomerService(
    {
      customer: {
        findUnique: async () => ({
          id: 'customer-multi-active',
          public_id: 'SW-MULTI-ACTIVE',
          device_id: 'device-multi-active',
          email: 'multi-active@example.com',
          phone: '79000000012',
          orders: [
            {
              id: 'order-current-active',
              order_ref: 'ORD-CURRENT-ACTIVE',
              status: 'FULFILLED',
              plan: { code: 'MONTH' },
              payment_ref: 'SWIMPAY_CONFIRMED:session:event',
              created_at: new Date('2026-05-03T00:00:00.000Z'),
              fulfilled_at: new Date('2026-05-03T00:00:00.000Z'),
              assignments: [
                {
                  id: 'assignment-current-active',
                  access_status: 'ACTIVE',
                  inventory_item_id: 'inventory-current-active',
                  inventory_item: {
                    id: 'inventory-current-active',
                    health_status: InventoryHealthStatus.HEALTHY,
                  },
                },
              ],
            },
            {
              id: 'order-older-active',
              order_ref: 'ORD-OLDER-ACTIVE',
              status: 'FULFILLED',
              plan: { code: 'WEEK' },
              payment_ref: 'CARD_MANUAL:APPROVED',
              created_at: new Date('2026-05-01T00:00:00.000Z'),
              fulfilled_at: new Date('2026-05-01T00:00:00.000Z'),
              assignments: [
                {
                  id: 'assignment-older-active',
                  access_status: 'ACTIVE',
                  inventory_item_id: 'inventory-older-active',
                  inventory_item: {
                    id: 'inventory-older-active',
                    health_status: InventoryHealthStatus.HEALTHY,
                  },
                },
              ],
            },
          ],
        }),
      },
      adminEvent: {
        create: async (event: unknown) => event,
      },
    } as any,
    {
      send: (pattern: unknown, payload: unknown) => {
        multiActiveRevocations.push({ pattern, payload });
        return of({ success: true });
      },
    } as any,
    {} as any,
    {} as any,
  );
  (multiActiveCancellationService as any).getProfile = async () => ({
    userNumber: 'SW-MULTI-ACTIVE',
    entitlementState: 'FREEMIUM',
    subscriptionUrl: null,
  });

  await multiActiveCancellationService.cancelCurrentSubscription({
    userNumber: 'SW-MULTI-ACTIVE',
    deviceId: 'device-multi-active',
    reason: 'CUSTOMER_CANCELLED_ALL',
  });

  assert(
    multiActiveRevocations.length === 2,
    'customer cancellation must revoke every active assignment so older access cannot reappear',
  );
  assert(
    multiActiveRevocations.some((entry: any) => entry.payload?.assignmentId === 'assignment-current-active') &&
      multiActiveRevocations.some((entry: any) => entry.payload?.assignmentId === 'assignment-older-active'),
    'customer cancellation must include both current and older active assignments',
  );

  const deepActiveRevocations: unknown[] = [];
  const deepActiveCancellationService = new CustomerService(
    {
      customer: {
        findUnique: async () => ({
          id: 'customer-deep-active',
          public_id: 'SW-DEEP-ACTIVE',
          device_id: 'device-deep-active',
          email: 'deep-active@example.com',
          phone: '79000000013',
          orders: [],
        }),
      },
      orderAssignment: {
        findMany: async ({ where }: any) => {
          assert(
            where?.customer_id === 'customer-deep-active' &&
              where?.access_status === AssignmentAccessStatus.ACTIVE,
            'cancel must query all active assignments directly by customer without relying on recent orders',
          );
          return [
            {
              id: 'assignment-deep-active',
              access_status: AssignmentAccessStatus.ACTIVE,
              inventory_item_id: 'inventory-deep-active',
              order: {
                order_ref: 'ORD-DEEP-ACTIVE',
              },
            },
          ];
        },
      },
      order: {
        findMany: async () => [],
      },
      adminEvent: {
        create: async (event: unknown) => event,
      },
    } as any,
    {
      send: (pattern: unknown, payload: unknown) => {
        deepActiveRevocations.push({ pattern, payload });
        return of({ success: true });
      },
    } as any,
    {} as any,
    {} as any,
  );
  (deepActiveCancellationService as any).getProfile = async () => ({
    userNumber: 'SW-DEEP-ACTIVE',
    entitlementState: 'FREEMIUM',
    subscriptionUrl: null,
  });

  await deepActiveCancellationService.cancelCurrentSubscription({
    userNumber: 'SW-DEEP-ACTIVE',
    deviceId: 'device-deep-active',
    reason: 'CUSTOMER_CANCELLED_DEEP_ACTIVE',
  });

  assert(
    deepActiveRevocations.length === 1 &&
      (deepActiveRevocations[0] as any).payload?.assignmentId === 'assignment-deep-active',
    'customer cancellation must revoke active assignments even when they are outside the recent-order window',
  );

  const multiPendingCancelledOrders: unknown[] = [];
  const multiPendingCancellationService = new CustomerService(
    {
      customer: {
        findUnique: async () => ({
          id: 'customer-multi-pending',
          public_id: 'SW-MULTI-PENDING',
          device_id: 'device-multi-pending',
          email: 'multi-pending@example.com',
          phone: '79000000014',
          orders: [
            {
              id: 'order-pending-one',
              order_ref: 'ORD-PENDING-ONE',
              status: 'PAID',
              assignments: [],
            },
            {
              id: 'order-pending-two',
              order_ref: 'ORD-PENDING-TWO',
              status: 'PENDING_FULFILLMENT',
              assignments: [],
            },
          ],
        }),
      },
      orderAssignment: {
        findMany: async () => [],
      },
      order: {
        findMany: async () => [
          {
            id: 'order-pending-one',
            order_ref: 'ORD-PENDING-ONE',
            status: 'PAID',
          },
          {
            id: 'order-pending-two',
            order_ref: 'ORD-PENDING-TWO',
            status: 'PENDING_FULFILLMENT',
          },
        ],
        update: async (args: unknown) => {
          multiPendingCancelledOrders.push(args);
          return args;
        },
      },
      adminEvent: {
        create: async (event: unknown) => event,
      },
    } as any,
    {} as any,
    {} as any,
    {} as any,
  );
  (multiPendingCancellationService as any).getProfile = async () => ({
    userNumber: 'SW-MULTI-PENDING',
    entitlementState: 'FREEMIUM',
    subscriptionUrl: null,
  });

  await multiPendingCancellationService.cancelCurrentSubscription({
    userNumber: 'SW-MULTI-PENDING',
    deviceId: 'device-multi-pending',
    reason: 'CUSTOMER_CANCELLED_PENDING_SET',
  });

  assert(
    multiPendingCancelledOrders.length === 2,
    'customer cancellation without active access must cancel every paid pending fulfillment order',
  );

  const revokedProfileService = new CustomerService(
    {
      customer: {
        findUnique: async () => ({
          id: 'customer-2',
          public_id: 'SW-REVOKED',
          device_id: 'device-2',
          email: 'revoked@example.com',
          phone: '79000000001',
          orders: [
            {
              id: 'order-revoked',
              order_ref: 'ORD-REVOKED',
              status: 'FULFILLED',
              plan: {
                code: 'WEEK',
              },
              payment_ref: 'CARD_MANUAL:APPROVED',
              created_at: new Date('2026-04-30T00:00:00.000Z'),
              fulfilled_at: new Date('2026-04-30T00:00:00.000Z'),
              assignments: [
                {
                  id: 'assignment-revoked',
                  access_status: 'REVOKED',
                  inventory_item_id: 'inventory-revoked',
                  revoked_at: new Date('2026-05-01T00:00:00.000Z'),
                  inventory_item: {
                    id: 'inventory-revoked',
                    source_quota_bytes: 1000n,
                    source_used_bytes: 10n,
                    supplier_expires_at: new Date('2026-05-09T00:13:00.000Z'),
                    health_status: InventoryHealthStatus.HEALTHY,
                  },
                },
              ],
            },
          ],
        }),
      },
      order: {
        findFirst: async () => null,
      },
    } as any,
    {} as any,
    {} as any,
    {} as any,
  );

  const revokedProfile = await revokedProfileService.getProfile('SW-REVOKED');
  assert(
    revokedProfile.entitlementState === 'FREEMIUM',
    'revoked subscriptions should return freemium, not expired subscription',
  );
  assert(revokedProfile.accessType === 'NONE', 'revoked subscriptions should not keep paid access type');
  assert(revokedProfile.offerCode === null, 'revoked subscriptions should not keep paid offer badge');
  assert(revokedProfile.subscriptionExpiresAt === null, 'revoked subscriptions should not show remaining paid days');

  const expiredPaidProfileService = new CustomerService(
    {
      customer: {
        findUnique: async () => ({
          id: 'customer-expired-paid',
          public_id: 'SW-EXPIRED-PAID',
          device_id: 'device-expired-paid',
          email: 'expired-paid@example.com',
          phone: '79000000004',
          orders: [
            {
              id: 'order-expired-paid',
              order_ref: 'ORD-EXPIRED-PAID',
              status: 'FULFILLED',
              plan: {
                code: 'MONTH',
                quota_label: '50 GB',
              },
              payment_ref: 'CARD_MANUAL:APPROVED',
              created_at: new Date('2026-04-01T00:00:00.000Z'),
              fulfilled_at: new Date('2026-04-01T00:00:00.000Z'),
              assignments: [
                {
                  id: 'assignment-expired-paid',
                  access_status: 'EXPIRED',
                  inventory_item_id: 'inventory-expired-paid',
                  expires_at: new Date('2026-04-30T00:00:00.000Z'),
                  measured_used_bytes: 1n,
                  inventory_item: {
                    id: 'inventory-expired-paid',
                    raw_config: 'vless://uuid@expired-paid.example:443#ExpiredPaid',
                    source_quota_bytes: 1000n,
                    source_used_bytes: 10n,
                    supplier_expires_at: new Date('2026-05-10T00:00:00.000Z'),
                    supplier_provider_name: 'Provider',
                    health_status: InventoryHealthStatus.HEALTHY,
                  },
                },
              ],
            },
          ],
        }),
      },
      order: {
        findFirst: async () => null,
      },
    } as any,
    {} as any,
    {} as any,
    {} as any,
  );

  const expiredPaidProfile = await expiredPaidProfileService.getProfile('SW-EXPIRED-PAID');
  assert(
    expiredPaidProfile.entitlementState === 'EXPIRED_SUBSCRIPTION',
    'latest expired paid assignment should be represented as expired subscription',
  );
  assert(expiredPaidProfile.status === 'EXPIRED', 'expired paid assignment should keep legacy expired status');
  assert(expiredPaidProfile.subscriptionUrl === null, 'expired paid assignment must not expose runtime config');

  const expiredTrialProfileService = new CustomerService(
    {
      customer: {
        findUnique: async () => ({
          id: 'customer-expired-trial',
          public_id: 'SW-EXPIRED-TRIAL',
          device_id: 'device-expired-trial',
          email: 'expired-trial@example.com',
          phone: '79000000005',
          orders: [
            {
              id: 'order-expired-trial',
              order_ref: 'TRIAL-SW-EXPIRED-TRIAL-1',
              status: 'FULFILLED',
              plan: {
                code: 'WEEK',
                quota_label: '7 GB',
              },
              payment_ref: 'TRIAL:3D',
              created_at: new Date('2026-04-25T00:00:00.000Z'),
              fulfilled_at: new Date('2026-04-25T00:00:00.000Z'),
              assignments: [
                {
                  id: 'assignment-expired-trial',
                  access_status: 'EXPIRED',
                  inventory_item_id: 'inventory-expired-trial',
                  expires_at: new Date('2026-04-28T00:00:00.000Z'),
                  measured_used_bytes: 0n,
                  inventory_item: {
                    id: 'inventory-expired-trial',
                    raw_config: 'vless://uuid@expired-trial.example:443#ExpiredTrial',
                    source_quota_bytes: null,
                    source_used_bytes: 0n,
                    supplier_expires_at: new Date('2026-05-10T00:00:00.000Z'),
                    supplier_provider_name: 'Provider',
                    health_status: InventoryHealthStatus.HEALTHY,
                  },
                },
              ],
            },
          ],
        }),
      },
      order: {
        findFirst: async () => ({ id: 'existing-trial' }),
      },
    } as any,
    {} as any,
    {} as any,
    {} as any,
  );

  const expiredTrialProfile = await expiredTrialProfileService.getProfile('SW-EXPIRED-TRIAL');
  assert(
    expiredTrialProfile.entitlementState === 'EXPIRED_TRIAL',
    'latest expired trial assignment should be represented as expired trial',
  );
  assert(expiredTrialProfile.status === 'EXPIRED', 'expired trial assignment should keep legacy expired status');
  assert(expiredTrialProfile.subscriptionUrl === null, 'expired trial assignment must not expose runtime config');

  const paidPendingProfileService = new CustomerService(
    {
      customer: {
        findUnique: async () => ({
          id: 'customer-3',
          public_id: 'SW-PAID-PENDING',
          device_id: 'device-3',
          email: 'paid-pending@example.com',
          phone: '79000000002',
          orders: [
            {
              id: 'order-paid-pending',
              order_ref: 'ORD-PAID-PENDING',
              status: 'PAID',
              plan: {
                code: 'WEEK',
              },
              payment_ref: 'CARD_MANUAL:APPROVED',
              created_at: new Date('2026-04-30T00:00:00.000Z'),
              fulfilled_at: null,
              assignments: [],
            },
          ],
        }),
      },
      order: {
        findFirst: async () => null,
      },
    } as any,
    {} as any,
    {} as any,
    {} as any,
  );

  const paidPendingProfile = await paidPendingProfileService.getProfile('SW-PAID-PENDING');
  assert(
    paidPendingProfile.entitlementState === 'PENDING_FULFILLMENT',
    'paid orders without assignment should remain pending fulfillment, not disappear into freemium',
  );

  const trialActiveWithPaidPendingService = new CustomerService(
    {
      customer: {
        findUnique: async () => ({
          id: 'customer-trial-active-paid-pending',
          public_id: 'SW-TRIAL-ACTIVE-PAID-PENDING',
          device_id: 'device-trial-active-paid-pending',
          email: 'trial-active-paid-pending@example.com',
          phone: '79000000018',
          orders: [
            {
              id: 'order-trial-active',
              order_ref: 'TRIAL-SW-TRIAL-ACTIVE-PAID-PENDING-1',
              status: 'FULFILLED',
              payment_ref: 'TRIAL:3D',
              created_at: new Date(Date.now() - 60 * 60 * 1000),
              fulfilled_at: new Date(Date.now() - 60 * 60 * 1000),
              plan: { code: 'WEEK', quota_label: 'UNLIMITED' },
              assignments: [
                {
                  id: 'assignment-trial-active-paid-pending',
                  access_status: AssignmentAccessStatus.ACTIVE,
                  inventory_item_id: 'inventory-trial-active-paid-pending',
                  measured_used_bytes: 0n,
                  expires_at: new Date(Date.now() + 60_000),
                  inventory_item: {
                    id: 'inventory-trial-active-paid-pending',
                    raw_config: 'vless://trial-active-paid-pending',
                    health_status: InventoryHealthStatus.HEALTHY,
                    source_quota_bytes: null,
                    source_used_bytes: 0n,
                    supplier_expires_at: new Date(Date.now() + 60_000),
                  },
                },
              ],
            },
            {
              id: 'order-paid-pending-while-trial-active',
              order_ref: 'ORD-PAID-PENDING-WHILE-TRIAL-ACTIVE',
              status: 'PENDING_FULFILLMENT',
              payment_ref: 'SWIMPAY_CONFIRMED:session:event',
              created_at: new Date('2026-05-02T00:00:00.000Z'),
              fulfilled_at: null,
              plan: { code: 'MONTH', quota_label: '150 GB' },
              assignments: [],
            },
          ],
        }),
      },
      order: {
        findFirst: async () => ({ id: 'existing-trial' }),
      },
    } as any,
    {} as any,
    {} as any,
    {} as any,
  );
  const trialActiveWithPaidPendingProfile =
    await trialActiveWithPaidPendingService.getProfile('SW-TRIAL-ACTIVE-PAID-PENDING');
  assert(
    trialActiveWithPaidPendingProfile.entitlementState === 'ACTIVE_TRIAL',
    'active trial must remain usable while paid fulfillment is pending',
  );
  assert(
    trialActiveWithPaidPendingProfile.fulfillmentStatus === 'PENDING_FULFILLMENT',
    'paid pending fulfillment should remain visible while active trial runtime stays usable',
  );
  assert(
    trialActiveWithPaidPendingProfile.subscriptionUrl === 'vless://trial-active-paid-pending',
    'paid pending must not hide the active trial runtime config before paid fulfillment succeeds',
  );

  let pendingCancellationFindCount = 0;
  const cancelledOrders: unknown[] = [];
  const pendingCancellationEvents: unknown[] = [];
  const pendingCancellationService = new CustomerService(
    {
      customer: {
        findUnique: async () => {
          pendingCancellationFindCount += 1;
          return {
            id: 'customer-4',
            public_id: 'SW-PENDING-CANCEL',
            device_id: 'device-4',
            email: 'pending-cancel@example.com',
            phone: '79000000003',
            orders: pendingCancellationFindCount === 1
              ? [
                  {
                    id: 'order-pending-cancel',
                    order_ref: 'ORD-PENDING-CANCEL',
                    status: 'PAID',
                    plan: {
                      code: 'WEEK',
                    },
                    payment_ref: 'CARD_MANUAL:APPROVED',
                    created_at: new Date('2026-04-30T00:00:00.000Z'),
                    fulfilled_at: null,
                    assignments: [],
                  },
                ]
              : [
                  {
                    id: 'order-pending-cancel',
                    order_ref: 'ORD-PENDING-CANCEL',
                    status: 'CANCELLED',
                    plan: {
                      code: 'WEEK',
                    },
                    payment_ref: 'CARD_MANUAL:APPROVED',
                    created_at: new Date('2026-04-30T00:00:00.000Z'),
                    fulfilled_at: null,
                    assignments: [],
                  },
                ],
          };
        },
      },
      order: {
        findFirst: async () => null,
        update: async (args: unknown) => {
          cancelledOrders.push(args);
          return args;
        },
      },
      adminEvent: {
        create: async (event: unknown) => {
          pendingCancellationEvents.push(event);
          return event;
        },
      },
    } as any,
    {} as any,
    {} as any,
    {} as any,
  );

  const pendingCancellationProfile = await (pendingCancellationService as any).cancelCurrentSubscription({
    userNumber: 'SW-PENDING-CANCEL',
    deviceId: 'device-4',
    reason: 'CUSTOMER_CANCELLED_PENDING',
  });
  assert(cancelledOrders.length === 1, 'cancelling pending fulfillment must close the pending paid order');
  assert((cancelledOrders[0] as any).data?.status === 'CANCELLED', 'pending paid order should become CANCELLED');
  assert(
    pendingCancellationProfile.entitlementState === 'FREEMIUM',
    'cancelling pending fulfillment should return to freemium/standard state',
  );
  assert(
    pendingCancellationEvents.length === 1 &&
      (pendingCancellationEvents[0] as any).data?.event_type === 'CUSTOMER_PENDING_ORDER_CANCELLED',
    'cancelling pending fulfillment must be audited',
  );

  const fulfillmentFailureEvents: unknown[] = [];
  const fulfillmentFailureService = new CustomerService(
    {
      order: {
        findUnique: async () => ({
          id: 'order-fail',
          order_ref: 'ORD-FAIL',
          status: 'PENDING',
          paid_at: null,
          payment_ref: null,
        }),
        update: async (_args: unknown) => ({ id: 'order-fail' }),
      },
      adminEvent: {
        create: async (event: unknown) => {
          fulfillmentFailureEvents.push(event);
          return event;
        },
      },
    } as any,
    {
      send: () => throwError(() => new Error('Inventory service unavailable')),
    } as any,
    {} as any,
    {} as any,
  );

  const fulfillmentFailure = await (fulfillmentFailureService as any).approveManualCardPayment({
    orderRef: 'ORD-FAIL',
    paymentRef: 'CARD_MANUAL:APPROVED:test-proof',
    proofEventId: 'test-proof',
  });
  assert(
    fulfillmentFailure.success === true && fulfillmentFailure.pendingFulfillment === true,
    'approved manual payment with failed fulfillment should remain approved and pending fulfillment',
  );
  assert(
    fulfillmentFailure.fulfillmentError.includes('Inventory service unavailable'),
    'failed fulfillment should expose the concrete inventory error',
  );
  assert(
    fulfillmentFailureEvents.some((event: any) => event.data?.event_type === 'FULFILLMENT_FAILED'),
    'failed fulfillment should be audited',
  );

  const structuredFulfillmentFailureEvents: unknown[] = [];
  const structuredFulfillmentFailureService = new CustomerService(
    {
      order: {
        findUnique: async () => ({
          id: 'order-structured-fail',
          order_ref: 'ORD-STRUCTURED-FAIL',
          status: 'PENDING',
          paid_at: null,
          payment_ref: null,
        }),
        update: async (_args: unknown) => ({ id: 'order-structured-fail' }),
      },
      adminEvent: {
        create: async (event: unknown) => {
          structuredFulfillmentFailureEvents.push(event);
          return event;
        },
      },
    } as any,
    {
      send: () => of({ success: false, error: 'Order not found or not in fulfillable state' }),
    } as any,
    {} as any,
    {} as any,
  );

  const structuredFulfillmentFailure = await (structuredFulfillmentFailureService as any).approveManualCardPayment({
    orderRef: 'ORD-STRUCTURED-FAIL',
    paymentRef: 'CARD_MANUAL:APPROVED:test-proof-structured',
    proofEventId: 'test-proof-structured',
  });
  assert(
    structuredFulfillmentFailure.success === true &&
      structuredFulfillmentFailure.pendingFulfillment === true,
    'structured inventory fulfillment failures should keep the approved payment pending fulfillment',
  );
  assert(
    structuredFulfillmentFailure.fulfillmentError.includes('Order not found or not in fulfillable state'),
    'structured inventory fulfillment failures should not be reduced to Internal server error',
  );
  assert(
    structuredFulfillmentFailureEvents.some((event: any) => event.data?.event_type === 'FULFILLMENT_FAILED'),
    'structured inventory fulfillment failures should be audited',
  );

  const activePaidTrialBlockService = new CustomerService(
    {
      customer: {
        findUnique: async () => ({
          id: 'customer-active-paid-trial-block',
          public_id: 'SW-ACTIVE-PAID-TRIAL-BLOCK',
          device_id: 'device-active-paid-trial-block',
          email: 'paid-block@example.com',
          phone: '79000000005',
        }),
        update: async (args: unknown) => args,
      },
      order: {
        create: async () => {
          throw new Error('trial order must not be created while paid access is active');
        },
      },
    } as any,
    {} as any,
    {} as any,
    {} as any,
  );
  (activePaidTrialBlockService as any).getProfile = async () => ({
    userNumber: 'SW-ACTIVE-PAID-TRIAL-BLOCK',
    entitlementState: 'ACTIVE_SUBSCRIPTION',
  });
  await assertRejectsWithRpcException(
    () =>
      activePaidTrialBlockService.activateTrial({
        userNumber: 'SW-ACTIVE-PAID-TRIAL-BLOCK',
        deviceId: 'device-active-paid-trial-block',
        email: 'paid-block@example.com',
        phone: '79000000005',
      }),
    'trial activation must not create a parallel trial when paid access is active',
  );

  const pendingPaidTrialBlockService = new CustomerService(
    {
      customer: {
        findUnique: async () => ({
          id: 'customer-pending-paid-trial-block',
          public_id: 'SW-PENDING-PAID-TRIAL-BLOCK',
          device_id: 'device-pending-paid-trial-block',
          email: 'pending-paid-block@example.com',
          phone: '79000000005',
        }),
        update: async (args: unknown) => args,
      },
      order: {
        findFirst: async ({ where }: any) => {
          assert(
            where?.customer_id === 'customer-pending-paid-trial-block' &&
              where?.status?.in?.includes('PAID') &&
              where?.status?.in?.includes('PENDING_FULFILLMENT'),
            'trial activation must check for paid orders still waiting on fulfillment',
          );
          return {
            id: 'order-paid-pending-trial-block',
            order_ref: 'ORD-PAID-PENDING-TRIAL-BLOCK',
            payment_ref: 'SWIMPAY_SESSION:session:order',
            status: 'PENDING_FULFILLMENT',
          };
        },
        create: async () => {
          throw new Error('trial order must not be created while paid fulfillment is pending');
        },
      },
    } as any,
    {} as any,
    {} as any,
    {} as any,
  );
  (pendingPaidTrialBlockService as any).getProfile = async () => ({
    userNumber: 'SW-PENDING-PAID-TRIAL-BLOCK',
    entitlementState: 'FREEMIUM',
  });
  await assertRejectsWithRpcException(
    () =>
      pendingPaidTrialBlockService.activateTrial({
        userNumber: 'SW-PENDING-PAID-TRIAL-BLOCK',
        deviceId: 'device-pending-paid-trial-block',
        email: 'pending-paid-block@example.com',
        phone: '79000000005',
      }),
    'trial activation must not create a parallel trial when paid fulfillment is pending',
  );

  const pendingPaidProfileUpdates: unknown[] = [];
  const pendingPaidNoProfileMutationService = new CustomerService(
    {
      customer: {
        findUnique: async () => ({
          id: 'customer-pending-paid-no-profile-mutation',
          public_id: 'SW-PENDING-PAID-NO-PROFILE-MUTATION',
          device_id: 'device-pending-paid-no-profile-mutation',
          email: 'original@example.com',
          phone: '79000000006',
        }),
        update: async (args: unknown) => {
          pendingPaidProfileUpdates.push(args);
          return args;
        },
      },
      order: {
        findFirst: async () => ({
          id: 'order-paid-pending-no-profile-mutation',
          order_ref: 'ORD-PAID-PENDING-NO-PROFILE-MUTATION',
          payment_ref: 'SWIMPAY_SESSION:session:order',
          status: 'PAID',
        }),
      },
    } as any,
    {} as any,
    {} as any,
    {} as any,
  );
  (pendingPaidNoProfileMutationService as any).getProfile = async () => ({
    userNumber: 'SW-PENDING-PAID-NO-PROFILE-MUTATION',
    entitlementState: 'FREEMIUM',
  });
  await assertRejectsWithRpcException(
    () =>
      pendingPaidNoProfileMutationService.activateTrial({
        userNumber: 'SW-PENDING-PAID-NO-PROFILE-MUTATION',
        deviceId: 'device-pending-paid-no-profile-mutation',
        email: 'changed@example.com',
        phone: '79000000007',
      }),
    'trial activation refusal on paid pending must not mutate profile contact fields',
  );
  assert(
    pendingPaidProfileUpdates.length === 0,
    'paid pending trial refusal must happen before customer email/phone update',
  );

  const trialFailureGrantCreates: unknown[] = [];
  const trialFailureEvents: unknown[] = [];
  const trialFailureCampaign = {
    id: 'trial-campaign-2026-05',
    code: 'trial-2026-05',
    duration_days: 3,
  };
  const trialFulfillmentFailureService = new CustomerService(
    {
      $transaction: async (callback: any) =>
        callback({
          trialCampaign: {
            findFirst: async () => trialFailureCampaign,
          },
          trialGrant: {
            findFirst: async () => null,
            create: async (args: unknown) => {
              trialFailureGrantCreates.push(args);
              return { id: 'trial-grant-fail', ...((args as any).data || {}) };
            },
          },
          trialConfig: {
            findFirst: async () => null,
          },
          adminEvent: {
            create: async (event: unknown) => {
              trialFailureEvents.push(event);
              return event;
            },
          },
        }),
      customer: {
        findUnique: async () => ({
          id: 'customer-trial-fail',
          public_id: 'SW-TRIAL-FAIL',
          device_id: 'device-trial-fail',
          email: 'trial-fail@example.com',
          phone: '79000000006',
        }),
        update: async (args: unknown) => args,
      },
      order: {
        findFirst: async () => null,
      },
      trialCampaign: {
        findFirst: async () => trialFailureCampaign,
      },
      trialGrant: {
        findFirst: async () => null,
      },
    } as any,
    {
      send: () => {
        throw new Error('trial activation must not call paid inventory fulfillment');
      },
    } as any,
    {} as any,
    {} as any,
  );
  (trialFulfillmentFailureService as any).getProfile = async () => ({
    userNumber: 'SW-TRIAL-FAIL',
    entitlementState: 'PENDING_FULFILLMENT',
  });

  const trialFailureProfile = await trialFulfillmentFailureService.activateTrial({
    userNumber: 'SW-TRIAL-FAIL',
    deviceId: 'device-trial-fail',
    email: 'trial-fail@example.com',
    phone: '79000000006',
  });
  assert(
    trialFailureProfile.entitlementState === 'PENDING_FULFILLMENT',
    'trial store without capacity should return pending fulfillment profile',
  );
  assert(
    trialFailureGrantCreates.some((args: any) => args.data?.status === 'PENDING'),
    'trial store without capacity should create a pending trial grant instead of a paid order',
  );
  assert(
    trialFailureGrantCreates.some((args: any) =>
      args.data?.identity_email === 'trial-fail@example.com' &&
        args.data?.identity_phone === '79000000006' &&
        args.data?.identity_device_id === 'device-trial-fail',
    ),
    'trial grant must persist normalized identity snapshots for race-proof campaign uniqueness',
  );
  assert(
    trialFailureEvents.some((event: any) => event.data?.event_type === 'TRIAL_PENDING_NO_CAPACITY'),
    'trial store without capacity should be audited with a trial-specific event',
  );

  const structuredTrialConfigUpdates: unknown[] = [];
  const structuredTrialFulfillmentFailureService = new CustomerService(
    {
      $transaction: async (callback: any) =>
        callback({
          trialCampaign: {
            findFirst: async () => trialFailureCampaign,
          },
          trialGrant: {
            findFirst: async () => null,
            create: async (args: unknown) => ({ id: 'trial-grant-structured', ...((args as any).data || {}) }),
            update: async (args: unknown) => ({ id: 'trial-grant-structured', ...((args as any).data || {}) }),
          },
          trialConfig: {
            findFirst: async () => ({
              id: 'trial-config-structured',
              raw_config: 'vless://trial-store',
              supplier_expires_at: null,
            }),
            updateMany: async (args: unknown) => {
              structuredTrialConfigUpdates.push(args);
              return { count: 1 };
            },
          },
          trialAssignment: {
            create: async (args: unknown) => args,
          },
          adminEvent: {
            create: async (event: unknown) => event,
          },
        }),
      customer: {
        findUnique: async () => ({
          id: 'customer-trial-structured-fail',
          public_id: 'SW-TRIAL-STRUCTURED-FAIL',
          device_id: 'device-trial-structured-fail',
          email: 'trial-structured-fail@example.com',
          phone: '79000000007',
        }),
        update: async (args: unknown) => args,
      },
      order: {
        findFirst: async () => null,
      },
      trialCampaign: {
        findFirst: async () => trialFailureCampaign,
      },
      trialGrant: {
        findFirst: async () => null,
      },
    } as any,
    {
      send: () => {
        throw new Error('trial activation must not call paid inventory fulfillment');
      },
    } as any,
    {} as any,
    {} as any,
  );
  (structuredTrialFulfillmentFailureService as any).getProfile = async () => ({
    userNumber: 'SW-TRIAL-STRUCTURED-FAIL',
    entitlementState: 'PENDING_FULFILLMENT',
  });

  await structuredTrialFulfillmentFailureService.activateTrial({
    userNumber: 'SW-TRIAL-STRUCTURED-FAIL',
    deviceId: 'device-trial-structured-fail',
    email: 'trial-structured-fail@example.com',
    phone: '79000000007',
  });
  assert(
    structuredTrialConfigUpdates.some((args: any) => args.data?.status === 'ASSIGNED'),
    'trial store config assignment should reserve a trial config without using paid inventory',
  );

  const futureTrialExpiry = new Date(Date.now() + 24 * 60 * 60 * 1000);
  const trialStoreProfileService = new CustomerService(
    {
      customer: {
        findUnique: async () => ({
          id: 'customer-trial-store-active',
          public_id: 'SW-TRIAL-STORE-ACTIVE',
          device_id: 'device-trial-store-active',
          email: 'trial-store-active@example.com',
          phone: '79000000009',
          orders: [],
        }),
      },
      order: {
        findFirst: async () => null,
        findMany: async () => [],
      },
      orderAssignment: {
        findMany: async () => [],
      },
      trialCampaign: {
        findFirst: async () => trialFailureCampaign,
      },
      trialGrant: {
        findFirst: async () => ({
          id: 'trial-grant-active',
          customer_id: 'customer-trial-store-active',
          campaign_id: trialFailureCampaign.id,
          status: 'ACTIVE',
          started_at: new Date(),
          expires_at: futureTrialExpiry,
          assignments: [
            {
              id: 'trial-assignment-active',
              status: 'ACTIVE',
              revoked_at: null,
              expires_at: futureTrialExpiry,
              trial_config: {
                id: 'trial-config-active',
                raw_config: 'vless://trial-store-active',
                status: 'ASSIGNED',
                supplier_provider_name: 'trial-store',
                supplier_expires_at: futureTrialExpiry,
              },
            },
          ],
        }),
      },
    } as any,
    {} as any,
    {} as any,
    {} as any,
  );

  const trialStoreProfile = await trialStoreProfileService.getProfile('SW-TRIAL-STORE-ACTIVE');
  assert(
    trialStoreProfile.entitlementState === 'ACTIVE_TRIAL',
    'trial store active grant should resolve to ACTIVE_TRIAL',
  );
  assert(
    trialStoreProfile.subscriptionUrl === 'vless://trial-store-active',
    'trial store active grant should expose its own runtime config',
  );

  const incompleteTrialStoreProfileService = new CustomerService(
    {
      customer: {
        findUnique: async () => ({
          id: 'customer-trial-store-incomplete',
          public_id: 'SW-TRIAL-STORE-INCOMPLETE',
          device_id: 'device-trial-store-incomplete',
          email: null,
          phone: '79000000021',
          orders: [],
        }),
      },
      order: {
        findFirst: async () => null,
        findMany: async () => [],
      },
      orderAssignment: {
        findMany: async () => [],
      },
      trialCampaign: {
        findFirst: async () => trialFailureCampaign,
      },
      trialGrant: {
        findFirst: async () => ({
          id: 'trial-grant-incomplete',
          customer_id: 'customer-trial-store-incomplete',
          campaign_id: trialFailureCampaign.id,
          status: 'ACTIVE',
          started_at: new Date(),
          expires_at: futureTrialExpiry,
          assignments: [
            {
              id: 'trial-assignment-incomplete',
              status: 'ACTIVE',
              revoked_at: null,
              expires_at: futureTrialExpiry,
              trial_config: {
                id: 'trial-config-incomplete',
                raw_config: 'vless://trial-store-incomplete',
                status: 'ASSIGNED',
                supplier_provider_name: 'trial-store',
                supplier_expires_at: futureTrialExpiry,
              },
            },
          ],
        }),
      },
    } as any,
    {} as any,
    {} as any,
    {} as any,
  );

  const incompleteTrialStoreProfile =
    await incompleteTrialStoreProfileService.getProfile('SW-TRIAL-STORE-INCOMPLETE');
  assert(
    incompleteTrialStoreProfile.entitlementState === 'PROFILE_INCOMPLETE',
    'profile completion must remain authoritative over an active trial store grant',
  );
  assert(
    incompleteTrialStoreProfile.subscriptionUrl === null,
    'profile incomplete trial store profile must not expose runtime config',
  );

  const disabledTrialConfigProfileService = new CustomerService(
    {
      customer: {
        findUnique: async () => ({
          id: 'customer-trial-store-disabled',
          public_id: 'SW-TRIAL-STORE-DISABLED',
          device_id: 'device-trial-store-disabled',
          email: 'trial-store-disabled@example.com',
          phone: '79000000019',
          orders: [],
        }),
      },
      order: {
        findFirst: async () => null,
        findMany: async () => [],
      },
      orderAssignment: {
        findMany: async () => [],
      },
      trialCampaign: {
        findFirst: async () => trialFailureCampaign,
      },
      trialGrant: {
        findFirst: async () => ({
          id: 'trial-grant-disabled',
          customer_id: 'customer-trial-store-disabled',
          campaign_id: trialFailureCampaign.id,
          status: 'ACTIVE',
          started_at: new Date(),
          expires_at: futureTrialExpiry,
          assignments: [
            {
              id: 'trial-assignment-disabled',
              status: 'ACTIVE',
              revoked_at: null,
              expires_at: futureTrialExpiry,
              trial_config: {
                id: 'trial-config-disabled',
                raw_config: 'vless://disabled-trial-store',
                status: 'DISABLED',
                supplier_provider_name: 'trial-store',
                supplier_expires_at: futureTrialExpiry,
              },
            },
          ],
        }),
      },
    } as any,
    {} as any,
    {} as any,
    {} as any,
  );

  const disabledTrialConfigProfile =
    await disabledTrialConfigProfileService.getProfile('SW-TRIAL-STORE-DISABLED');
  assert(
    disabledTrialConfigProfile.entitlementState === 'EXPIRED_TRIAL',
    'disabled trial config must not keep an active trial entitlement',
  );
  assert(
    disabledTrialConfigProfile.subscriptionUrl === null,
    'disabled trial config must not expose its raw runtime config',
  );

  const supplierExpiredTrialConfigProfileService = new CustomerService(
    {
      customer: {
        findUnique: async () => ({
          id: 'customer-trial-store-supplier-expired',
          public_id: 'SW-TRIAL-STORE-SUPPLIER-EXPIRED',
          device_id: 'device-trial-store-supplier-expired',
          email: 'trial-store-supplier-expired@example.com',
          phone: '79000000021',
          orders: [],
        }),
      },
      order: {
        findFirst: async () => null,
        findMany: async () => [],
      },
      orderAssignment: {
        findMany: async () => [],
      },
      trialCampaign: {
        findFirst: async () => trialFailureCampaign,
      },
      trialGrant: {
        findFirst: async () => ({
          id: 'trial-grant-supplier-expired',
          customer_id: 'customer-trial-store-supplier-expired',
          campaign_id: trialFailureCampaign.id,
          status: 'ACTIVE',
          started_at: new Date(Date.now() - 60_000),
          assigned_at: new Date(Date.now() - 60_000),
          expires_at: new Date(Date.now() + 24 * 60 * 60 * 1000),
          assignments: [
            {
              id: 'trial-assignment-supplier-expired',
              status: 'ACTIVE',
              revoked_at: null,
              assigned_at: new Date(Date.now() - 60_000),
              expires_at: new Date(Date.now() + 24 * 60 * 60 * 1000),
              trial_config: {
                id: 'trial-config-supplier-expired',
                raw_config: 'vless://supplier-expired-trial-store',
                status: 'ASSIGNED',
                supplier_provider_name: 'trial-store',
                supplier_expires_at: new Date(Date.now() - 30_000),
              },
            },
          ],
        }),
        updateMany: async () => ({ count: 1 }),
      },
      trialAssignment: {
        updateMany: async () => ({ count: 1 }),
      },
      adminEvent: {
        create: async (args: unknown) => args,
      },
    } as any,
    {} as any,
    {} as any,
    {} as any,
  );

  const supplierExpiredTrialConfigProfile =
    await supplierExpiredTrialConfigProfileService.getProfile('SW-TRIAL-STORE-SUPPLIER-EXPIRED');
  assert(
    supplierExpiredTrialConfigProfile.entitlementState === 'EXPIRED_TRIAL',
    'supplier-expired trial config must expire the trial profile even when grant expiry is later',
  );
  assert(
    supplierExpiredTrialConfigProfile.subscriptionUrl === null,
    'supplier-expired trial config must not expose its raw runtime config',
  );

  const paidOverTrialStoreProfileService = new CustomerService(
    {
      customer: {
        findUnique: async () => ({
          id: 'customer-paid-over-trial-store',
          public_id: 'SW-PAID-OVER-TRIAL-STORE',
          device_id: 'device-paid-over-trial-store',
          email: 'paid-over-trial-store@example.com',
          phone: '79000000010',
          orders: [
            {
              id: 'order-paid-over-trial-store',
              order_ref: 'ORD-PAID-OVER-TRIAL-STORE',
              payment_ref: 'SWIMPAY_CONFIRMED:test',
              status: 'FULFILLED',
              created_at: new Date(),
              fulfilled_at: new Date(),
              plan: {
                code: 'MONTH',
                quota_label: '100 GB',
              },
              assignments: [
                {
                  id: 'assignment-paid-over-trial-store',
                  inventory_item_id: 'inventory-paid-over-trial-store',
                  access_status: AssignmentAccessStatus.ACTIVE,
                  measured_used_bytes: BigInt(0),
                  expires_at: futureTrialExpiry,
                  inventory_item: {
                    id: 'inventory-paid-over-trial-store',
                    raw_config: 'vless://paid-over-trial-store',
                    health_status: 'HEALTHY',
                    source_quota_bytes: null,
                    source_used_bytes: BigInt(0),
                    supplier_provider_name: 'paid-store',
                    supplier_expires_at: futureTrialExpiry,
                  },
                },
              ],
            },
          ],
        }),
      },
      order: {
        findFirst: async () => null,
        findMany: async () => [],
      },
      orderAssignment: {
        findMany: async () => [],
      },
      trialCampaign: {
        findFirst: async () => trialFailureCampaign,
      },
      trialGrant: {
        findFirst: async () => ({
          id: 'trial-grant-paid-over',
          customer_id: 'customer-paid-over-trial-store',
          campaign_id: trialFailureCampaign.id,
          status: 'ACTIVE',
          started_at: new Date(),
          expires_at: futureTrialExpiry,
          assignments: [
            {
              id: 'trial-assignment-paid-over',
              status: 'ACTIVE',
              revoked_at: null,
              expires_at: futureTrialExpiry,
              trial_config: {
                id: 'trial-config-paid-over',
                raw_config: 'vless://trial-should-not-win',
                status: 'ASSIGNED',
                supplier_expires_at: futureTrialExpiry,
              },
            },
          ],
        }),
      },
    } as any,
    {} as any,
    {} as any,
    {} as any,
  );

  const paidOverTrialStoreProfile =
    await paidOverTrialStoreProfileService.getProfile('SW-PAID-OVER-TRIAL-STORE');
  assert(
    paidOverTrialStoreProfile.entitlementState === 'ACTIVE_SUBSCRIPTION',
    'active paid assignment must keep priority over active trial store grant',
  );
  assert(
    paidOverTrialStoreProfile.subscriptionUrl === 'vless://paid-over-trial-store',
    'active paid assignment must expose paid config instead of trial config',
  );

  const paidExpiredOverTrialExpiredService = new CustomerService(
    {
      customer: {
        findUnique: async () => ({
          id: 'customer-paid-expired-over-trial-expired',
          public_id: 'SW-PAID-EXPIRED-OVER-TRIAL-EXPIRED',
          device_id: 'device-paid-expired-over-trial-expired',
          email: 'paid-expired-over-trial-expired@example.com',
          phone: '79000000020',
          orders: [
            {
              id: 'order-paid-expired-over-trial-expired',
              order_ref: 'ORD-PAID-EXPIRED-OVER-TRIAL-EXPIRED',
              payment_ref: 'SWIMPAY_CONFIRMED:test',
              status: 'FULFILLED',
              created_at: new Date(Date.now() - 3 * 24 * 60 * 60 * 1000),
              fulfilled_at: new Date(Date.now() - 3 * 24 * 60 * 60 * 1000),
              plan: {
                code: 'MONTH',
                quota_label: '100 GB',
              },
              assignments: [
                {
                  id: 'assignment-paid-expired-over-trial-expired',
                  inventory_item_id: 'inventory-paid-expired-over-trial-expired',
                  access_status: AssignmentAccessStatus.EXPIRED,
                  measured_used_bytes: BigInt(0),
                  expires_at: new Date(Date.now() - 60_000),
                  inventory_item: {
                    id: 'inventory-paid-expired-over-trial-expired',
                    raw_config: 'vless://paid-expired',
                    health_status: 'HEALTHY',
                    source_quota_bytes: null,
                    source_used_bytes: BigInt(0),
                    supplier_provider_name: 'paid-store',
                    supplier_expires_at: new Date(Date.now() - 60_000),
                  },
                },
              ],
            },
          ],
        }),
      },
      order: {
        findFirst: async () => null,
        findMany: async () => [],
      },
      orderAssignment: {
        findMany: async () => [],
      },
      trialCampaign: {
        findFirst: async () => trialFailureCampaign,
      },
      trialGrant: {
        findFirst: async () => ({
          id: 'trial-grant-expired-under-paid',
          customer_id: 'customer-paid-expired-over-trial-expired',
          campaign_id: trialFailureCampaign.id,
          status: 'EXPIRED',
          started_at: new Date(Date.now() - 5 * 24 * 60 * 60 * 1000),
          expires_at: new Date(Date.now() - 2 * 24 * 60 * 60 * 1000),
          assignments: [
            {
              id: 'trial-assignment-expired-under-paid',
              status: 'EXPIRED',
              revoked_at: null,
              expires_at: new Date(Date.now() - 2 * 24 * 60 * 60 * 1000),
              trial_config: {
                id: 'trial-config-expired-under-paid',
                raw_config: 'vless://trial-expired-under-paid',
                status: 'ASSIGNED',
                supplier_expires_at: new Date(Date.now() - 2 * 24 * 60 * 60 * 1000),
              },
            },
          ],
        }),
      },
    } as any,
    {} as any,
    {} as any,
    {} as any,
  );

  const paidExpiredOverTrialExpiredProfile =
    await paidExpiredOverTrialExpiredService.getProfile('SW-PAID-EXPIRED-OVER-TRIAL-EXPIRED');
  assert(
    paidExpiredOverTrialExpiredProfile.entitlementState === 'EXPIRED_SUBSCRIPTION',
    'expired paid history must keep priority over expired trial store state',
  );

  const trialUniqueRaceService = new CustomerService(
    {
      $transaction: async (callback: any) =>
        callback({
          trialCampaign: {
            findFirst: async () => trialFailureCampaign,
          },
          trialGrant: {
            findFirst: async () => null,
            create: async () => {
              throw new Prisma.PrismaClientKnownRequestError('Unique constraint failed on TrialGrant', {
                code: 'P2002',
                clientVersion: 'test',
                meta: { target: ['customer_id', 'campaign_id'] },
              });
            },
          },
        }),
      customer: {
        findUnique: async () => ({
          id: 'customer-trial-race',
          public_id: 'SW-TRIAL-RACE',
          device_id: 'device-trial-race',
          email: 'trial-race@example.com',
          phone: '79000000011',
        }),
        update: async (args: unknown) => args,
      },
      order: {
        findFirst: async () => null,
      },
      trialCampaign: {
        findFirst: async () => trialFailureCampaign,
      },
      trialGrant: {
        findFirst: async () => null,
      },
    } as any,
    {} as any,
    {} as any,
    {} as any,
  );
  (trialUniqueRaceService as any).getProfile = async () => ({
    userNumber: 'SW-TRIAL-RACE',
    entitlementState: 'FREEMIUM',
  });
  await assertRejectsWithRpcException(
    () =>
      trialUniqueRaceService.activateTrial({
        userNumber: 'SW-TRIAL-RACE',
        deviceId: 'device-trial-race',
        email: 'trial-race@example.com',
        phone: '79000000011',
      }),
    'trial activation must map unique customer/campaign races to a business refusal',
  );

  const expiredTrialGrantUpdates: unknown[] = [];
  const expiredTrialAssignmentUpdates: unknown[] = [];
  const expiredTrialEvents: unknown[] = [];
  const expiredTrialStoreProfileService = new CustomerService(
    {
      customer: {
        findUnique: async () => ({
          id: 'customer-trial-store-expired',
          public_id: 'SW-TRIAL-STORE-EXPIRED',
          device_id: 'device-trial-store-expired',
          email: 'trial-store-expired@example.com',
          phone: '79000000012',
          orders: [],
        }),
      },
      order: {
        findFirst: async () => null,
        findMany: async () => [],
      },
      orderAssignment: {
        findMany: async () => [],
      },
      trialCampaign: {
        findFirst: async () => trialFailureCampaign,
      },
      trialGrant: {
        findFirst: async ({ where }: any) => {
          if (where?.customer_id === 'customer-trial-store-expired' && where?.campaign_id) {
            return { id: 'trial-grant-expired-existing' };
          }

          return {
            id: 'trial-grant-expired',
            customer_id: 'customer-trial-store-expired',
            campaign_id: trialFailureCampaign.id,
            status: 'ACTIVE',
            started_at: new Date(Date.now() - 4 * 24 * 60 * 60 * 1000),
            expires_at: new Date(Date.now() - 60_000),
            assignments: [
              {
                id: 'trial-assignment-expired',
                status: 'ACTIVE',
                revoked_at: null,
                expires_at: new Date(Date.now() - 60_000),
                trial_config: {
                  id: 'trial-config-expired',
                  raw_config: 'vless://expired-trial-store',
                  supplier_expires_at: new Date(Date.now() + 60_000),
                },
              },
            ],
          };
        },
        updateMany: async (args: unknown) => {
          expiredTrialGrantUpdates.push(args);
          return { count: 1 };
        },
      },
      trialAssignment: {
        updateMany: async (args: unknown) => {
          expiredTrialAssignmentUpdates.push(args);
          return { count: 1 };
        },
      },
      adminEvent: {
        create: async (args: unknown) => {
          expiredTrialEvents.push(args);
          return args;
        },
      },
    } as any,
    {} as any,
    {} as any,
    {} as any,
  );

  const expiredTrialStoreProfile =
    await expiredTrialStoreProfileService.getProfile('SW-TRIAL-STORE-EXPIRED');
  assert(
    expiredTrialStoreProfile.entitlementState === 'EXPIRED_TRIAL',
    'expired trial store grant should resolve to EXPIRED_TRIAL',
  );
  assert(
    expiredTrialGrantUpdates.some((args: any) => args.data?.status === 'EXPIRED'),
    'expired trial store grant should be persisted as EXPIRED',
  );
  assert(
    expiredTrialAssignmentUpdates.some((args: any) => args.data?.status === 'EXPIRED'),
    'expired trial store assignment should be persisted as EXPIRED',
  );
  assert(
    expiredTrialEvents.some((args: any) => args.data?.event_type === 'TRIAL_EXPIRED'),
    'expired trial store grant should emit TRIAL_EXPIRED audit event',
  );

  const usageWithoutActiveOrderService = new CustomerService(
    {
      customer: {
        findUnique: async () => ({
          id: 'customer-usage-no-active',
          public_id: 'SW-USAGE-NO-ACTIVE',
          device_id: 'device-usage-no-active',
          email: 'usage-no-active@example.com',
          phone: '79000000008',
          orders: [],
        }),
      },
    } as any,
    {
      send: () => {
        throw new Error('usage should not be recorded without an active fulfilled order');
      },
    } as any,
    {} as any,
    {} as any,
  );
  (usageWithoutActiveOrderService as any).getProfile = async () => ({
    userNumber: 'SW-USAGE-NO-ACTIVE',
    status: 'FREEMIUM',
    entitlementState: 'FREEMIUM',
    subscriptionUrl: null,
  });

  const usageWithoutActiveOrderProfile = await usageWithoutActiveOrderService.reportUsage({
    userNumber: 'SW-USAGE-NO-ACTIVE',
    deviceId: 'device-usage-no-active',
    measuredUsedBytes: '123',
  });
  assert(
    usageWithoutActiveOrderProfile.entitlementState === 'FREEMIUM',
    'usage reporting without active fulfilled order should still return an access profile',
  );
  assert(
    !('ignored' in usageWithoutActiveOrderProfile),
    'usage reporting response should not switch to a non-profile ignored payload',
  );

  const checkoutOrders: unknown[] = [];
  const checkoutCustomerUpdates: unknown[] = [];
  const checkoutService = new CustomerService(
    {
      plan: {
        findUnique: async () => ({
          id: 'plan-month',
          active: true,
          price_rub: 500,
          name: 'Premium',
          duration_label: 'Month',
          quota_label: '50 GB',
        }),
      },
      customer: {
        findUnique: async (args: any) => {
          if (args.where?.public_id === 'SW-CHECKOUT') {
            return {
              id: 'customer-checkout',
              public_id: 'SW-CHECKOUT',
              device_id: 'device-checkout',
              email: 'old-checkout@example.com',
              phone: '79000000009',
            };
          }
          return null;
        },
        findFirst: async () => {
          throw new Error('checkout should prefer provided userNumber before contact lookup');
        },
        update: async (args: unknown) => {
          checkoutCustomerUpdates.push(args);
          return {
            id: 'customer-checkout',
            public_id: 'SW-CHECKOUT',
            device_id: 'device-checkout',
            email: 'new-checkout@example.com',
            phone: '79000000010',
          };
        },
      },
      order: {
        create: async (args: unknown) => {
          checkoutOrders.push(args);
          return {
            id: 'order-checkout',
            order_ref: 'ORD-CHECKOUT',
            status: 'PENDING',
          };
        },
        update: async (args: unknown) => args,
      },
    } as any,
    {} as any,
    {} as any,
    {
      createInvoice: async () => ({
        invoice_id: 'invoice-checkout',
        bot_invoice_url: 'https://pay.example/invoice-checkout',
      }),
    } as any,
  );

  await checkoutService.createCheckout({
    userNumber: 'SW-CHECKOUT',
    deviceId: 'device-checkout',
    email: 'new-checkout@example.com',
    phone: '79000000010',
    planId: 'plan-month',
    paymentMethod: 'CRYPTO',
  } as any);
  assert(
    (checkoutOrders[0] as any).data?.customer_id === 'customer-checkout',
    'checkout should attach order to provided userNumber customer when supplied',
  );
  assert(checkoutCustomerUpdates.length === 1, 'checkout should update contact fields on the existing customer');

  const checkoutWithoutDeviceService = new CustomerService(
    {
      plan: {
        findUnique: async () => ({
          id: 'plan-month',
          active: true,
          price_rub: 500,
          name: 'Premium',
          duration_label: 'Month',
          quota_label: '50 GB',
        }),
      },
    } as any,
    {} as any,
    {} as any,
    {} as any,
  );

  await assertRejectsWithRpcException(
    () => checkoutWithoutDeviceService.createCheckout({
      userNumber: 'SW-CHECKOUT',
      email: 'new-checkout@example.com',
      phone: '79000000010',
      planId: 'plan-month',
      paymentMethod: 'CARD_MANUAL',
    } as any),
    'user-bound checkout must require the device id',
  );

  console.log('backend security policy tests passed');
}

main().catch((error) => {
  console.error(error);
  process.exit(1);
});
