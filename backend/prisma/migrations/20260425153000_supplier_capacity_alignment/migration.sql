ALTER TYPE "OrderStatus" ADD VALUE IF NOT EXISTS 'PENDING_FULFILLMENT';

DO $$
BEGIN
  IF NOT EXISTS (SELECT 1 FROM pg_type WHERE typname = 'InventoryHealthStatus') THEN
    CREATE TYPE "InventoryHealthStatus" AS ENUM ('HEALTHY', 'DEGRADED', 'FULL', 'EXPIRED', 'DISABLED');
  END IF;
END $$;

DO $$
BEGIN
  IF NOT EXISTS (SELECT 1 FROM pg_type WHERE typname = 'AssignmentAccessStatus') THEN
    CREATE TYPE "AssignmentAccessStatus" AS ENUM ('PENDING', 'ACTIVE', 'EXPIRED', 'REVOKED', 'FAILED');
  END IF;
END $$;

ALTER TABLE "Plan"
  ADD COLUMN IF NOT EXISTS "slot_count" INTEGER NOT NULL DEFAULT 1;

UPDATE "Plan"
SET "slot_count" = CASE
  WHEN "code" = 'MONTH' THEN 2
  WHEN "code" = 'QUARTER' THEN 4
  ELSE 1
END;

ALTER TABLE "InventoryItem"
  ADD COLUMN IF NOT EXISTS "health_status" "InventoryHealthStatus" NOT NULL DEFAULT 'HEALTHY',
  ADD COLUMN IF NOT EXISTS "max_resale_slots" INTEGER NOT NULL DEFAULT 4,
  ADD COLUMN IF NOT EXISTS "used_resale_slots" INTEGER NOT NULL DEFAULT 0,
  ADD COLUMN IF NOT EXISTS "supplier_expires_at" TIMESTAMP(3),
  ADD COLUMN IF NOT EXISTS "supplier_provider_name" TEXT,
  ADD COLUMN IF NOT EXISTS "supplier_device_limit" INTEGER;

ALTER TABLE "OrderAssignment"
  ADD COLUMN IF NOT EXISTS "access_status" "AssignmentAccessStatus" NOT NULL DEFAULT 'PENDING',
  ADD COLUMN IF NOT EXISTS "slot_count" INTEGER NOT NULL DEFAULT 1,
  ADD COLUMN IF NOT EXISTS "expires_at" TIMESTAMP(3),
  ADD COLUMN IF NOT EXISTS "revoked_at" TIMESTAMP(3),
  ADD COLUMN IF NOT EXISTS "status_reason" TEXT;

ALTER TABLE "OrderAssignment"
  ALTER COLUMN "inventory_item_id" DROP NOT NULL;

UPDATE "OrderAssignment" oa
SET
  "slot_count" = CASE
    WHEN o."payment_ref" = 'TRIAL:3D' OR o."order_ref" LIKE 'TRIAL-%' THEN 1
    WHEN p."code" = 'MONTH' THEN 2
    WHEN p."code" = 'QUARTER' THEN 4
    ELSE 1
  END,
  "access_status" = CASE
    WHEN o."status" = 'FULFILLED' THEN 'ACTIVE'::"AssignmentAccessStatus"
    WHEN o."status" = 'FAILED' THEN 'FAILED'::"AssignmentAccessStatus"
    ELSE 'PENDING'::"AssignmentAccessStatus"
  END
FROM "Order" o
JOIN "Plan" p ON p."id" = o."plan_id"
WHERE oa."order_id" = o."id";

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
SET "health_status" = CASE
  WHEN "used_resale_slots" >= "max_resale_slots" THEN 'FULL'::"InventoryHealthStatus"
  ELSE 'HEALTHY'::"InventoryHealthStatus"
END;
