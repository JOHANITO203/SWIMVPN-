import { IsNotEmpty, IsString, IsArray, IsOptional, IsEnum } from 'class-validator';
import { PlanCategory } from '@prisma/client';

export class ImportConfigsDto {
  @IsEnum(PlanCategory)
  category: PlanCategory;

  @IsArray()
  @IsString({ each: true })
  @IsNotEmpty({ each: true })
  configs: string[]; // Array of raw vless/vmess strings

  @IsString()
  @IsOptional()
  batchName?: string;
}

export class FulfillOrderDto {
  @IsString()
  @IsNotEmpty()
  orderId: string;
}
