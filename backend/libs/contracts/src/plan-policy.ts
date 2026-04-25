import { PlanCategory } from '@prisma/client';

export const DEFAULT_RESALE_SLOT_CAP = 4;
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

export function getPlanSlotCount(code: PlanCategory): number {
  switch (code) {
    case PlanCategory.MONTH:
      return 2;
    case PlanCategory.QUARTER:
      return 4;
    case PlanCategory.WEEK:
    default:
      return 1;
  }
}

