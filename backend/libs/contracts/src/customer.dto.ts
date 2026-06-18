import { IsNotEmpty, IsString, IsOptional, IsEmail, IsNumber, Min, IsObject } from 'class-validator';

export const ENTITLEMENT_STATES = [
  'PROFILE_INCOMPLETE',
  'TRIAL_AVAILABLE',
  'ACTIVE_TRIAL',
  'ACTIVE_SUBSCRIPTION',
  'PENDING_FULFILLMENT',
  'EXPIRED_TRIAL',
  'EXPIRED_SUBSCRIPTION',
  'FREEMIUM',
] as const;

export type EntitlementState = (typeof ENTITLEMENT_STATES)[number];
export type AccessType = 'TRIAL' | 'PAID' | 'NONE';
export type FulfillmentStatus = 'NONE' | 'PENDING_FULFILLMENT' | 'DELIVERED';
export type LegacyProfileStatus = 'ACTIVE' | 'EXPIRED' | EntitlementState;

export interface AccessProfileResponse {
  userNumber: string;
  email: string | null;
  phone: string | null;
  accessType: AccessType;
  offerCode: string | null;
  planDisplayName: string | null;
  planType: AccessType;
  status: LegacyProfileStatus;
  entitlementState: EntitlementState;
  trialStartedAt: string | null;
  trialExpiresAt: string | null;
  subscriptionExpiresAt: string | null;
  subscriptionUrl: string | null;
  devicesAllowed: number;
  fulfillmentStatus: FulfillmentStatus;
  dataLimitGB: number;
  dataUsedBytes: string;
  supplierProviderName: string | null;
  supplierExpiresAt: string | null;
  profileCompletionRequired: boolean;
  trialEligible: boolean;
}

export interface BootstrapAccessResponse {
  userNumber: string;
  email: string | null;
  phone: string | null;
  trialEligible: boolean;
  profileCompletionRequired: boolean;
  hasActiveAccess: boolean;
  profile: AccessProfileResponse;
}

export interface CheckoutResponse {
  orderRef: string;
  status: string;
  amountRub: string;
  paymentMethod: 'CRYPTO' | 'SWIMPAY' | 'TRIBUTE';
  redirectUrl: string | null;
  message: string;
}

// Telegram Login Widget payload (web-only Tribute flow). Validated server-side
// against the bot token before the pending order is created.
export interface TelegramAuthPayload {
  id: number | string;
  first_name?: string;
  last_name?: string;
  username?: string;
  photo_url?: string;
  auth_date: number | string;
  hash: string;
}

export class BootstrapAccessDto {
  @IsString()
  @IsNotEmpty()
  deviceId: string;

  @IsString()
  @IsOptional()
  platform?: string;

  @IsString()
  @IsOptional()
  locale?: string;
}

export class StartTrialDto {
  @IsString()
  @IsNotEmpty()
  deviceId: string;

  @IsString()
  @IsOptional()
  platform?: string;

  @IsString()
  @IsOptional()
  locale?: string;
}

export class ActivateTrialDto {
  @IsString()
  @IsNotEmpty()
  userNumber: string;

  @IsString()
  @IsNotEmpty()
  deviceId: string;

  @IsEmail()
  email: string;

  @IsString()
  @IsNotEmpty()
  phone: string;
}

export class CompleteProfileDto {
  @IsString()
  @IsNotEmpty()
  userNumber: string;

  @IsString()
  @IsNotEmpty()
  deviceId: string;

  @IsEmail()
  @IsOptional()
  email?: string;

  @IsString()
  @IsOptional()
  phone?: string;
}

export class CancelCurrentSubscriptionDto {
  @IsString()
  @IsNotEmpty()
  userNumber: string;

  @IsString()
  @IsNotEmpty()
  deviceId: string;

  @IsString()
  @IsOptional()
  reason?: string;
}

export class CreateOrderDto {
  @IsEmail()
  @IsOptional()
  email?: string;

  @IsString()
  @IsOptional()
  phone?: string;

  @IsString()
  @IsNotEmpty()
  planId: string;

  @IsNumber()
  @Min(0)
  amountRub: number;
}

export class CreateCheckoutDto {
  @IsString()
  @IsOptional()
  userNumber?: string;

  @IsString()
  @IsOptional()
  deviceId?: string;

  @IsEmail()
  @IsOptional()
  email?: string;

  @IsString()
  @IsOptional()
  phone?: string;

  @IsString()
  @IsNotEmpty()
  planId: string;

  @IsString()
  @IsNotEmpty()
  paymentMethod: 'CRYPTO' | 'SWIMPAY' | 'TRIBUTE';

  @IsString()
  @IsOptional()
  cryptoAsset?: string;

  // Web-only Tribute flow: the Telegram user id (verified server-side from telegramAuth)
  // is the correlation key for the payment webhook. Ignored for other methods.
  @IsString()
  @IsOptional()
  telegramUserId?: string;

  // Raw Telegram Login Widget payload. Required for TRIBUTE; its hash is validated
  // against the bot token before the order is created (anti-spoof of telegramUserId).
  @IsObject()
  @IsOptional()
  telegramAuth?: TelegramAuthPayload;
}

export class CryptoWebhookDto {
  @IsString()
  @IsOptional()
  update_type?: string;

  @IsOptional()
  payload?: Record<string, unknown>;
}


export class ReportUsageDto {
  @IsString()
  @IsNotEmpty()
  userNumber: string;

  @IsString()
  @IsNotEmpty()
  measuredUsedBytes: string;

  @IsString()
  @IsNotEmpty()
  deviceId: string;
}
