-- Bump each plan price by 25% (absolute values → idempotent, safe to re-run).
-- Basic:    299 RUB / 3.49 USD  -> 374 RUB / 4.36 USD
-- Premium:  699 RUB / 7.99 USD  -> 874 RUB / 9.99 USD
-- Platinum: 1899 RUB / 21.99 USD -> 2374 RUB / 27.49 USD
UPDATE "Plan" SET "price_rub" = 374,  "price_usd" = 4.36  WHERE "code" = 'WEEK';
UPDATE "Plan" SET "price_rub" = 874,  "price_usd" = 9.99  WHERE "code" = 'MONTH';
UPDATE "Plan" SET "price_rub" = 2374, "price_usd" = 27.49 WHERE "code" = 'QUARTER';
