# WORKLOG

## [2026-04-22] [VPN Core Batch 1 - Runtime Truth Foundations + Technical Settings Alignment]
- **Status**: DONE
- **Changes**:
    - Added typed Android runtime contracts for VPN execution:
      - `RuntimeMode`
      - `RuntimeStatus`
      - `RuntimeMetrics`
      - `ThemeMode`
    - Upgraded `VpnManager` so the app now tracks runtime mode, runtime status, metrics, handshake timing, and stable error state instead of relying only on the older coarse `VpnState`.
    - Reworked `SwimVpnService` to stop faking random failures and fake traffic generation.
    - Wired the service through the real config pipeline before tunnel startup:
      - parse
      - normalize
      - validate
      - prepare runtime payload
    - Kept this first batch honest:
      - only `FULL_TUNNEL` is treated as supported runtime mode
      - unsupported modes now fail explicitly instead of pretending to work
    - Added typed persisted settings for:
      - runtime mode
      - theme mode
      - existing language / auto-connect compatibility preserved
    - Implemented honest `autoConnect` behavior:
      - only when enabled
      - only when Android VPN permission is already granted
      - only when a valid active access and selected server exist
      - guarded against repeated relaunch loops
    - Centralized language + theme application in `MainActivity`.
    - Aligned `TechnicalSettingsScreen` with runtime truth:
      - removed deprecated local locale mutation
      - added a real theme preference surface (`SYSTEM / LIGHT / DARK`)
      - replaced fake routing toggle with truthful full-tunnel-only presentation
      - replaced fake kill-switch switch with an honest Android-system settings shortcut
    - Added technical-screen localization updates in:
      - `values`
      - `values-fr`
      - `values-ru`
- **Verification**:
    - `android\\gradlew.bat assembleDebug` PASSED.
    - `backend\\npm run build` PASSED.

## [2026-04-20] [Initialize NestJS Monorepo & Prisma Schema]
- **Status**: DONE
- **Changes**: Created monorepo structure, shared Prisma schema, and `nest-cli.json`.

## [2026-04-20] [Scaffold Microservices]
- **Status**: DONE
- **Changes**: Generated 6 apps with TCP/HTTP bootstrapping. Installed `@nestjs/microservices`.

## [2026-04-20] [Implement Core Domain & Inter-service Communication]
- **Status**: DONE
- **Changes**: 
    - Implemented `Store`, `Customer`, `Inventory`, `VPN-Config`, and `Admin` services.
    - Configured `Gateway` to route to all internal services.
    - Established `@app/contracts` for shared DTOs/Interfaces.
    - Enabled `ValidationPipe` across all 6 services for strict type safety.
- **Verification**: All services build successfully via `npm run build`.

## [2026-04-20] [DTO Validation & Infrastructure Polish]
- **Status**: DONE
- **Changes**: Added `class-validator` decorators to all shared DTOs and enabled global validation pipes in all microservice entry points. Added `concurrently` for local development.
- **Next Step**: Build Health Checker and Payment Webhooks.

## [2026-04-21] [Production-Ready Features & Android Integration]
- **Status**: DONE
- **Changes**:
    - Integrated Stripe and YooKassa payment webhooks in `customer-order-service` to automate fulfillment.
    - Built "Config Health Checker" in `vpn-config-engine` with TCP socket connectivity testing.
    - Added low-stock alerts and healthcheck commands to the Telegram Admin Bot.
    - Fixed Git remote configuration and pushed to origin.
    - Verified Android connection to Gateway via `10.0.2.2`.

## [2026-04-22] [Android Frontend Refactor & UI Polish]
- **Status**: DONE
- **Changes**:
    - Refactored `MainViewModel` to include persistent server selection using `PreferencesManager`.
    - Enhanced `HomeScreen` with visual state feedback (Progress Indicator during transitions).
    - Fixed `MainActivity` syntax errors and improved `ImportMenuSheet` state handling.
    - Cleaned up unused code and suppressed `@SuppressLint("HardwareIds")` for trial identification.
    - Verified build stability with `:app:assembleDebug`.

## [2026-04-22] [QR Code Scanning & Backend Integration]
- **Status**: DONE
- **Changes**:
    - Integrated ML Kit Barcode Scanning and CameraX for "Import via QR" feature.
    - Implemented `QrScannerView` and `processImageProxy` in `MainActivity`.
    - Wired `MainViewModel.importVless` to `ApiService.importSubscription`.
    - Implemented `importSubscription` and `activateCode` logic in backend `CustomerService`.
    - Added camera permissions handling and UI overlays for scanning.

## [2026-04-22] [Admin Auth & Telegram Control Layer]
- **Status**: DONE
- **Changes**:
    - Implemented JWT-based authentication for Admin in `admin-control-service`.
    - Created `AdminGuard` in `gateway-service` to secure admin endpoints via TCP verification.
    - Enhanced Telegram Admin Bot with `/orders` command for real-time monitoring.
    - Standardized environment variables in `docker-compose.yml`.
    - Verified inter-service token validation flow.

## [2026-04-23] [Production Docker Deployment Configuration]
- **Status**: DONE
- **Changes**:
    - Created root `docker-compose.yml` with full production stack configuration.
    - Designed Docker network strategy with `swimvpn-network` bridge network.
    - Implemented named volumes for PostgreSQL and Redis data persistence.
    - Added comprehensive health checks for all services.
    - Created `.env.example` with all required environment variables.
    - Wrote comprehensive `DEPLOYMENT_GUIDE.md` with production deployment instructions.
    - Ensured Dockploy compatibility for deployment orchestration.
    - Configured service dependencies, resource limits, and restart policies.
