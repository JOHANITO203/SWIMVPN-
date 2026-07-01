import { PlanCategory } from '@prisma/client';

export const DEFAULT_RESALE_SLOT_CAP = 2;
export const DEFAULT_SUPPLIER_USER_CAPACITY = 2;
export const DEFAULT_SUPPLIER_DEVICE_LIMIT = 5;

export type PublicPlanName = 'Basic' | 'Premium' | 'Platinum';

export function getPublicPlanName(code: PlanCategory): PublicPlanName {
  switch (code) {
    case PlanCategory.MONTH:
      return 'Premium';
    case PlanCategory.QUARTER:
      return 'Platinum';
    case PlanCategory.WEEK:
    default:
      return 'Basic';
  }
}

export function getPublicPlanDeviceAllowance(_code: PlanCategory): number {
  // Every offer now includes connectivity for up to 3 devices.
  return 3;
}

export function getSupplierCapacityUnitsPerUser(code: PlanCategory): number {
  switch (code) {
    case PlanCategory.MONTH:
      return 2;
    case PlanCategory.QUARTER:
      return 3;
    case PlanCategory.WEEK:
    default:
      return 1;
  }
}

export function getDefaultSupplierCapacityUnits(code: PlanCategory): number {
  return DEFAULT_SUPPLIER_USER_CAPACITY * getSupplierCapacityUnitsPerUser(code);
}

export function getPlanDeviceAllowance(code: PlanCategory): number {
  return getPublicPlanDeviceAllowance(code);
}

export function getPlanResaleSlotCount(code: PlanCategory): number {
  return getSupplierCapacityUnitsPerUser(code);
}

export function getPlanSlotCount(code: PlanCategory): number {
  return getPlanResaleSlotCount(code);
}

