import { Injectable } from '@nestjs/common';
import { ConfigService } from '@nestjs/config';
import { createHash, createHmac, timingSafeEqual } from 'crypto';
import { TelegramAuthPayload } from '@app/contracts';

// Tribute is a WEB-ONLY payment rail (the Android app keeps SwimPay + Crypto).
// Hard constraints verified against tribute.tg/api/v1/openapi (2026-06):
//   - No checkout-creation API: products are fixed pre-created links.
//   - The payment webhook carries ONLY Telegram identity (telegram_user_id),
//     no email / order-ref / metadata / comment.
// So correlation is done BEFORE payment: the offer page binds a verified Telegram
// identity (Login Widget) to the email, stored on the pending order. The webhook
// then matches by telegram_user_id + product → plan.

export interface TributePurchase {
  telegramUserId: string;
  productId: string;
  purchaseId: string;
  amount?: string;
  currency?: string;
  eventName: string;
}

export interface TributeVerifiedAuth {
  telegramUserId: string;
  username?: string;
  firstName?: string;
}

// New-purchase events we act on. Renewals/cancellations are out of scope for v1
// (no pending web order to match) and are acknowledged + ignored.
const PURCHASE_EVENT_NAMES = new Set(['newDigitalProduct', 'newSubscription']);

@Injectable()
export class TributePayService {
  // Telegram Login Widget auth is rejected if older than this (replay guard).
  private static readonly LOGIN_MAX_AGE_MS = 24 * 60 * 60 * 1000;

  constructor(private readonly configService: ConfigService) {}

  // Webhook handling needs the API key (signature); the Login Widget needs the bot token.
  isConfigured() {
    return !!this.apiKey && !!this.botToken;
  }

  // ── Telegram Login Widget validation ──────────────────────────────────────
  // https://core.telegram.org/widgets/login#checking-authorization
  // secret = SHA256(bot_token); hash = HMAC_SHA256(data_check_string, secret).
  verifyLoginWidget(auth: TelegramAuthPayload | undefined): TributeVerifiedAuth {
    if (!this.botToken) {
      throw new Error('Telegram bot token is not configured');
    }
    if (!auth || typeof auth !== 'object') {
      throw new Error('Telegram auth payload is required');
    }
    const { hash } = auth;
    if (!hash || typeof hash !== 'string') {
      throw new Error('Telegram auth hash is missing');
    }

    const dataCheckString = Object.keys(auth)
      .filter((k) => k !== 'hash' && auth[k as keyof TelegramAuthPayload] !== undefined)
      .sort()
      .map((k) => `${k}=${auth[k as keyof TelegramAuthPayload]}`)
      .join('\n');

    const secret = createHash('sha256').update(this.botToken).digest();
    const expected = createHmac('sha256', secret).update(dataCheckString).digest('hex');

    if (!this.safeEqualHex(expected, hash)) {
      throw new Error('Invalid Telegram login signature');
    }

    const authDateMs = Number(auth.auth_date) * 1000;
    if (!Number.isFinite(authDateMs) || Date.now() - authDateMs > TributePayService.LOGIN_MAX_AGE_MS) {
      throw new Error('Telegram login is stale');
    }

    const telegramUserId = this.stringFrom(auth.id);
    if (!telegramUserId) {
      throw new Error('Telegram login is missing user id');
    }

    return {
      telegramUserId,
      username: this.stringFrom(auth.username),
      firstName: this.stringFrom(auth.first_name),
    };
  }

  // ── Webhook signature verification ─────────────────────────────────────────
  // Tribute signs the raw body: trbt-signature = HMAC_SHA256(rawBody, apiKey).
  verifyWebhook(
    rawBody: string | Buffer,
    headers: Record<string, string | string[] | number | undefined>,
  ): Record<string, unknown> {
    if (!this.apiKey) {
      throw new Error('Tribute API key is not configured');
    }

    const signature = this.getHeader(headers, 'trbt-signature');
    if (!signature) {
      throw new Error('Tribute signature header is required');
    }

    const payload = Buffer.isBuffer(rawBody) ? rawBody.toString('utf8') : rawBody;
    const expected = createHmac('sha256', this.apiKey).update(payload).digest('hex');
    if (!this.safeEqualHex(expected, signature)) {
      throw new Error('Invalid Tribute webhook signature');
    }

    const parsed = JSON.parse(payload);
    if (!parsed || typeof parsed !== 'object' || Array.isArray(parsed)) {
      throw new Error('Tribute webhook payload must be a JSON object');
    }
    return parsed as Record<string, unknown>;
  }