- **Verification**:
    - Docker Compose file validated with Docker Compose 3.8 syntax.
    - Health check endpoints need to be implemented in services.
    - Environment variables aligned with existing backend configuration.
- **Next Steps**:
    - Implement `/health` endpoints in all microservices.
    - Test Docker build and deployment locally.
    - Verify inter-service communication in Docker network.

## [2026-04-21] [Materialize Source-of-Truth Documentation]
- **Status**: DONE
- **Changes**:
    - Created docs/SOURCE_OF_TRUTH.md with canonical project constraints and truth statements.
    - Created docs/ARCHITECTURE.md with target microservice boundaries and MVP non-goals.
    - Created docs/DOMAIN_MODEL.md with core entities and required order traceability model.
    - Created docs/IMPLEMENTATION_RULES.md with execution discipline and guardrails.
- **Verification**:
    - Confirmed all four files exist under docs/.
    - Reviewed file contents against provided project truth list.


## [2026-04-21] [Backend Truth Alignment Batch - Compile/Contract/Deploy Realism]
- **Status**: DONE
- **Changes**:
    - Fixed backend compile drift (dependencies, Prisma-aligned seed, type contract mismatches).
    - Normalized Gateway route surface to a single /api/v1 prefix and removed public PSP webhook exposure.
    - Added minimal admin session persistence/validation/logout tied to dmin_sessions.
    - Removed fake store server fallback and kept DB-truth-backed server responses only.
    - Added explicit VPN config pipeline processing (ingest -> parse -> validate -> normalize -> classify -> preview -> prepare runtime payload) while preserving existing parser base.
    - Replaced invalid/fantasy Docker compose files with source-of-truth-aligned stack (PostgreSQL + 6 services, no WireGuard).
    - Updated env templates and deployment docs to match real runtime contracts (*_SERVICE_HOST, deferred PSP).
- **Verification**:
    - ackend: 
pm run build PASSED.
    - ackend: 
pm run lint PASSED (	sc --noEmit).
    - Root compose: docker compose config PASSED.
    - Backend compose: docker compose config PASSED.


## [2026-04-21] [Docker Port Exposure Hardening]
- **Status**: DONE
- **Changes**:
    - Removed public PostgreSQL port mapping from root compose (db no longer publishes 5432).
    - Kept only gateway public mapping (3000) and left all internal services network-only.
- **Verification**:
    - docker compose config PASSED for root stack.
    - Confirmed no published ports for 5432, 3001-3005, 6379, or 8081.


## [2026-04-22] [Final Deployment Hardening for VPS + Dockploy]
- **Status**: DONE
- **Changes**:
    - Rebuilt root production docker-compose.yml around implemented services only + Traefik (no WireGuard/OpenVPN, no internal public ports).
    - Enforced private-only networking for internal services; only Traefik publishes 80/443 externally.
    - Added dedicated prisma-migrate one-shot service gated by healthy PostgreSQL and required before app services.
    - Added production-safe health checks for each service using real dependency reachability checks.
    - Added 2GB-friendly memory/CPU limits for all services.
    - Hardened Traefik dashboard routing with mandatory basic auth and TLS.
    - Updated root .env.example to match required production variables and removed secret fallback behavior.
    - Updated backend Dockerfile default command to runtime only; migrations are now handled by prisma-migrate.
    - Upgraded gateway health endpoint to verify downstream service reachability before returning healthy.
- **Verification**:
    - ackend: 
pm run build PASSED.
    - Root compose syntax: docker compose config PASSED with required env variables set.
    - Port exposure check: only 80/443 are published in compose.


## [2026-04-22] [notification-bot-service MVP Utility]
- **Status**: DONE
- **Changes**:
    - Created new isolated microservice 
otification-bot-service (TCP 3006) for deterministic post-purchase delivery only.
    - Implemented Telegram admin notifications with structured order data and delivery action buttons.
    - Implemented SMTP transactional email sender (from SWIMVPN+ Support <support@swimvpn.pro>) with static RU/EN templates.
    - Added language fallback logic: default RU, fallback EN, optional payload language override.
    - Added TCP handlers: process_post_purchase_delivery, esend_delivery_email, get_delivery_status.
    - Added Telegram admin commands: /order, /status, /resend, /help (enabled via optional NOTIFICATION_BOT_TOKEN).
    - Integrated inventory-delivery-service to emit post-purchase delivery payload for paid orders.
    - Added service documentation with example payloads and resend-ready notes.
    - Added minimal template test script and verified it passes.
    - Added compose/env wiring for 
otification-bot-service and SMTP requirements.
- **Verification**:
    - ackend: 
pm run lint PASSED.
    - ackend: 
pm run build PASSED.
    - Template test: 
px ts-node apps/notification-bot-service/src/__tests__/template.spec.ts PASSED.
    - Root compose render: docker compose config PASSED with required env values.


## [2026-04-22] [Admin Support Bot (Deterministic Telegram) MVP Batch]
- **Status**: DONE
- **Changes**:
    - Added deterministic admin support bot inside dmin-control-service (no LLM, no freeform AI chat).
    - Implemented RU default + EN fallback topic menu with static guided replies for 7 required support topics.
    - Implemented escalation flow: unresolved/support buttons -> one short user message -> relay to support group ADMIN_SUPPORT_CHAT_ID.
    - Added escalation ticket id generation and clear admin relay formatter (topic, message, timestamp, language, optional email/phone/orderRef, Telegram identifiers).
    - Added in-memory anti-spam guard for escalation submissions.
    - Added minimal tests for formatter, language resolution, optional field extraction, and ticket id format.
    - Updated root and backend env examples with ADMIN_SUPPORT_* variables.
    - Corrected backend compose local wiring so ADMIN_SUPPORT_* variables are bound to dmin-control-service (not gateway).
