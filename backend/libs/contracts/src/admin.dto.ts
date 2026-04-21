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
  price_rub: number;
}

export class TriggerImportDto {
  category: PlanCategory;
  configs: string[];
  batchName?: string;
  adminId: string;
}
