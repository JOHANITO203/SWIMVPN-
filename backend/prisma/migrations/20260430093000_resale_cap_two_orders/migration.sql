ALTER TABLE "InventoryItem"
  ALTER COLUMN "max_resale_slots" SET DEFAULT 2;

UPDATE "Plan"
SET "slot_count" = 1;

UPDATE "OrderAssignment"
SET "slot_count" = 1;

UPDATE "InventoryItem"
SET "max_resale_slots" = CASE
  WHEN "max_resale_slots" > 2 THEN 2
  ELSE "max_resale_slots"
END;

UPDATE "InventoryItem" i
SET "used_resale_slots" = COALESCE(agg."slot_sum", 0)
FROM (
  SELECT "inventory_item_id", SUM("slot_count")::INTEGER AS "slot_sum"
  FROM "OrderAssignment"
  WHERE "inventory_item_id" IS NOT NULL
    AND "access_status" = 'ACTIVE'
  GROUP BY "inventory_item_id"
) agg
WHERE i."id" = agg."inventory_item_id";

UPDATE "InventoryItem"
SET "used_resale_slots" = 0
WHERE "id" NOT IN (
  SELECT DISTINCT "inventory_item_id"
  FROM "OrderAssignment"
  WHERE "inventory_item_id" IS NOT NULL
    AND "access_status" = 'ACTIVE'
);

UPDATE "InventoryItem"
SET "health_status" = CASE
  WHEN "health_status" IN ('EXPIRED'::"InventoryHealthStatus", 'DISABLED'::"InventoryHealthStatus") THEN "health_status"
  WHEN "used_resale_slots" >= "max_resale_slots" THEN 'FULL'::"InventoryHealthStatus"
  ELSE 'HEALTHY'::"InventoryHealthStatus"
END;
