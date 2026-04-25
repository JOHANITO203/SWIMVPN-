import { PlanCategory } from '@prisma/client';

export class AdminLoginDto {
  username: string;
  password_plain: string;
}

export class CreatePlanDto {
  code: PlanCategory;
  name: string;
  duration_label: string;
  quota_label: string;
  slot_count?: number;
  price_rub: number;
}

export class TriggerImportDto {
  category: PlanCategory;
  configs: string[];
  batchName?: string;
  sourceQuotaGb?: number;
  maxUsersPerConfig?: number;
  maxResaleSlots?: number;
  supplierExpiresAt?: string;
  supplierProviderName?: string;
  supplierDeviceLimit?: number;
  adminId: string;
}

export class UpdateInventoryHealthDto {
  inventoryItemId: string;
  healthStatus: 'HEALTHY' | 'DEGRADED' | 'FULL' | 'EXPIRED' | 'DISABLED';
  adminId: string;
}

export class RevokeAssignmentDto {
  assignmentId: string;
  reason?: string;
  adminId: string;
}

export class MoveAssignmentDto {
  assignmentId: string;
  targetInventoryItemId: string;
  adminId: string;
}

export class RetryFulfillmentDto {
  orderId: string;
  adminId: string;
}
