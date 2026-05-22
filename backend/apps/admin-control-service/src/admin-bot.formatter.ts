import { PlanCategory } from '@prisma/client';

type InventoryOverviewItem = {
  id?: string;
  category: PlanCategory | string;
  batchName?: string | null;
  displayProtocol?: string | null;
  inventoryStatus?: string | null;
  healthStatus: string;
  usedResaleSlots: number;
  maxResaleSlots: number;
  folderCode?: string | null;
  adminLabel?: string | null;
  nodeCount?: number | null;
  countriesPreview?: unknown;
  adminPreview?: unknown;
  supplierExpiresAt?: string | null;
};

type TrialInventoryOverviewItem = {
  id?: string;
  campaignCode?: string | null;
  batchName?: string | null;
  displayProtocol?: string | null;
  status: string;
  usedDeviceAssignments: number;
  maxDeviceAssignments: number;
  folderCode?: string | null;
  adminLabel?: string | null;
  nodeCount?: number | null;
  countriesPreview?: unknown;
  adminPreview?: unknown;
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

const CATEGORY_SUPPLIER_CAPACITY: Record<string, number> = {
  WEEK: 2,
  MONTH: 4,
  QUARTER: 6,
};

export const ADMIN_BOT_COMMANDS = [
  { command: 'help', description: 'Show admin command list' },
  { command: 'whoami', description: 'Show your Telegram ids' },
  { command: 'status', description: 'Check admin bot status' },
  { command: 'stock', description: 'Show inventory by plan bucket' },
  { command: 'add_wizard', description: 'Guided supplier config import' },
  { command: 'import', description: 'Show direct import instructions' },
  { command: 'trial_import', description: 'Show trial config import instructions' },
  { command: 'trial_wizard', description: 'Guided trial config import' },
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
  const lines = ['SWIMVPN+ Paid inventory'];

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
      const folder = item.folderCode ? ` ${item.folderCode}` : '';
      const countries = formatCountriesPreview(item.countriesPreview);
      const preview = countries ? ` ${countries}` : '';
      lines.push(`- ${id}${folder}: ${item.healthStatus} ${item.usedResaleSlots}/${item.maxResaleSlots}${preview}${expiry}`);
    }
  }

  return lines.join('\n');
}

export function formatTrialInventoryOverview(items: TrialInventoryOverviewItem[]) {
  const lines = ['SWIMVPN+ Trial Store'];
  const allocatable = items.filter((item) =>
    ['AVAILABLE', 'ASSIGNED'].includes(item.status) &&
    item.usedDeviceAssignments < item.maxDeviceAssignments,
  ).length;

  lines.push(`Trial configs: ${allocatable} allocatable / ${items.length} total`);

  for (const item of items.slice(0, 8)) {
    const id = item.id ? item.id.slice(0, 8) : 'unknown';
    const expiry = item.supplierExpiresAt ? ` expires ${item.supplierExpiresAt.slice(0, 10)}` : '';
    const folder = item.folderCode ? ` ${item.folderCode}` : '';
    const countries = formatCountriesPreview(item.countriesPreview);
    const preview = countries ? ` ${countries}` : '';
    lines.push(`- ${id}${folder}: ${item.status} ${item.usedDeviceAssignments}/${item.maxDeviceAssignments}${preview}${expiry}`);
  }

  return lines.join('\n');
}

export function formatCombinedInventoryOverview(input: {
  paid: InventoryOverviewItem[];
  trial: TrialInventoryOverviewItem[];
}) {
  return [
    formatInventoryOverview(input.paid),
    '',
    formatTrialInventoryOverview(input.trial),
  ].join('\n');
}

