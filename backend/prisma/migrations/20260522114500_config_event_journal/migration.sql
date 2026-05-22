DO $$
BEGIN
    CREATE TYPE "ConfigEventScope" AS ENUM ('PAID', 'TRIAL');
EXCEPTION
    WHEN duplicate_object THEN NULL;
END $$;

DO $$
BEGIN
    CREATE TYPE "ConfigEventType" AS ENUM (
        'CONFIG_IMPORTED',
        'TRIAL_CONFIG_IMPORTED',
        'CONFIG_ASSIGNED',
        'TRIAL_CONFIG_ASSIGNED'
    );
EXCEPTION
    WHEN duplicate_object THEN NULL;
END $$;

CREATE TABLE IF NOT EXISTS "ConfigEvent" (
    "id" TEXT NOT NULL,
    "config_scope" "ConfigEventScope" NOT NULL,
    "config_id" TEXT NOT NULL,
    "folder_code" TEXT,
    "event_type" "ConfigEventType" NOT NULL,
    "payload_json" JSONB NOT NULL,
    "created_at" TIMESTAMP(3) NOT NULL DEFAULT CURRENT_TIMESTAMP,

    CONSTRAINT "ConfigEvent_pkey" PRIMARY KEY ("id")
);

CREATE INDEX IF NOT EXISTS "ConfigEvent_config_scope_config_id_created_at_idx"
ON "ConfigEvent"("config_scope", "config_id", "created_at");

CREATE INDEX IF NOT EXISTS "ConfigEvent_folder_code_created_at_idx"
ON "ConfigEvent"("folder_code", "created_at");
