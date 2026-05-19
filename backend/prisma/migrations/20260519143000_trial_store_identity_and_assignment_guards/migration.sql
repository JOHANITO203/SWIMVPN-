ALTER TABLE "TrialGrant"
ADD COLUMN IF NOT EXISTS "identity_email" TEXT,
ADD COLUMN IF NOT EXISTS "identity_phone" TEXT,
ADD COLUMN IF NOT EXISTS "identity_device_id" TEXT;

CREATE UNIQUE INDEX IF NOT EXISTS "TrialGrant_campaign_id_identity_email_key"
ON "TrialGrant"("campaign_id", "identity_email");

CREATE UNIQUE INDEX IF NOT EXISTS "TrialGrant_campaign_id_identity_phone_key"
ON "TrialGrant"("campaign_id", "identity_phone");

CREATE UNIQUE INDEX IF NOT EXISTS "TrialGrant_campaign_id_identity_device_id_key"
ON "TrialGrant"("campaign_id", "identity_device_id");

DROP INDEX IF EXISTS "TrialAssignment_grant_id_idx";

CREATE UNIQUE INDEX IF NOT EXISTS "TrialAssignment_grant_id_key"
ON "TrialAssignment"("grant_id");
