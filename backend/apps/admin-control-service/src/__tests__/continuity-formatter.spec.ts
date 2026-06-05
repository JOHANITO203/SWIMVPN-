import {
  formatContinuityNoStockAlert,
  formatContinuityPassSummary,
  formatLowStockAlert,
  formatStockHealth,
  type StockHealthItem,
} from '../admin-bot.formatter';

function assert(condition: boolean, message: string) {
  if (!condition) throw new Error(message);
}

const noStock = formatContinuityNoStockAlert({ category: 'WEEK', count: 2 });
assert(noStock.includes('Basic'), 'no-stock alert names the plan label');
assert(/2/.test(noStock), 'no-stock alert shows the at-risk count');
assert(noStock.includes('/stock_health') || noStock.includes('Import'), 'no-stock alert hints an action');

const summary = formatContinuityPassSummary({ reallocated: 3, failed: 1 });
assert(summary.includes('3'), 'summary shows reallocated count');
assert(summary.includes('1'), 'summary shows failed count');

const low = formatLowStockAlert({ category: 'MONTH', remaining: 1 });
assert(low.includes('Premium'), 'low-stock names the plan label');
assert(/1/.test(low), 'low-stock shows remaining');

const now = 1_000_000_000_000;
const day = 24 * 60 * 60 * 1000;
const items: StockHealthItem[] = [
  { category: 'WEEK', healthStatus: 'HEALTHY', usedResaleSlots: 0, maxResaleSlots: 2, supplierExpiresAtMs: now + 10 * day },
  { category: 'WEEK', healthStatus: 'HEALTHY', usedResaleSlots: 2, maxResaleSlots: 2, supplierExpiresAtMs: now + 2 * day },
  { category: 'MONTH', healthStatus: 'DEGRADED', usedResaleSlots: 0, maxResaleSlots: 2, supplierExpiresAtMs: null },
];
const health = formatStockHealth(items, now);
assert(health.includes('Basic'), 'health view lists Basic');
assert(health.includes('Premium'), 'health view lists Premium');
assert(health.includes('Platinum'), 'health view lists Platinum (even if empty)');
assert(health.includes('1 allocatable'), 'Basic has 1 allocatable (one healthy with a free slot)');

console.log('continuity-formatter.spec.ts passed');
