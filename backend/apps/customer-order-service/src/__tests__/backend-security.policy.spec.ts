import { RpcException } from '@nestjs/microservices';
import { InventoryHealthStatus } from '@prisma/client';
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

  console.log('backend security policy tests passed');
}

main().catch((error) => {
  console.error(error);
  process.exit(1);
});
