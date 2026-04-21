import { IsEmail, IsNotEmpty, IsOptional, IsString } from 'class-validator';

export type DeliveryLanguage = 'ru' | 'en';

export class DeliveryPayloadDto {
  @IsString()
  @IsNotEmpty()
  orderRef: string;

  @IsEmail()
  customerEmail: string;

  @IsString()
  @IsOptional()
  customerPhone?: string;

  @IsString()
  @IsNotEmpty()
  planCode: string;

  @IsString()
  @IsNotEmpty()
  planLabel: string;

  @IsString()
  @IsNotEmpty()
  vpnLink: string;

  @IsString()
  @IsOptional()
  expiryLabel?: string;

  @IsString()
  @IsOptional()
  customerLanguage?: DeliveryLanguage;
}

export class OrderRefDto {
  @IsString()
  @IsNotEmpty()
  orderRef: string;

  @IsString()
  @IsOptional()
  language?: DeliveryLanguage;
}
