import { PlanCategory } from '@prisma/client';
import { Markup } from 'telegraf';

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
  WEEK: 'Basic 🟢',
  MONTH: 'Premium 💎',
  QUARTER: 'Platinum 🏆',
};

const STATUS_EMOJIS: Record<string, string> = {
  HEALTHY: '✅',
  AVAILABLE: '✅',
  ASSIGNED: '👤',
  DISABLED: '🛑',
  EXPIRED: '⏳',
  DEAD: '💀',
  QUOTA_REACHED: '⚠️',
  UNKNOWN: '❓',
};

function getStatusEmoji(status?: string | null): string {
  if (!status) return STATUS_EMOJIS.UNKNOWN;
  return STATUS_EMOJIS[status] || STATUS_EMOJIS.UNKNOWN;
}

function formatProgressBar(current: number, max: number): string {
  const size = 5;
  const progress = Math.min(Math.max(Math.round((current / max) * size), 0), size);
  const filled = '🔵'.repeat(progress);
  const empty = '⚪'.repeat(size - progress);
  return `${filled}${empty}`;
}

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
  { command: 'review', description: 'Review one paid or trial config' },
  { command: 'add_wizard', description: 'Guided supplier config import' },
  { command: 'import', description: 'Show direct import instructions' },
  { command: 'trial_import', description: 'Show trial config import instructions' },
  { command: 'trial_wizard', description: 'Guided trial config import' },
  { command: 'pending', description: 'Show orders waiting for capacity' },
  { command: 'retry', description: 'Retry one order or all pending orders' },
  { command: 'orders', description: 'Show recent orders' },
  { command: 'orders_today', description: 'Show today order count' },
  { command: 'revenue_today', description: 'Show today revenue' },
  { command: 'finance', description: 'Unified financial dashboard' },
  { command: 'add_expense', description: 'Record business expense' },
  { command: 'profit_month', description: 'Show current month profit' },
  { command: 'expire', description: 'Mark supplier config expired' },
  { command: 'disable', description: 'Disable supplier config' },
  { command: 'quota_reached', description: 'Mark supplier quota exhausted' },
  { command: 'delete', description: 'Delete a paid config' },
  { command: 'delete_trial', description: 'Delete a trial config' },
  { command: 'superdelete', description: 'FORCE delete item & revoke user access' },
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
  const lines = ['📦 *SWIMVPN+ Paid Inventory*'];

  for (const category of [PlanCategory.WEEK, PlanCategory.MONTH, PlanCategory.QUARTER]) {
    const categoryItems = items.filter((item) => item.category === category);
    const allocatable = categoryItems.filter((item) =>
      item.healthStatus === 'HEALTHY' &&
      item.usedResaleSlots < item.maxResaleSlots,
    ).length;

    lines.push(`\n*${CATEGORY_LABELS[category]}*`);
    lines.push(`${allocatable} allocatable / ${categoryItems.length} total`);

    for (const item of categoryItems.slice(0, 5)) {
      const id = item.id ? item.id.slice(0, 8) : 'unknown';
      const folder = item.folderCode ? ` \`${item.folderCode}\`` : ` \`${item.id}\``;
      const countries = formatCountriesPreview(item.countriesPreview);
      const preview = countries ? ` ${countries}` : '';
      const emoji = getStatusEmoji(item.healthStatus);
      const bar = formatProgressBar(item.usedResaleSlots, item.maxResaleSlots);

      lines.push(`${emoji}${folder} ${bar} (${item.usedResaleSlots}/${item.maxResaleSlots})${preview}`);
    }
  }

  return lines.join('\n');
}

