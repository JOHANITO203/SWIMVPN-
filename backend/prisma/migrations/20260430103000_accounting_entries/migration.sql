CREATE TYPE "AccountingEntryType" AS ENUM ('REVENUE', 'EXPENSE', 'ADJUSTMENT');
CREATE TYPE "AccountingEntrySource" AS ENUM ('ORDER', 'SUPPLIER_CONFIG', 'MANUAL', 'CRYPTO', 'REFUND');

CREATE TABLE "AccountingEntry" (
  "id" TEXT NOT NULL,
  "type" "AccountingEntryType" NOT NULL,
  "amount" DECIMAL(12,2) NOT NULL,
  "currency" TEXT NOT NULL DEFAULT 'RUB',
  "crypto_asset" TEXT,
  "exchange_rate_rub" DECIMAL(18,8),
  "source" "AccountingEntrySource" NOT NULL,
  "order_ref" TEXT,
  "inventory_item_id" TEXT,
  "note" TEXT,
  "created_by_admin" TEXT,
  "created_at" TIMESTAMP(3) NOT NULL DEFAULT CURRENT_TIMESTAMP,
  CONSTRAINT "AccountingEntry_pkey" PRIMARY KEY ("id")
);

CREATE UNIQUE INDEX "AccountingEntry_type_source_order_ref_key"
  ON "AccountingEntry"("type", "source", "order_ref");
CREATE INDEX "AccountingEntry_created_at_idx" ON "AccountingEntry"("created_at");
CREATE INDEX "AccountingEntry_order_ref_idx" ON "AccountingEntry"("order_ref");
CREATE INDEX "AccountingEntry_inventory_item_id_idx" ON "AccountingEntry"("inventory_item_id");