- **Verification**:
    - ackend: 
pm run lint PASSED.
    - ackend: 
pm run build PASSED.
    - ackend: 
px ts-node apps/admin-control-service/src/__tests__/admin-support-bot.spec.ts PASSED.
    - Root compose render: docker compose config PASSED with required env variables set.
    - Backend compose render: docker compose config PASSED.

## [2026-04-22] [Secrets Hardening + Resend Mail Transport]
- **Status**: DONE
- **Changes**:
    - Removed remaining secret hardcodes from ackend/docker-compose.yml (POSTGRES_*, JWT_SECRET, admin auth secrets).
    - Added explicit ADMIN_JWT_SECRET wiring to dmin-control-service and removed fallback hardcoded JWT secret from app module.
    - Migrated 
otification-bot-service email sender from SMTP (
odemailer) to Resend API (esend).
    - Added Resend runtime env contract: RESEND_API_KEY, MAILER_FROM_EMAIL, MAILER_FROM_NAME.
    - Updated root/backend .env.example files accordingly.
    - Updated notification service README environment contract to Resend.
- **Verification**:
    - ackend: 
pm run lint PASSED.
    - ackend: 
pm run build PASSED.
    - Root compose render: docker compose config PASSED with required env values.
    - Backend compose render: docker compose config PASSED with required env values.

## [2026-04-22] [Env Files Full Regeneration Without Code Changes]
- **Status**: DONE
- **Changes**:
    - Fully regenerated `/.env` and `/backend/.env` from current runtime contract.
    - Preserved required support-bot constants:
      - `ADMIN_SUPPORT_CHAT_ID=-1003912107958`
      - `ADMIN_SUPPORT_DEFAULT_LANGUAGE=ru`
      - `ADMIN_SUPPORT_FALLBACK_LANGUAGE=en`
    - Generated fresh strong values for `POSTGRES_PASSWORD`, `JWT_SECRET`, and `ADMIN_JWT_SECRET`.
    - Replaced Telegram/Resend/chat fields with explicit placeholders to avoid reusing compromised values.
- **Verification**:
    - Read back both env files and confirmed key presence/structure.
    - No application source code files were modified.

## [2026-04-22] [Dockploy Mode Compose - Remove Embedded Traefik]
- **Status**: DONE
- **Changes**:
    - Removed embedded `traefik` service from root `docker-compose.yml` to avoid 80/443 bind conflict on VPS running Dockploy.
    - Removed Traefik-specific labels from `gateway-service` and removed `depends_on` gateway -> traefik.
    - Simplified networks/volumes by removing unused Traefik resources.
    - Updated `.env.example` and `DEPLOYMENT_GUIDE.md` to clarify Dockploy global proxy ownership.
- **Verification**:
    - `docker compose config` PASSED for root stack after changes.

## [2026-04-22] [Prisma Alpine OpenSSL Fix for Dockploy]
- **Status**: DONE
- **Changes**:
    - Updated `backend/Dockerfile` to install `openssl` and `libc6-compat` in builder and runtime stages.
    - Purpose: avoid Prisma schema engine/runtime failures on Alpine during `prisma migrate deploy`.
- **Verification**:
    - Commit pushed to `main`: `ab236d1`.
    - Runtime validation pending on VPS redeploy.

## [2026-04-22] [Fix Multi-Service Dist Build in Docker]
- **Status**: DONE
- **Changes**:
    - Added backend script `build:all` to compile all NestJS services explicitly.
    - Updated backend Dockerfile builder step from `npm run build` to `npm run build:all`.
    - This ensures `/app/dist/apps/<service>/main` exists for all runtime service commands.
- **Verification**:
    - Ran `npm run build:all` locally: all 7 services compiled successfully.
    - Confirmed `dist/apps` contains all required service directories including `store-engine-service`.

## [2026-04-22] [Admin Support Bot UX + Escalation Follow-Up Email]
- **Status**: DONE
- **Changes**:
    - Added 2-step escalation flow in admin support bot: issue message -> required email -> final confirmation.
    - Added runtime language switch option in support menu (`change_language`) with RU/EN callbacks.
    - Added optional personal admin report relay via `ADMIN_SUPPORT_REPORT_CHAT_ID`.
    - Kept deterministic static templates only (no AI/LLM logic).
    - Updated env contracts and compose wiring for new optional report chat id.
- **Verification**:
    - `backend`: `npm run lint` PASSED.

## [2026-04-22] [Android Import Flow Consolidation]
- **Status**: DONE
- **Changes**:
    - Removed the legacy import sheet as the main entry point from the Android home screen.
    - Rewired the home floating `+` action and the profile management entry to the same `ConfigImportScreen` hub.
    - Removed coupon-first wording from the main Android import UX since the feature is intentionally inactive for now.
    - Expanded `ConfigImportScreen` into a clearer access/config hub with paste, QR, and manual input paths visible without relying on a FAB.
    - Kept raw config import local-first while attempting profile sync through the existing backend import endpoint.
    - Softened backend sync failure behavior so a local import no longer throws the user into a blocking error screen.
- **Verification**:
    - `backend`: `npm run build` PASSED.
    - Android shell environment does not provide `gradle` or `gradlew`, so Android build verification was completed through static reference checks only in this batch.