export function formatTrialInventoryOverview(items: TrialInventoryOverviewItem[]) {
  const lines = ['🧪 *SWIMVPN+ Trial Store*'];
  const allocatable = items.filter((item) =>
    ['AVAILABLE', 'ASSIGNED'].includes(item.status) &&
    item.usedDeviceAssignments < item.maxDeviceAssignments,
  ).length;

  lines.push(`${allocatable} allocatable / ${items.length} total`);

  for (const item of items.slice(0, 8)) {
    const id = item.id ? item.id.slice(0, 8) : 'unknown';
    const folder = item.folderCode ? ` \`${item.folderCode}\`` : ` \`${item.id}\``;
    const countries = formatCountriesPreview(item.countriesPreview);
    const preview = countries ? ` ${countries}` : '';
    const emoji = getStatusEmoji(item.status);
    const bar = formatProgressBar(item.usedDeviceAssignments, item.maxDeviceAssignments);

    lines.push(`${emoji}${folder} ${bar} (${item.usedDeviceAssignments}/${item.maxDeviceAssignments})${preview}`);
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
    '',
    'Review existing stock:',
    '/review <id-or-folder>',
    '/review paid <id-or-folder>',
    '/review trial <id-or-folder>',
  ].join('\n');
}

export function formatInventoryReview(
  scope: 'paid' | 'trial',
  item: InventoryOverviewItem | TrialInventoryOverviewItem | null,
) {
  if (!item) {
    return '❌ *Review unavailable.*\nThe item was not found or has been deleted.';
  }

  const countries = formatCountriesPreview(item.countriesPreview) || 'None';
  const previewStatus = extractPreviewStatus(item.adminPreview);
  const nodeCount = Number(item.nodeCount ?? 0);
  const folderCode = item.folderCode || item.adminLabel || item.id?.slice(0, 12) || 'unknown';
  const protocol = item.displayProtocol || 'unknown';
  const expires = item.supplierExpiresAt ? String(item.supplierExpiresAt).slice(0, 10) : 'never';

  if (scope === 'trial') {
    const trialItem = item as TrialInventoryOverviewItem;
    const emoji = getStatusEmoji(trialItem.status);
    const bar = formatProgressBar(trialItem.usedDeviceAssignments, trialItem.maxDeviceAssignments);

    return [
      `🧪 *Trial Config: ${folderCode}*`,
      '',
      `📍 *Campaign:* \`${trialItem.campaignCode || 'trial'}\``,
      `🔌 *Protocol:* \`${protocol}\``,
      `🛡️ *Status:* ${emoji} ${trialItem.status}`,
      `📊 *Capacity:* ${bar} (${trialItem.usedDeviceAssignments}/${trialItem.maxDeviceAssignments})`,
      `🗺️ *Countries:* ${countries}`,
      `📉 *Nodes:* ${nodeCount}`,
      `👁️ *Preview:* ${previewStatus}`,
      `📅 *Supplier expiry:* ${expires}`,
      '',
      `🆔 *ID (cliquez pour copier) :*`,
      `\`${item.id}\``,
    ].join('\n');
  }

  const paidItem = item as InventoryOverviewItem;
  const emoji = getStatusEmoji(paidItem.healthStatus);
  const bar = formatProgressBar(paidItem.usedResaleSlots, paidItem.maxResaleSlots);

  return [
    `💎 *Paid Config: ${folderCode}*`,
    '',
    `🏷️ *Bucket:* ${CATEGORY_LABELS[String(paidItem.category)] || paidItem.category}`,
    `🔌 *Protocol:* \`${protocol}\``,
    `🛡️ *Status:* ${emoji} ${paidItem.inventoryStatus || paidItem.healthStatus}`,
    `📊 *Capacity:* ${bar} (${paidItem.usedResaleSlots}/${paidItem.maxResaleSlots})`,
    `🗺️ *Countries:* ${countries}`,
    `📉 *Nodes:* ${nodeCount}`,
    `👁️ *Preview:* ${previewStatus}`,
    `📅 *Supplier expiry:* ${expires}`,
    '',
    `🆔 *ID (cliquez pour copier) :*`,
    `\`${item.id}\``,
  ].join('\n');
}

export function getInventoryActionKeyboard(scope: 'paid' | 'trial', id: string) {
  const buttons = [
    [
      Markup.button.callback('🛑 Disable', `disable:${scope}:${id}`),
      Markup.button.callback('⏳ Expire', `expire:${scope}:${id}`),
    ],
    [
      Markup.button.callback('🗑️ Delete', `delete_confirm:${scope}:${id}`),
      Markup.button.callback('💀 SUPER DELETE', `superdelete_confirm:${scope}:${id}`),
    ],
    [
      Markup.button.callback('🔄 Refresh', `review:${scope}:${id}`),
      Markup.button.callback('🔙 Back to Stock', 'stock'),
    ],
  ];
  return Markup.inlineKeyboard(buttons);
}

