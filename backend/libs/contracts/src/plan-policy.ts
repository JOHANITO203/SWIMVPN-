import { PlanCategory } from '@prisma/client';

export const DEFAULT_RESALE_SLOT_CAP = 2;
export const DEFAULT_SUPPLIER_DEVICE_LIMIT = 5;
export const DEFAULT_PLAN_DEVICE_ALLOWANCE = 2;

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

export function getPlanResaleSlotCount(code: PlanCategory): number {
  return 1;
}

export function getPlanDeviceAllowance(code: PlanCategory): number {
  return DEFAULT_PLAN_DEVICE_ALLOWANCE;
}

export function getPlanSlotCount(code: PlanCategory): number {
  return getPlanResaleSlotCount(code);
}

