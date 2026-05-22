ALTER TABLE "InventoryItem"
ADD COLUMN IF NOT EXISTS "config_fingerprint" TEXT,
ADD COLUMN IF NOT EXISTS "folder_code" TEXT,
ADD COLUMN IF NOT EXISTS "admin_label" TEXT,
ADD COLUMN IF NOT EXISTS "node_count" INTEGER NOT NULL DEFAULT 0,
ADD COLUMN IF NOT EXISTS "countries_preview" JSONB,
ADD COLUMN IF NOT EXISTS "admin_preview_json" JSONB;

ALTER TABLE "TrialConfig"
ADD COLUMN IF NOT EXISTS "config_fingerprint" TEXT,
ADD COLUMN IF NOT EXISTS "folder_code" TEXT,
ADD COLUMN IF NOT EXISTS "admin_label" TEXT,
ADD COLUMN IF NOT EXISTS "node_count" INTEGER NOT NULL DEFAULT 0,
ADD COLUMN IF NOT EXISTS "countries_preview" JSONB,
ADD COLUMN IF NOT EXISTS "admin_preview_json" JSONB;

CREATE INDEX IF NOT EXISTS "InventoryItem_config_fingerprint_idx"
ON "InventoryItem"("config_fingerprint");

CREATE INDEX IF NOT EXISTS "InventoryItem_folder_code_idx"
ON "InventoryItem"("folder_code");

CREATE INDEX IF NOT EXISTS "TrialConfig_config_fingerprint_idx"
ON "TrialConfig"("config_fingerprint");

CREATE INDEX IF NOT EXISTS "TrialConfig_folder_code_idx"
ON "TrialConfig"("folder_code");
