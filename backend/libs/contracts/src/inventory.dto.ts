import { IsNotEmpty, IsString, IsArray, IsOptional, IsEnum, IsInt, Min, Max, IsDateString } from 'class-validator';
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

  @IsOptional()
  @IsInt()
  @Min(1)
  sourceQuotaGb?: number;

  @IsOptional()
  @IsInt()
  @Min(1)
  maxUsersPerConfig?: number;

  @IsOptional()
  @IsInt()
  @Min(1)
  @Max(4)
  maxResaleSlots?: number;

  @IsOptional()
  @IsDateString()
  supplierExpiresAt?: string;

  @IsOptional()
  @IsString()
  supplierProviderName?: string;

  @IsOptional()
  @IsInt()
  @Min(1)
  supplierDeviceLimit?: number;
}

export class FulfillOrderDto {
  @IsString()
  @IsNotEmpty()
  orderId: string;
}

export class RecordUsageDto {
  @IsString()
  @IsNotEmpty()
  orderRef: string;

  @IsString()
  @IsNotEmpty()
  measuredUsedBytes: string;
}
