import {
  parseHttpHeaders,
  parseSubscriptionMetadata,
  headerHasValues,
  extractCountryEmoji,
} from '../subscription-metadata.parser';

function assert(cond: boolean, message: string) {
  if (!cond) throw new Error(message);
}

// ── 1. Subscription-Userinfo header (PRIMARY expiry source, like v2rayNG/Happ) ──────────────────
{
  // expire is unix epoch seconds; total/upload/download in bytes.
  const expireEpoch = Math.floor(Date.UTC(2027, 5, 19) / 1000); // 2027-06-19
  const h = parseHttpHeaders(
    `upload=1073741824; download=2147483648; total=107374182400; expire=${expireEpoch}`,
    '12',
    'https://panel.example.com/sub?token=abc',
  );
  assert(h.expiresAt === new Date(expireEpoch * 1000).toISOString().replace(/.000Z$/, 'Z'), 'header expire parsed to ISO');
  assert(h.expiresAt!.startsWith('2027-06-19'), 'header expiry is the right day');
  assert(h.trafficUsedBytes === 1073741824 + 2147483648, 'used = upload + download');
  assert(h.trafficTotalBytes === 107374182400, 'total parsed');
  assert(h.autoUpdateIntervalHours === 12, 'profile-update-interval parsed');
  assert(h.providerName === 'panel.example.com', 'provider from source host');
  assert(headerHasValues(h) === true, 'header has values');
}

// header with no useful fields → hasValues false
{
  const h = parseHttpHeaders(undefined, undefined, undefined);
  assert(headerHasValues(h) === false, 'empty header has no values');
}

// ── 2. Text body expiry — German DD.MM.YYYY ─────────────────────────────────────────────────────
{
  const m = parseSubscriptionMetadata('MyProvider\nExpires: 19.06.2027\n15.3GB/1000.0GB');
  assert(m.expiresAt!.startsWith('2027-06-19'), `German date parsed (got ${m.expiresAt})`);
  assert(m.trafficTotalBytes === Math.round(1000.0 * 1024 ** 3), 'total traffic parsed');
  assert(m.trafficUsedBytes === Math.round(15.3 * 1024 ** 3), 'used traffic parsed');
}

// ── 3. Bare date anywhere ───────────────────────────────────────────────────────────────────────
{
  const m = parseSubscriptionMetadata('some header line\n31.12.2027\nvless://x');
  assert(m.expiresAt!.startsWith('2027-12-31'), `bare date parsed (got ${m.expiresAt})`);
}

// ── 4. Russian text date: "истекает 19 июня 2027" ───────────────────────────────────────────────
{
  const m = parseSubscriptionMetadata('Ваша подписка\nистекает 19 июня 2027 года\n10 МБ / ∞');
  assert(m.expiresAt!.startsWith('2027-06-19'), `russian date parsed (got ${m.expiresAt})`);
  assert(m.trafficTotalBytes === undefined, 'unlimited (∞) total = undefined');
  assert(m.trafficUsedBytes === Math.round(10 * 1024 ** 2), 'cyrillic МБ parsed as MB');
}

// ── 5. Invalid date is rejected (no crash, undefined) ───────────────────────────────────────────
{
  const m = parseSubscriptionMetadata('Expires: 31.02.2027');
  assert(m.expiresAt === undefined, 'impossible date 31.02 rejected');
}

// ── 6. Header wins over text ────────────────────────────────────────────────────────────────────
{
  const expireEpoch = Math.floor(Date.UTC(2030, 0, 1) / 1000);
  const header = parseHttpHeaders(`expire=${expireEpoch}`, undefined, undefined);
  const m = parseSubscriptionMetadata('Expires: 19.06.2027', undefined, header);
  assert(m.expiresAt === new Date(expireEpoch * 1000).toISOString().replace(/.000Z$/, 'Z'), 'header expiry overrides body text');
}

// ── 7. Provider name detection skips metadata lines ─────────────────────────────────────────────
{
  const m = parseSubscriptionMetadata('🇩🇪 Germany Premium\nExpires: 19.06.2027\n1GB/10GB');
  assert(m.providerName === 'Germany Premium', `provider strips flag (got ${m.providerName})`);
}

// ── 8. Country emoji extraction ─────────────────────────────────────────────────────────────────
{
  assert(extractCountryEmoji('🇫🇷 France') === '🇫🇷', 'flag emoji extracted');
  assert(extractCountryEmoji('no flag here') === undefined, 'no flag → undefined');
}

console.log('subscription-metadata-parser.spec.ts passed');
