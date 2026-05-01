import { RpcException } from '@nestjs/microservices';
import { InventoryHealthStatus } from '@prisma/client';
import { of } from 'rxjs';
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

  console.log('backend security policy tests passed');
}

main().catch((error) => {
  console.error(error);
  process.exit(1);
});
