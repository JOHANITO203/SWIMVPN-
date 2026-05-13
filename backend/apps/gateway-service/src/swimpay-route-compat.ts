import { RequestMethod } from '@nestjs/common';

export const SWIMPAY_LEGACY_WEBHOOK_ROUTE = {
  path: 'webhooks/swimpay',
  method: RequestMethod.POST,
} as const;