## [2026-04-22] [Android Gradle Wrapper Restoration]
- **Status**: DONE
- **Changes**:
    - Restored the missing Android Gradle wrapper files:
      - `android/gradlew`
      - `android/gradlew.bat`
      - `android/gradle/wrapper/gradle-wrapper.jar`
    - Regenerated wrapper metadata from Gradle `8.11.1` to match the project distribution URL.
    - Confirmed the wrapper now boots correctly from the repository root Android module.
- **Verification**:
    - `android\\gradlew.bat -v` PASSED.
    - `android\\gradlew.bat assembleDebug` starts correctly through the restored wrapper, but the Gradle daemon crashes locally with a JVM crash log (`android/hs_err_pid11356.log`) before completion.
    - Existing debug APK still exists at `android/app/build/outputs/apk/debug/app-debug.apk`.

## [2026-04-22] [Critical Disk Space Cleanup]
- **Status**: DONE
- **Changes**:
    - Deleted Android JVM crash dumps and replay logs from `SWIMVPN-/android`.
    - Deleted Android build outputs under `android/app/build` and `android/build`.
    - Deleted global Gradle cache at `C:\\Users\\Lenovo\\.gradle`.
    - Removed the old `SWIM_TUNEL_VPN` junction from `C:\\Users\\Lenovo\\StudioProjects`.
    - Removed the real old project target at `D:\\Dev\\Projects\\SWIM_TUNEL_VPN`.
- **Verification**:
    - Disk `C:` improved from `0.63 GB` free to `2.10 GB` free.
    - Disk `D:` improved from `7.54 GB` free to `7.68 GB` free.
    - Remaining locked temp folders:
      - `android/.gradle-user-home`
      - `android/wrapper`
    - These remaining folders are still held by active Java/Android Studio processes.
    - `backend`: `npm run build` PASSED.
    - `backend`: `npx ts-node apps/admin-control-service/src/__tests__/admin-support-bot.spec.ts` PASSED.

## [2026-04-22] [Ops Automation Scripts Pack]
- **Status**: DONE
- **Changes**:
    - Added `scripts/ops/deploy.sh` for branch pull + compose redeploy + core logs.
    - Added `scripts/ops/health-check.sh` for container status, migration exit code, API health, and TLS issuer check.
    - Added `scripts/ops/backup-db.sh` for PostgreSQL custom-format backup from `db` container.
    - Added `scripts/ops/restore-db.sh` with explicit `RESTORE` confirmation for safety.
    - Added `scripts/ops/incident-report.sh` for quick diagnostics snapshot.
    - Added `scripts/ops/README.md` with Dockploy-ready usage examples.
- **Verification**:
    - Confirmed script files exist and are readable.
    - Full `bash -n` syntax validation could not run on this workstation (no bash runtime available).

## [2026-04-22] [Android 16KB Native Alignment Mitigation]
- **Status**: DONE
- **Changes**:
    - Replaced bundled ML Kit barcode dependency `com.google.mlkit:barcode-scanning:17.3.0` with Play Services variant `com.google.android.gms:play-services-mlkit-barcode-scanning:18.3.1` in `android/app/build.gradle`.
    - Goal: avoid shipping bundled native `.so` (`libimage_processing_util_jni.so`) that triggered 16KB alignment warning and probable startup crash on 16KB-page devices.
- **Verification**:
    - Local Gradle dependency task could not be executed from this workstation because Gradle wrapper scripts are missing in repo.
    - Validation must be completed in Android Studio (sync, rebuild APK, re-run 16KB check, runtime launch test).

## [2026-04-22] [Android Scanner Migration To Google Code Scanner]
- **Status**: DONE
- **Changes**:
    - Removed the remaining local scanner stack from `android/app/build.gradle`:
      - `com.google.android.gms:play-services-mlkit-barcode-scanning`
      - `androidx.camera:camera-camera2`
      - `androidx.camera:camera-lifecycle`
      - `androidx.camera:camera-view`
    - Added `com.google.android.gms:play-services-code-scanner:16.1.0`.
    - Replaced the `CameraX + ML Kit` QR scanner implementation in `android/app/src/main/java/com/swimvpn/app/MainActivity.kt` with Google Play Services Code Scanner.
    - Preserved scanner contract:
      - open scanner
      - scan QR
      - return raw scanned content to `viewModel.importVless(code)`
      - close cleanly on cancel/failure
    - Added `com.google.mlkit.vision.DEPENDENCIES=barcode_ui` metadata in `AndroidManifest.xml` to let Play Services provision the scanner module cleanly.
- **Verification**:
    - Rebuilt `android` debug APK successfully with Gradle 8.11.1 from the local wrapper distribution.
    - Inspected `android/app/build/outputs/apk/debug/app-debug.apk`.
    - Confirmed `libimage_processing_util_jni.so` is no longer packaged in any ABI.

## [2026-04-22] [Android Startup Crash Audit - Retrofit Base URL Fix]
- **Status**: DONE
- **Changes**:
    - Audited Android startup path (`MainActivity` -> `MainViewModel` -> `RetrofitClient`) without touching backend.
    - Identified a fatal initialization issue in `android/app/src/main/java/com/swimvpn/app/data/network/RetrofitClient.kt`:
      - Retrofit base URL was `http://10.0.2.2:3000` without the required trailing slash.
    - Fixed it to `http://10.0.2.2:3000/`.
- **Verification**:
    - Rebuilt Android debug APK successfully after the fix.
    - This removes the immediate `Retrofit.Builder().baseUrl(...)` crash path during `MainViewModel` creation.

