-- Shared quota + measured usage foundation
ALTER TABLE "InventoryItem"
  DROP CONSTRAINT IF EXISTS "InventoryItem_assigned_order_id_key";

ALTER TABLE "InventoryItem"
  ADD COLUMN "source_quota_bytes" BIGINT,
  ADD COLUMN "source_used_bytes" BIGINT NOT NULL DEFAULT 0,
  ADD COLUMN "max_customer_allocations" INTEGER NOT NULL DEFAULT 5;

ALTER TABLE "OrderAssignment"
  DROP CONSTRAINT IF EXISTS "OrderAssignment_inventory_item_id_key";

ALTER TABLE "OrderAssignment"
  ADD COLUMN "customer_id" TEXT,
  ADD COLUMN "measured_used_bytes" BIGINT NOT NULL DEFAULT 0,
  ADD COLUMN "last_measured_at" TIMESTAMP(3);

UPDATE "OrderAssignment" oa
SET "customer_id" = o."customer_id"
FROM "Order" o
WHERE oa."order_id" = o."id";

ALTER TABLE "OrderAssignment"
  ALTER COLUMN "customer_id" SET NOT NULL;

CREATE INDEX IF NOT EXISTS "OrderAssignment_inventory_item_id_idx" ON "OrderAssignment"("inventory_item_id");
CREATE INDEX IF NOT EXISTS "OrderAssignment_customer_id_idx" ON "OrderAssignment"("customer_id");
CREATE INDEX IF NOT EXISTS "OrderAssignment_order_id_idx" ON "OrderAssignment"("order_id");

ALTER TABLE "OrderAssignment"
  ADD CONSTRAINT "OrderAssignment_customer_id_fkey"
  FOREIGN KEY ("customer_id") REFERENCES "Customer"("id") ON DELETE RESTRICT ON UPDATE CASCADE;
