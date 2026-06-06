/** Currencies SwimPay actually collects right now. Driven by env so the user flips rails on without a
 *  code change; the truthful indicator renders exactly this list. Crypto is NOT here (separate rail). */
const KNOWN = new Set(['USD', 'RUB', 'XOF']);

export function resolveSupportedCurrencies(raw: string | undefined): string[] {
  if (!raw || !raw.trim()) return ['RUB']; // today: SwimPay collects RUB only
  const seen = new Set<string>();
  const out: string[] = [];
  for (const part of raw.split(',')) {
    const c = part.trim().toUpperCase();
    if (KNOWN.has(c) && !seen.has(c)) { seen.add(c); out.push(c); }
  }
  return out.length ? out : ['RUB'];
}
