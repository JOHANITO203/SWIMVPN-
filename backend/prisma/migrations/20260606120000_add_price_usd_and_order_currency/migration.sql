-- AlterTable
ALTER TABLE "Plan" ADD COLUMN     "price_usd" DECIMAL(10,2) NOT NULL DEFAULT 0;

-- AlterTable
ALTER TABLE "Order" ADD COLUMN     "amount" DECIMAL(12,2),
ADD COLUMN     "currency" TEXT;

-- Backfill catalog USD prices (validated, derived from the RUB tiers ~86 RUB/USD). Guarded on the
-- freshly-added default 0 so it never clobbers a value set later via admin. No $0 window on deploy.
UPDATE "Plan" SET "price_usd" = 3.49  WHERE "code" = 'WEEK'    AND "price_usd" = 0;
UPDATE "Plan" SET "price_usd" = 7.99  WHERE "code" = 'MONTH'   AND "price_usd" = 0;
UPDATE "Plan" SET "price_usd" = 21.99 WHERE "code" = 'QUARTER' AND "price_usd" = 0;
