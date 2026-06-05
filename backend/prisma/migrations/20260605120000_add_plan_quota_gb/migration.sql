ALTER TABLE "Plan" ADD COLUMN "quota_gb" INTEGER;

UPDATE "Plan"
SET "quota_gb" = CAST(substring("quota_label" FROM '\d+') AS INTEGER)
WHERE "quota_gb" IS NULL AND "quota_label" ~ '\d';
