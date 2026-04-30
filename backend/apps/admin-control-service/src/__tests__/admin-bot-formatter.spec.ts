import {
  formatInventoryOverview,
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

console.log('admin bot formatter tests passed');
