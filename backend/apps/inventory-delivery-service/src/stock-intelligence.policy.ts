// Pure stock-intelligence helpers (no I/O) — proactive thresholds, consumption velocity,
// exhaustion forecast and reorder suggestion. Read-only analytics; testable in isolation.

export const DEFAULT_STOCK_THRESHOLDS: Record<string, number> = { WEEK: 8, MONTH: 5, QUARTER: 3 };
export const DEFAULT_VELOCITY_WINDOW_DAYS = 14;
export const DEFAULT_TARGET_DAYS_COVER = 21;
export const DEFAULT_FORECAST_ALERT_DAYS = 5;
const FALLBACK_THRESHOLD = 5;

/** Parse "WEEK=8,MONTH=5,QUARTER=3" into per-category thresholds, merged over defaults. */
export function parseStockThresholds(raw: string | undefined): Record<string, number> {
  const out: Record<string, number> = { ...DEFAULT_STOCK_THRESHOLDS };
  if (!raw) return out;
  for (const pair of raw.split(',')) {
    const [k, v] = pair.split('=').map((s) => s.trim());
    const n = Number(v);
    if (k && Number.isFinite(n) && n >= 0) out[k.toUpperCase()] = Math.floor(n);
  }
  return out;
}

export function resolveThreshold(category: string, thresholds: Record<string, number>): number {
  const t = thresholds[String(category).toUpperCase()];
  return Number.isFinite(t) ? t : FALLBACK_THRESHOLD;
}

/** Parse a positive-integer env value, falling back when absent/invalid. */
export function parsePositiveIntEnv(raw: string | undefined, fallback: number): number {
  const n = Number(raw);
  return Number.isFinite(n) && n > 0 ? Math.floor(n) : fallback;
}

/** Recent sales count over a window → smoothed daily consumption rate. */
export function computeDailyRate(soldCount: number, windowDays: number): number {
  if (!Number.isFinite(soldCount) || soldCount < 0) return 0;
  const w = windowDays > 0 ? windowDays : 1;
  return soldCount / w;
}

/** Days of stock remaining at the current rate. 0 if empty; Infinity if no recent sales. */
export function computeDaysOfStock(available: number, dailyRate: number): number {
  if (available <= 0) return 0;
  if (dailyRate <= 0) return Infinity;
  return available / dailyRate;
}

/** Units to reorder to reach the target days of cover (never negative). */
export function computeReorderQty(available: number, dailyRate: number, targetDaysCover: number): number {
  const target = Math.ceil(Math.max(0, dailyRate) * Math.max(0, targetDaysCover));
  return Math.max(0, target - Math.max(0, available));
}

/** Alert when below the per-plan threshold OR forecast to run out within the alert window. */
export function shouldAlert(
  available: number,
  threshold: number,
  daysOfStock: number,
  forecastAlertDays: number,
): boolean {
  return available < threshold || daysOfStock < forecastAlertDays;
}

export interface StockForecast {
  category: string;
  available: number;
  threshold: number;
  dailyRate: number;
  daysOfStock: number; // Infinity when there are no recent sales
  reorderQty: number;
  alert: boolean;
}

export function computeStockForecast(input: {
  category: string;
  available: number;
  soldCount: number;
  windowDays: number;
  thresholds: Record<string, number>;
  targetDaysCover: number;
  forecastAlertDays: number;
}): StockForecast {
  const threshold = resolveThreshold(input.category, input.thresholds);
  const dailyRate = computeDailyRate(input.soldCount, input.windowDays);
  const daysOfStock = computeDaysOfStock(input.available, dailyRate);
  const reorderQty = computeReorderQty(input.available, dailyRate, input.targetDaysCover);
  const alert = shouldAlert(input.available, threshold, daysOfStock, input.forecastAlertDays);
  return {
    category: input.category,
    available: input.available,
    threshold,
    dailyRate,
    daysOfStock,
    reorderQty,
    alert,
  };
}
