/**
 * Faithful TypeScript port of the Android app's `SubscriptionMetadataParser.kt`.
 *
 * This is what lets the backend read a supplier config's EXPIRATION (and traffic/provider) at the
 * same level as the main app parser. Two sources, by priority:
 *   1. The `Subscription-Userinfo` HTTP response header (`expire=<unix>; total=; upload=; download=`)
 *      — the standard v2rayNG/Happ mechanism, parsed by parseHttpHeaders().
 *   2. The subscription body / pasted text — parsed by parse() (broad date + traffic regexes,
 *      German `DD.MM.YYYY`, bare dates, Russian `истекает N mois AAAA`).
 *
 * Pure module (no I/O) so it is unit-testable without network.
 */

export interface SubscriptionHeaderMetadata {
  providerName?: string;
  trafficUsedBytes?: number;
  trafficTotalBytes?: number;
  expiresAt?: string; // ISO 8601 UTC
  autoUpdateIntervalHours?: number;
  warnings: string[];
}

export interface SubscriptionMetadataEnvelope {
  providerName?: string;
  trafficUsedBytes?: number;
  trafficTotalBytes?: number;
  expiresAt?: string; // ISO 8601 UTC
  autoUpdateIntervalHours?: number;
  warnings: string[];
}

const RUSSIAN_MONTH_NUMBERS: Record<string, number> = {
  января: 1,
  февраля: 2,
  марта: 3,
  апреля: 4,
  мая: 5,
  июня: 6,
  июля: 7,
  августа: 8,
  сентября: 9,
  октября: 10,
  ноября: 11,
  декабря: 12,
};

// NOTE: regexes are rebuilt per use where the global/find semantics matter; source strings mirror
// the Kotlin originals (case-insensitive + unicode).
const TRAFFIC_SRC =
  '(\\d+(?:[.,]\\d+)?)\\s*((?:[KMGT]B|[КМГТ]Б))\\s*/\\s*(∞|infinity|unlimited|illimited|безлимит(?:ный)?|неогранич(?:енно|енный)?|(\\d+(?:[.,]\\d+)?)\\s*((?:[KMGT]B|[КМГТ]Б)))';
const EXPIRY_SRC =
  '(?:expires?|expiry|exp|истекает|действует\\s+до|до)\\s*[:\\-]?\\s*(\\d{2}\\.\\d{2}\\.\\d{4})';
const RUSSIAN_TEXT_DATE_SRC =
  '(?:истекает|действует\\s+до|до)\\s*[:\\-]?\\s*(\\d{1,2})\\s+([а-яё]+)\\s+(\\d{4})\\s*(?:года|г\\.?)?';
const BARE_DATE_SRC = '\\b(\\d{2}\\.\\d{2}\\.\\d{4})\\b';
const AUTO_UPDATE_SRC =
  '(?:autoupdate|auto[-\\s]?update|автообновление)\\s*[-: ]*\\s*(\\d+)\\s*(?:h|hr|hrs|hour|hours|ч|час|часа|часов)\\.?';

const trafficRegex = () => new RegExp(TRAFFIC_SRC, 'iu');
const expiryRegex = () => new RegExp(EXPIRY_SRC, 'iu');
const russianTextDateRegex = () => new RegExp(RUSSIAN_TEXT_DATE_SRC, 'iu');
const bareDateRegex = () => new RegExp(BARE_DATE_SRC, 'iu');
const autoUpdateRegex = () => new RegExp(AUTO_UPDATE_SRC, 'iu');

// Match the app's Kotlin `Instant.toString()`, which OMITS the fractional part when it is zero
// (`2026-05-21T00:00:00Z`, not `...00.000Z`). Our dates are whole-second, so strip `.000`.
function toInstantIso(date: Date): string {
  return date.toISOString().replace(/\.000Z$/, 'Z');
}

function isoFromEpochSeconds(epochSeconds: number): string | undefined {
  if (!Number.isFinite(epochSeconds) || epochSeconds <= 0) return undefined;
  try {
    return toInstantIso(new Date(epochSeconds * 1000));
  } catch {
    return undefined;
  }
}

function isoFromYmd(year: number, month: number, day: number): string | undefined {
  // month is 1-based here. Validate to reject impossible dates (e.g. 31.02.2026).
  const d = new Date(Date.UTC(year, month - 1, day));
  if (d.getUTCFullYear() !== year || d.getUTCMonth() !== month - 1 || d.getUTCDate() !== day) {
    return undefined;
  }
  return toInstantIso(d);
}

