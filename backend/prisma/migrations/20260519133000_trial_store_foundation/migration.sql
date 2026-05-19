CREATE TYPE "TrialCampaignStatus" AS ENUM ('ACTIVE', 'CLOSED', 'DISABLED');

CREATE TYPE "TrialConfigStatus" AS ENUM ('AVAILABLE', 'ASSIGNED', 'DEAD', 'DISABLED');

CREATE TYPE "TrialGrantStatus" AS ENUM ('PENDING', 'ACTIVE', 'EXPIRED', 'SUPERSEDED_BY_PAID', 'REVOKED', 'FAILED');

CREATE TABLE "TrialCampaign" (
    "id" TEXT NOT NULL,
    "code" TEXT NOT NULL,
    "title" TEXT NOT NULL,
    "status" "TrialCampaignStatus" NOT NULL DEFAULT 'ACTIVE',
    "starts_at" TIMESTAMP(3) NOT NULL DEFAULT CURRENT_TIMESTAMP,
    "ends_at" TIMESTAMP(3) NOT NULL,
    "duration_days" INTEGER NOT NULL DEFAULT 3,
    "quota_label" TEXT NOT NULL DEFAULT 'UNLIMITED',
    "created_at" TIMESTAMP(3) NOT NULL DEFAULT CURRENT_TIMESTAMP,
    "updated_at" TIMESTAMP(3) NOT NULL,

    CONSTRAINT "TrialCampaign_pkey" PRIMARY KEY ("id")
);

CREATE TABLE "TrialConfig" (
    "id" TEXT NOT NULL,
    "campaign_id" TEXT NOT NULL,
    "raw_config" TEXT NOT NULL,
    "config_type" TEXT NOT NULL,
    "display_protocol" TEXT NOT NULL,
    "batch_name" TEXT,
    "status" "TrialConfigStatus" NOT NULL DEFAULT 'AVAILABLE',
    "supplier_provider_name" TEXT,
    "supplier_expires_at" TIMESTAMP(3),
    "imported_at" TIMESTAMP(3) NOT NULL DEFAULT CURRENT_TIMESTAMP,
    "assigned_at" TIMESTAMP(3),

    CONSTRAINT "TrialConfig_pkey" PRIMARY KEY ("id")
);

CREATE TABLE "TrialGrant" (
    "id" TEXT NOT NULL,
    "customer_id" TEXT NOT NULL,
    "campaign_id" TEXT NOT NULL,
    "status" "TrialGrantStatus" NOT NULL DEFAULT 'PENDING',
    "started_at" TIMESTAMP(3) NOT NULL DEFAULT CURRENT_TIMESTAMP,
    "expires_at" TIMESTAMP(3),
    "assigned_at" TIMESTAMP(3),
    "superseded_at" TIMESTAMP(3),
    "revoked_at" TIMESTAMP(3),
    "status_reason" TEXT,

    CONSTRAINT "TrialGrant_pkey" PRIMARY KEY ("id")
);

CREATE TABLE "TrialAssignment" (
    "id" TEXT NOT NULL,
    "grant_id" TEXT NOT NULL,
    "trial_config_id" TEXT NOT NULL,
    "customer_id" TEXT NOT NULL,
    "status" "TrialGrantStatus" NOT NULL DEFAULT 'ACTIVE',
    "assigned_at" TIMESTAMP(3) NOT NULL DEFAULT CURRENT_TIMESTAMP,
    "expires_at" TIMESTAMP(3),
    "revoked_at" TIMESTAMP(3),
    "status_reason" TEXT,

    CONSTRAINT "TrialAssignment_pkey" PRIMARY KEY ("id")
);

CREATE UNIQUE INDEX "TrialCampaign_code_key" ON "TrialCampaign"("code");
CREATE INDEX "TrialCampaign_status_starts_at_ends_at_idx" ON "TrialCampaign"("status", "starts_at", "ends_at");
CREATE INDEX "TrialConfig_campaign_id_status_imported_at_idx" ON "TrialConfig"("campaign_id", "status", "imported_at");
CREATE UNIQUE INDEX "TrialGrant_customer_id_campaign_id_key" ON "TrialGrant"("customer_id", "campaign_id");
CREATE INDEX "TrialGrant_customer_id_status_started_at_idx" ON "TrialGrant"("customer_id", "status", "started_at");
CREATE INDEX "TrialGrant_campaign_id_status_idx" ON "TrialGrant"("campaign_id", "status");
CREATE UNIQUE INDEX "TrialAssignment_trial_config_id_key" ON "TrialAssignment"("trial_config_id");
CREATE INDEX "TrialAssignment_grant_id_idx" ON "TrialAssignment"("grant_id");
CREATE INDEX "TrialAssignment_customer_id_status_idx" ON "TrialAssignment"("customer_id", "status");

ALTER TABLE "TrialConfig" ADD CONSTRAINT "TrialConfig_campaign_id_fkey" FOREIGN KEY ("campaign_id") REFERENCES "TrialCampaign"("id") ON DELETE RESTRICT ON UPDATE CASCADE;
ALTER TABLE "TrialGrant" ADD CONSTRAINT "TrialGrant_customer_id_fkey" FOREIGN KEY ("customer_id") REFERENCES "Customer"("id") ON DELETE RESTRICT ON UPDATE CASCADE;
ALTER TABLE "TrialGrant" ADD CONSTRAINT "TrialGrant_campaign_id_fkey" FOREIGN KEY ("campaign_id") REFERENCES "TrialCampaign"("id") ON DELETE RESTRICT ON UPDATE CASCADE;
ALTER TABLE "TrialAssignment" ADD CONSTRAINT "TrialAssignment_grant_id_fkey" FOREIGN KEY ("grant_id") REFERENCES "TrialGrant"("id") ON DELETE RESTRICT ON UPDATE CASCADE;
ALTER TABLE "TrialAssignment" ADD CONSTRAINT "TrialAssignment_trial_config_id_fkey" FOREIGN KEY ("trial_config_id") REFERENCES "TrialConfig"("id") ON DELETE RESTRICT ON UPDATE CASCADE;
ALTER TABLE "TrialAssignment" ADD CONSTRAINT "TrialAssignment_customer_id_fkey" FOREIGN KEY ("customer_id") REFERENCES "Customer"("id") ON DELETE RESTRICT ON UPDATE CASCADE;

INSERT INTO "TrialCampaign" (
    "id",
    "code",
    "title",
    "status",
    "starts_at",
    "ends_at",
    "duration_days",
    "quota_label",
    "updated_at"
) VALUES (
    'trial-campaign-2026-05',
    'trial-2026-05',
    'SWIMVPN launch trial campaign',
    'ACTIVE',
    '2026-05-19T00:00:00.000Z',
    '2026-06-19T23:59:59.999Z',
    3,
    'UNLIMITED',
    CURRENT_TIMESTAMP
) ON CONFLICT ("code") DO NOTHING;