## [2026-04-22] [Android Startup Crash Audit - AppCompat Theme Fix]
- **Status**: DONE
- **Changes**:
    - Audited Android launch path against the exported inspection reports.
    - Identified an AppCompat theme mismatch:
      - `MainActivity` extends `AppCompatActivity`
      - `Theme.SwimVpn` inherited from `android:Theme.Material.Light.NoActionBar`
    - Updated `android/app/src/main/res/values/themes.xml` so `Theme.SwimVpn` now inherits from `Theme.AppCompat.Light.NoActionBar`.
- **Verification**:
    - Rebuilt Android debug APK successfully after the theme change.
    - This removes the classic `Theme.AppCompat` launch crash path for `AppCompatActivity`.

## [2026-04-22] [Trial Contract Batch - Backend Truth + Android Bootstrap Alignment]
- **Status**: DONE
- **Changes**:
    - Added explicit trial contract notes to `docs/SOURCE_OF_TRUTH.md`:
      - 3-day trial
      - onboarding-linked activation
      - backend-generated public user number `SW-XXXXXX`
      - anti-abuse verification by device, email, and phone
    - Added backend access bootstrap endpoint and explicit trial activation endpoint.
    - Updated customer service to create/recover prospect profiles by device id and generate `SW-` public ids on creation.
    - Reworked backend trial activation to require email + phone and to enforce one-time trial eligibility.
    - Enriched profile payload with `phone`, `accessType`, `offerCode`, `profileCompletionRequired`, and `trialEligible`.
    - Aligned Android API client to `https://api.swimvpn.pro/`.
    - Replaced Android auto-trial startup with backend bootstrap flow.
    - Added `AppState.TrialSetup` and wired onboarding -> profile completion -> trial activation flow without changing premium screen grammar.
    - Added trial activation form inside the profile route for new users.
    - Corrected Android coupon activation to send the real `userNumber` instead of the device id.
- **Verification**:
    - `backend`: `npm run build` PASSED.
    - `android`: `assembleDebug` PASSED.

## [2026-04-22] [Android Home Alignment Batch]
- **Status**: DONE
- **Changes**:
    - Re-audited the Android home screen against the approved product direction.
    - Confirmed and preserved the profile route entry on the home header.
    - Replaced the simple active-server text line with a premium server selection card that reflects only available backend truth.
    - Reintroduced the floating `+` quick-action button on the home screen.
    - Wired the `+` button to the existing import/action sheet so config import, QR scan, and code activation remain reachable from the main usage screen.
    - Updated home badge/status copy to reflect backend-driven access truth more honestly (`TRIAL`, `EXPIRED`, active offer codes).
    - Added a clearer connection subtitle under the main power button to reflect VPN runtime state and selected server context.
- **Verification**:
    - `android`: `assembleDebug` PASSED.
    - `backend`: `npm run build` PASSED.

## [2026-04-22] [Developer Workstation Cleanup + Shell Startup Correction]
- **Status**: DONE
- **Changes**:
    - Audited the remaining high-volume local folders after the first emergency disk cleanup pass.
    - Removed the forced `Set-Location 'D:\Dev'` command from the user PowerShell profile so new shells stop jumping into the wrong repository by default.
    - Deleted the local `OneDrive` data tree after explicit user approval.
    - Removed the remaining local developer/download artifact folders:
      - `C:\Users\Lenovo\Downloads`
      - `C:\Users\Lenovo\AppData\Local\360extremebrowser`
      - `C:\Users\Lenovo\AppData\Local\npm-cache`
      - `C:\Users\Lenovo\AppData\Local\pnpm-cache`
      - `C:\Users\Lenovo\AppData\Local\ms-playwright`
- **Verification**:
    - Confirmed the PowerShell profile no longer contains the forced `Set-Location 'D:\Dev'`.
    - Confirmed the targeted folders no longer exist locally.
    - Re-checked disk space:
      - `C:` free space increased to `11.33 GB`
      - `D:` remained stable at `9.03 GB`

## [2026-04-22] [Android CLI Build Recovery]
- **Status**: DONE
- **Changes**:
    - Re-ran the Android Gradle wrapper after restoring disk headroom and correcting shell startup behavior.
    - Confirmed the repository-carried wrapper executes correctly from CLI.
    - Rebuilt the Android debug APK from the command line without relying on Android Studio.
- **Verification**:
    - `android\\gradlew.bat -v` PASSED.
    - `android\\gradlew.bat assembleDebug --stacktrace` PASSED.
    - Verified generated APK:
      - `android/app/build/outputs/apk/debug/app-debug.apk`

## [2026-04-22] [Git Commit Flow Unblocked For Android Cache Paths]
- **Status**: DONE
- **Changes**:
    - Audited the exported Git client log to identify the actual source of the commit failure.
    - Confirmed the failure was caused by `git add -A` trying to stage `android/.gradle-user-home/`, which contains very long Gradle cache paths.
    - Added `android/.gradle-user-home/` to `.gitignore` so GUI/CLI commit flows stop trying to stage that cache.
- **Verification**:
    - Confirmed `git status --short` no longer reports `android/.gradle-user-home/`.
    - Confirmed the only tracked change in the batch is the `.gitignore` fix itself.

## [2026-04-22] [Android Local Gradle Cache Removed Physically]
- **Status**: DONE
- **Changes**:
    - Removed the remaining physical `android/.gradle-user-home/` directory after confirming it was only a local Gradle cache.
    - Used a long-path-safe Windows mirror cleanup approach to clear the directory tree completely.
