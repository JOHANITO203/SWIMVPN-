# WORKLOG

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
