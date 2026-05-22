import {
  ADMIN_BOT_COMMANDS,
  formatAccountingSummary,
  formatCombinedInventoryOverview,
  formatInventoryOverview,
  formatInventoryReview,
  formatImportWizardCategoryPrompt,
  formatImportWizardConfigPrompt,
  formatImportWizardConfirmation,
  formatTrialImportInstructions,
  formatTrialImportResult,
  formatTrialInventoryOverview,
  formatTrialImportWizardConfirmation,
  formatTrialImportWizardConfigPrompt,
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
    id: 'paid-inventory-review-1',
    category: 'WEEK',
    inventoryStatus: 'AVAILABLE',
    healthStatus: 'HEALTHY',
    usedResaleSlots: 1,
    maxResaleSlots: 2,
    folderCode: 'WEEK-VLESS-ABC',
    nodeCount: 2,
    countriesPreview: ['France', 'Germany'],
    adminPreview: { previewStatus: 'PARSED', sample: 'must-not-include-secret-host' },
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
assert(overview.includes('France, Germany'), 'paid stock summary should expose safe country preview');

const trialOverview = formatTrialInventoryOverview([
  {
    id: 'trial-config-review-1',
    campaignCode: 'trial-2026-05',
    status: 'AVAILABLE',
    usedDeviceAssignments: 2,
    maxDeviceAssignments: 5,
    folderCode: 'TRIAL-VLESS-ABC',
    nodeCount: 3,
    countriesPreview: ['France', 'Canada'],
    adminPreview: { previewStatus: 'PARSED', host: 'secret-host' },
  },
] as any);
assert(trialOverview.includes('Trial Store'), 'trial stock summary must name Trial Store');
assert(trialOverview.includes('1 allocatable / 1 total'), 'trial stock allocatable summary failed');
assert(trialOverview.includes('2/5'), 'trial stock capacity summary failed');
assert(trialOverview.includes('France, Canada'), 'trial stock summary should expose safe country preview');

const combinedOverview = formatCombinedInventoryOverview({ paid: [], trial: [] });
assert(combinedOverview.includes('Paid inventory'), 'combined stock must include paid section');
assert(combinedOverview.includes('Trial Store'), 'combined stock must include trial section');

const paidReview = formatInventoryReview('paid', {
  id: 'paid-review-id',
  category: 'WEEK',
  inventoryStatus: 'AVAILABLE',
  healthStatus: 'HEALTHY',
  usedResaleSlots: 1,
  maxResaleSlots: 2,
  folderCode: 'WEEK-VLESS-REVIEW',
  displayProtocol: 'VLESS',
  nodeCount: 2,
  countriesPreview: ['France'],
  adminPreview: { previewStatus: 'PARSED', uuid: 'runtime-secret-uuid' },
} as any);
assert(paidReview.includes('Paid config review'), 'paid review must have title');
assert(paidReview.includes('WEEK-VLESS-REVIEW'), 'paid review must expose folder code');
assert(paidReview.includes('France'), 'paid review must expose countries');
assert(!paidReview.includes('runtime-secret-uuid'), 'paid review must not expose admin preview secrets');

const trialReview = formatInventoryReview('trial', {
  id: 'trial-review-id',
  campaignCode: 'trial-2026-05',
  status: 'AVAILABLE',
  usedDeviceAssignments: 1,
  maxDeviceAssignments: 5,
  folderCode: 'TRIAL-VLESS-REVIEW',
  displayProtocol: 'VLESS',
  nodeCount: 2,
  countriesPreview: ['Germany'],
  adminPreview: { previewStatus: 'PARSED', host: 'trial-secret-host' },
} as any);
assert(trialReview.includes('Trial config review'), 'trial review must have title');
assert(trialReview.includes('1/5 devices'), 'trial review must expose trial device capacity');
assert(!trialReview.includes('trial-secret-host'), 'trial review must not expose admin preview secrets');

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
assert(configPrompt.includes('4 internal capacity units'), 'wizard config prompt must show Premium supplier capacity');

const confirmation = formatImportWizardConfirmation('WEEK' as any, 'vless://1234567890abcdefghijklmnopqrstuvwxyz');
assert(confirmation.includes('Basic'), 'wizard confirmation must show category');
assert(confirmation.includes('confirm'), 'wizard confirmation must ask for confirm');
assert(confirmation.includes('2 internal capacity units'), 'wizard confirmation must show Basic supplier capacity');
assert(!confirmation.includes('abcdefghijklmnopqrstuvwxyz'), 'wizard confirmation must not expose full raw config');

const trialInstructions = formatTrialImportInstructions();
assert(trialInstructions.includes('/add_trial'), 'trial import instructions must expose direct add command');
assert(trialInstructions.includes('/trial_wizard'), 'trial import instructions must expose guided wizard');
assert(trialInstructions.includes('Trial Store'), 'trial import instructions must name Trial Store');

const trialPrompt = formatTrialImportWizardConfigPrompt();
assert(trialPrompt.includes('trial config'), 'trial wizard prompt must request a trial config');
assert(trialPrompt.includes('/cancel_import'), 'trial wizard prompt must show cancel command');

const trialConfirmation = formatTrialImportWizardConfirmation('vless://trial-secret-config-value');
assert(trialConfirmation.includes('Confirm trial config import'), 'trial confirmation must identify trial import');
assert(trialConfirmation.includes('confirm'), 'trial confirmation must ask for confirm');
assert(!trialConfirmation.includes('trial-secret-config-value'), 'trial confirmation must not expose full raw config');

const trialResult = formatTrialImportResult({
  importedCount: 1,
  recoveredPendingCount: 2,
  details: [
    {
      id: 'trial-config-1234567890',
      status: 'IMPORTED',
      campaignCode: 'trial-2026-05',
      configType: 'VLESS',
      supplierProviderName: 'trial-provider',
    },
  ],
});
assert(trialResult.includes('Trial config import finished'), 'trial result must have trial title');
assert(trialResult.includes('Imported: 1'), 'trial result must show imported count');
assert(trialResult.includes('Recovered pending grants: 2'), 'trial result must show recovered grants');
assert(trialResult.includes('trial-2026-05'), 'trial result must show campaign');

assert(isImportWizardConfirm('confirm'), 'confirm text must confirm wizard');
assert(isImportWizardConfirm('yes'), 'yes text must confirm wizard');
assert(isImportWizardCancel('cancel'), 'cancel text must cancel wizard');
assert(isImportWizardCancel('/cancel_import'), 'cancel command must cancel wizard');

assert(
  ADMIN_BOT_COMMANDS.some((command) => command.command === 'add_wizard'),
  'telegram command menu must expose add_wizard',
);
assert(
  ADMIN_BOT_COMMANDS.some((command) => command.command === 'trial_import'),
  'telegram command menu must expose trial_import',
);
assert(
  ADMIN_BOT_COMMANDS.some((command) => command.command === 'trial_wizard'),
  'telegram command menu must expose trial_wizard',
);
assert(
  ADMIN_BOT_COMMANDS.some((command) => command.command === 'stock'),
  'telegram command menu must expose stock',
);
assert(
  ADMIN_BOT_COMMANDS.some((command) => command.command === 'whoami'),
  'telegram command menu must expose whoami',
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
