import { IsEmail, IsOptional, IsString, MaxLength } from 'class-validator';

export class EmailOptInDto {
  @IsEmail()
  email: string;

  @IsOptional()
  @IsString()
  @MaxLength(8)
  locale?: string;

  @IsOptional()
  @IsString()
  @MaxLength(40)
  source?: string;
}

export class ConfirmSubscriptionDto {
  @IsString()
  token: string;
}
