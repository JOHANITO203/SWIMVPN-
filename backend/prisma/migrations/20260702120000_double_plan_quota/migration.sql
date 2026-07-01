-- Double the VPN data sold on each offer (absolute values → idempotent, safe to re-run).
-- Basic:    50 GB  -> 100 GB
-- Premium:  150 GB -> 300 GB
-- Platinum: 500 GB -> 1000 GB
UPDATE "Plan" SET "quota_label" = '100 GB',  "quota_gb" = 100  WHERE "code" = 'WEEK';
UPDATE "Plan" SET "quota_label" = '300 GB',  "quota_gb" = 300  WHERE "code" = 'MONTH';
UPDATE "Plan" SET "quota_label" = '1000 GB', "quota_gb" = 1000 WHERE "code" = 'QUARTER';