export function formatInventoryReview(
  scope: 'paid' | 'trial',
  item: InventoryOverviewItem | TrialInventoryOverviewItem | null,
) {
  if (!item) {
    return 'Review unavailable. The item was not found in current inventory.';
  }

  const countries = formatCountriesPreview(item.countriesPreview) || 'No countries parsed';
  const previewStatus = extractPreviewStatus(item.adminPreview);
  const nodeCount = Number(item.nodeCount ?? 0);
  const folderCode = item.folderCode || item.adminLabel || item.id?.slice(0, 12) || 'unknown';
  const protocol = item.displayProtocol || 'unknown';
  const expires = item.supplierExpiresAt ? String(item.supplierExpiresAt).slice(0, 10) : 'none';

  if (scope === 'trial') {
    const trialItem = item as TrialInventoryOverviewItem;
    return [
      'Trial config review',
      `Folder: ${folderCode}`,
      `Campaign: ${trialItem.campaignCode || 'trial'}`,
      `Protocol: ${protocol}`,
      `Status: ${trialItem.status}`,
      `Capacity: ${trialItem.usedDeviceAssignments}/${trialItem.maxDeviceAssignments} devices`,
      `Nodes: ${nodeCount}`,
      `Countries: ${countries}`,
      `Preview: ${previewStatus}`,
      `Supplier expiry: ${expires}`,
    ].join('\n');
  }

  const paidItem = item as InventoryOverviewItem;
  return [
    'Paid config review',
    `Folder: ${folderCode}`,
    `Bucket: ${CATEGORY_LABELS[String(paidItem.category)] || paidItem.category}`,
    `Protocol: ${protocol}`,
    `Status: ${paidItem.inventoryStatus || paidItem.healthStatus}`,
    `Health: ${paidItem.healthStatus}`,
    `Capacity: ${paidItem.usedResaleSlots}/${paidItem.maxResaleSlots} units`,
    `Nodes: ${nodeCount}`,
    `Countries: ${countries}`,
    `Preview: ${previewStatus}`,
    `Supplier expiry: ${expires}`,
  ].join('\n');
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
    `Supplier stock capacity: ${formatSupplierCapacity(category)}`,
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

export function formatTrialImportInstructions() {
  return [
    'Import a supplier config into the Trial Store:',
    '',
    '/trial_wizard - guided trial config import',
    '/add_trial <config-or-subscription-url>',
    '',
    'Trial configs are stored outside paid inventory.',
    'Raw config is preserved in PostgreSQL.',
    'Pending trial grants can be assigned automatically when capacity becomes available.',
  ].join('\n');
}

export function formatTrialImportResult(result: any) {
  const importedCount = result?.importedCount ?? 0;
  const recoveredPendingCount = result?.recoveredPendingCount ?? 0;
  const failures = Array.isArray(result?.details)
    ? result.details.filter((item: any) => item.status !== 'IMPORTED').length
    : 0;
  const importedDetails = Array.isArray(result?.details)
    ? result.details.filter((item: any) => item.status === 'IMPORTED')
    : [];

  const lines = [
    'Trial config import finished',
    `Imported: ${importedCount}`,
    `Failed: ${failures}`,
    `Recovered pending grants: ${recoveredPendingCount}`,
  ];

  for (const item of importedDetails.slice(0, 3)) {
    const expires = item.supplierExpiresAt ? `Expires: ${String(item.supplierExpiresAt).slice(0, 10)}` : null;
    const provider = item.supplierProviderName ? `Provider: ${item.supplierProviderName}` : null;
    lines.push([
      '',
      `Trial config: ${String(item.id || 'unknown').slice(0, 12)}`,
      `Campaign: ${item.campaignCode || 'trial-2026-05'}`,
      `Protocol: ${item.configType || item.displayProtocol || 'unknown'}`,
      provider,
      expires,
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
    `This supplier link will be stored with ${formatSupplierCapacity(category)}.`,
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
    `Supplier stock capacity: ${formatSupplierCapacity(category)}`,
    'Supplier device limit metadata default: 5',
    '',
    'Reply confirm to import, or cancel to stop.',
  ].join('\n');
}

export function formatTrialImportWizardConfigPrompt() {
  return [
    'Trial config import wizard',
    '',
    'Send the supplier trial config or subscription URL now.',
    'It will be stored in the dedicated Trial Store, not paid inventory.',
    'Raw config will be preserved in PostgreSQL.',
    '',
    'Use /cancel_import to stop.',
  ].join('\n');
}

export function formatTrialImportWizardConfirmation(rawConfig: string) {
  return [
    'Confirm trial config import',
    `Config preview: ${previewSecret(rawConfig)}`,
    'Target: Trial Store',
    'Default campaign: trial-2026-05',
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

function formatCountriesPreview(value: unknown) {
  if (!Array.isArray(value)) {
    return '';
  }

  const countries = value
    .filter((item): item is string => typeof item === 'string' && item.trim().length > 0)
    .map((item) => item.trim())
    .slice(0, 4);

  if (countries.length === 0) {
    return '';
  }

  return countries.join(', ');
}

function extractPreviewStatus(value: unknown) {
  if (
    typeof value === 'object' &&
    value !== null &&
    'previewStatus' in value &&
    typeof (value as { previewStatus?: unknown }).previewStatus === 'string'
  ) {
    return (value as { previewStatus: string }).previewStatus;
  }

  return 'UNKNOWN';
}

function formatSupplierCapacity(category: PlanCategory | string) {
  const capacity = CATEGORY_SUPPLIER_CAPACITY[String(category)] ?? 2;
  return `${capacity} internal capacity units`;
}
