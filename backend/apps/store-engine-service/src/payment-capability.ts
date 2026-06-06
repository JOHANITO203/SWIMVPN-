/** Currencies SwimPay actually collects right now. Driven by env so the user flips rails on without a
 *  code change; the truthful indicator renders exactly this list. Crypto is NOT here (separate rail). */
const KNOWN = new Set(['USD', 'RUB', 'XOF']);

// SwimPay's live rails today: RUB, XOF, USD. Env can still override/trim the list without a code change.
const DEFAULT_SUPPORTED = ['RUB', 'XOF', 'USD'];

export function resolveSupportedCurrencies(raw: string | undefined): string[] {
  if (!raw || !raw.trim()) return [...DEFAULT_SUPPORTED];
  const seen = new Set<string>();
  const out: string[] = [];
  for (const part of raw.split(',')) {
    const c = part.trim().toUpperCase();
    if (KNOWN.has(c) && !seen.has(c)) { seen.add(c); out.push(c); }
  }
  return out.length ? out : [...DEFAULT_SUPPORTED];
}
