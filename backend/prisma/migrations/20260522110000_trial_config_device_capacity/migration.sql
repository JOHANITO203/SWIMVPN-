ALTER TABLE "TrialConfig"
ADD COLUMN IF NOT EXISTS "max_device_assignments" INTEGER NOT NULL DEFAULT 5,
ADD COLUMN IF NOT EXISTS "used_device_assignments" INTEGER NOT NULL DEFAULT 0;

UPDATE "TrialConfig"
SET "used_device_assignments" = assignment_counts.assignment_count
FROM (
  SELECT "trial_config_id", COUNT(*)::INTEGER AS assignment_count
  FROM "TrialAssignment"
  WHERE "status" IN ('ACTIVE', 'PENDING')
  GROUP BY "trial_config_id"
) AS assignment_counts
WHERE "TrialConfig"."id" = assignment_counts."trial_config_id";

DROP INDEX IF EXISTS "TrialAssignment_trial_config_id_key";

CREATE INDEX IF NOT EXISTS "TrialAssignment_trial_config_id_idx"
ON "TrialAssignment"("trial_config_id");

CREATE INDEX IF NOT EXISTS "TrialConfig_campaign_id_status_used_device_assignments_imported_at_idx"
ON "TrialConfig"("campaign_id", "status", "used_device_assignments", "imported_at");
