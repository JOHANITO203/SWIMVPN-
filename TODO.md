# TODO

## Now
- Start VPN core batch 2:
  - package prebuilt native runtime artifacts
  - replace prepared-runtime placeholder with real native tunnel execution
  - keep `FULL_TUNNEL` as the only exposed mode until the execution path is truly live
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
  - floating `+` -> unified import hub
- Decide whether the home server card should eventually display real backend latency/load once the backend exposes those metrics explicitly.

## Import Flow Follow-up
- Re-test on device that both entry points lead to the same experience:
  - home `+` -> import hub
  - profile management import row -> import hub
- Confirm QR, paste, and manual input all:
  - preserve raw config locally
  - update the visible imported profile list
  - attempt backend profile sync without throwing a blocking full-screen error
- Decide later whether code activation should return as a separate approved product flow instead of a hidden generic import action.
- If backend usage metering is introduced later, replace the current honest hybrid display (`backend dataUsedBytes + local session bytes`) with a fully server-driven total.

## Android Build Follow-up
- Re-test Technical Settings on device:
  - theme `SYSTEM / LIGHT / DARK`
  - language switching `EN / FR / RU`
  - honest kill-switch shortcut -> Android VPN settings
  - auto-connect only when permission and access are already valid
  - routing selection:
    - `FULL_TUNNEL`
    - `LOCAL_PROXY`
- Validate the new native Xray local proxy path on device:
  - start / stop lifecycle
  - Xray process stays alive
  - local SOCKS port responds
  - logs are written under runtime session directory
- Complete the next VPN core native batch:
  - validate the JNI-driven `FULL_TUNNEL` data plane end-to-end on device
  - confirm `tun fd` forwarding is stable across repeated start/stop cycles
  - decide whether executable fallback remains only diagnostic or should now be removed
  - add runtime bytes telemetry instead of session-only placeholders
  - confirm upstream auto-build of `tun2socks` remains reproducible on clean environments
- Re-test the subscription screen on device:
  - backend plans render with correct name, duration, quota, and RUB price
  - selecting a plan still enables order creation
  - create order shows the honest pending-payment message instead of a fake checkout URL
- Re-test the support screen on device:
  - email row -> opens mail app with support@swimvpn.pro
  - Telegram row -> opens @SWIMVPNSUPPORTADMINBOT in Telegram or web fallback
  - renew button -> routes to subscription
- Use the restored repository wrapper as the default Android verification path:
  - `android\\gradlew.bat assembleDebug`
- Re-test the freshly rebuilt APK on device before making the next frontend alignment batch.
- Re-test the aligned profile screen on device for:
  - trial active
  - paid active
  - expired
  - profile incomplete
- [FIXED] Updated `MainViewModel.kt` to handle HTTP 500 and network timeouts with localized error messages.
- [INVESTIGATED] HTTP 500 on `bootstrap` is likely due to `backend/.env` using `localhost` for `DATABASE_URL` instead of `db` (when running in Docker), or missing `npx prisma db seed` on the server.

## Prisma / Production Follow-up
- Apply the new Prisma production rollout on the real deployment target:
  - back up production database
  - run `npm run prisma:baseline:prod` once if the schema was previously created without migrations
  - run `npm run prisma:migrate:deploy`
  - run `npm run prisma:seed`
- Preferred operational path on the server:
  - `scripts/ops/prisma-rollout.sh --baseline` for the first rollout on the old production DB
  - `scripts/ops/prisma-rollout.sh` for subsequent normal rollouts
- Execute the exact Dockploy runbook now that it is documented in `DEPLOYMENT_GUIDE.md`.
- Re-run `scripts/ops/prisma-rollout.sh --baseline` on Dockploy after the seed stability fix (`--transpile-only` + `256m` seed container).
- Redeploy after attaching `gateway-service` to `dokploy-network` and adding explicit Traefik labels for `api.swimvpn.pro` / `admin.swimvpn.pro`.
- After rollout, re-test public backend endpoints:
  - `GET /api/v1/store/plans`
  - `POST /api/v1/access/bootstrap`
  - `POST /api/v1/access/trial`
- Re-test the Android VPN engine on device after the Xray APK extraction fix:
  - confirm the old `Packaged Xray runtime is missing for ABI arm64-v8a` error is gone
  - capture the next runtime/logcat signal if connection still fails
- Re-test the Android VPN engine after enabling extracted native libraries:
  - confirm the old `Cannot run program .../no_backup/.../bin/xray` error is gone
  - capture the next runtime/logcat signal if connection still fails
- Re-test the Android VPN engine after the parser/runtime payload gRPC fix:
  - validate a real `vless://...type=grpc&serviceName=...` inventory config on device
  - confirm the runtime now carries `grpcSettings.serviceName`
  - capture the next device/logcat signal if the tunnel still fails after parser preservation
- If production still returns `500`, collect runtime logs from:
  - gateway-service
  - store-engine-service
  - customer-order-service

## Disk Cleanup Follow-up
- Re-check Android build behavior now that disk pressure has been reduced significantly and shell startup no longer forces `D:\Dev`.
- If Android build temp folders are recreated and become large again, clean them after each heavy build/debug batch:
  - `android/.gradle-user-home/`
  - `android/wrapper/`
- Keep `android/.gradle-user-home/` out of commits permanently; it is now intentionally ignored by Git.
- If `android/.gradle-user-home/` reappears later, treat it as disposable local cache and delete it after the build/debug batch instead of trying to version it.
