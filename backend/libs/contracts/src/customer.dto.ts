import { IsNotEmpty, IsString, IsOptional, IsEmail, IsNumber, Min } from 'class-validator';

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

  @IsEmail()
  email: string;

  @IsString()
  @IsNotEmpty()
  phone: string;
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
  paymentMethod: 'CRYPTO' | 'CARD_MANUAL';

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
  @IsOptional()
  deviceId?: string;
}