function toBytes(value: string, unit: string): number {
  const normalizedValue = Number.parseFloat(value.replace(',', '.'));
  if (!Number.isFinite(normalizedValue)) return 0;
  const normalizedUnit = unit
    .toUpperCase()
    .replace('К', 'K')
    .replace('М', 'M')
    .replace('Г', 'G')
    .replace('Т', 'T')
    .replace('Б', 'B');
  const multiplier =
    normalizedUnit === 'KB'
      ? 1024
      : normalizedUnit === 'MB'
        ? 1024 * 1024
        : normalizedUnit === 'GB'
          ? 1024 * 1024 * 1024
          : normalizedUnit === 'TB'
            ? 1024 * 1024 * 1024 * 1024
            : 1;
  return Math.round(normalizedValue * multiplier);
}

export function extractCountryEmoji(text?: string | null): string | undefined {
  if (!text || !text.trim()) return undefined;
  const codePoints = Array.from(text).map((c) => c.codePointAt(0) ?? 0);
  for (let i = 0; i < codePoints.length - 1; i += 1) {
    const first = codePoints[i];
    const second = codePoints[i + 1];
    if (first >= 0x1f1e6 && first <= 0x1f1ff && second >= 0x1f1e6 && second <= 0x1f1ff) {
      return String.fromCodePoint(first) + String.fromCodePoint(second);
    }
  }
  return undefined;
}

function parseTraffic(text: string): { usedBytes?: number; totalBytes?: number; warnings: string[] } {
  const match = trafficRegex().exec(text);
  const warnings: string[] = [];

  const usedBytes = match ? toBytes(match[1], match[2]) : undefined;

  const totalToken = (match?.[3] ?? '').trim();
  const explicitTotalValue = (match?.[4] ?? '').trim();
  const explicitTotalUnit = (match?.[5] ?? '').trim();

  let totalBytes: number | undefined;
  if (explicitTotalValue && explicitTotalUnit) {
    totalBytes = toBytes(explicitTotalValue, explicitTotalUnit);
  } else {
    totalBytes = undefined; // ∞ / unlimited / безлимит / неогранич → unlimited (undefined)
  }

  if (/Закончился трафик/i.test(text)) warnings.push('Закончился трафик');
  if (/Сбросить/i.test(text)) warnings.push('Сбросить');

  return { usedBytes, totalBytes, warnings };
}

function parseExpiry(text: string): string | undefined {
  const numericDate = expiryRegex().exec(text)?.[1] ?? bareDateRegex().exec(text)?.[1];
  if (numericDate) {
    const [dd, mm, yyyy] = numericDate.split('.').map((n) => Number.parseInt(n, 10));
    return isoFromYmd(yyyy, mm, dd);
  }

  const russian = russianTextDateRegex().exec(text);
  if (!russian) return undefined;
  const day = Number.parseInt(russian[1], 10);
  const monthName = (russian[2] || '').toLowerCase();
  const month = RUSSIAN_MONTH_NUMBERS[monthName];
  const year = Number.parseInt(russian[3], 10);
  if (!Number.isFinite(day) || !month || !Number.isFinite(year)) return undefined;
  return isoFromYmd(year, month, day);
}

function parseAutoUpdateHours(text: string): number | undefined {
  const m = autoUpdateRegex().exec(text);
  const n = m ? Number.parseInt(m[1], 10) : NaN;
  return Number.isFinite(n) ? n : undefined;
}

function detectProviderName(text: string, sourceUrl?: string): string | undefined {
  const line = text
    .split('\n')
    .map((l) => l.trim())
    .find(
      (l) =>
        l &&
        !/^https?:\/\//i.test(l) &&
        !/^vless:\/\//i.test(l) &&
        !/^vmess:\/\//i.test(l) &&
        !/^trojan:\/\//i.test(l) &&
        !/^ss:\/\//i.test(l) &&
        !trafficRegex().test(l) &&
        !expiryRegex().test(l) &&
        !russianTextDateRegex().test(l) &&
        !autoUpdateRegex().test(l) &&
        !/ваша подписка/i.test(l) &&
        !/осталось/i.test(l) &&
        !/тариф/i.test(l) &&
        !/трафик/i.test(l) &&
        !/израсходовано/i.test(l) &&
        !/лимит устройств/i.test(l) &&
        !/подключили/i.test(l) &&
        !/^Закончился трафик$/i.test(l) &&
        !/^Сбросить$/i.test(l) &&
        !l.startsWith('{') &&
        !l.startsWith('['),
    );

  const candidate = line?.replace(/^"|"$/g, '').trim();
  if (candidate) {
    const flag = extractCountryEmoji(candidate);
    const stripped = flag ? candidate.slice(flag.length).trim() : candidate;
    return stripped || candidate.trim();
  }

  if (sourceUrl) {
    try {
      const host = new URL(sourceUrl).hostname.replace(/^www\./, '').trim();
      return host || undefined;
    } catch {
      return undefined;
    }
  }
  return undefined;
}