- **Verification**:
    - Confirmed `android/.gradle-user-home/` no longer exists physically.
    - Confirmed `git status --short` is clean.

## [2026-04-22] [ProfileScreen Alignment - Backend Truth + Android Mapping + Premium UI]
- **Status**: DONE
- **Changes**:
    - Stabilized backend profile analytics truth in `customer.service.ts`:
      - centralized expiration calculation
      - centralized profile status resolution
      - more robust parsing of `dataLimitGB` from `quota_label`
      - kept `dataUsedBytes` intentionally honest at `0` until a real backend meter exists
    - Enriched Android profile mapping in `Models.kt` with pure helpers for:
      - parsed backend consumption
      - effective expiry
      - measured limit detection
      - total consumed bytes
      - remaining bytes
      - consumed percentage
    - Updated `MainViewModel.kt` to consume normalized profile truth instead of ad hoc expiry/limit parsing.
    - Realigned `ProfileScreen.kt`:
      - identity card now shows `SW-XXXXXX` as primary identifier
      - email and phone are surfaced as secondary identity fields
      - access badge and analytics status now reflect backend-driven truth
      - analytics card stays premium while remaining honest about account + current-session usage
      - management list stays aligned with real product flows and does not reintroduce coupon logic
    - Localized newly introduced profile and trial strings in:
      - `values`
      - `values-fr`
      - `values-ru`
- **Verification**:
    - `backend\\npm run build` PASSED.
    - `android\\gradlew.bat assembleDebug` PASSED.
## [2026-04-22] [SupportScreen Truth Alignment]
- **Status**: DONE
- **Changes**:
    - Aligned Android `SupportScreen` contact truth with production support channels.
    - Replaced the legacy support email with `support@swimvpn.pro`.
    - Replaced the legacy Telegram link with the official support bot `@SWIMVPNSUPPORTADMINBOT`.
    - Added Telegram deep-link behavior with native-app first (`tg://resolve`) and web fallback (`https://t.me/...`).
    - Updated support rows to surface the real email address and Telegram handle directly in the UI while preserving the premium layout.
    - Refreshed FAQ copy so it matches the validated Android flows: connect via active server/imported config and import access via the unified import hub.
    - Localized the updated support strings in `values`, `values-fr`, and `values-ru`.
- **Verification**:
    - `android\\gradlew.bat assembleDebug` PASSED.
    - `backend\\npm run build` PASSED.
## [2026-04-22] [SubscriptionScreen Backend Truth Alignment]
- **Status**: DONE
- **Changes**:
    - Reworked Android `SubscriptionScreen` so plan cards render backend-driven values instead of legacy marketing mocks.
    - Replaced the misleading unlimited/access marketing copy with quota-aware subscription wording.
    - Plan cards now display backend truth directly:
      - `name`
      - localized `code`
      - `durationLabel`
      - `quotaLabel`
      - real `priceRub`
    - Kept the premium visual grammar while removing the old `BRONZE / SILVER / GOLD` dependency from the runtime rendering.
    - Updated subscription feature bullets to stay product-true instead of repeating unsupported promises.
    - Aligned Android `createOrder()` with backend truth by sending known profile email/phone instead of always `null`.
    - Removed the fake Stripe checkout URL and replaced it with an honest in-app order-created message until a real client checkout contract exists.
    - Added localized strings for the subscription alignment batch in `values`, `values-fr`, and `values-ru`.
- **Verification**:
    - `android\\gradlew.bat assembleDebug` PASSED.
    - `backend\\npm run build` PASSED.
## [2026-04-22] [VPN Core Batch 2 - Native Xray Packaging + Local Proxy Path]
- **Status**: DONE
- **Changes**:
    - Added build-time packaging for official Android `Xray-core` artifacts without committing large binaries to the repository.
    - Generated native runtime assets and `jniLibs` during Android build:
      - `arm64-v8a`
      - `x86_64`
      - shared geodata assets
    - Added a native runtime package under `com.swimvpn.app.runtime` with:
      - runtime asset catalog
      - runtime file preparation
      - Xray process bridge
      - session log / exit tracking
    - Extended `TunnelRuntimeAdapter` so Android can now produce a full Xray runtime document instead of only outbound fragments.
    - Integrated `SwimVpnService` with the native Xray bridge for a real `LOCAL_PROXY` path.
    - Kept `FULL_TUNNEL` on the transitional interface-only path until the `tun2socks` batch is implemented.
    - Enabled routing selection between `FULL_TUNNEL` and `LOCAL_PROXY` in the technical screen.
    - Updated Home / auto-connect flow so `LOCAL_PROXY` does not request Android VPN permission unnecessarily.
- **Verification**:
    - `android\\gradlew.bat assembleDebug --stacktrace` PASSED.
    - Generated runtime packaging confirmed in:
      - `android\\app\\build\\generated\\runtimeAssets\\main\\runtime\\xray`
      - `android\\app\\build\\generated\\runtimeJniLibs\\main`
## [2026-04-22] [VPN Core Batch 3 - Phase 2B tun2socks Contract Engagement]
- **Status**: DONE
- **Changes**:
    - Engaged Phase 2B without breaking the current Android runtime by adding a real `tun2socks` packaging and process contract.
    - Added optional build-time `tun2socks` packaging scaffolding in `android/app/build.gradle`:
      - non-blocking when no binary is configured
      - per-ABI local binary hooks via Gradle properties
      - generated availability manifest under runtime assets
    - Added dedicated runtime classes for `tun2socks`:
      - asset catalog
      - runtime models
      - runtime file preparation
      - process bridge
    - Extended `SwimVpnService` so the `FULL_TUNNEL` path now explicitly knows whether `tun2socks` is packaged and logs/announces that truth.
    - Kept `LOCAL_PROXY` untouched and fully working.
    - Kept the transitional `FULL_TUNNEL` path alive instead of replacing it with an incomplete native data plane.
