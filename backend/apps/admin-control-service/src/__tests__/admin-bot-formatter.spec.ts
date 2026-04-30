import {
  ADMIN_BOT_COMMANDS,
  formatAccountingSummary,
  formatInventoryOverview,
  formatImportWizardCategoryPrompt,
  formatImportWizardConfigPrompt,
  formatImportWizardConfirmation,
  formatPendingFulfillment,
  isImportWizardCancel,
  isImportWizardConfirm,
  parseExpenseCommand,
  parseInventoryActionCommand,
  parseRetryCommand,
  mapBotPlanInputToCategory,
} from '../admin-bot.formatter';

function assert(condition: boolean, message: string) {
  if (!condition) {
    throw new Error(message);
  }
}

assert(mapBotPlanInputToCategory('basic') === 'WEEK', 'basic must map to WEEK');
assert(mapBotPlanInputToCategory('premium') === 'MONTH', 'premium must map to MONTH');
assert(mapBotPlanInputToCategory('platinum') === 'QUARTER', 'platinum must map to QUARTER');
assert(mapBotPlanInputToCategory('month') === 'MONTH', 'month alias must map to MONTH');
assert(mapBotPlanInputToCategory('bad') === null, 'invalid plan input must return null');

const overview = formatInventoryOverview([
  {
    category: 'WEEK',
    healthStatus: 'HEALTHY',
    usedResaleSlots: 1,
    maxResaleSlots: 2,
  },
  {
    category: 'WEEK',
    healthStatus: 'FULL',
    usedResaleSlots: 2,
    maxResaleSlots: 2,
  },
  {
    category: 'MONTH',
    healthStatus: 'HEALTHY',
    usedResaleSlots: 0,
    maxResaleSlots: 2,
  },
] as any);

assert(overview.includes('Basic: 1 allocatable / 2 total'), 'Basic stock summary failed');
assert(overview.includes('Premium: 1 allocatable / 1 total'), 'Premium stock summary failed');
assert(overview.includes('Platinum: 0 allocatable / 0 total'), 'Platinum empty summary failed');

const retryOne = parseRetryCommand('/retry ORD-123');
assert(retryOne.mode === 'one' && retryOne.orderRef === 'ORD-123', 'retry order ref parse failed');

const retryAll = parseRetryCommand('/retry all');
assert(retryAll.mode === 'all', 'retry all parse failed');

const retryInvalid = parseRetryCommand('/retry');
assert(retryInvalid.mode === 'invalid', 'retry invalid parse failed');

const inventoryAction = parseInventoryActionCommand('/quota_reached inv-123 quota consumed');
assert(inventoryAction.inventoryItemId === 'inv-123', 'inventory action id parse failed');
assert(inventoryAction.reason === 'quota consumed', 'inventory action reason parse failed');

const pending = formatPendingFulfillment([
  {
    orderRef: 'ORD-1',
    planName: 'Basic',
    planCode: 'WEEK',
    amountRub: '100.00',
    customerEmail: 'a@example.com',
    createdAt: '2026-04-30T00:00:00.000Z',
  },
] as any);
assert(pending.includes('ORD-1'), 'pending fulfillment order missing');
assert(pending.includes('/retry ORD-1'), 'pending fulfillment retry hint missing');

const noPending = formatPendingFulfillment([]);
assert(noPending.includes('No pending fulfillment'), 'empty pending fulfillment message failed');

const accounting = formatAccountingSummary({
  title: 'Revenue today',
  orderCount: 2,
  amountRub: '300.50',
});
assert(accounting.includes('Revenue today'), 'accounting title missing');
assert(accounting.includes('Orders: 2'), 'accounting count missing');
assert(accounting.includes('300.50 RUB'), 'accounting amount missing');

const expense = parseExpenseCommand('/add_expense 1500 RUB supplier renewal');
assert(expense.valid, 'expense parse should be valid');
assert(expense.valid && expense.amount === '1500', 'expense amount parse failed');
assert(expense.valid && expense.currency === 'RUB', 'expense currency parse failed');
assert(expense.valid && expense.note === 'supplier renewal', 'expense note parse failed');

const invalidExpense = parseExpenseCommand('/add_expense abc RUB');
assert(!invalidExpense.valid, 'invalid expense amount should fail');

const categoryPrompt = formatImportWizardCategoryPrompt();
assert(categoryPrompt.includes('Basic'), 'wizard category prompt must mention Basic');
assert(categoryPrompt.includes('Premium'), 'wizard category prompt must mention Premium');
assert(categoryPrompt.includes('Platinum'), 'wizard category prompt must mention Platinum');

const configPrompt = formatImportWizardConfigPrompt('MONTH' as any);
assert(configPrompt.includes('Premium'), 'wizard config prompt must show selected category');
assert(configPrompt.includes('2 customer orders'), 'wizard config prompt must show resale cap');

const confirmation = formatImportWizardConfirmation('WEEK' as any, 'vless://1234567890abcdefghijklmnopqrstuvwxyz');
assert(confirmation.includes('Basic'), 'wizard confirmation must show category');
assert(confirmation.includes('confirm'), 'wizard confirmation must ask for confirm');
assert(!confirmation.includes('abcdefghijklmnopqrstuvwxyz'), 'wizard confirmation must not expose full raw config');

assert(isImportWizardConfirm('confirm'), 'confirm text must confirm wizard');
assert(isImportWizardConfirm('yes'), 'yes text must confirm wizard');
assert(isImportWizardCancel('cancel'), 'cancel text must cancel wizard');
assert(isImportWizardCancel('/cancel_import'), 'cancel command must cancel wizard');

assert(
  ADMIN_BOT_COMMANDS.some((command) => command.command === 'add_wizard'),
  'telegram command menu must expose add_wizard',
);
assert(
  ADMIN_BOT_COMMANDS.some((command) => command.command === 'stock'),
  'telegram command menu must expose stock',
);
assert(
  ADMIN_BOT_COMMANDS.every((command) => /^[a-z0-9_]{1,32}$/.test(command.command)),
  'telegram commands must use valid bot command names',
);
assert(
  ADMIN_BOT_COMMANDS.every((command) => command.description.length > 0 && command.description.length <= 256),
  'telegram command descriptions must be present and valid',
);

console.log('admin bot formatter tests passed');
