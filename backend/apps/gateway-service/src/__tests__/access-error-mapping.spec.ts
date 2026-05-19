import { strict as assert } from 'assert';
import { ConflictException } from '@nestjs/common';
import { throwError } from 'rxjs';
import { AccessController } from '../controllers/access.controller';

async function assertConflict(message: string) {
  const controller = new AccessController(
    {
      send: () => throwError(() => ({ error: message })),
    } as any,
    {} as any,
  );

  try {
    await controller.activateTrial({
      userNumber: 'SW-TEST',
      deviceId: 'device-1',
      email: 'test@example.com',
      phone: '79000000000',
    });
  } catch (error) {
    assert(error instanceof ConflictException, `"${message}" must map to ConflictException`);
    return;
  }

  throw new Error(`"${message}" must reject`);
}

async function main() {
  await assertConflict('Active subscription already exists');
  await assertConflict('Paid subscription fulfillment is already in progress');

  console.log('access error mapping tests passed');
}

main().catch((error) => {
  console.error(error);
  process.exit(1);
});
