import { IsString, IsNotEmpty, IsUrl } from 'class-validator';

export class ImportSubscriptionDto {
  @IsString()
  @IsNotEmpty()
  userNumber: string;

  @IsUrl()
  @IsNotEmpty()
  subscriptionUrl: string;
}
