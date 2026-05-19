import { IsNotEmpty, IsString, IsOptional, IsEmail, IsNumber, Min } from 'class-validator';

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
  paymentMethod: 'CRYPTO' | 'CARD_MANUAL' | 'SWIMPAY';
  redirectUrl: string | null;
  message: string;
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
  paymentMethod: 'CRYPTO' | 'CARD_MANUAL' | 'SWIMPAY';

  @IsString()
  @IsOptional()
  cryptoAsset?: string;
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
