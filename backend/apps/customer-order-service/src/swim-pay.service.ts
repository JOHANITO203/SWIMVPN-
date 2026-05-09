import { Injectable } from '@nestjs/common';
import { ConfigService } from '@nestjs/config';
import { createHmac, timingSafeEqual } from 'crypto';

type SwimPayEventType = 'payment.confirmed' | 'payment.rejected' | 'payment.expired';
type SwimPayDecision = 'manual_confirmed' | 'manual_rejected' | 'expired';

interface CreateSwimPayCheckoutParams {
  orderRef: string;
  amountRub: string;
  planLabel: string;
  customerPhone?: string | null;
}

interface SwimPayCheckoutResponse {
  orderId: string;
  paymentSessionId: string;
  checkoutUrl: string;
  status: string;
  expiresAt?: string;
}

export interface SwimPayPublicWebhookEvent {
  id: string;
  type: SwimPayEventType;
  createdAt: string;
  data: {
    externalOrderId?: string;
    orderId: string;
    paymentSessionId: string;
    amountMinor: number;
    currency: string;
    confirmationType?: 'notification_signal';
    officialBankConfirmation: false;
    decision?: SwimPayDecision;
  };
}

const PUBLIC_EVENT_TYPES = new Set<SwimPayEventType>([
  'payment.confirmed',
  'payment.rejected',
  'payment.expired',
]);

const PUBLIC_DECISIONS = new Set<SwimPayDecision>([
  'manual_confirmed',
  'manual_rejected',
  'expired',
]);

@Injectable()
export class SwimPayService {
  private static readonly DEFAULT_API_BASE_URL = 'https://staging.swimpay.pro';
  private static readonly DEFAULT_WEBHOOK_TOLERANCE_MS = 5 * 60 * 1000;

  constructor(private readonly configService: ConfigService) {}

  isConfigured() {
    return !!this.secretKey && !!this.webhookSecret;
  }

  async createCheckout(params: CreateSwimPayCheckoutParams): Promise<SwimPayCheckoutResponse> {
    if (!this.secretKey) {
      throw new Error('SwimPay API is not configured');
    }

    const response = await fetch(`${this.apiBaseUrl}/v1/orders`, {
      method: 'POST',
      headers: {
        Authorization: `Bearer ${this.secretKey}`,
        'Content-Type': 'application/json',
        'Idempotency-Key': params.orderRef,
      },
      body: JSON.stringify({
        external_id: params.orderRef,
        amount: {
          value: this.normalizeAmountRub(params.amountRub),
          currency: 'RUB',
        },
        description: `SWIMVPN+ ${params.planLabel}`,
        return_url: `${this.returnBaseUrl}?orderRef=${encodeURIComponent(params.orderRef)}`,
        webhook_url: this.webhookUrl,
        customer: this.stripUndefined({
          phone: params.customerPhone || undefined,
        }),
        metadata: {
          product: 'swimvpn_subscription',
          confirmation_type: 'notification_signal',
          official_bank_confirmation: false,
        },
      }),
    });

    const json = await response.json().catch(() => ({}));
    if (!response.ok) {
      const message =
        json?.error?.message ||
        json?.error ||
        response.statusText ||
        'unknown';
      throw new Error(`SwimPay create order failed: ${message}`);
    }

    return this.parseCheckoutResponse(json);
  }

  verifyWebhook(rawBody: string | Buffer, headers: Record<string, string | string[] | number | undefined>): SwimPayPublicWebhookEvent {
    if (!this.webhookSecret) {
      throw new Error('SwimPay webhook secret is not configured');
    }

    const eventId = this.getHeader(headers, 'SwimPay-Event-Id');
    const timestamp = this.getHeader(headers, 'SwimPay-Timestamp');
    const signature = this.getHeader(headers, 'SwimPay-Signature');

    if (!eventId || !timestamp || !signature) {
      throw new Error('SwimPay signature headers are required');
    }

    this.assertFreshTimestamp(timestamp);

    const payload = Buffer.isBuffer(rawBody) ? rawBody.toString('utf8') : rawBody;
    const expected = `sha256=${createHmac('sha256', this.webhookSecret)
      .update(`${timestamp}.${payload}`)
      .digest('hex')}`;

    if (!this.safeEqual(expected, signature)) {
      throw new Error('Invalid SwimPay webhook signature');
    }

    const parsed = JSON.parse(payload);
    const event = this.parsePublicWebhookEvent(parsed);
    if (event.id !== eventId) {
      throw new Error('Webhook event id header does not match payload');
    }

    return event;
  }

