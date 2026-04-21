import { IsNotEmpty, IsString, IsOptional, IsEmail, IsNumber, Min } from 'class-validator';

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