  // Pull the correlation fields out of a new-purchase webhook. Returns null for
  // events we don't act on (renewals, cancellations, donations, physical orders).
  extractPurchase(event: Record<string, unknown>): TributePurchase | null {
    const eventName =
      this.stringFrom(event.name) || this.stringFrom(event.event) || this.stringFrom(event.type);
    if (!eventName || !PURCHASE_EVENT_NAMES.has(eventName)) {
      return null;
    }

    const payload = (event.payload && typeof event.payload === 'object'
      ? (event.payload as Record<string, unknown>)
      : event) as Record<string, unknown>;

    const telegramUserId = this.stringFrom(payload.telegram_user_id);
    // newDigitalProduct → product_id; newSubscription → subscription_id.
    const productId = this.stringFrom(payload.product_id) || this.stringFrom(payload.subscription_id);
    const purchaseId =
      this.stringFrom(payload.purchase_id) ||
      this.stringFrom(payload.transaction_id) ||
      this.stringFrom(payload.subscription_id);

    if (!telegramUserId || !productId || !purchaseId) {
      return null;
    }

    return {
      telegramUserId,
      productId,
      purchaseId,
      // NOTE: confirm the amount unit (major vs minor) against a live Tribute webhook
      // before trusting `amount` for accounting; fulfillment does not depend on it.
      amount: this.stringFrom(payload.amount),
      currency: this.stringFrom(payload.currency),
      eventName,
    };
  }

  // product_id → plan code (WEEK/MONTH/QUARTER), from TRIBUTE_PRODUCT_MAP.
  resolvePlanCodeFromProduct(productId: string): string | undefined {
    return this.productMap[productId];
  }

  // plan code → fixed Tribute product link, from TRIBUTE_LINK_MAP. Used as the
  // checkout redirectUrl so the backend owns the single source of truth for links.
  resolveProductLink(planCode: string): string | null {
    return this.linkMap[planCode] || null;
  }

  // ── helpers ────────────────────────────────────────────────────────────────
  private safeEqualHex(expected: string, actual: string) {
    const a = Buffer.from(expected, 'hex');
    const b = Buffer.from(actual, 'hex');
    if (a.length === 0 || a.length !== b.length) {
      return false;
    }
    return timingSafeEqual(a, b);
  }

  private stringFrom(value: unknown): string | undefined {
    if (typeof value === 'string') return value.length > 0 ? value : undefined;
    if (typeof value === 'number' && Number.isFinite(value)) return String(value);
    return undefined;
  }

  private getHeader(headers: Record<string, string | string[] | number | undefined>, name: string) {
    const lowerName = name.toLowerCase();
    for (const [key, value] of Object.entries(headers)) {
      if (key.toLowerCase() !== lowerName) continue;
      if (Array.isArray(value)) return value[0];
      if (value === undefined) return undefined;
      return String(value);
    }
    return undefined;
  }

  // Accepts JSON ({"id":"WEEK",...}) or compact ("id:WEEK,id2:MONTH").
  private parseMap(raw: string | undefined): Record<string, string> {
    const trimmed = raw?.trim();
    if (!trimmed) return {};
    if (trimmed.startsWith('{')) {
      try {
        const obj = JSON.parse(trimmed);
        return obj && typeof obj === 'object' ? (obj as Record<string, string>) : {};
      } catch {
        return {};
      }
    }
    const out: Record<string, string> = {};
    for (const entry of trimmed.split(',')) {
      const idx = entry.indexOf(':');
      if (idx <= 0) continue;
      const k = entry.slice(0, idx).trim();
      const v = entry.slice(idx + 1).trim();
      if (k && v) out[k] = v;
    }
    return out;
  }

  // ── config ───────────────────────────────────────────────────────────────
  // Dedicated login bot token falls back to the shared TELEGRAM_BOT_TOKEN.
  private get botToken() {
    return (
      this.configService.get<string>('TRIBUTE_LOGIN_BOT_TOKEN')?.trim() ||
      this.configService.get<string>('TELEGRAM_BOT_TOKEN')?.trim() ||
      undefined
    );
  }

  private get apiKey() {
    return this.configService.get<string>('TRIBUTE_API_KEY')?.trim() || undefined;
  }

  // Link maps may contain ':' and so are best provided as JSON; product maps
  // accept the compact form too.
  private get productMap() {
    return this.parseMap(this.configService.get<string>('TRIBUTE_PRODUCT_MAP'));
  }

  private get linkMap() {
    return this.parseMap(this.configService.get<string>('TRIBUTE_LINK_MAP'));
  }
}
