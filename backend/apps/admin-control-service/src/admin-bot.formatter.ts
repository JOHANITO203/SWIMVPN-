import { PlanCategory } from '@prisma/client';

type InventoryOverviewItem = {
  id?: string;
  category: PlanCategory | string;
  healthStatus: string;
  usedResaleSlots: number;
  maxResaleSlots: number;
  supplierExpiresAt?: string | null;
};

type PendingFulfillmentItem = {
  orderRef: string;
  planName: string;
  planCode: string;
  amountRub: string;
  customerEmail?: string | null;
  createdAt: string;
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

    for (const item of categoryItems.slice(0, 5)) {
      const id = item.id ? item.id.slice(0, 8) : 'unknown';
      const expiry = item.supplierExpiresAt ? ` expires ${item.supplierExpiresAt.slice(0, 10)}` : '';
      lines.push(`- ${id}: ${item.healthStatus} ${item.usedResaleSlots}/${item.maxResaleSlots}${expiry}`);
    }
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

export type ParsedRetryCommand =
  | { mode: 'all' }
  | { mode: 'one'; orderRef: string }
  | { mode: 'invalid' };

export function parseRetryCommand(text: string): ParsedRetryCommand {
  const match = text.trim().match(/^\/retry(?:@\w+)?\s+(.+)$/i);
  const target = match?.[1]?.trim();
  if (!target) {
    return { mode: 'invalid' };
  }

  if (target.toLowerCase() === 'all') {
    return { mode: 'all' };
  }

  return { mode: 'one', orderRef: target };
}

export function parseInventoryActionCommand(text: string) {
  const match = text.trim().match(/^\/\w+(?:@\w+)?\s+(\S+)(?:\s+([\s\S]+))?$/i);
  return {
    inventoryItemId: match?.[1]?.trim() || null,
    reason: match?.[2]?.trim() || null,
  };
}

export function formatPendingFulfillment(items: PendingFulfillmentItem[]) {
  if (items.length === 0) {
    return 'No pending fulfillment orders.';
  }

  return [
    'Pending fulfillment orders',
    ...items.slice(0, 10).map((order) => [
      `Order: ${order.orderRef}`,
      `Plan: ${order.planName} (${order.planCode})`,
      `Amount: ${order.amountRub} RUB`,
      `Customer: ${order.customerEmail || 'missing email'}`,
      `Created: ${order.createdAt}`,
      `Retry: /retry ${order.orderRef}`,
    ].join('\n')),
  ].join('\n\n');
}

export function formatAccountingSummary(input: {
  title: string;
  orderCount: number;
  amountRub: string;
}) {
  return [
    input.title,
    `Orders: ${input.orderCount}`,
    `Revenue: ${input.amountRub} RUB`,
  ].join('\n');
}
