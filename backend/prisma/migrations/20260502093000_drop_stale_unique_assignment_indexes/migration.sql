-- Remove stale unique indexes left by the initial single-assignment schema.
-- The current product model allows one supplier config to back multiple orders
-- up to InventoryItem.max_resale_slots, enforced by fulfillment transactions.
DROP INDEX IF EXISTS "OrderAssignment_inventory_item_id_key";
DROP INDEX IF EXISTS "InventoryItem_assigned_order_id_key";

CREATE INDEX IF NOT EXISTS "OrderAssignment_inventory_item_id_idx"
  ON "OrderAssignment"("inventory_item_id");