  private parsePublicWebhookEvent(payload: unknown): SwimPayPublicWebhookEvent {
    if (!payload || typeof payload !== 'object' || Array.isArray(payload)) {
      throw new Error('Webhook payload must be a JSON object');
    }

    const body = payload as Record<string, unknown>;
    const id = this.stringFrom(body.id);
    const type = this.stringFrom(body.type);
    const createdAt = this.stringFrom(body.created_at) || this.stringFrom(body.createdAt);
    const data = body.data;

    if (!id || !type || !createdAt || !data || typeof data !== 'object' || Array.isArray(data)) {
      throw new Error('Webhook payload is missing required fields');
    }

    if (!PUBLIC_EVENT_TYPES.has(type as SwimPayEventType)) {
      throw new Error(`Unsupported public webhook event type: ${type}`);
    }

    const eventData = data as Record<string, unknown>;
    this.assertNoUnsafePublicFields(eventData);

    const officialBankConfirmation =
      this.booleanFrom(eventData.official_bank_confirmation) ??
      this.booleanFrom(eventData.officialBankConfirmation);
    if (officialBankConfirmation !== false) {
      throw new Error('officialBankConfirmation must be false');
    }

    const orderId = this.stringFrom(eventData.order_id) || this.stringFrom(eventData.orderId);
    const paymentSessionId =
      this.stringFrom(eventData.payment_session_id) || this.stringFrom(eventData.paymentSessionId);
    const amountMinor = this.amountMinorFrom(eventData);
    const currency = this.currencyFrom(eventData);
    const confirmationType =
      this.stringFrom(eventData.confirmation_type) || this.stringFrom(eventData.confirmationType);
    const decision = this.stringFrom(eventData.decision);

    if (!orderId || !paymentSessionId || amountMinor === undefined || !currency) {
      throw new Error('Webhook event data is missing required payment fields');
    }
    if (confirmationType !== undefined && confirmationType !== 'notification_signal') {
      throw new Error('Unsupported confirmation type');
    }
    if (decision !== undefined && !PUBLIC_DECISIONS.has(decision as SwimPayDecision)) {
      throw new Error('Unsupported public webhook decision');
    }

    return this.stripUndefined({
      id,
      type: type as SwimPayEventType,
      createdAt,
      data: this.stripUndefined({
        externalOrderId:
          this.stringFrom(eventData.external_id) || this.stringFrom(eventData.externalOrderId),
        orderId,
        paymentSessionId,
        amountMinor,
        currency,
        confirmationType: confirmationType as 'notification_signal' | undefined,
        officialBankConfirmation: false,
        decision: decision as SwimPayDecision | undefined,
      }),
    }) as SwimPayPublicWebhookEvent;
  }

  private parseCheckoutResponse(response: Record<string, any>): SwimPayCheckoutResponse {
    const orderId = this.stringFrom(response.order_id) || this.stringFrom(response.orderId);
    const paymentSessionId =
      this.stringFrom(response.payment_session_id) || this.stringFrom(response.paymentSessionId);
    const checkoutUrl = this.stringFrom(response.checkout_url) || this.stringFrom(response.checkoutUrl);
    const status = this.stringFrom(response.status);
    const expiresAt = this.stringFrom(response.expires_at) || this.stringFrom(response.expiresAt);

    if (!orderId || !paymentSessionId || !checkoutUrl || !status) {
      throw new Error('SwimPay order response is missing required checkout fields');
    }

    return this.stripUndefined({ orderId, paymentSessionId, checkoutUrl, status, expiresAt }) as SwimPayCheckoutResponse;
  }

