import { LANDING_BASE_URL } from '../landing/landingContent';

// https://api.swimvpn.pro/api/v1 (dérivé comme dans OfferPage)
const API_BASE = `${LANDING_BASE_URL.replace(/^https?:\/\/app\./, 'https://api.')}/api/v1`;

/**
 * Inscription opt-in (double confirmation côté backend). Client-only.
 * Retourne true si la requête a été acceptée (200) — le backend envoie alors le mail de confirmation.
 */
export async function subscribeEmail(email: string, locale: string, source: string): Promise<boolean> {
  if (typeof window === 'undefined') return false; // garde SSR
  try {
    const r = await fetch(`${API_BASE}/newsletter/subscribe`, {
      method: 'POST',
      headers: { 'Content-Type': 'application/json', Accept: 'application/json' },
      body: JSON.stringify({ email, locale, source }),
    });
    return r.ok;
  } catch {
    return false;
  }
}