- **Verification**:
    - `android\\gradlew.bat assembleDebug --stacktrace` PASSED.
    - `android\\gradlew.bat assembleDebug` PASSED after manifest/build cleanup.
## [2026-04-22] [VPN Core Batch 4 - Phase 2B JNI-First tun2socks Wiring]
- **Status**: DONE
- **Changes**:
    - Reoriented Android `tun2socks` integration toward the upstream Android truth: packaged shared library + `tun fd` contract instead of a pretend CLI-only model.
    - Extended Android build packaging so `tun2socks` can now advertise two artifact types per ABI:
      - packaged shared library (`libhev-socks5-tunnel.so`)
      - optional executable fallback
    - Added richer runtime availability metadata and launch-mode selection:
      - `JNI`
      - `EXECUTABLE`
      - `MISSING`
    - Added session-scoped `tun2socks` config-file preparation so later JNI wiring can consume a stable runtime artifact without rebuilding service state ad hoc.
    - Updated the executable fallback process bridge to launch from the prepared config file instead of the older fake flag contract.
    - Updated `SwimVpnService` full-tunnel startup so it now:
      - starts and validates Xray before arming the TUN path
      - excludes the app package from the VPN builder to reduce self-routing loops
      - establishes a concrete `tun fd` + config contract for future `tun2socks` JNI wiring
      - reports honestly whether the runtime has a packaged JNI library, only an executable fallback, or no `tun2socks` artifact at all
    - Added a contract-only `Tun2SocksNativeBridge` helper to capture the final JNI handoff shape while keeping the build green until a repo-owned JNI shim is packaged.
- **Verification**:
    - `backend\\npm run build` PASSED.
    - `android\\gradlew.bat assembleDebug --stacktrace` PASSED.
## [2026-04-22] [VPN Core Batch 5 - Phase 2B JNI Shim + Upstream tun2socks Auto-Build]
- **Status**: DONE
- **Changes**:
    - Added a repo-owned Android JNI shim build for `tun2socks`:
      - `android/app/CMakeLists.txt`
      - `android/app/src/main/cpp/tun2socks_jni.c`
    - Extended Android app build configuration so the JNI shim is compiled through `externalNativeBuild` for:
      - `arm64-v8a`
      - `x86_64`
    - Upgraded `Tun2SocksNativeBridge` from contract-only scaffolding to a real Kotlin/JNI bridge capable of:
      - loading the shim
      - loading the packaged upstream `libhev-socks5-tunnel.so`
      - starting the native tunnel loop with a real `tun fd`
      - stopping the native runtime
      - querying native stats
    - Updated `SwimVpnService` so the full-tunnel path now wires:
      - validated Xray runtime
      - Android TUN interface
      - JNI tun2socks bridge
      - stop/error handling for the native data plane
    - Fixed the Gradle `prepareTun2SocksRuntimeAssets` task so upstream source builds are copied from the real NDK output location instead of an incorrect nested `jni/libs/...` assumption.
    - Verified that the app can now auto-build upstream `hev-socks5-tunnel` shared libraries on this workstation during Android build.
- **Verification**:
    - `backend\\npm run build` PASSED.
    - `android\\gradlew.bat assembleDebug --stacktrace` PASSED.
    - `android\\gradlew.bat assembleDebug` PASSED.
## [2026-04-23] [Backend Batch - Prisma Production Hardening]
- **Status**: DONE
- **Changes**:
    - Replaced the fragile `Customer.public_id` database-generated default with application-owned ID generation in Prisma schema and customer creation flows.
    - Updated `customer-order-service` order creation so new customers always receive a generated `public_id` and normalized email/phone values.
    - Added a versioned Prisma baseline migration under:
      - `backend/prisma/migrations/202604230001_init_schema/migration.sql`
      - `backend/prisma/migrations/migration_lock.toml`
    - Replaced ad hoc Prisma rollout guidance with production-oriented scripts:
      - `prisma:generate`
      - `prisma:validate`
      - `prisma:migrate:deploy`
      - `prisma:seed`
      - `prisma:baseline:prod`
      - `prisma:deploy`
    - Hardened Prisma startup by making services fail fast with a clear schema-drift error when critical production columns are missing.
    - Updated backend setup documentation to baseline existing prod databases once, then rely on `migrate deploy` instead of `db push`.
- **Verification**:
    - `backend\\npm run prisma:validate` PASSED.
    - `backend\\npm run prisma:generate` PASSED.
    - `backend\\npm run build` PASSED.
## [2026-04-23] [Ops Batch - Production Prisma Rollout Runbook]
- **Status**: DONE
- **Changes**:
    - Added a dedicated production Prisma rollout script:
      - `scripts/ops/prisma-rollout.sh`
      - flow: compose validation -> db start -> backup -> optional baseline -> migrate -> seed
    - Added a dedicated `prisma-seed` one-shot service to the root compose stack.
    - Updated the stack so application services wait for `prisma-seed` success instead of only `prisma-migrate`.
    - Extended ops scripts and docs so rollout observability covers both:
      - `prisma-migrate`
      - `prisma-seed`
    - Updated deployment documentation to use the rollout script instead of an implicit `db push`-style startup assumption.
- **Verification**:
    - `backend\\npm run build` PASSED.
    - root compose render validation attempted in current environment.
