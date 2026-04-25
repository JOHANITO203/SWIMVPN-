import { Injectable } from '@nestjs/common';
import { ConfigService } from '@nestjs/config';
import { createHash, createHmac, timingSafeEqual } from 'crypto';

interface CreateInvoiceParams {
  amountRub: string;
  orderRef: string;
  planLabel: string;
  asset?: string;
}

interface CryptoInvoiceResponse {
  invoice_id: number;
  bot_invoice_url?: string;
  mini_app_invoice_url?: string;
  web_app_invoice_url?: string;
  status?: string;
  amount?: string;
  fiat?: string;
  paid_asset?: string;
  paid_amount?: string;
  payload?: string;
}

@Injectable()
export class CryptoPayService {
  private readonly token?: string;
  private readonly baseUrl: string;
  private readonly acceptedAssets?: string;
  private readonly expiresInSeconds: number;

  constructor(private readonly configService: ConfigService) {
    this.token = this.configService.get<string>('CRYPTO_PAY_API_TOKEN')?.trim() || undefined;
    this.baseUrl = this.configService
      .get<string>('CRYPTO_PAY_API_BASE_URL', 'https://pay.crypt.bot/api')
      .replace(/\/+$/, '');
    this.acceptedAssets = this.configService.get<string>('CRYPTO_PAY_ACCEPTED_ASSETS')?.trim() || undefined;
    this.expiresInSeconds = Number.parseInt(
      this.configService.get<string>('CRYPTO_PAY_INVOICE_EXPIRES_IN', '1800'),
      10,
    );
  }

  isConfigured() {
    return !!this.token;
  }

  async createInvoice(params: CreateInvoiceParams): Promise<CryptoInvoiceResponse> {
    if (!this.token) {
      throw new Error('Crypto Pay API is not configured');
    }

    const body: Record<string, string | number | undefined> = {
      currency_type: 'fiat',
      fiat: 'RUB',
      amount: params.amountRub,
      description: `SWIMVPN+ ${params.planLabel}`,
      payload: params.orderRef,
      expires_in: this.expiresInSeconds,
    };

    if (params.asset) {
      body.accepted_assets = params.asset;
    } else if (this.acceptedAssets) {
      body.accepted_assets = this.acceptedAssets;
    }

    const response = await fetch(`${this.baseUrl}/createInvoice`, {
      method: 'POST',
      headers: {
        'Content-Type': 'application/json',
        'Crypto-Pay-API-Token': this.token,
      },
      body: JSON.stringify(body),
    });

    const json = await response.json().catch(() => ({}));
    if (!response.ok || json?.ok === false || !json?.result) {
      const apiError =
        json?.error?.name ||
        json?.error?.message ||
        json?.error ||
        response.statusText ||
        'unknown';
      throw new Error(`Crypto Pay createInvoice failed: ${apiError}`);
    }

    return json.result as CryptoInvoiceResponse;
  }

  verifyWebhook(body: unknown, signature?: string | string[]) {
    if (!this.token) {
      return false;
    }

    const signatureValue = Array.isArray(signature) ? signature[0] : signature;
    if (!signatureValue) {
      return false;
    }

    const normalizedBody = this.normalizePayload(body);
    const checkString = Object.keys(normalizedBody)
      .sort()
      .map((key) => `${key}=${JSON.stringify(normalizedBody[key])}`)
      .join('\n');

    const secret = createHash('sha256').update(this.token).digest();
    const digest = createHmac('sha256', secret).update(checkString).digest();
    const provided = Buffer.from(signatureValue, 'hex');

    return provided.length === digest.length && timingSafeEqual(provided, digest);
  }

  private normalizePayload(body: unknown): Record<string, unknown> {
    if (!body || typeof body !== 'object' || Array.isArray(body)) {
      return {};
    }

    return body as Record<string, unknown>;
  }
}
