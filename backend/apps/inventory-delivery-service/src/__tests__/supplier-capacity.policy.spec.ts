import { PlanCategory } from '@prisma/client';
import {
  DEFAULT_RESALE_SLOT_CAP,
  DEFAULT_SUPPLIER_USER_CAPACITY,
  getDefaultSupplierCapacityUnits,
  getPublicPlanDeviceAllowance,
  getPlanDeviceAllowance,
  getPlanResaleSlotCount,
  getPlanSlotCount,
  getPublicPlanName,
  getSupplierCapacityUnitsPerUser,
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
assert(DEFAULT_SUPPLIER_USER_CAPACITY === 2, 'Supplier configs should support max 2 SWIMVPN customers internally');

assert(getPublicPlanDeviceAllowance(PlanCategory.WEEK) === 1, 'Basic should publicly display 1 device');
assert(getPublicPlanDeviceAllowance(PlanCategory.MONTH) === 2, 'Premium should publicly display 2 devices');
assert(getPublicPlanDeviceAllowance(PlanCategory.QUARTER) === 3, 'Platinum should publicly display 3 devices');
assert(getPlanDeviceAllowance(PlanCategory.WEEK) === 1, 'Compatibility helper should publicly display Basic as 1 device');
assert(getPlanDeviceAllowance(PlanCategory.MONTH) === 2, 'Compatibility helper should publicly display Premium as 2 devices');
assert(getPlanDeviceAllowance(PlanCategory.QUARTER) === 3, 'Compatibility helper should publicly display Platinum as 3 devices');

assert(getSupplierCapacityUnitsPerUser(PlanCategory.WEEK) === 1, 'Basic should consume 1 supplier capacity unit');
assert(getSupplierCapacityUnitsPerUser(PlanCategory.MONTH) === 2, 'Premium should consume 2 supplier capacity units');
assert(getSupplierCapacityUnitsPerUser(PlanCategory.QUARTER) === 3, 'Platinum should consume 3 supplier capacity units');
assert(getDefaultSupplierCapacityUnits(PlanCategory.WEEK) === 2, 'Basic supplier configs should default to 2 capacity units');
assert(getDefaultSupplierCapacityUnits(PlanCategory.MONTH) === 4, 'Premium supplier configs should default to 4 capacity units');
assert(getDefaultSupplierCapacityUnits(PlanCategory.QUARTER) === 6, 'Platinum supplier configs should default to 6 capacity units');
assert(getPlanResaleSlotCount(PlanCategory.WEEK) === 1, 'Basic compatibility helper should consume 1 supplier unit');
assert(getPlanResaleSlotCount(PlanCategory.MONTH) === 2, 'Premium compatibility helper should consume 2 supplier units');
assert(getPlanResaleSlotCount(PlanCategory.QUARTER) === 3, 'Platinum compatibility helper should consume 3 supplier units');
assert(getPlanSlotCount(PlanCategory.WEEK) === 1, 'Plan slot compatibility helper should return resale slots');
assert(getPlanSlotCount(PlanCategory.MONTH) === 2, 'Premium compatibility helper should return 2 supplier units');
assert(getPlanSlotCount(PlanCategory.QUARTER) === 3, 'Platinum compatibility helper should return 3 supplier units');

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
