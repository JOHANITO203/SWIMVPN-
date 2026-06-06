-- AlterTable
ALTER TABLE "Plan" ADD COLUMN     "price_usd" DECIMAL(10,2) NOT NULL DEFAULT 0;

-- AlterTable
ALTER TABLE "Order" ADD COLUMN     "amount" DECIMAL(12,2),
ADD COLUMN     "currency" TEXT;
