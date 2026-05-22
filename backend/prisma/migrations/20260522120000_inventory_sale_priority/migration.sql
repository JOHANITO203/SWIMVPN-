ALTER TABLE "InventoryItem"
ADD COLUMN IF NOT EXISTS "sale_priority_score" INTEGER NOT NULL DEFAULT 0;

UPDATE "InventoryItem"
SET "sale_priority_score" = CASE
  WHEN "used_resale_slots" <= 0 OR "max_resale_slots" <= 0 THEN 0
  ELSE FLOOR(("used_resale_slots"::numeric * 100000) / "max_resale_slots")::INTEGER
END;

CREATE INDEX IF NOT EXISTS "InventoryItem_category_health_status_sale_priority_score_imported_at_idx"
ON "InventoryItem"("category", "health_status", "sale_priority_score", "imported_at");
