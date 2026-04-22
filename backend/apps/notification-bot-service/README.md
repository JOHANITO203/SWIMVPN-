# notification-bot-service

Lightweight utility microservice for post-purchase delivery only.

## Scope
- deterministic Telegram admin notifications
- deterministic transactional email sending
- static templates (RU default, EN fallback)
- no LLM, no generative API, no payment logic, no VPN runtime logic

## TCP handlers
- `process_post_purchase_delivery`
- `resend_delivery_email`
- `get_delivery_status`

## Expected payload (`process_post_purchase_delivery`)
```json
{
  "orderRef": "SW12345",
  "customerEmail": "user@example.com",
  "customerPhone": "+79990001122",
  "planCode": "MONTH",
  "planLabel": "1 Month",
  "vpnLink": "vless://...",
  "expiryLabel": "30 days",
  "customerLanguage": "ru"
}
```

## Resend payload (`resend_delivery_email`)
```json
{
  "orderRef": "SW12345",
  "language": "en"
}
```

## Telegram admin commands
- `/order SW12345`
- `/status SW12345`
- `/resend SW12345`
- `/help`

## Failure rules
- Telegram failure: warning log, continue processing.
- Email failure: update delivery state and return error result.

## Environment
Required:
- `DATABASE_URL`
- `ADMIN_CHAT_ID`
- `TELEGRAM_BOT_TOKEN` (sender)
- `RESEND_API_KEY`
- `MAILER_FROM_EMAIL`
- `MAILER_FROM_NAME`

Optional:
- `NOTIFICATION_BOT_TOKEN` for dedicated Telegram command bot polling.

## Resend-ready notes
- Resend uses order reference and current assigned VPN link from DB.
- Language can be forced via resend payload (`ru`/`en`).
- Delivery history is stored in `Delivery.notes` as serialized status snapshot.
