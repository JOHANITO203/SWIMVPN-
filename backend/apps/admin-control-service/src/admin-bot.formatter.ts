import { PlanCategory } from '@prisma/client';

type InventoryOverviewItem = {
  category: PlanCategory | string;
  healthStatus: string;
  usedResaleSlots: number;
  maxResaleSlots: number;
};

const CATEGORY_LABELS: Record<string, string> = {
  WEEK: 'Basic',
  MONTH: 'Premium',
  QUARTER: 'Platinum',
};

export function mapBotPlanInputToCategory(input: string): PlanCategory | null {
  switch (input.trim().toLowerCase()) {
    case 'basic':
    case 'week':
    case 'weekly':
      return PlanCategory.WEEK;
    case 'premium':
    case 'month':
    case 'monthly':
      return PlanCategory.MONTH;
    case 'platinum':
    case 'quarter':
    case 'quarterly':
      return PlanCategory.QUARTER;
    default:
      return null;
  }
}

export function formatInventoryOverview(items: InventoryOverviewItem[]) {
  const lines = ['SWIMVPN+ Inventory'];

  for (const category of [PlanCategory.WEEK, PlanCategory.MONTH, PlanCategory.QUARTER]) {
    const categoryItems = items.filter((item) => item.category === category);
    const allocatable = categoryItems.filter((item) =>
      item.healthStatus === 'HEALTHY' &&
      item.usedResaleSlots < item.maxResaleSlots,
    ).length;
    lines.push(`${CATEGORY_LABELS[category]}: ${allocatable} allocatable / ${categoryItems.length} total`);
  }

  return lines.join('\n');
}

export function formatImportResult(category: PlanCategory, result: any) {
  const importedCount = result?.importedCount ?? 0;
  const failures = Array.isArray(result?.details)
    ? result.details.filter((item: any) => item.status !== 'IMPORTED').length
    : 0;

  return [
    'Config import finished',
    `Plan bucket: ${CATEGORY_LABELS[category] || category}`,
    `Imported: ${importedCount}`,
    `Failed: ${failures}`,
    'Resale cap: 2 customer orders per supplier link',
  ].join('\n');
}
