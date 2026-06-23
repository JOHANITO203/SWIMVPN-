import {
  parseStockThresholds,
  resolveThreshold,
  parsePositiveIntEnv,
  computeDailyRate,
  computeDaysOfStock,
  computeReorderQty,
  shouldAlert,
  computeStockForecast,
  DEFAULT_STOCK_THRESHOLDS,
} from '../stock-intelligence.policy';

function assert(condition: boolean, message: string) {
  if (!condition) {
    throw new Error(message);
  }
}

// parseStockThresholds
{
  const def = parseStockThresholds(undefined);
  assert(def.WEEK === DEFAULT_STOCK_THRESHOLDS.WEEK, 'defaults when raw absent');
  const over = parseStockThresholds('WEEK=12, month=4 , bogus, QUARTER=x');
  assert(over.WEEK === 12, 'WEEK overridden');
  assert(over.MONTH === 4, 'month override is case-insensitive');
  assert(over.QUARTER === DEFAULT_STOCK_THRESHOLDS.QUARTER, 'invalid value keeps default');
}

// resolveThreshold
{
  const t = parseStockThresholds(undefined);
  assert(resolveThreshold('MONTH', t) === DEFAULT_STOCK_THRESHOLDS.MONTH, 'known category');
  assert(resolveThreshold('UNKNOWN', t) === 5, 'unknown category falls back to 5');
}

// parsePositiveIntEnv
{
  assert(parsePositiveIntEnv(undefined, 14) === 14, 'fallback when absent');
  assert(parsePositiveIntEnv('21', 14) === 21, 'parses valid');
  assert(parsePositiveIntEnv('0', 14) === 14, 'rejects zero');
  assert(parsePositiveIntEnv('-3', 14) === 14, 'rejects negative');
}

// computeDailyRate
{
  assert(computeDailyRate(28, 14) === 2, '28 sales / 14d = 2/day');
  assert(computeDailyRate(0, 14) === 0, 'no sales = 0');
  assert(computeDailyRate(-5, 14) === 0, 'negative guarded to 0');
  assert(computeDailyRate(10, 0) === 10, 'window 0 guarded to 1');
}

// computeDaysOfStock
{
  assert(computeDaysOfStock(10, 2) === 5, '10 stock / 2 per day = 5 days');
  assert(computeDaysOfStock(0, 2) === 0, 'empty stock = 0 days');
  assert(computeDaysOfStock(10, 0) === Infinity, 'no consumption = Infinity');
}

// computeReorderQty
{
  assert(computeReorderQty(5, 2, 21) === 37, 'ceil(2*21) - 5 = 42 - 5 = 37');
  assert(computeReorderQty(50, 1, 21) === 0, 'enough stock = 0 reorder');
  assert(computeReorderQty(0, 0, 21) === 0, 'no rate, no stock = 0 reorder');
}

// shouldAlert
{
  assert(shouldAlert(3, 5, 100, 5) === true, 'below threshold alerts');
  assert(shouldAlert(20, 5, 3, 5) === true, 'forecast under window alerts even if above threshold');
  assert(shouldAlert(20, 5, 100, 5) === false, 'healthy stock + long runway = no alert');
}

// computeStockForecast (integration)
{
  const thresholds = parseStockThresholds('WEEK=8,MONTH=5,QUARTER=3');
  const f = computeStockForecast({
    category: 'MONTH',
    available: 4,
    soldCount: 28,
    windowDays: 14,
    thresholds,
    targetDaysCover: 21,
    forecastAlertDays: 5,
  });
  assert(f.threshold === 5, 'threshold resolved');
  assert(f.dailyRate === 2, 'rate computed');
  assert(f.daysOfStock === 2, '4 / 2 = 2 days');
  assert(f.reorderQty === Math.max(0, Math.ceil(2 * 21) - 4), 'reorder computed');
  assert(f.alert === true, 'below threshold (4<5) → alert');

  const healthy = computeStockForecast({
    category: 'WEEK',
    available: 40,
    soldCount: 14,
    windowDays: 14,
    thresholds,
    targetDaysCover: 21,
    forecastAlertDays: 5,
  });
  assert(healthy.alert === false, '40 stock, 1/day, 40 days runway → no alert');
}

console.log('stock-intelligence policy tests passed');
