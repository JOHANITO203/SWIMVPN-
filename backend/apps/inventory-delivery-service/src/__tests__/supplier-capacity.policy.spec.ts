import { PlanCategory } from '@prisma/client';
import { getPlanSlotCount, getPublicPlanName } from '@app/contracts';
import { canAllocateSupplierConfig } from '../supplier-capacity.policy';

function assert(condition: boolean, message: string) {
  if (!condition) {
    throw new Error(message);
  }
}

assert(getPublicPlanName(PlanCategory.WEEK) === 'Basic', 'WEEK should map to Basic');
assert(getPublicPlanName(PlanCategory.MONTH) === 'Premium', 'MONTH should map to Premium');
assert(getPublicPlanName(PlanCategory.QUARTER) === 'Platinum', 'QUARTER should map to Platinum');

assert(getPlanSlotCount(PlanCategory.WEEK) === 1, 'Basic should consume 1 slot');
assert(getPlanSlotCount(PlanCategory.MONTH) === 2, 'Premium should consume 2 slots');
assert(getPlanSlotCount(PlanCategory.QUARTER) === 4, 'Platinum should consume 4 slots');

assert(
  canAllocateSupplierConfig({
    healthStatus: 'HEALTHY',
    usedResaleSlots: 0,
    maxResaleSlots: 4,
    requiredSlots: 4,
  }),
  'Platinum should fit into an empty supplier config',
);

assert(
  !canAllocateSupplierConfig({
    healthStatus: 'HEALTHY',
    usedResaleSlots: 3,
    maxResaleSlots: 4,
    requiredSlots: 2,
  }),
  'Premium should not fit when only one slot remains',
);

assert(
  !canAllocateSupplierConfig({
    healthStatus: 'HEALTHY',
    usedResaleSlots: 3,
    maxResaleSlots: 4,
    requiredSlots: 4,
  }),
  'Platinum should not fit into a nearly full supplier config',
);

assert(
  !canAllocateSupplierConfig({
    healthStatus: 'FULL',
    usedResaleSlots: 0,
    maxResaleSlots: 4,
    requiredSlots: 1,
  }),
  'FULL supplier configs must not accept new allocations',
);

assert(
  !canAllocateSupplierConfig({
    healthStatus: 'EXPIRED',
    usedResaleSlots: 0,
    maxResaleSlots: 4,
    requiredSlots: 1,
  }),
  'EXPIRED supplier configs must not accept new allocations',
);

console.log('supplier capacity policy tests passed');
