import { RpcException } from '@nestjs/microservices';
import { AssignmentAccessStatus, InventoryHealthStatus } from '@prisma/client';
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
                  access_status: 'ACTIVE',
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
                  access_status: 'ACTIVE',
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

  const trialFailureOrderUpdates: unknown[] = [];
  const trialFailureEvents: unknown[] = [];
  const trialFulfillmentFailureService = new CustomerService(
    {
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
        create: async () => ({
          id: 'order-trial-fail',
          order_ref: 'TRIAL-SW-TRIAL-FAIL-1',
        }),
        update: async (args: unknown) => {
          trialFailureOrderUpdates.push(args);
          return args;
        },
      },
      plan: {
        findFirst: async () => ({ id: 'plan-week' }),
      },
      adminEvent: {
        create: async (event: unknown) => {
          trialFailureEvents.push(event);
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
    'trial fulfillment exception should return pending fulfillment profile',
  );
  assert(
    trialFailureOrderUpdates.some((args: any) => args.data?.status === 'PENDING_FULFILLMENT'),
    'trial fulfillment exception should leave trial order pending fulfillment instead of silently burning it',
  );
  assert(trialFailureEvents.length === 1, 'trial fulfillment exception should be audited');

  const structuredTrialFailureOrderUpdates: unknown[] = [];
  const structuredTrialFulfillmentFailureService = new CustomerService(
    {
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
        create: async () => ({
          id: 'order-trial-structured-fail',
          order_ref: 'TRIAL-SW-TRIAL-STRUCTURED-FAIL-1',
        }),
        update: async (args: unknown) => {
          structuredTrialFailureOrderUpdates.push(args);
          return args;
        },
      },
      plan: {
        findFirst: async () => ({ id: 'plan-week' }),
      },
      adminEvent: {
        create: async (event: unknown) => event,
      },
    } as any,
    {
      send: () => of({ success: false, error: 'No inventory available' }),
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
    structuredTrialFailureOrderUpdates.some((args: any) => args.data?.status === 'PENDING_FULFILLMENT'),
    'trial fulfillment success:false should leave trial order pending fulfillment',
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
