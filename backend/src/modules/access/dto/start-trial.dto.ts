import { IsString, IsNotEmpty, IsOptional } from 'class-validator';

export class StartTrialDto {
  @IsString()
  @IsNotEmpty()
  deviceId: string;

  @IsString()
  @IsOptional()
  appVersion?: string;

  @IsString()
  @IsNotEmpty()
  platform: string;

  @IsString()
  @IsNotEmpty()
  locale: string;
}