export function parseHttpHeaders(
  subscriptionUserInfo?: string | null,
  profileUpdateInterval?: string | null,
  sourceUrl?: string,
): SubscriptionHeaderMetadata {
  const fields: Record<string, string> = {};
  if (subscriptionUserInfo) {
    for (const part of subscriptionUserInfo.split(';')) {
      const eq = part.indexOf('=');
      if (eq < 0) continue;
      const key = part.slice(0, eq).trim().toLowerCase();
      const value = part.slice(eq + 1).trim();
      if (key && value) fields[key] = value;
    }
  }

  const toLong = (v?: string) => {
    if (v == null) return undefined;
    const n = Number.parseInt(v, 10);
    return Number.isFinite(n) ? n : undefined;
  };

  const upload = toLong(fields['upload']);
  const download = toLong(fields['download']);
  const total = toLong(fields['total']);
  const expireEpoch = toLong(fields['expire']);
  const expiresAt = expireEpoch != null ? isoFromEpochSeconds(expireEpoch) : undefined;

  const intervalRaw = profileUpdateInterval?.trim();
  const interval = intervalRaw ? Number.parseInt(intervalRaw, 10) : NaN;
  const autoUpdateIntervalHours = Number.isFinite(interval) && interval > 0 ? interval : undefined;

  const usedParts = [upload, download].filter((n): n is number => typeof n === 'number');
  const used = usedParts.length ? usedParts.reduce((a, b) => a + b, 0) : undefined;

  const warnings: string[] = [];
  if (subscriptionUserInfo && subscriptionUserInfo.trim()) warnings.push('Parsed subscription-userinfo response header');
  if (profileUpdateInterval && profileUpdateInterval.trim()) warnings.push('Parsed profile-update-interval response header');

  let providerName: string | undefined;
  if (sourceUrl) {
    try {
      providerName = new URL(sourceUrl).hostname.replace(/^www\./, '');
    } catch {
      providerName = undefined;
    }
  }

  return {
    providerName,
    trafficUsedBytes: used,
    trafficTotalBytes: total,
    expiresAt,
    autoUpdateIntervalHours,
    warnings,
  };
}

export function headerHasValues(m?: SubscriptionHeaderMetadata | null): boolean {
  if (!m) return false;
  return (
    m.expiresAt != null ||
    m.trafficUsedBytes != null ||
    m.trafficTotalBytes != null ||
    m.autoUpdateIntervalHours != null
  );
}

/**
 * Full metadata extraction from a subscription body / pasted text, with header metadata taking
 * priority over text-parsed values (mirrors the app's SubscriptionMetadataParser.parse).
 */
export function parseSubscriptionMetadata(
  payload: string,
  sourceUrl?: string,
  headerMetadata?: SubscriptionHeaderMetadata | null,
): SubscriptionMetadataEnvelope {
  const normalized = (payload || '').replace(/\r/g, '\n');
  const traffic = parseTraffic(normalized);
  const expiresAt = parseExpiry(normalized);
  const providerName = detectProviderName(normalized, sourceUrl);
  const autoUpdateHours = parseAutoUpdateHours(normalized);
  const warnings = [...traffic.warnings, ...(headerMetadata?.warnings ?? [])];

  return {
    providerName: headerMetadata?.providerName ?? providerName,
    trafficUsedBytes: headerMetadata?.trafficUsedBytes ?? traffic.usedBytes,
    trafficTotalBytes: headerMetadata?.trafficTotalBytes ?? traffic.totalBytes,
    expiresAt: headerMetadata?.expiresAt ?? expiresAt,
    autoUpdateIntervalHours: headerMetadata?.autoUpdateIntervalHours ?? autoUpdateHours,
    warnings,
  };
}
