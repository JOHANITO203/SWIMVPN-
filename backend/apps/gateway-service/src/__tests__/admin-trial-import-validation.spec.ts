import { BadRequestException } from '@nestjs/common';
import { AdminController } from '../controllers/admin.controller';

function assert(condition: boolean, message: string) {
  if (!condition) {
    throw new Error(message);
  }
}

const controller = new AdminController(
  {
    send: () => {
      throw new Error('invalid trial import body must not reach admin service');
    },
  } as any,
  {} as any,
);

try {
  controller.importTrialConfigs(
    {
      configs: ['vless://uuid@example.com:443?security=tls#Trial'],
      supplierExpiresAt: 123456 as any,
    },
    { admin: { id: 'admin-1' } } as any,
  );
  throw new Error('numeric supplierExpiresAt should be rejected');
} catch (error) {
  assert(
    error instanceof BadRequestException,
    'non-string supplierExpiresAt should be rejected with BadRequestException',
  );
}

console.log('admin trial import validation tests passed');
