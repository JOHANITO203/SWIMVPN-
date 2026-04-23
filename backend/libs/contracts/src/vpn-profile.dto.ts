import { IsNotEmpty, IsString } from 'class-validator';

export class ParseConfigDto {
  @IsString()
  @IsNotEmpty()
  rawConfig: string;
}

export class GenerateSwimCryptImportDto {
  @IsString()
  @IsNotEmpty()
  rawConfig: string;

  compress?: boolean;
}
