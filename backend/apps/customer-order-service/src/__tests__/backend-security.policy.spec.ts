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
  assert(fulfillmentFailure.success === false, 'failed fulfillment should not be reported as success');
  assert(
    fulfillmentFailure.error.includes('Inventory service unavailable'),
    'failed fulfillment should expose the concrete inventory error',
  );
  assert(fulfillmentFailureEvents.length === 1, 'failed fulfillment should be audited');

  console.log('backend security policy tests passed');
}

main().catch((error) => {
  console.error(error);
  process.exit(1);
});
