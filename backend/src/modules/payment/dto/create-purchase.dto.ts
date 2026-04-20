import { IsString, IsNotEmpty, IsEmail } from 'class-validator';

export class CreatePurchaseDto {
  @IsString()
  @IsNotEmpty()
  userNumber: string;

  @IsEmail()
  @IsNotEmpty()
  email: string;

  @IsString()
  @IsNotEmpty()
  planId: string;
}
