import { PlanCategory } from '@prisma/client';
import {
  DEFAULT_RESALE_SLOT_CAP,
  getPlanDeviceAllowance,
  getPlanResaleSlotCount,
  getPlanSlotCount,
  getPublicPlanName,
} from '@app/contracts';
import { canAllocateSupplierConfig } from '../supplier-capacity.policy';

function assert(condition: boolean, message: string) {
  if (!condition) {
    throw new Error(message);
  }
}

assert(getPublicPlanName(PlanCategory.WEEK) === 'Basic', 'WEEK should map to Basic');
assert(getPublicPlanName(PlanCategory.MONTH) === 'Premium', 'MONTH should map to Premium');
assert(getPublicPlanName(PlanCategory.QUARTER) === 'Platinum', 'QUARTER should map to Platinum');

assert(DEFAULT_RESALE_SLOT_CAP === 2, 'Supplier configs should be resold to max 2 customer orders');

assert(getPlanResaleSlotCount(PlanCategory.WEEK) === 1, 'Basic should consume 1 resale slot');
assert(getPlanResaleSlotCount(PlanCategory.MONTH) === 1, 'Premium should consume 1 resale slot');
assert(getPlanResaleSlotCount(PlanCategory.QUARTER) === 1, 'Platinum should consume 1 resale slot');
assert(getPlanSlotCount(PlanCategory.WEEK) === 1, 'Plan slot compatibility helper should return resale slots');
assert(getPlanSlotCount(PlanCategory.MONTH) === 1, 'Premium compatibility helper should return 1 resale slot');
assert(getPlanSlotCount(PlanCategory.QUARTER) === 1, 'Platinum compatibility helper should return 1 resale slot');

assert(getPlanDeviceAllowance(PlanCategory.WEEK) === 2, 'Basic should display up to 2 devices');
assert(getPlanDeviceAllowance(PlanCategory.MONTH) === 2, 'Premium should display up to 2 devices');
assert(getPlanDeviceAllowance(PlanCategory.QUARTER) === 2, 'Platinum should display up to 2 devices');

assert(
  canAllocateSupplierConfig({
    healthStatus: 'HEALTHY',
    usedResaleSlots: 0,
    maxResaleSlots: 2,
    requiredSlots: 1,
  }),
  'A paid order should fit into an empty supplier config',
);

assert(
  canAllocateSupplierConfig({
    healthStatus: 'HEALTHY',
    usedResaleSlots: 1,
    maxResaleSlots: 2,
    requiredSlots: 1,
  }),
  'A second paid order should fit into a supplier config',
);

assert(
  !canAllocateSupplierConfig({
    healthStatus: 'HEALTHY',
    usedResaleSlots: 2,
    maxResaleSlots: 2,
    requiredSlots: 1,
  }),
  'A third paid order must not fit into a supplier config',
);

assert(
  !canAllocateSupplierConfig({
    healthStatus: 'FULL',
    usedResaleSlots: 0,
    maxResaleSlots: 2,
    requiredSlots: 1,
  }),
  'FULL supplier configs must not accept new allocations',
);

assert(
  !canAllocateSupplierConfig({
    healthStatus: 'EXPIRED',
    usedResaleSlots: 0,
    maxResaleSlots: 2,
    requiredSlots: 1,
  }),
  'EXPIRED supplier configs must not accept new allocations',
);

console.log('supplier capacity policy tests passed');
