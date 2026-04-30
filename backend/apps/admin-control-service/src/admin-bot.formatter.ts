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

export const ADMIN_BOT_COMMANDS = [
  { command: 'help', description: 'Show admin command list' },
  { command: 'status', description: 'Check admin bot status' },
  { command: 'stock', description: 'Show inventory by plan bucket' },
  { command: 'add_wizard', description: 'Guided supplier config import' },
  { command: 'import', description: 'Show direct import instructions' },
  { command: 'pending', description: 'Show orders waiting for capacity' },
  { command: 'retry', description: 'Retry one order or all pending orders' },
  { command: 'orders', description: 'Show recent orders' },
  { command: 'orders_today', description: 'Show today order count' },
  { command: 'revenue_today', description: 'Show today revenue' },
  { command: 'add_expense', description: 'Record a manual business expense' },
  { command: 'profit_month', description: 'Show current month profit' },
  { command: 'expire', description: 'Mark supplier config expired' },
  { command: 'disable', description: 'Disable supplier config' },
  { command: 'quota_reached', description: 'Mark supplier quota exhausted' },
  { command: 'healthcheck', description: 'Run inventory health check' },
  { command: 'cancel_import', description: 'Cancel guided config import' },
  { command: 'users', description: 'Show customer statistics' },
];

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
  const importedDetails = Array.isArray(result?.details)
    ? result.details.filter((item: any) => item.status === 'IMPORTED')
    : [];

  const lines = [
    'Config import finished',
    `Plan bucket: ${CATEGORY_LABELS[category] || category}`,
    `Imported: ${importedCount}`,
    `Failed: ${failures}`,
    'Resale cap: 2 customer orders per supplier link',
  ];

  for (const item of importedDetails.slice(0, 3)) {
    const quota = item.sourceQuotaBytes ? `Quota: ${formatBytes(item.sourceQuotaBytes)}` : null;
    const used = item.sourceUsedBytes ? `Used: ${formatBytes(item.sourceUsedBytes)}` : null;
    const expires = item.supplierExpiresAt ? `Expires: ${String(item.supplierExpiresAt).slice(0, 10)}` : null;
    const provider = item.supplierProviderName ? `Provider: ${item.supplierProviderName}` : null;
    lines.push([
      '',
      `Inventory: ${String(item.id || 'unknown').slice(0, 8)}`,
      `Protocol: ${item.configType || item.displayProtocol || 'unknown'}`,
      provider,
      quota,
      used,
      expires,
      `Slots: ${item.usedResaleSlots ?? 0}/${item.maxResaleSlots ?? 2}`,
    ].filter(Boolean).join('\n'));
  }

  return lines.join('\n');
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

export type ParsedExpenseCommand =
  | { valid: true; amount: string; currency: string; note: string }
  | { valid: false; reason: string; amount?: never; currency?: never; note?: never };

export function parseExpenseCommand(text: string): ParsedExpenseCommand {
  const match = text.trim().match(/^\/add_expense(?:@\w+)?\s+(\d+(?:[.,]\d{1,2})?)\s+([A-Z]{3,8})(?:\s+([\s\S]+))?$/i);
  if (!match) {
    return { valid: false, reason: 'Usage: /add_expense <amount> <currency> <note>' };
  }

  const amount = match[1].replace(',', '.');
  if (Number(amount) <= 0) {
    return { valid: false, reason: 'Amount must be greater than zero.' };
  }

  return {
    valid: true,
    amount,
    currency: match[2].toUpperCase(),
    note: match[3]?.trim() || 'Manual expense',
  };
}

export function formatImportWizardCategoryPrompt() {
  return [
    'Supplier config import wizard',
    '',
    'Choose the boutique bucket:',
    '- Basic',
    '- Premium',
    '- Platinum',
    '',
    'Reply with one word: basic, premium, or platinum.',
    'Use /cancel_import to stop.',
  ].join('\n');
}

export function formatImportWizardConfigPrompt(category: PlanCategory) {
  return [
    `Selected bucket: ${CATEGORY_LABELS[category] || category}`,
    '',
    'Send the supplier config or subscription URL now.',
    'This supplier link will be capped at 2 customer orders.',
    'Raw config will be preserved in PostgreSQL.',
    '',
    'Use /cancel_import to stop.',
  ].join('\n');
}

export function formatImportWizardConfirmation(category: PlanCategory, rawConfig: string) {
  return [
    'Confirm supplier config import',
    `Bucket: ${CATEGORY_LABELS[category] || category}`,
    `Config preview: ${previewSecret(rawConfig)}`,
    'Resale cap: 2 customer orders',
    'Supplier device limit metadata default: 5',
    '',
    'Reply confirm to import, or cancel to stop.',
  ].join('\n');
}

export function isImportWizardConfirm(text: string) {
  return ['confirm', 'yes', 'ok', 'oui'].includes(text.trim().toLowerCase());
}

export function isImportWizardCancel(text: string) {
  return ['/cancel_import', 'cancel', 'annuler', 'stop'].includes(text.trim().toLowerCase());
}

function previewSecret(value: string) {
  const compact = value.trim().replace(/\s+/g, ' ');
  if (compact.length <= 18) {
    return `${compact.slice(0, 6)}...`;
  }
  return `${compact.slice(0, 12)}...${compact.slice(-6)}`;
}

function formatBytes(value: string | number | bigint) {
  const bytes = Number(value);
  if (!Number.isFinite(bytes) || bytes <= 0) {
    return 'Unknown';
  }

  const units = ['B', 'KB', 'MB', 'GB', 'TB'];
  let current = bytes;
  let unitIndex = 0;
  while (current >= 1024 && unitIndex < units.length - 1) {
    current /= 1024;
    unitIndex += 1;
  }

  return `${current.toFixed(unitIndex === 0 ? 0 : 1)} ${units[unitIndex]}`;
}