  private normalizeAmountRub(value: string) {
    const amount = Number.parseFloat(value.replace(',', '.'));
    if (!Number.isFinite(amount) || amount <= 0) {
      throw new Error('SwimPay amount must be positive');
    }
    return amount.toFixed(2);
  }

  private amountMinorFrom(data: Record<string, unknown>) {
    if (Number.isInteger(data.amount_minor)) return data.amount_minor as number;
    if (Number.isInteger(data.amountMinor)) return data.amountMinor as number;

    const amount = data.amount;
    if (!amount || typeof amount !== 'object' || Array.isArray(amount)) return undefined;

    const value = this.stringFrom((amount as Record<string, unknown>).value);
    if (!value || !/^\d+(?:[.,]\d{1,2})?$/.test(value)) return undefined;

    const [whole = '0', decimals = ''] = value.replace(',', '.').split('.');
    return Number(whole) * 100 + Number(decimals.padEnd(2, '0'));
  }

  private currencyFrom(data: Record<string, unknown>) {
    const direct = this.stringFrom(data.currency);
    if (direct) return direct;

    const amount = data.amount;
    if (!amount || typeof amount !== 'object' || Array.isArray(amount)) return undefined;
    return this.stringFrom((amount as Record<string, unknown>).currency);
  }

  private assertNoUnsafePublicFields(value: Record<string, unknown>, path: string[] = []) {
    for (const [key, nested] of Object.entries(value)) {
      const fullPath = [...path, key].join('.');
      if (/raw|notification[_-]?text|title|body|phone|card|source[_-]?card|cvv|cvc|expir/iu.test(key)) {
        throw new Error(`Public webhook event contains unsafe field: ${fullPath}`);
      }
      if (nested && typeof nested === 'object' && !Array.isArray(nested)) {
        this.assertNoUnsafePublicFields(nested as Record<string, unknown>, [...path, key]);
      }
    }
  }

  private assertFreshTimestamp(timestamp: string) {
    const timestampMs = Date.parse(timestamp);
    if (!Number.isFinite(timestampMs)) {
      throw new Error('Webhook timestamp is malformed');
    }
    if (Math.abs(Date.now() - timestampMs) > SwimPayService.DEFAULT_WEBHOOK_TOLERANCE_MS) {
      throw new Error('Webhook timestamp is outside tolerance');
    }
  }

  private safeEqual(expected: string, actual: string) {
    const expectedBuffer = Buffer.from(expected);
    const actualBuffer = Buffer.from(actual);
    if (expectedBuffer.length !== actualBuffer.length) {
      timingSafeEqual(expectedBuffer, expectedBuffer);
      return false;
    }
    return timingSafeEqual(expectedBuffer, actualBuffer);
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

  private stringFrom(value: unknown) {
    return typeof value === 'string' && value.length > 0 ? value : undefined;
  }

  private booleanFrom(value: unknown) {
    return typeof value === 'boolean' ? value : undefined;
  }

  private stripUndefined<T extends Record<string, unknown>>(value: T) {
    return Object.fromEntries(Object.entries(value).filter(([, nested]) => nested !== undefined)) as Partial<T>;
  }

  private get secretKey() {
    return this.configService.get<string>('SWIMPAY_SECRET_KEY')?.trim() || undefined;
  }

  private get webhookSecret() {
    return this.configService.get<string>('SWIMPAY_WEBHOOK_SECRET')?.trim() || undefined;
  }

  private get apiBaseUrl() {
    return (this.configService.get<string>('SWIMPAY_API_BASE_URL') || SwimPayService.DEFAULT_API_BASE_URL).replace(/\/+$/, '');
  }

  private get returnBaseUrl() {
    return (
      this.configService.get<string>('SWIMPAY_RETURN_BASE_URL') ||
      'https://api.swimvpn.pro/api/v1/payments/swimpay/return'
    ).replace(/\/+$/, '');
  }

  private get webhookUrl() {
    return (
      this.configService.get<string>('SWIMPAY_WEBHOOK_URL') ||
      'https://api.swimvpn.pro/api/v1/payments/swimpay/webhook'
    ).trim();
  }
}
