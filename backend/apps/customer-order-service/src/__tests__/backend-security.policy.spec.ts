import { RpcException } from '@nestjs/microservices';
import { InventoryHealthStatus } from '@prisma/client';
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
              : [],
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
  assert(fulfillmentFailureEvents.length === 1, 'failed fulfillment should be audited');

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
    structuredFulfillmentFailureEvents.length === 1,
    'structured inventory fulfillment failures should be audited',
  );

  console.log('backend security policy tests passed');
}

main().catch((error) => {
  console.error(error);
  process.exit(1);
});
