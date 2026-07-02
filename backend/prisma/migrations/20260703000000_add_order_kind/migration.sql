-- CreateEnum
CREATE TYPE "OrderKind" AS ENUM ('PURCHASE', 'TRIAL', 'COMP', 'MANUAL');

-- AlterTable
ALTER TABLE "Order" ADD COLUMN "kind" "OrderKind" NOT NULL DEFAULT 'PURCHASE';

-- Update existing trial orders
UPDATE "Order" SET "kind" = 'TRIAL' WHERE "order_ref" LIKE 'TRIAL-%';

-- Clean up (fail) legacy trial orders that are stuck in PENDING or PENDING_FULFILLMENT
UPDATE "Order" SET "status" = 'FAILED' WHERE "kind" = 'TRIAL' AND "status" IN ('PENDING', 'PENDING_FULFILLMENT');
