-- AlterTable: web-only Tribute flow correlation key.
-- Tribute's payment webhook carries ONLY Telegram identity (no email/order-ref/metadata),
-- so the pending web order stores the verified telegram_user_id to match the webhook.
ALTER TABLE "Order" ADD COLUMN     "telegram_user_id" TEXT;

-- CreateIndex: webhook lookup is by telegram_user_id among PENDING orders.
CREATE INDEX "Order_telegram_user_id_idx" ON "Order"("telegram_user_id");
