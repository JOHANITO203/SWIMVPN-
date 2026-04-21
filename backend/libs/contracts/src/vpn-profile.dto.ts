import { IsNotEmpty, IsString } from 'class-validator';

export class ParseConfigDto {
  @IsString()
  @IsNotEmpty()
  rawConfig: string;
}