export function getDeleteConfirmationKeyboard(scope: 'paid' | 'trial', id: string) {
  return Markup.inlineKeyboard([
    [
      Markup.button.callback('✅ Yes, Delete', `delete_execute:${scope}:${id}`),
      Markup.button.callback('❌ No, Cancel', `review:${scope}:${id}`),
    ],
  ]);
}

export function getSuperDeleteConfirmationKeyboard(scope: 'paid' | 'trial', id: string) {
  return Markup.inlineKeyboard([
    [
      Markup.button.callback('💀 FORCE SUPER DELETE', `superdelete_execute:${scope}:${id}`),
    ],
    [
      Markup.button.callback('❌ Cancel', `review:${scope}:${id}`),
    ],
  ]);
}

export function getOrderActionKeyboard(orderRef: string) {
  return Markup.inlineKeyboard([
    [
      Markup.button.callback('🔄 Retry Fulfillment', `retry_order:${orderRef}`),
    ],
  ]);
}

export function getFinanceDashboardKeyboard() {
  return Markup.inlineKeyboard([
    [
      Markup.button.callback('📈 Refresh', 'finance_refresh'),
      Markup.button.callback('💸 Add Expense', 'add_expense_start'),
    ],
    [
      Markup.button.callback('📅 Month Profit', 'profit_month_action'),
      Markup.button.callback('🔙 Back', 'stock'),
    ],
  ]);
}

export function formatFinanceDashboard(data: {
  todayOrders: number;
  todayRevenue: string;
  monthRevenue: string;
  monthExpense: string;
  monthProfit: string;
}) {
  return [
    '📊 *SWIMVPN+ Financial Dashboard*',
    '',
    '*Today Activity*',
    `✅ Orders: ${data.todayOrders}`,
    `💰 Revenue: ${data.todayRevenue} RUB`,
    '',
    '*Monthly Overview*',
    `💎 Revenue: ${data.monthRevenue} RUB`,
    `📉 Expenses: ${data.monthExpense} RUB`,
    `📈 Profit: ${data.monthProfit} RUB`,
    '',
    `_Updated: ${new Date().toLocaleTimeString()}_`,
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
      `🆔 *ID:* \`${item.id}\``,
      `🔌 *Protocol:* \`${item.configType || item.displayProtocol || 'unknown'}\``,
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
      `🆔 *ID:* \`${item.id}\``,
      `📍 *Campaign:* \`${item.campaignCode || 'trial-2026-05'}\``,
      `🔌 *Protocol:* \`${item.configType || item.displayProtocol || 'unknown'}\``,
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

export type ParsedReviewCommand =
  | { valid: true; scope: 'paid' | 'trial' | 'any'; query: string }
  | { valid: false; reason: string };

export function parseReviewCommand(text: string): ParsedReviewCommand {
  const match = text.trim().match(/^\/review(?:@\w+)?(?:\s+([\s\S]+))?$/i);
  const raw = match?.[1]?.trim();
  if (!raw) {
    return {
      valid: false,
      reason: 'Usage: /review <id-or-folder> or /review paid|trial <id-or-folder>',
    };
  }

  const scoped = raw.match(/^(paid|trial)\s+(.+)$/i);
  if (scoped) {
    const query = scoped[2].trim();
    if (!query) {
      return {
        valid: false,
        reason: 'Usage: /review paid|trial <id-or-folder>',
      };
    }
    return {
      valid: true,
      scope: scoped[1].toLowerCase() as 'paid' | 'trial',
      query,
    };
  }

  return {
    valid: true,
    scope: 'any',
    query: raw,
  };
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
    `📊 *${input.title}*`,
    `📦 Orders: ${input.orderCount}`,
    `💰 Revenue: ${input.amountRub} RUB`,
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
