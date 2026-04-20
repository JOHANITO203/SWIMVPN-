import { IsString, IsNotEmpty } from 'class-validator';

export class ActivateCodeDto {
  @IsString()
  @IsNotEmpty()
  userNumber: string;

  @IsString()
  @IsNotEmpty()
  code: string;
}