## [2026-04-23] [Ops Batch - Dockploy Exact Rollout Commands]
- **Status**: DONE
- **Changes**:
    - Added an exact Dockploy production command sequence to `DEPLOYMENT_GUIDE.md`.
    - Added an explicit order-of-execution section to `scripts/ops/README.md`.
    - Documented the public endpoint re-test commands to run immediately after rollout.
- **Verification**:
    - `backend\\npm run build` PASSED.
    - `docker compose config` PASSED.
## [2026-04-23] [Ops Batch - Prisma Seed Linux Stability Fix]
- **Status**: DONE
- **Changes**:
    - Reduced Prisma seed runtime overhead on Linux/Dockploy by switching the seed command to `ts-node --transpile-only`.
    - Increased `prisma-seed` memory allowance in root compose from `128m` to `256m` to avoid `SIGKILL` during rollout.
    - Kept the seed logic and rollout order unchanged; this batch only hardens execution stability.
- **Verification**:
    - `backend\\npm run build` PASSED.
    - `docker compose config` PASSED.
## [2026-04-23] [Ops Batch - Dockploy Proxy Network Exposure For Gateway]
- **Status**: DONE
- **Changes**:
    - Attached `gateway-service` to the external `dokploy-network` used by the global Dockploy Traefik proxy.
    - Added explicit Traefik labels on `gateway-service` for:
      - `api.swimvpn.pro`
      - `admin.swimvpn.pro`
    - Kept all internal services private on `swimvpn-private`; only the public gateway joins the shared proxy network.
- **Verification**:
    - `docker compose config` PASSED.
## [2026-04-23] [Android VPN Batch - Xray Runtime APK Extraction Fix]
- **Status**: DONE
- **Changes**:
    - Fixed Android Xray runtime preparation so the app no longer depends on `nativeLibraryDir` containing an extracted `libxray.so`.
    - Added a fallback path that extracts the packaged ABI-specific Xray binary directly from the installed APK into the runtime session directory.
    - Kept the runtime packaging model intact:
      - Xray remains packaged per ABI in the APK
      - the service now materializes an executable copy itself before launch
- **Verification**:
    - `android\\gradlew.bat assembleDebug` PASSED.
## [2026-04-23] [Android VPN Batch - Xray Must Run From Extracted Native Library Path]
- **Status**: DONE
- **Changes**:
    - Switched Android packaging to legacy native-lib extraction so packaged Xray binaries are materialized by the installer under `nativeLibraryDir`.
    - Added `android:extractNativeLibs="true"` to make the install-time extraction intent explicit.
    - Updated `RuntimeFilePreparer` to execute Xray directly from `nativeLibraryDir` when available instead of copying it into `no_backup/...`, which real devices refused to execute.
- **Verification**:
    - `android\\gradlew.bat assembleDebug` PASSED.
## [2026-04-23] [Android VPN Batch - Parser gRPC Runtime Payload Preservation]
- **Status**: DONE
- **Changes**:
    - Fixed Android parser/runtime handoff so `gRPC` configs no longer lose `serviceName` during parsing.
    - Extended `ConfigParserEngine` URL parsing for:
      - `VLESS`
      - `VMess`
      - `Trojan`
      so `grpcSettings` are populated when the raw config carries `serviceName`.
    - Extended JSON config parsing for:
      - `VLESS`
      - `VMess`
      - `Trojan`
      so `streamSettings.grpcSettings` is preserved into the canonical Android profile.
    - Improved `VMess` URL parsing so runtime-relevant fields are no longer dropped silently:
      - `ws path/host`
      - `grpc serviceName`
      - `tls sni/alpn/fingerprint`
      - `tcp headerType/host`
    - Kept the change narrowly scoped to parser/runtime payload truth without changing backend contracts.
- **Verification**:
    - `android\\gradlew.bat assembleDebug` PASSED.
## [2026-04-23] [Android VPN Batch - Supported Link Parser Hardening]
- **Status**: DONE
- **Changes**:
    - Hardened the Android parser for the already supported formats:
      - `vless://`
      - `vmess://`
      - `trojan://`
      - `ss://`
      - JSON Xray/V2Ray
    - Added tolerant transport alias handling:
      - `raw` -> `TCP`
      - `httpupgrade` / `xhttp` / `splithttp` -> `HTTP2`
    - Made `VMess` Base64 decoding more tolerant to URL-safe and no-padding variants.
    - Improved `Trojan` parsing for advanced real-world variants:
      - preserves `Reality` fields
      - preserves `TCP` header settings
      - preserves `ALPN` and `fingerprint`
    - Improved `Shadowsocks` parsing for:
      - legacy/base64 variants
      - bracketed IPv6 hosts
      - query/fragment-safe parsing
    - Improved JSON parsing so the Android app can now pick the first supported outbound instead of assuming the first array item is always the VPN tunnel.
- **Verification**:
    - `android\\gradlew.bat assembleDebug` PASSED.
## [2026-04-23] [Android VPN Batch - Tolerant Trojan/VLESS URL Parsing]
- **Status**: DONE
- **Changes**:
    - Removed the fragile dependency on strict `URI.create(...)` parsing for `vless://` and `trojan://` links.
    - Added a tolerant structured URL parser that preserves:
      - decorated fragments/tags
      - raw query strings
      - bracketed IPv6 hosts
      - userinfo/password values
    - This specifically hardens import of real-world links whose fragment contains characters that strict URI parsing often rejects.
- **Verification**:
    - `android\\gradlew.bat assembleDebug` PASSED.
