# TODO

## Now
- Set up Docker Compose networking logic to replace `0.0.0.0` with proper service names.

## Next
- Implement Telegram Admin webhook integration for order notifications and fulfillment alerts.
- Build basic Admin Panel Frontend (optional, or focus on Bot).
- End-to-end testing of VpnState transitions from Android UI to Backend.

## Done
- Scaffold and implement 6 backend microservices and shared libraries.
- Implement inter-service TCP communication and HTTP Gateway.
- Define Android Network Models and API Service.
- Connect Android `MainViewModel` and `SubscriptionScreen` to the real backend.
- Perform PostgreSQL migration to enable `nanoid` extension and seed initial plans.
- Implement robust `vpn-config-engine-service` logic for VLESS (Reality/GRPC) and Shadowsocks.
- Add DTO validation using `class-validator` to all TCP/HTTP endpoints.
- Replace mock logic in services with real Prisma DB operations.
- Refactor Android backend integration and polish UI (VpnState handling, persistent selection).
- Implement OCR and QR code scanning for the "Import via QR" feature in Android.
- Implement Admin authentication and Telegram admin control layer.

## Alignment Follow-ups
- Expand vpn-config-engine protocol coverage beyond current parser support to match full priority ecosystem (VMess, Trojan, JSON Xray, JSON V2Ray).
- Add deeper admin session hardening (token rotation / refresh flow) when moving beyond minimal MVP compliance.
- Add integration tests for order paid->fulfilled path and session-bound admin auth flow.


## Notification Bot Next Steps
- Add pre-expiry reminder workflow (quota/offer reminder) in a separate scheduler-safe batch.
- Add explicit delivery history endpoint contract for admin panel consumption.
- Add integration tests for inventory-delivery-service -> 
otification-bot-service event handoff.


## Admin Support Bot Next Steps
- Persist support escalation tickets in PostgreSQL (support_cases) when this scope is approved.
- Add admin command handlers for /order, /resend, /status, /help in support-bot context (currently only guided customer escalation flow is implemented).
- Add optional callback actions in admin group (mark delivered, copy VPN link) when linked to finalized support-case storage.
- Add integration tests covering Telegram callback flow and support-group relay failure paths.

## Secrets Follow-up
- Provide real RESEND_API_KEY in root .env and ackend/.env before enabling delivery emails in production.
- Verify sender domain/email (support@swimvpn.pro) is validated in Resend account.
- Rotate previously exposed Telegram bot token and keep only regenerated token in non-versioned .env files.

## Env Provisioning
- Provide real values for placeholders in `/.env` and `/backend/.env`:
  - `TRAEFIK_DASHBOARD_AUTH`
  - `TELEGRAM_BOT_TOKEN`
  - `NOTIFICATION_BOT_TOKEN` (or leave empty intentionally)
  - `ADMIN_CHAT_ID`
  - `ADMIN_SUPPORT_BOT_TOKEN`
  - `RESEND_API_KEY`

## Dockploy Routing Follow-up
- Configure Dockploy Domain mapping for `api.swimvpn.pro` -> `gateway-service:3000`.
- Configure Dockploy Domain mapping for `admin.swimvpn.pro` according to desired backend route policy.
- Re-run deployment and verify health endpoint externally.

## Admin Support Bot Next
- Set `ADMIN_SUPPORT_REPORT_CHAT_ID=7161959711` in Dockploy env to receive personal support reports.
- Redeploy `main` and verify escalation flow: message -> email -> confirmation + group relay + personal report relay.

## Ops Scripts Follow-up
- Run `chmod +x scripts/ops/*.sh` on VPS after pulling latest `main`.
- Validate scripts on VPS with `bash -n scripts/ops/*.sh`.
- Wire periodic `backup-db.sh` via Dockploy schedule or cron.

## Android Follow-up
- In Android Studio: install the rebuilt APK on the affected device and confirm startup crash is resolved after scanner migration + Retrofit base URL fix.
- Re-test on device after the AppCompat theme fix.
- Re-run Android Studio's 16 KB APK compatibility check and confirm the old `libimage_processing_util_jni.so` warning is gone.
- If startup still crashes, collect `adb logcat` for the first fatal exception before making any broader Android changes.
- If production must support non-GMS devices later, evaluate a separate fallback scanner path as an explicit future task.

## Trial Contract Follow-up
- Re-test on a real Android device with a fresh install:
  - first open -> onboarding
  - onboarding complete -> profile completion
  - email + phone submission -> trial activation
  - trial profile -> home
- Validate one-time trial protection by retrying activation with the same device, email, and phone.
- Decide whether the frontend should show `offerCode` (`WEEK` / `MONTH` / `QUARTER`) more explicitly in premium badges or keep the current `accessType` labeling.
- Replace the placeholder Android payment URL flow once the client-side checkout contract is approved.

## Home Screen Follow-up
- Re-test the rebuilt Android home screen on device:
  - selected server card click -> servers page
  - profile icon -> profile page
  - floating `+` -> import/action sheet
- Decide whether the home server card should eventually display real backend latency/load once the backend exposes those metrics explicitly.
- Decide whether the home quick-action `+` should remain a sheet or evolve into a dedicated premium quick-actions surface later.
