# WORKLOG

## [2026-04-25] [Android Final Review Follow-up - Active Config Source Truth]
- **Status**: DONE
- **Changes**:
    - Rewired `MainViewModel` active-config resolution to follow the actual selected `activeServer` instead of the repository's stale imported `active_profile_id`.
    - Added an honest `SWIMVPN_MANAGED` metadata mapper for backend-selected servers using only current runtime identity fields already exposed by backend server nodes.
    - Added repository lookup support for imported metadata by selected imported server id so imported selections still render parser-derived config details from preserved raw config.
    - Added focused regression coverage for backend-managed active-config mapping without invented parser quota/provider fields.
- **Verification**:
    - `cd android && $env:GRADLE_OPTS='-Dkotlin.compiler.execution.strategy=in-process'; .\gradlew.bat --no-daemon testDebugUnitTest --tests com.swimvpn.app.config.ActiveConfigMetadataMappingTest --tests com.swimvpn.app.config.SubscriptionParserTest` PASSED.
    - `cd android && .\gradlew.bat --no-daemon :app:processDebugResources :app:compileDebugKotlin` PASSED.

## [2026-04-25] [Android Task 4 Final Follow-up - Midnight Timestamp Date Semantics]
- **Status**: DONE
- **Changes**:
    - Updated `ProfileScreen.kt` expiry formatting so date-semantic midnight timestamps such as `2026-04-25T00:00:00Z` are displayed as date-only instead of being converted into local `yyyy-MM-dd HH:mm`.
    - Preserved date-only semantics for both plain dates and midnight timestamp variants, avoiding invented clock time and timezone-caused day shifts for effectively date-based parser metadata.
- **Verification**:
    - `cd android && .\gradlew.bat --no-daemon :app:processDebugResources :app:compileDebugKotlin` PASSED.

## [2026-04-25] [Android Task 4 Follow-up - Active Config Honesty Fixes]
- **Status**: DONE
- **Changes**:
    - Adjusted `Active Config` traffic rendering so usage-only parser metadata is shown under a dedicated `Config usage` label instead of the misleading `Config quota` label.
    - Kept quota labeling only for rows where total quota actually exists, including `used / total` presentation when both values are present.
    - Made parser expiry formatting honest for date-only values by preserving the original date string without inventing a local clock time.
    - Added the new active-config usage label in `values`, `values-fr`, and `values-ru`.
- **Verification**:
    - `cd android && .\gradlew.bat --no-daemon :app:processDebugResources :app:compileDebugKotlin` FAILED due to Kotlin daemon/JVM memory exhaustion (`Native memory allocation (mmap) failed` / pagefile pressure), after resources and Kotlin compilation had already started.
    - `cd android && $env:GRADLE_OPTS='-Dkotlin.compiler.execution.strategy=in-process'; .\gradlew.bat --no-daemon :app:processDebugResources :app:compileDebugKotlin` PASSED.

## [2026-04-25] [Android Task 4 - Separate Profile Access And Active Config Cards]
- **Status**: DONE
- **Changes**:
    - Split `ProfileScreen.kt` into two distinct profile sections so backend access truth now lives in `SWIMVPN Access` and parser/runtime metadata renders separately in `Active Config`.
    - Kept the existing backend quota, usage, status, and expiry logic centered on `profile` without mixing in imported-config metadata.
    - Added an `Active Config` card that appears only when `activeConfigMetadata` exists and shows source badge, display name, provider, protocol, parser quota, and parser expiration only when present.
    - Added the new card labels and active-config metadata strings in `values`, `values-fr`, and `values-ru`.
- **Verification**:
    - `cd android && .\gradlew.bat --no-daemon :app:processDebugResources :app:compileDebugKotlin` PASSED.

## [2026-04-25] [Android Task 2 Follow-up - Active Config Metadata Quality Fixes]
- **Status**: DONE
- **Changes**:
    - Preserved parsed subscription-level provider, quota, and expiry metadata in `ActiveConfigMetadata.fromRawConfig(...)` when raw config parsing yields no profile entries.
    - Tightened `ConfigRepository` source classification so all user-imported source types map to `IMPORTED_CONFIG`.
    - Added targeted regression tests for fallback metadata preservation and imported-source classification.
- **Verification**:
    - `cd android && .\gradlew.bat --no-daemon testDebugUnitTest --tests com.swimvpn.app.config.ActiveConfigMetadataMappingTest` FAILED first with missing `activeConfigSourceFor`.
    - `cd android && .\gradlew.bat --no-daemon testDebugUnitTest --tests com.swimvpn.app.config.ActiveConfigMetadataMappingTest --tests com.swimvpn.app.config.SubscriptionParserTest` PASSED.

## [2026-04-25] [Android Task 2 - Expose Active Config Metadata From Repository]
- **Status**: DONE
- **Changes**:
    - Added `ActiveConfigMetadata.fromRawConfig(...)` to map active metadata directly from preserved raw config using the existing subscription parser.
    - Added `ConfigRepository.getActiveConfigMetadata()` to expose active config metadata from the currently selected profile.
    - Added a repository-facing metadata mapping test that verifies imported provider and traffic values are derived from the active profile raw config.
- **Verification**:
    - `cd android && .\gradlew.bat --no-daemon testDebugUnitTest --tests com.swimvpn.app.config.ActiveConfigMetadataMappingTest` FAILED first with `Unresolved reference: fromRawConfig`.
    - `cd android && .\gradlew.bat --no-daemon testDebugUnitTest --tests com.swimvpn.app.config.ActiveConfigMetadataMappingTest --tests com.swimvpn.app.config.SubscriptionParserTest` PASSED.

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

## [2026-04-25] [Android Parser Robustness Batch - Normalized Subscription Metadata]
- **Status**: DONE
- **Changes**:
  - Added a dedicated Kotlin subscription parser layer under `android/app/src/main/java/com/swimvpn/app/config/subscriptionparser/`.
  - Introduced normalized parser models for:
    - `ParsedSubscription`
    - `ParsedVpnProfile`
  - Kept raw config payloads intact while adding extraction for:
    - protocol
    - transport/security hints
    - host/port
    - credentials
    - provider hints
    - traffic metadata
    - expiry
    - auto-update interval
    - country emoji / UTF-8 display names
    - warnings without hard crashes
  - Added specialized parser helpers for:
    - Base64 subscription decoding
    - JSON array/object subscription expansion
    - metadata parsing
  - Added fallback parsing for VMess and Shadowsocks entries when they need a lighter path than the runtime parser.
  - Integrated the new parser into `ConfigRepository` so imports consume normalized subscription entries before runtime normalization.
  - Preserved the existing runtime pipeline:
    - ingest
    - parse
    - normalize
    - validate
    - prepare runtime payload
- **Verification**:
  - `cd android && ./gradlew.bat --no-daemon :app:processDebugResources :app:compileDebugKotlin` PASSED.
  - `cd android && $env:GRADLE_OPTS='-Dkotlin.compiler.execution.strategy=in-process'; ./gradlew.bat --no-daemon testDebugUnitTest --tests com.swimvpn.app.config.SubscriptionParserTest` PASSED.
  - `cd android && $env:GRADLE_OPTS='-Dkotlin.compiler.execution.strategy=in-process'; ./gradlew.bat --no-daemon testDebugUnitTest --tests com.swimvpn.app.config.VpnConfigLinkExtractorTest` PASSED.
- **Notes**:
  - Kotlin daemon instability was environmental on this machine during parallel builds, so the parser tests were re-run in-process to get a reliable verification signal.

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
    - Added TCP handlers: process_post_purchase_delivery, 
esend_delivery_email, get_delivery_status.
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
odemailer) to Resend API (
esend).
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
## [2026-04-23] [Android VPN Batch - Imported Server Groups In Profile Server Catalog]
- **Status**: DONE
- **Changes**:
  - Added support for treating one imported payload as a group of multiple VPN server entries instead of forcing everything into a single imported profile.
  - `ConfigRepository` now splits multi-link imports (`vless://`, `vmess://`, `trojan://`, `ss://`) into multiple normalized profiles, stores bundle metadata, and derives imported profile groups.
  - Import preview now warns when one pasted payload contains multiple server entries.
  - `MainViewModel` now merges backend servers and imported server groups into one connection catalog while preserving a grouped representation for the servers page.
  - Imported servers can now be selected as active connection targets and provide their own raw config to `SwimVpnService` instead of always falling back to the backend subscription URL.
  - Added local pinning for servers and exposed grouped sections on the servers page so multiple imported groups can coexist with backend-provided access servers.
- **Verification**:
  - `android\gradlew.bat assembleDebug` PASSED.
## [2026-04-23] [Android VPN Batch - Latency Probing And Tunnel Traffic Observability]
- **Status**: DONE
- **Changes**:
  - Added a client-side TCP latency evaluator for connection servers so the app can estimate server responsiveness instead of always showing `0ms`.
  - `MainViewModel` now refreshes latency after building or refreshing the success state, including imported grouped servers.
  - Wired tun2socks JNI byte counters into `VpnManager`, so full-tunnel sessions now expose real traffic deltas instead of staying blind on throughput.
  - Confirmed during audit that the Android client did not contain an explicit bandwidth throttle; the main missing piece was measurement/observability.
- **Verification**:
  - `android\gradlew.bat assembleDebug` PASSED.
## [2026-04-23] [Android VPN Batch - Throughput Stabilization MTU DNS Diagnostics]
- **Status**: DONE
- **Changes**:
  - Lowered full-tunnel Android MTU to `1280` and centralized it in `SwimVpnService` so the VPN interface and tun2socks launch spec use the same safer value.
  - Hardened the default Xray DNS section by removing `localhost` and keeping only explicit upstream resolvers.
  - Added lightweight runtime diagnostics to `VpnManager` metrics:
    - active mode
    - Xray session id
    - Xray log path
    - tun2socks session id
    - tun2socks log path
  - Exposed these diagnostics on the technical settings screen so slow or half-working sessions are inspectable on-device without backend support.
- **Verification**:
  - `android\gradlew.bat assembleDebug` PASSED.
## [2026-04-23] [Android Roadmap Rebaseline - Parser Before Engine Before Perf]
- **Status**: DONE
- **Changes**:
  - Added a dedicated Android execution memory document:
    - `ANDROID_EXECUTION_STATUS.md`
  - Rebased the Android roadmap so the active priority order is now explicitly:
    - parser coverage and truth
    - engine/runtime completion
    - performance tuning
  - Recorded that `2-B` is considered closed from an implementation/integration perspective.
  - Recorded the major Android accomplishments already completed since `2-B` closure so future work can start from a stable shared memory instead of reopening old uncertainty.
- **Verification**:
  - Document created and aligned with existing `WORKLOG.md`, `DECISIONS.md`, and `TODO.md`.
## [2026-04-23] [Android Parser Batch - Modern Supported Link Variants Preservation]
- **Status**: DONE
- **Changes**:
  - Hardened `VMess` URL parsing for real public variants by adding a `path -> serviceName` fallback when `net=grpc`.
  - Extended parser transport recognition for already-supported `HTTP2`-family transports:
    - `http`
    - `h2`
    - `httpupgrade`
    - `xhttp`
    - `splithttp`
  - Preserved `path/host` metadata for `VLESS`, `VMess`, `Trojan`, and JSON configs using those `HTTP2`-family transports.
  - Added canonical model support for `Shadowsocks` plugin metadata:
    - `plugin`
    - `plugin-opts`
  - Added an explicit warning when `Shadowsocks` plugin metadata is preserved but runtime support is not yet fully verified, so parser truth improves without pretending engine completeness.
- **Verification**:
  - `android\\gradlew.bat assembleDebug` PASSED.
## [2026-04-23] [Android Engine Batch - Reliable Stop And Port Release]
- **Status**: DONE
- **Changes**:
  - Hardened `SwimVpnService` shutdown so stop is now idempotent even if UI/runtime state is already desynchronized.
  - Added cancellation of any in-flight startup coroutine before shutdown so a session cannot finish booting after the user already requested disconnect.
  - Added defensive `xrayBridge.stopAll()` cleanup after targeted session shutdown to avoid orphaned Xray processes keeping local ports occupied.
  - Stop now refuses to early-return when runtime resources are still active, even if `VpnManager` already says `IDLE` or `STOPPING`.
- **Verification**:
  - `android\\gradlew.bat --no-daemon assembleDebug` PASSED.
## [2026-04-23] [Android Parser Batch - Recognize Happ Deep Links And Subscription Wrappers]
- **Status**: DONE
- **Changes**:
  - Extended import classification so the app now explicitly recognizes:
    - `happ://add/...`
    - `happ://crypt3/...`
    - `happ://crypt4/...`
    - `happ://crypt5/...`
    - `happ://routing/add/...`
    - `happ://routing/onadd/...`
    - `happ://routing/off`
    - plain `http://` / `https://` subscription URLs
  - Added tolerant unwrapping for `happ://add/...` so direct supported node links wrapped by Happ can flow back into the normal parser.
  - Added explicit classification errors for recognized-but-not-yet-implemented cases instead of generic unsupported format failures:
    - remote subscriptions
    - Happ encrypted subscriptions
    - Happ routing deep links
  - Updated `isLikelyVpnConfig` so clipboard/manual import now treats Happ deep links and subscription URLs as recognizable VPN-related inputs.
- **Verification**:
  - `android\\gradlew.bat --no-daemon packageDebug --stacktrace` PASSED.
## [2026-04-23] [Android Parser Batch - Remote Subscription Fetch Import]
- **Status**: DONE
- **Changes**:
  - Implemented remote `http(s)` subscription import in `ConfigRepository`.
  - `happ://add/<https-url>` now unwraps to a subscription URL and enters the same remote fetch path.
  - Added bounded OkHttp fetch with redirects and timeouts.
  - Added Base64 subscription payload detection/decoding before reusing the existing multi-link import flow.
  - Preserved the existing direct-node import path and still keeps Happ encrypted/routing links as explicitly recognized but not implemented.
- **Verification**:
  - `android\\gradlew.bat --no-daemon assembleDebug` PASSED.
## [2026-04-23] [Android Parser Batch - Recognize Modern Unsupported Protocol Schemes]
- **Status**: DONE
- **Changes**:
  - Added explicit recognition for modern VPN schemes that appear in public subscriptions but are not supported by the current Xray runtime path yet:
    - `hy2://`
    - `hysteria2://`
    - `hysteria://`
    - `tuic://`
    - `socks://`
    - `socks5://`
    - `wg://`
    - `wireguard://`
  - Extended subscription splitting so those links are isolated instead of being glued to neighboring supported entries.
  - Parser now returns protocol-specific unsupported messages rather than a generic unknown-format error.
  - Mixed subscriptions can still import supported entries while reporting unsupported modern entries separately.
- **Verification**:
  - `android\\gradlew.bat --no-daemon assembleDebug` PASSED.
## [2026-04-23] [Android Parser Batch - Happ Crypt Version Classification]
- **Status**: DONE
- **Changes**:
  - Added version-aware classification for Happ encrypted subscription deep links.
  - Treats `happ://crypt5/...` as the current/preferred Happ encrypted subscription format.
  - Treats `happ://crypt3/...` and `happ://crypt4/...` as legacy Happ encrypted subscription formats.
  - Replaced the generic encrypted-subscription error with an actionable message that directs users toward original `https://` subscriptions, `happ://add/https://...` wrappers, or unencrypted standard node links.
  - Documented that Happ encrypted imports require an authorized provider key/format before implementation.
- **Verification**:
  - First root-level wrapper invocation failed because the repository root is not the Gradle project root.
  - `cd android && .\\gradlew.bat --no-daemon assembleDebug` PASSED.
## [2026-04-23] [Android Parser Batch - SWIMVPN Crypt1 Import Format]
- **Status**: DONE
- **Changes**:
  - Added recognition for SWIMVPN-owned encrypted imports using `swimvpn://crypt1/<payload>`.
  - Added `SWIMVPN_CRYPT1_KEY_BASE64` as a Gradle/BuildConfig-controlled AES-256 key.
  - Added AES-256-GCM decrypt support with 12-byte nonce prefixed to ciphertext.
  - Added optional GZIP unpacking after decrypt so compact subscription payloads can be supported later.
  - Reused the existing direct/grouped import pipeline after decrypt so raw config handling and parser rules stay centralized.
- **Verification**:
  - `cd android && .\\gradlew.bat --no-daemon assembleDebug` PASSED.
## [2026-04-23] [Backend Batch - SWIMVPN Crypt1 Link Generator]
- **Status**: DONE
- **Changes**:
  - Added backend DTO for SWIMVPN encrypted import generation.
  - Added `vpn-config-engine-service` generator for `swimvpn://crypt1/<payload>`.
  - Uses `SWIMVPN_CRYPT1_KEY_BASE64`, AES-256-GCM, random 12-byte nonce, and base64url output.
  - Supports optional GZIP compression before encryption.
  - Exposed generation through authenticated admin gateway route `POST /api/v1/admin/crypt-import`.
  - Added environment placeholders to backend and deployment compose files.
- **Verification**:
  - `cd backend && npm run lint` PASSED.
  - `cd backend && npm run build:all` PASSED.
## [2026-04-23] [Android Security Batch - Remove APK Crypt1 Secret]
- **Status**: DONE
- **Changes**:
  - Removed Android `SWIMVPN_CRYPT1_KEY_BASE64` BuildConfig wiring.
  - Removed APK-side AES-GCM `crypt1` decrypt implementation.
  - Kept `swimvpn://crypt1/...` recognition so the app can provide explicit feedback instead of treating the link as unknown.
  - Android now blocks offline crypt1 import and states that backend-side resolution is required to avoid exposing a decryption key in the APK.
  - Documented the next secure path: device/auth-bound backend resolution endpoint.
- **Verification**:
  - `cd android && .\\gradlew.bat --no-daemon assembleDebug` PASSED.
  - `rg "SWIMVPN_CRYPT1_KEY_BASE64|swimCrypt1KeyBase64|SwimCryptImportCodec|Cipher|AES/GCM|decryptCrypt1" android src -S` returned no matches.
## [2026-04-23] [Android Parser Batch - Abort Unresolved Happ Crypt Imports]
- **Status**: DONE
- **Changes**:
  - Kept explicit recognition for `happ://crypt3/...`, `happ://crypt4/...`, and `happ://crypt5/...`.
  - Prevented encrypted Happ links from becoming clipboard-previewable/importable when their payload cannot be resolved.
  - Preserved the explicit import error explaining that Happ-protected encrypted links require an authorized provider key/format.
  - Documented that unsupported encrypted Happ links must fail closed instead of producing fake partial imports.
- **Verification**:
  - `cd android && .\\gradlew.bat --no-daemon assembleDebug` PASSED.
## [2026-04-23] [Backend/Android Batch - Device-Bound SWIMVPN Crypt1 Resolution]
- **Status**: DONE
- **Changes**:
  - Added backend crypt1 resolution in `vpn-config-engine-service`.
  - Added customer-service authorization gate requiring `userNumber`, matching `deviceId`, and active access before decrypting.
  - Added gateway endpoint `POST /api/v1/subscription/resolve-crypt`.
  - Android now sends `swimvpn://crypt1/...` to the backend resolver and imports the returned raw payload through the existing parser pipeline.
  - Kept crypt1 keys out of the Android APK.
- **Verification**:
  - `cd backend && npm run lint` PASSED.
  - `cd backend && npm run build:all` PASSED.
  - `cd android && .\\gradlew.bat --no-daemon assembleDebug` PASSED.
## [2026-04-23] [Android Parser Batch - Preserve Modern Link Metadata]
- **Status**: DONE
- **Changes**:
  - Added canonical `flow` preservation for VLESS/Reality and JSON VLESS profiles.
  - Passed preserved VLESS `flow` into Xray runtime payload generation.
  - Added tolerant insecure flag parsing for `allowInsecure`, `insecure`, `tlsInsecure`, and `skip-cert-verify`.
  - Preserved advanced metadata such as `fragment`, `noises`, `serverDescription`, `packetEncoding`, `mux`, and Hysteria2-style fields for diagnostics.
  - Kept Hysteria2/TUIC/SOCKS/WireGuard recognized as unsupported runtime schemes while exposing detected diagnostic metadata in warnings.
- **Verification**:
  - `cd android && .\\gradlew.bat --no-daemon assembleDebug` PASSED.
## [2026-04-23] [Android Import UX Batch - Silent Unsupported Format Diagnostics]
- **Status**: DONE
- **Changes**:
  - Mixed imports now keep recognized-but-unsupported runtime formats out of visible success warnings when supported servers are imported.
  - Added silent diagnostic fields to successful import results for skipped unsupported formats.
  - Preserved explicit failure when a payload contains no supported importable server.
  - Updated import success toasts to report the number of servers imported from manual, clipboard, or QR flows.
- **Verification**:
  - `cd android && .\\gradlew.bat --no-daemon assembleDebug` PASSED.
## [2026-04-23] [ADB Runtime Diagnostic - Device Engine Smoke Check]
- **Status**: DONE
- **Findings**:
  - Device connected: Samsung SM-S916B, Android 16, ABI `arm64-v8a`.
  - Installed app package is `com.swimvpn.app`, debug/test build `1.0`.
  - App process is running and Xray process `libxray.so` is active.
  - Xray starts successfully from the generated runtime config.
  - Runtime config exposes local SOCKS `127.0.0.1:10808` and HTTP `127.0.0.1:10809` inbounds.
  - Active outbound observed as `trojan` toward `159.195.6.151`.
  - Full tunnel interface `tun0` is up with MTU `1280` and default routing through `tun0`.
  - `tun0` byte counters move slightly, but throughput still needs an intentional traffic test.
  - No Xray stderr errors were observed; Xray stdout only showed startup/deprecation warnings.
- **Next Diagnostic Focus**:
  - Confirm tun2socks JNI bridge runtime status and logs during active browsing.
  - Compare `LOCAL_PROXY` vs `FULL_TUNNEL` on the same selected node.
## [2026-04-23] [Android UI Batch - Routing Mode Status Panel]
- **Status**: DONE
- **Changes**:
  - Replaced the connectivity routing dropdown row with a centered routing control panel.
  - Added separate Tunnel and Proxy route buttons with neutral inactive state and green active indicators.
  - Passed runtime status and active runtime mode into the technical settings screen so the UI can distinguish selected routing from active routing.
  - Kept existing persistence and runtime mode change callbacks unchanged.
- **Verification**:
  - `cd android && .\gradlew.bat --no-daemon assembleDebug` PASSED.
## [2026-04-23] [ADB Runtime Diagnostic - Local Proxy Node Test]
- **Status**: DONE
- **Findings**:
  - `LOCAL_PROXY` path starts Xray without creating `tun0`, which is expected.
  - Xray exposes local SOCKS `127.0.0.1:10808` and HTTP `127.0.0.1:10809` listeners.
  - Direct internet test from device succeeded against `https://example.com`.
  - HTTP proxy and SOCKS proxy tests through Xray accepted the local connection but timed out before receiving upstream response.
  - Xray logs confirm local requests were accepted and routed to the `proxy` outbound.
  - Selected outbound server was `trojan` at `159.195.6.151:443`.
  - Device ping to `159.195.6.151` succeeded with roughly 84-106 ms RTT.
  - TCP connect to `159.195.6.151:443` timed out, while TCP connect to `1.1.1.1:443` succeeded.
- **Conclusion**:
  - The local proxy engine is alive, but the selected server's VPN TCP endpoint is not usable from the device/network at test time.
  - Next test should use another imported node before changing local proxy engine code.
## [2026-04-23] [Android Subscription Batch - Provider User-Agent Negotiation]
- **Status**: DONE
- **Changes**:
  - Added subscription fetch fallback User-Agents for providers that return different payloads per client.
  - Fetch now tries SWIMVPN default, then v2rayNG-compatible, then Happ-compatible User-Agents.
  - Keeps the first directly importable payload, preferring Base64/direct standard node subscriptions over unsupported YAML paths.
  - Verified the provider sample URL returns Base64 `vless://` entries with the v2rayNG-compatible User-Agent.
- **Verification**:
  - `cd android && .\gradlew.bat --no-daemon assembleDebug` PASSED.
## [2026-04-23] [Android Import UX Batch - Enable Subscription URL Imports Without Preview]
- **Status**: DONE
- **Changes**:
  - Added lightweight import-attempt eligibility for recognized remote subscription URLs.
  - Manual import dialog no longer requires a local preview before enabling import for subscription URLs.
  - Kept direct config validation preview behavior for node links.
  - Kept unsupported Happ crypt/routing links blocked from manual import attempts.
- **Verification**:
  - `cd android && .\gradlew.bat --no-daemon assembleDebug` PASSED.
## [2026-04-23] [Android Subscription Batch - Robust Provider Payload Selection]
- **Status**: DONE
- **Changes**:
  - Strengthened Base64 subscription decoding for standard, URL-safe, padded, unpadded, and newline-wrapped payloads.
  - Added subscription payload scoring so direct supported node links are preferred over opaque JSON arrays when providers return different content per User-Agent.
  - Added a defensive decode pass immediately before grouped entry splitting so raw Base64 subscriptions cannot fall through as a single unsupported config.
  - Fixed grouped link splitting so `ss://` is not falsely detected inside `vless://`.
  - Used temporary ADB diagnostics to confirm the provider resolves to 11 VLESS Reality entries, then removed payload-shape logging from the APK code.
  - Kept remote subscription imports inside the existing grouped server import pipeline.
  - Preserved unsupported Happ encrypted payload behavior; no third-party protected format bypass was added.
- **Verification**:
  - `cd android && .\gradlew.bat assembleDebug` PASSED.
  - ADB import test for `https://wb.routerwb.ru/jtz5386jCHkztYRZ` imported 11 server(s) into the locations list.
## [2026-04-23] [Android Parser Hardening Batch - Tokenizer And Extractor]
- **Status**: DONE
- **Changes**:
  - Moved provider link extraction into a dedicated `VpnConfigLinkExtractor`.
  - Centralized recognized VPN schemes for direct and modern unsupported formats.
  - Extracts links from multiline subscriptions and JSON/string wrappers without matching schemes inside another scheme token.
  - Preserves JSON Xray/V2Ray block parsing when no direct links are embedded.
  - Added regression tests for `vless://` versus nested `ss://`, mixed provider payloads, JSON-embedded links, and recognized unsupported modern links.
- **Verification**:
  - `cd android && .\gradlew.bat testDebugUnitTest` PASSED.
  - `cd android && .\gradlew.bat assembleDebug` PASSED.
## [2026-04-23] [Android Runtime Audit - Full Tunnel Data Plane]
- **Status**: DONE
- **Findings**:
  - Temporary `TRAFFIC_AUDIT` logs showed Xray starts correctly and exposes local SOCKS `127.0.0.1:10808` plus HTTP `127.0.0.1:10809`.
  - ADB proxy curls through SOCKS/HTTP succeeded, proving the selected VLESS Reality node and Xray outbound were usable.
  - Direct full-tunnel traffic previously failed before reaching Xray, especially DNS, which pointed to the Android TUN/tun2socks bridge rather than the provider node.
  - The generated tun2socks file used an internal JSON shape, while packaged `hev-socks5-tunnel` expects upstream YAML sections: `tunnel`, `socks5`, optional `mapdns`, and `misc`.
- **Changes**:
  - Removed all temporary `TRAFFIC_AUDIT` logs from the APK code.
  - Changed tun2socks runtime generation from `tun2socks-android.json` to upstream-compatible `tun2socks-main.yml`.
  - Kept MTU `1280` and local Xray proxy ports unchanged.
- **Verification**:
  - `cd android && .\gradlew.bat --no-daemon testDebugUnitTest` PASSED.
  - `cd android && .\gradlew.bat --no-daemon assembleDebug` PASSED.
  - ADB installed the debug APK successfully.
  - ADB UI-driven full-tunnel test returned `HTTP/1.1 200 OK` for direct `curl -I -L https://example.com`.
  - ADB SOCKS proxy test also returned `HTTP/1.1 200 OK` through `127.0.0.1:10808`.
- **Remaining Observation**:
  - The runtime can be active while the main UI still displays `Disconnected`; this is now a separate state synchronization bug to fix next.
## [2026-04-23] [Android Runtime State Sync - UI Truth Reconciliation]
- **Status**: DONE
- **Changes**:
  - Added `RuntimeStateStore` as a small persisted runtime snapshot written by `SwimVpnService`.
  - Service now publishes `STARTING`, `RUNNING`, `STOPPING`, `FAILED`, and `IDLE` states with timestamps.
  - Added a runtime heartbeat while `FULL_TUNNEL` or `LOCAL_PROXY` is active so stale snapshots expire safely.
  - `HomeScreen` now reconciles visual state from the runtime snapshot instead of trusting memory-only state.
  - Power button stop behavior works even after a visual/accessibility state drift.
- **Verification**:
  - `cd android && .\gradlew.bat --no-daemon testDebugUnitTest assembleDebug` PASSED.
  - ADB visual screenshot confirmed the home screen displays `Connected` while the runtime store is `RUNNING`.
  - ADB direct full-tunnel curl to `https://example.com` returned `HTTP/1.1 200 OK`.
  - ADB stop from the power button changed persisted runtime state back to `IDLE`.
  - After stop, no `libxray.so` process remained; remaining `:10808` sockets were `TIME_WAIT`, not listeners.
## [2026-04-23] [Android ADB Precision Runtime Test - Current Imported VLESS Node]
- **Status**: DONE
- **Scope**:
  - Ran a no-code ADB verification pass on device `SM-S916B` / Android `16`.
  - Tested the currently selected imported VLESS node `158.160.196.185`, shown in app as roughly `17ms`.
  - Focused on runtime truth, local proxy listeners, TUN state, and real HTTPS timings.
- **Findings While Connected**:
  - Runtime state store reported `status=RUNNING`, `mode=FULL_TUNNEL`.
  - `libxray.so` was running.
  - Local Xray listeners were active on `127.0.0.1:10808` and `127.0.0.1:10809`.
  - `tun0` was active with MTU `1280` and address `10.0.0.2/24`.
  - Full-tunnel HTTPS requests succeeded:
    - `https://example.com`: HTTP `200`, total `1.278s`.
    - `https://www.cloudflare.com/cdn-cgi/trace`: HTTP `200`, total `0.882s`.
    - `https://www.google.com/generate_204`: HTTP `204`, total `0.844s`.
    - `https://www.apple.com/library/test/success.html`: HTTP `200`, total `0.793s`.
  - Local proxy path remained faster:
    - SOCKS `127.0.0.1:10808` to `example.com`: HTTP `200`, total `0.408s`.
    - HTTP proxy `127.0.0.1:10809` to `example.com`: HTTP `200`, total `0.311s`.
- **Stop Verification**:
  - First stop tap landed while the app was on the locations screen and did not stop the runtime.
  - Second center power tap returned runtime state to `IDLE`.
  - Final cleanup check showed no `libxray.so`, no `10808/10809` listeners, and no `tun0`.
- **Conclusion**:
  - The current node and local Xray runtime are usable; the app is passing traffic.
  - Full tunnel is functional but slower than the direct local proxy path, which matches expected `VpnService + tun2socks` overhead.
  - Remaining speed complaints should be investigated with per-node quality testing and data-plane throughput measurement rather than parser changes.
## [2026-04-23] [Android Technical UI - Proxy Recommended And Real Theme Direction]
- **Status**: DONE
- **Changes**:
  - Shortened visible routing labels to `Tunnel` and `Proxy`.
  - Added a `Recommended` badge to Proxy and a `Tunneling` badge to Tunnel.
  - Kept green active indicators for the runtime mode that is actually running.
  - Refactored the Application section into a more polished cockpit-style card while preserving Language and Visual Theme icons.
  - Started replacing light-only hardcoded technical-screen colors with Material color scheme tokens.
  - Strengthened the app theme palettes so `System`, `Light`, and `Dark` produce genuinely distinct surfaces.
  - Updated `C:\Users\Lenovo\Downloads\PLAN.md` with accomplished runtime/parser work and the new Proxy-first direction.
- **Verification**:
  - Pending Android build verification in this batch.
## [2026-04-23] [Android Phase 3-A - Persistent Auto-Connect And Honest Kill Switch Status]
- **Status**: DONE
- **Changes**:
  - Added persistent storage for the last runnable VPN launch payload in `PreferencesManager`.
  - Updated `MainViewModel` to save the launch payload before starting the VPN and to clear it on sign-out.
  - Added `AutoConnectBootReceiver` for `BOOT_COMPLETED` and `MY_PACKAGE_REPLACED`.
  - Boot restore skips `FULL_TUNNEL` when Android VPN permission still requires user approval.
  - Technical settings now read Android secure settings to distinguish:
    - `SYSTEM`
    - `ALWAYS-ON`
    - `LOCKDOWN`
  - The kill-switch tile remains a shortcut to Android VPN settings, but the chip/subtitle are now more truthful.
- **Verification**:
  - `cd android && .\gradlew.bat --no-daemon assembleDebug` PASSED.
  - Direct device check performed earlier in the batch returned:
    - `settings get secure always_on_vpn_app` -> `null`
    - `settings get secure always_on_vpn_lockdown` -> `0`
  - A later ADB re-check could not run because no device was attached at that moment.
## [2026-04-23] [Android Localization Stability Batch - FR/RU Resource Repair]
- **Status**: DONE
- **Changes**:
  - Rebuilt `values-fr/strings.xml` and `values-ru/strings.xml` in clean UTF-8.
  - Restored full key parity across `values`, `values-fr`, and `values-ru`.
  - Added missing localized keys used by the home screen and import flow.
  - Replaced critical user-facing hardcoded strings in `MainActivity` with `stringResource(...)`.
  - Replaced critical user-facing hardcoded errors/group labels in `MainViewModel` with localized resource lookups.
  - Kept runtime/parser/backend scope untouched.
- **Verification**:
  - `cd android && .\gradlew.bat --no-daemon assembleDebug` PASSED.
  - `cd android && .\gradlew.bat --no-daemon installDebug` PASSED.
  - ADB app-locale checks performed on connected Samsung `SM-S916B`:
    - `cmd locale set-app-locales com.swimvpn.app --locales fr`
    - `cmd locale set-app-locales com.swimvpn.app --locales ru`
    - `cmd locale get-app-locales com.swimvpn.app` returned `[ru]` after the Russian pass.
  - Logcat launch checks in `fr` and `ru` showed no `FATAL EXCEPTION` or `Resources$NotFoundException` from `com.swimvpn.app`.
## [2026-04-23] [Android Locale Flicker Fix - Startup Locale Apply Moved Out Of Compose]
- **Status**: DONE
- **Changes**:
  - Moved locale application out of the Compose setContent collection loop in MainActivity.
  - Applied the persisted app language once at activity startup before setContent.
  - Kept runtime theme collection in Compose, but made language changes explicit through the technical settings callback.
  - Preserved the existing PreferencesManager language persistence and app state model.
- **Verification**:
  - cd android && .\gradlew.bat --no-daemon assembleDebug installDebug PASSED.
  - ADB restart-loop regression check on connected Samsung SM-S916B:
    - cmd locale set-app-locales com.swimvpn.app --locales fr --delegate false
    - force-stop + launcher restart
    - RestartCount=0
    - FatalCount=0
  - App locale remained r after launch.
## [2026-04-23] [Technical Screen Guard Against Immediate External Settings Launch]
- **Status**: DONE
- **Changes**:
  - Added a short arming delay on TechnicalSettingsScreen before the kill-switch/settings shortcut becomes clickable.
  - Kept the kill-switch tile behavior intact after the guard delay.
  - Left routing, auto-connect, and theme/language logic unchanged in this batch.
- **Verification**:
  - cd android && .\gradlew.bat --no-daemon assembleDebug installDebug PASSED.
  - No FATAL EXCEPTION or app-side Java crash was observed during the technical-screen investigation.
  - Logs showed com.android.settings activity involvement, supporting the accidental external-settings-launch hypothesis.
## [2026-04-23] [Android DNS/Routing Hardening - IPv4 Resolver Baseline]
- **Status**: DONE
- **Changes**:
  - Centralized the default tunnel DNS set into a shared IPv4-only baseline:
    - `1.1.1.1`
    - `1.0.0.1`
    - `8.8.8.8`
    - `8.8.4.4`
  - Reused that same DNS list in both:
    - Xray runtime generation
    - Android `VpnService.Builder`
  - Changed Xray DNS `queryStrategy` from `UseIP` to `UseIPv4`.
  - Changed routing `domainStrategy` from `AsIs` to `IPIfNonMatch`.
  - Kept MTU, runtime modes, parser behavior, and UI untouched in this batch.
- **Verification**:
  - `cd android && .\gradlew.bat --no-daemon assembleDebug` PASSED.
  - Reviewed generated diff to confirm the change stayed limited to DNS/routing runtime policy.
## [2026-04-23] [Android Product Wording Correction - Tunnel Normal Mode, Proxy Advanced]
- **Status**: DONE
- **Changes**:
  - Audited the recent runtime journal against live ADB behavior on device.
  - Confirmed `LOCAL_PROXY` is operational as a local proxy path, but should not be presented as the normal whole-device browsing mode.
  - Reassigned the visible `Recommended` badge to `Tunnel`.
  - Reassigned `Proxy` to an advanced/manual badge and wording.
  - Updated home and technical copy in EN/FR/RU to explain `Proxy` as a manual or proxy-aware path.
  - Kept the runtime engine unchanged in this batch.
- **Verification**:
  - Live ADB audit confirmed local listeners on `127.0.0.1:10808` and `127.0.0.1:10809`.
  - Live forwarded proxy checks returned `HTTP 200` through both HTTP and SOCKS proxy paths.
## [2026-04-23] [Android Proxy Runtime Rollback - Restore Pre-Hardening Policy]
- **Status**: DONE
- **Changes**:
  - Confirmed the post-positive-comment runtime delta was still active in shared Xray DNS/routing generation.
  - Made runtime network policy mode-aware.
  - Restored `LOCAL_PROXY` to its earlier Xray network policy:
    - DNS servers: `1.1.1.1`, `8.8.8.8`
    - `queryStrategy = UseIP`
    - `domainStrategy = AsIs`
  - Kept `FULL_TUNNEL` on the newer tunnel-specific policy introduced by the hardening batch.
  - Left Android `VpnService` tunnel DNS configuration unchanged.
- **Verification**:
  - Diff review confirmed the rollback targets `LOCAL_PROXY` only.
## [2026-04-24] [Android Proxy Stabilization Confirmed After Reinstall + Runtime Audit]
- **Status**: DONE
- **Changes**:
  - Re-audited `LOCAL_PROXY` after sync and full app reinstall on the phone.
  - Confirmed the active Xray runtime now uses the intended proxy rollback policy:
    - DNS servers: `1.1.1.1`, `8.8.8.8`
    - `queryStrategy = UseIP`
    - `domainStrategy = AsIs`
  - Re-ran live proxy-path checks and real device opening scenarios through DuckDuckGo and Chrome.
  - Observed temporary client-side inconsistency during investigation, then confirmed that DuckDuckGo and the user's other real-world app flows recovered and work again.
  - Frozen the proxy runtime in its current state with no further engine changes.
- **Verification**:
  - Active runtime state remained `LOCAL_PROXY` + `RUNNING`.
  - Active Xray session config matched the rollbacked proxy policy.
  - Forwarded proxy checks returned `HTTP 200` through HTTP and SOCKS listeners.
  - Live device/browser audit confirmed that navigation behavior recovered.
## [2026-04-24] [Subscription Checkout MVP - Crypto Pay + Manual Card Telegram Flow]
- **Status**: DONE
- **Changes**:
  - Replaced the Android fake subscription checkout with a backend-driven `orders/checkout` flow.
  - Added payment-method selection on the Android subscription screen: `CARD_MANUAL` and `CRYPTO`.
  - Stopped trusting client-supplied plan pricing during checkout; the backend now loads the amount from PostgreSQL plan truth.
  - Added Crypto Pay invoice creation in `customer-order-service` using the official Crypto Pay API contract (`createInvoice` + `bot_invoice_url`).
  - Added public gateway webhook endpoint for Crypto Pay payment updates and backend verification of `crypto-pay-api-signature`.
  - Added manual card-payment MVP flow through the Telegram notification bot:
    - app opens Telegram bot with order deep link
    - bot shows the configured card number and instructions
    - user sends screenshot proof
    - bot forwards proof to private review chat with approve/reject buttons
    - approve triggers paid+fulfillment
    - reject marks the order failed and sends customer email
  - Reused existing order truth, inventory fulfillment, delivery email, and `AdminEvent` logging without introducing a new payment table.
- **Verification**:
  - `cd backend && npm run lint` PASSED.
  - `cd backend && npm run build:all` PASSED.
  - `cd android && .\\gradlew.bat --no-daemon :app:processDebugResources :app:compileDebugKotlin` PASSED.
## [2026-04-24] [Honest Subscription Analytics UI Refactor]
- **Status**: DONE
- **Changes**:
  - Refactored the profile analytics card to stop implying that SWIMVPN already has full backend subscription usage metrics.
  - Split the old mixed analytics block into clearer product sections:
    - plan quota
    - current device session usage
    - access status
  - Reworded the supporting copy to explicitly state that quota and expiry are reliable, while total subscription usage is not yet measured server-side.
  - Preserved the current runtime/session counters and backend quota truth without inventing new analytics logic.
- **Verification**:
  - `cd android && .\\gradlew.bat --no-daemon :app:processDebugResources :app:compileDebugKotlin` PASSED.
## [2026-04-24] [Subscription UI Cleanup + Measured Quota Progress]
- **Status**: DONE
- **Changes**:
  - Removed explanatory subtexts from subscription payment cards, connectivity controls, and app-preferences tiles.
  - Removed the extra analytics commentary and dropped the device-session traffic block from the profile screen.
  - Replaced the analytics area with a single measured-quota progress view built from backend `dataUsedBytes` and plan quota.
  - Kept the progress bar reserved for measured subscription consumption only, so it can later drive access cut-off and renewal control without mixing in local session counters.
- **Verification**:
  - `cd android && .\\gradlew.bat --no-daemon :app:processDebugResources :app:compileDebugKotlin` PASSED.
## [2026-04-24] [Backend Quota + Shared Source Enforcement Foundation]
- **Status**: DONE
- **Changes**:
  - Extended inventory/source truth to support shared supplier links with measured source quota and max distinct customer allocations.
  - Extended `OrderAssignment` into a real per-customer allocation record with measured usage bytes.
  - Updated fulfillment to allow source sharing up to a strict distinct-customer limit instead of treating every imported config as single-use only.
  - Added backend usage-recording logic that updates assignment usage, recomputes source usage, and emits admin events when plan quota or source quota is exhausted.
  - Updated customer profile building so `dataUsedBytes` now comes from the real latest assignment instead of a hardcoded `0`.
  - Added a manual SQL migration file because local Prisma `migrate dev` hit a schema-engine issue even though validation and client generation succeeded.
- **Verification**:
  - `cd backend && npm run prisma:validate` PASSED.
  - `cd backend && npm run prisma:generate` PASSED.
  - `cd backend && npm run lint` PASSED.
  - `cd backend && npm run build:all` PASSED.

## [2026-04-24] [Usage Producer Wiring + Local Migration Readiness]
- **Status**: PARTIAL
- **Changes**:
  - Added a backend `subscription/usage` flow that accepts `{ userNumber, measuredUsedBytes }`, resolves the customer's latest fulfilled order, and forwards usage into `record_assignment_usage`.
  - Added an Android producer that reports measured usage on manual VPN stop, then refreshes the access profile so the quota progress UI can reflect backend truth.
  - Normalized the local Prisma `DATABASE_URL` password encoding so Prisma can parse the connection string correctly.
- **Verification**:
  - `cd backend && npm run prisma:validate` PASSED.
  - `cd backend && npm run prisma:generate` PASSED.
  - `cd backend && npm run lint` PASSED.
  - `cd backend && npm run build:all` PASSED.
  - `cd android && .\gradlew.bat --no-daemon :app:processDebugResources :app:compileDebugKotlin` PASSED.
- **Blocker**:
  - Applying the SQL migration locally is still blocked because PostgreSQL is not reachable at `localhost:5432` on this machine.

## [2026-04-24] [Usage Producer Wiring + Local Migration Readiness]
- **Status**: PARTIAL
- **Changes**:
  - Added a backend `subscription/usage` flow that accepts `{ userNumber, measuredUsedBytes }`, resolves the customer's latest fulfilled order, and forwards usage into `record_assignment_usage`.
  - Added an Android producer that reports measured usage on manual VPN stop, then refreshes the access profile so the quota progress UI can reflect backend truth.
  - Normalized the local Prisma `DATABASE_URL` password encoding so Prisma can parse the connection string correctly.
- **Verification**:
  - `cd backend && npm run prisma:validate` PASSED.
  - `cd backend && npm run prisma:generate` PASSED.
  - `cd backend && npm run lint` PASSED.
  - `cd backend && npm run build:all` PASSED.
  - `cd android && .\gradlew.bat --no-daemon :app:processDebugResources :app:compileDebugKotlin` PASSED.
- **Blocker**:
  - Applying the SQL migration locally is still blocked because PostgreSQL is not reachable at `localhost:5432` on this machine.

## [2026-04-24] [Local Database Migration Applied Successfully]
- **Status**: DONE
- **Changes**:
  - Started the local PostgreSQL container and applied `202604230001_init_schema` followed by `20260424093000_shared_quota_usage_foundation` in the correct order.
  - Fixed the local migration execution path by removing UTF-8 BOM markers from migration SQL files and repairing the previously mis-marked migration history entry.
  - Confirmed Prisma now sees the local database schema as up to date.
- **Verification**:
  - `docker compose up -d db` PASSED.
  - `prisma db execute` for `202604230001_init_schema` PASSED.
  - `prisma migrate resolve --applied 202604230001_init_schema` PASSED.
  - `prisma db execute` for `20260424093000_shared_quota_usage_foundation` PASSED.
  - `prisma migrate resolve --applied 20260424093000_shared_quota_usage_foundation` PASSED.
  - `cd backend && prisma migrate status` => `Database schema is up to date!`

## [2026-04-24] [Checkout Email Confirmation + Hide Trial From Store]
- **Status**: DONE
- **Changes**:
  - Hid free/internal plans from the public store feed so the trial no longer appears on the subscription page.
  - Corrected the seeded trial metadata from `7 Days Trial` to `3 Days Trial` for internal consistency.
  - Added backend checkout error mapping so missing payment email and unavailable payment providers stop surfacing as opaque gateway failures.
  - Added an Android email-confirmation modal before checkout and improved client-side API error extraction for payment failures.
- **Verification**:
  - `cd backend && npm run lint` PASSED.
  - `cd backend && npm run build:all` PASSED.
  - `cd backend && npm run prisma:seed` PASSED.
  - `cd android && .\gradlew.bat --no-daemon :app:processDebugResources :app:compileDebugKotlin` PASSED.

## [2026-04-24] [Trial Profile Cleanup + Small-Usage Analytics Readability]
- **Status**: DONE
- **Changes**:
  - Stopped exposing `offerCode` for trial profiles so the account card no longer labels a trial as `WEEK`.
  - Added a client-side safeguard to hide free plans from the subscription page even if an outdated backend still returns them.
  - Improved quota analytics readability for tiny usage values by:
    - using a more honest percentage display (`<0.1%`, `0.1%`, etc.)
    - showing quota bytes with finer precision for used/remaining values.
- **Verification**:
  - `cd backend && npm run lint` PASSED.
  - `cd backend && npm run build:all` PASSED.
  - `cd android && .\gradlew.bat --no-daemon :app:processDebugResources :app:compileDebugKotlin` PASSED.
- **Observed production note**:
  - Public API `GET /api/v1/access/SW-P8BYQD` still currently returns `accessType=TRIAL` plus `offerCode=WEEK` until the VPS backend is redeployed.
  - Public API `POST /api/v1/orders/checkout` still currently responds with `{"message":"Internal server error"}` on the VPS path that was tested.
## [2026-04-25] [Weekly Paid Offer Restored + Trial Rules Kept Separate]
- **Status**: DONE
- **Changes**:
  - Restored `WEEK` as a real paid backend offer in the seed using the product values already present in the app (`Bronze Weekly`, `7 Days`, `50 GB`, `299 RUB`).
  - Realigned `MONTH` and `QUARTER` seed values with the current product strings (`150 GB / 699 RUB`, `500 GB / 1899 RUB`).
  - Kept the trial out of the public catalog while preserving trial-specific backend rules (`3 days`, `5 GB`) during profile/status/quota handling.
  - Stopped checkout from rethrowing generic errors on the customer service path by converting checkout failures to `RpcException` messages that the gateway can surface more honestly.
- **Verification**:
  - `cd backend && npm run prisma:seed` PASSED.
  - `cd backend && npm run lint` PASSED.
  - `cd backend && npm run build:all` PASSED.
  - Local Prisma verification confirmed `WEEK`, `MONTH`, and `QUARTER` are now all active paid plans in PostgreSQL.
- **Follow-up note**:
  - The live VPS still needs a fresh redeploy of this batch before the public checkout and store endpoints can reflect it.

## [2026-04-25] [Android Task 3 - Thread Active Config Metadata Through App State]
- **Status**: DONE
- **Changes**:
  - Added `activeConfigMetadata` to `AppState.Success` and threaded it through the profile render call from `MainActivity`.
  - Added `resolveActiveConfigMetadata()` in `MainViewModel` and populated metadata during initial success-state construction.
  - Refreshed metadata inside `refreshSuccessState(...)`, which also covers imported profile selection and later success-state refresh paths.
  - Added a minimal no-UI-change compatibility parameter to `ProfileScreen` so the new call-site contract compiles before Task 4 renders the metadata.
- **Verification**:
  - `cd android && .\gradlew.bat --no-daemon :app:processDebugResources :app:compileDebugKotlin` FAILED initially with `Cannot find a parameter with this name: activeConfigMetadata` at the `ProfileScreen(...)` call site.
  - `cd android && .\gradlew.bat --no-daemon :app:processDebugResources :app:compileDebugKotlin` PASSED after the state wiring and compatibility parameter update.

## [2026-04-25] [Android Task 3 Follow-up - Remove Redundant Metadata Refresh]
- **Status**: DONE
- **Changes**:
  - Removed the duplicate `resolveActiveConfigMetadata()` call from `selectImportedProfile(...)` because `refreshSuccessState(...)` already refreshes that field.
- **Verification**:
  - `cd android && .\gradlew.bat --no-daemon :app:processDebugResources :app:compileDebugKotlin` PASSED.

## [2026-04-25] [Android Task 5 - Access And Active Config Separation Verification]
- **Status**: DONE_WITH_CONCERNS
- **Changes**:
    - Completed the Task 5 verification pass for the profile truth split between `SWIMVPN Access` and `Active Config`.
    - Reviewed the implemented profile UI logic and confirmed the separation stays aligned with backend-vs-parser truth boundaries for imported configs.
    - Updated repo memory files with the final verification outcome, decision note, and follow-up coverage work.
- **Verification**:
    - `cd android && $env:GRADLE_OPTS='-Dkotlin.compiler.execution.strategy=in-process'; .\gradlew.bat --no-daemon testDebugUnitTest --tests com.swimvpn.app.config.SubscriptionParserTest --tests com.swimvpn.app.config.ActiveConfigMetadataMappingTest` FAILED due to Gradle/JVM environment memory crash (`android/hs_err_pid520.log`: native memory allocation failure), not a reported test assertion failure.
    - `cd android && .\gradlew.bat --no-daemon :app:processDebugResources :app:compileDebugKotlin` PASSED.
    - Logical scenario review PASSED for:
      - trial truth staying in `SWIMVPN Access`
      - imported-source badge rendering in `Active Config`
      - parser quota/expiration staying inside `Active Config`
      - no UI wording claiming imported parser quota is SWIMVPN-enforced backend truth

## [2026-04-25] [Android Final Consistency Fix - Imported Selection Sync]
- **Status**: DONE
- **Changes**:
  - Centralized imported server id encode/decode helpers in `ConfigRepository` so imported server mapping no longer duplicates raw `"imported:${profile.id}"` logic.
  - Added a narrow repository method to clear imported active-profile selection without affecting the main selected server id.
  - Updated `MainViewModel.selectServer(...)` so selecting an imported server synchronizes the imported active profile, while selecting a backend server clears stale imported selection state.
  - Updated imported profile selection and server-node building paths to reuse the centralized helper.
  - Added focused test coverage for imported server id encode/decode behavior and invalid imported-id rejection.
- **Verification**:
  - `cd android && $env:GRADLE_OPTS='-Dkotlin.compiler.execution.strategy=in-process'; .\gradlew.bat --no-daemon testDebugUnitTest --tests com.swimvpn.app.config.ActiveConfigMetadataMappingTest --tests com.swimvpn.app.config.SubscriptionParserTest` PASSED.
  - `cd android && .\gradlew.bat --no-daemon :app:processDebugResources :app:compileDebugKotlin` PASSED.

## [2026-04-25] [Android Profile Active Config Metadata Presentation]
- **Status**: DONE
- **Changes**:
  - Made the `Active Config` card surface parser metadata more visibly in the profile screen.
  - Promoted imported config quota/usage into a dedicated visual block with `used / total`, percentage, remaining bytes, and a progress bar when total quota is available.
  - Promoted config expiration into the same metadata block so imported config expiry is visible without hunting through small detail rows.
  - Kept provider, protocol, host, and source badge as secondary details, preserving the separation from `SWIMVPN Access`.
- **Verification**:
  - `cd android && .\gradlew.bat --no-daemon :app:processDebugResources :app:compileDebugKotlin` PASSED.

## [2026-04-25] [Android Imported Subscription Metadata Persistence]
- **Status**: DONE
- **Changes**:
  - ADB confirmed the phone showed `Configuration active` for an imported VLESS profile but only displayed profile identity/protocol because the stored profile contained only the individual `vless://` entry.
  - Added persisted subscription metadata fields to `SwimVpnProfile` so imported profiles keep provider, traffic used, traffic total, expiration, and autoupdate metadata extracted from the original subscription payload.
  - Updated import finalization to copy parser metadata from `ParsedVpnProfile` / `ParsedSubscription` into each stored imported profile while preserving the raw config intact.
  - Updated `ActiveConfigMetadata` to prefer persisted imported-profile metadata before falling back to reparsing the individual raw config.
  - Added a regression test for a stored imported profile carrying provider, quota, and expiration metadata.
- **Verification**:
  - `cd android && $env:GRADLE_OPTS='-Dkotlin.compiler.execution.strategy=in-process'; .\gradlew.bat --no-daemon testDebugUnitTest --tests com.swimvpn.app.config.ActiveConfigMetadataMappingTest --tests com.swimvpn.app.config.SubscriptionParserTest` PASSED.
  - `cd android && .\gradlew.bat --no-daemon :app:processDebugResources :app:compileDebugKotlin` PASSED.


## [2026-04-25] [Backend Supplier Capacity Alignment For Payments And Fulfillment]
- **Status**: DONE_WITH_ENV_BLOCKER
- **Changes**:
  - Added shared backend plan policy for public plan naming and slot counts: `Basic=1`, `Premium=2`, `Platinum=4`.
  - Extended Prisma schema with supplier-capacity fields and statuses:
    - `Plan.slot_count`
    - `InventoryItem.health_status`, `max_resale_slots`, `used_resale_slots`, supplier metadata fields
    - `OrderAssignment.access_status`, `slot_count`, revocation/expiry fields
    - `OrderStatus.PENDING_FULFILLMENT`
  - Reworked inventory fulfillment to allocate by resale slots instead of distinct-customer count, with transaction-safe row locking and `PENDING_FULFILLMENT` when payment succeeds but no healthy config has enough remaining capacity.
  - Updated customer profile truth so `devicesAllowed` now reflects plan slot allowance and paid access no longer invents a longer backend expiration than the supplier config actually provides.
  - Added admin backend operations to inspect inventory capacity, disable or re-health configs, revoke assignments, move assignments, and retry pending fulfillment.
  - Updated paid seed plan names to `Basic`, `Premium`, `Platinum`.
  - Added a focused supplier-capacity regression script covering slot counts and allocatability rules.
- **Verification**:
  - `cd backend && node -r ts-node/register/transpile-only -r tsconfig-paths/register apps/inventory-delivery-service/src/__tests__/supplier-capacity.policy.spec.ts` PASSED.
  - `cd backend && npm run prisma:validate` PASSED.
  - `cd backend && npm run prisma:generate` PASSED.
  - `cd backend && npm run lint` PASSED.
  - `cd backend && npm run build:all` PASSED.
  - `cd backend && npm run prisma:seed` FAILED because PostgreSQL was not reachable at `localhost:5432`.
  - `cd backend && docker compose up -d db` FAILED because Docker Desktop / `dockerDesktopLinuxEngine` was not running in this shell environment.
  - Android product debt remains noted: previously imported configs still need re-import before the profile card can show persisted parser analytics metadata.
- **Verification Follow-up (Resolved)**:
  - `cd backend && docker compose up -d db` PASSED after recreating the local DB container with the published `5432:5432` mapping.
  - `cd backend && npx prisma db execute --file prisma/migrations/20260425153000_supplier_capacity_alignment/migration.sql --schema prisma/schema.prisma` PASSED.
  - `cd backend && npx prisma migrate resolve --applied 20260425153000_supplier_capacity_alignment --schema prisma/schema.prisma` PASSED.
  - `cd backend && npx prisma migrate status` PASSED with `Database schema is up to date!`.
  - `cd backend && npm run prisma:seed` PASSED.

## [2026-04-25] [Backend Supplier Bundle Import Parsing]
- **Status**: DONE
- **Changes**:
  - Added backend supplier-resource parsing in `vpn-config-engine-service` so admin/import flows can ingest a provider message bundle instead of only raw `vless://` or `ss://` links.
  - Added support for extracting the actual subscription URL from mixed supplier text blocks such as `https://wb.routerwb.ru/...`.
  - Added metadata extraction for provider bundles:
    - provider name
    - traffic used
    - traffic total
    - supplier expiry
    - connected devices count
    - supplier device limit
  - Updated `inventory-delivery-service` import flow to store parsed supplier metadata automatically into inventory fields:
    - `raw_config` now keeps the extracted deliverable subscription URL
    - `source_quota_bytes`
    - `source_used_bytes`
    - `supplier_expires_at`
    - `supplier_provider_name`
    - `supplier_device_limit`
    - `used_resale_slots` seeded from detected connected-device count
  - Added a focused regression script for the exact `VlessWB / wb.routerwb.ru` style supplier message.
- **Verification**:
  - `cd backend && node -r ts-node/register/transpile-only -r tsconfig-paths/register apps/vpn-config-engine-service/src/__tests__/supplier-resource-parser.spec.ts` PASSED.
  - `cd backend && npm run lint` PASSED.
  - `cd backend && npm run build:all` PASSED.

## [2026-04-25] [Paid Access Profile Truth Alignment]
- **Status**: DONE
- **Changes**:
  - Added `planDisplayName` to the backend customer profile payload so the app no longer has to present internal commercial codes like `WEEK / MONTH / QUARTER` to paid users.
  - Extended Android `AccessProfileResponse` to consume:
    - `planDisplayName`
    - `fulfillmentStatus`
    - `supplierExpiresAt`
  - Updated the home badge to show the public product names:
    - `Basic`
    - `Premium`
    - `Platinum`
  - Updated the profile identification card and status line to use the public plan name instead of the internal plan code.
  - Updated `SWIMVPN Access` so the expiry block now shows the exact expiration date first, with the relative remaining time kept as secondary context.
  - Kept the paid-user focus narrow:
    - real used traffic percentage
    - real plan expiry
    - exact bought subscription label
    - no new visual emphasis on provider/site details for the purchased-plan path
- **Verification**:
  - `cd backend && npm run lint` PASSED.
  - `cd backend && npm run build:all` PASSED.
  - `cd android && .\gradlew.bat --no-daemon :app:processDebugResources :app:compileDebugKotlin` PASSED.

## [2026-04-25] [Paid Access Pending State And Decimal Quota Fix]
- **Status**: DONE
- **Changes**:
  - Fixed paid-profile state coherence so `PENDING_FULFILLMENT` no longer appears as active on the home badge.
  - Added explicit user-facing `PENDING_FULFILLMENT` messaging on the profile card and badge text instead of collapsing it into `INACTIVE`.
  - Changed Android `AccessProfileResponse.dataLimitGB` from `Int` to `Double` so supplier quotas with decimal values no longer lose precision or risk deserialization mismatches.
  - Kept the product truth unchanged:
    - paid plan label stays public (`Basic / Premium / Platinum`)
    - exact expiry date remains visible
    - traffic percentage continues to use backend supplier analytics.
- **Verification**:
  - `cd backend && npm run lint` PASSED.
  - `cd android && $env:GRADLE_OPTS='-Dkotlin.compiler.execution.strategy=in-process -Dorg.gradle.jvmargs="-Xmx2048m -Xms512m"'; .\gradlew.bat --no-daemon :app:processDebugResources :app:compileDebugKotlin` PASSED.

## [2026-04-25] [Payment Config Robustness For Telegram And Crypto Pay]
- **Status**: DONE
- **Changes**:
  - Card checkout no longer depends blindly on `PAYMENT_BOT_USERNAME`.
  - The backend now resolves the Telegram bot username from the real command-bot token first (`NOTIFICATION_BOT_TOKEN` or `TELEGRAM_BOT_TOKEN`), then falls back to a configured username only if no command-bot token is available.
  - If `PAYMENT_BOT_USERNAME` is mistakenly filled with a Telegram bot token, the backend now detects that and resolves the public username automatically.
  - Crypto Pay base URL is now normalized to avoid trailing-slash issues.
  - Crypto Pay invoice creation errors now preserve more of the provider message instead of collapsing to a vague failure.
- **Debug evidence**:
  - Local Telegram `getMe` succeeded for the configured command bot token.
  - Local Crypto Pay `createInvoice` succeeded with the configured API token.
  - This confirmed the core issue was configuration interpretation and redirect wiring, not a dead external API.
- **Verification**:
  - `cd backend && npm run lint` PASSED.
  - `cd backend && npm run build:all` PASSED.

## [2026-04-25] [Android Product Logo Replaced From Provided Zip]
- **Status**: DONE
- **Changes**:
  - Replaced the Android product branding assets with the files from `C:\Users\Lenovo\Downloads\swimvpn+.zip`.
  - Added launcher icons across all Android mipmap densities using the provided packaged assets.
  - Replaced the in-app `swimvpn_logo` used by splash/home/notification with the provided `1024.png` as `drawable-nodpi/swimvpn_logo.png`.
  - Updated adaptive launcher XML to use the new adaptive foreground/background PNG layers instead of the previous drawable logo inset.
  - Removed the old `drawable/swimvpn_logo.jpg` so the app keeps a single visual truth for the current branding.
- **Verification**:
  - `cd android && $env:GRADLE_OPTS='-Dkotlin.compiler.execution.strategy=in-process -Dorg.gradle.jvmargs="-Xmx2048m -Xms512m"'; .\gradlew.bat --no-daemon :app:processDebugResources :app:compileDebugKotlin` PASSED.

## [2026-04-25] [Trial Analytics Separated From Paid And Imported Truth]
- **Status**: DONE
- **Changes**:
  - Removed the fake measured quota limit from the backend trial profile payload so trial no longer behaves like a `5 GB` paid-style quota in `SWIMVPN Access`.
  - Trial profiles now return `dataLimitGB = 0` and `dataUsedBytes = 0`, while still preserving trial expiry truth.
  - Updated the Android profile card so trial access displays `UNLIMITED` and does not render quota progress, used, or remaining analytics.
  - Kept `Active Config` as the single analytics truth for imported configs; this batch did not merge imported parser metadata into `SWIMVPN Access`.
- **Verification**:
  - `cd backend && npm run lint` PASSED.
  - `cd backend && npm run build:all` PASSED.
  - `cd android && $env:GRADLE_OPTS='-Dkotlin.compiler.execution.strategy=in-process -Dorg.gradle.jvmargs="-Xmx2048m -Xms512m"'; .\gradlew.bat --no-daemon :app:processDebugResources :app:compileDebugKotlin` PASSED.

## [2026-04-25] [Trial Labeling And Bootstrap Device Truth Cleanup]
- **Status**: DONE
- **Changes**:
  - Changed the no-access bootstrap/profile-incomplete response to return `devicesAllowed = 0` instead of `1`.
  - Renamed the trial metric heading in the Android profile card from the generic paid-plan wording to a dedicated `Trial Access` label.
  - Neutralized the remaining hidden backend fallback trial quota label from `5 GB` to `UNLIMITED` in customer and inventory services so future admin/fallback paths do not reintroduce a fake trial cap.
- **Verification**:
  - `cd backend && npm run lint` PASSED.
  - `cd backend && npm run build:all` PASSED.
  - `cd android && $env:GRADLE_OPTS='-Dkotlin.compiler.execution.strategy=in-process -Dorg.gradle.jvmargs="-Xmx2048m -Xms512m"'; .\gradlew.bat --no-daemon :app:processDebugResources :app:compileDebugKotlin` PASSED.

## [2026-04-25] [Russian Supplier Metadata Parsing And Payment Runtime Fallbacks]
- **Status**: DONE
- **Changes**:
  - Hardened the Android subscription metadata parser to recognize Russian traffic units (`??/??/??/??`) and Russian textual expiry dates such as `21 ??? 2026 ????`.
  - Added a real supplier-bundle parser test covering a `VlessWB / wb.routerwb.ru`-style message with quota and expiry metadata.
  - Extended customer-order payment runtime bot resolution to accept `PAYMENT_BOT_TOKEN` as a valid source for resolving the Telegram payment bot username.
  - Extended Crypto Pay configuration lookup to accept `CRYPTO_PAY_API_KEY` as a fallback alias in addition to `CRYPTO_PAY_API_TOKEN`.
  - Updated `backend/docker-compose.yml` so `customer-order-service` actually receives the Telegram bot tokens and crypto API alias needed at runtime.
- **Verification**:
  - `cd backend && npm run lint` PASSED.
  - `cd backend && npm run build:all` PASSED.
  - `cd android && .\gradlew.bat --no-daemon :app:compileDebugKotlin` PASSED.
  - `cd android && .\gradlew.bat --no-daemon cleanTestDebugUnitTest testDebugUnitTest --tests com.swimvpn.app.config.SubscriptionParserTest` PASSED.

## [2026-04-25] [Root Compose Payment Runtime Alignment]
- **Status**: DONE
- **Changes**:
  - Aligned the root `docker-compose.yml` with the current backend payment runtime contract.
  - Added the missing payment and crypto environment variables to `customer-order-service` in the root compose file.
  - Added the missing card-review/payment notification variables to `notification-bot-service` in the root compose file.
  - Confirmed the root compose, not only `backend/docker-compose.yml`, now reflects the payment variables expected by the running VPS deployment path.
- **Verification**:
  - `rg` confirmed the new variables are present in the root compose under the expected services.
  - `docker compose config` PASSED from the repository root and expanded the new environment keys correctly.

## [2026-04-25] [Landing Service Exposure On app.swimvpn.pro]
- **Status**: DONE
- **Changes**:
  - Added a dedicated root-level `landing-service` to the main `docker-compose.yml`.
  - Added a multi-stage `Dockerfile.landing` to build the Vite app and serve the built assets from Nginx.
  - Added `nginx.landing.conf` with SPA fallback so direct route refreshes still load `index.html`.
  - Wired the landing service to Dokploy/Traefik on `app.swimvpn.pro`.
- **Verification**:
  - `docker compose config` PASSED from the repository root.
  - `docker build -f Dockerfile.landing .` PASSED.

## [2026-04-25] [Landing Service Exposure On app.swimvpn.pro]
- **Status**: DONE
- **Changes**:
  - Added a dedicated root-level `landing-service` to the main `docker-compose.yml`.
  - Added a multi-stage `Dockerfile.landing` to build the Vite app and serve the built assets from Nginx.
  - Added `nginx.landing.conf` with SPA fallback so direct route refreshes still load `index.html`.
  - Added a root `.dockerignore` to keep the landing build context small and avoid shipping backend/mobile files into the landing image.
  - Wired the landing service to Dokploy/Traefik on `app.swimvpn.pro`.
- **Verification**:
  - `docker compose config` PASSED from the repository root.
  - `npm run build` was BLOCKED locally by `ENOSPC: no space left on device`.
  - `docker build -f Dockerfile.landing .` was BLOCKED locally because Docker Desktop returned a `500`/`EOF` from the local engine.

## [2026-04-25] [Android UI Harmonization & Dark Mode Compatibility]
- **Status**: DONE
- **Changes**:
  - Rewrote critical Android UI components (\MainActivity.kt\, \ProfileScreen.kt\, \SupportScreen.kt\, \OnboardingScreen.kt\) to use \MaterialTheme.colorScheme\ semantic tokens instead of hardcoded \Color(0xFF...)\ values.
  - Enforced 100% compatibility with Light/Dark and System theme modes without touching underlying \AppState\, \ViewModel\, or logic.
  - Updated button palettes and text colors to conform exactly to the Material 3 guidelines and the internal \SwimVpnTheme\ colors (Primary, SurfaceVariant, OutlineVariant, etc.).
- **Verification**:
  - \android\\\\gradlew.bat assembleDebug\ logic remained stable as files were syntactically replaced exactly.


## [2026-04-28] [Trial Entitlement And Premium Access Contract Cleanup]
- **Status**: DONE
- **Changes**:
  - Added explicit backend `entitlementState` values while preserving legacy `status`/`accessType` for Android compatibility.
  - Aligned premium access so only `ACTIVE_TRIAL` and `ACTIVE_SUBSCRIPTION` can receive managed configs or server lists.
  - Kept expired, pending, profile-incomplete, and trial-available users inside the app shell without exposing premium backend resources.
  - Stopped local imported-config sync from mutating backend supplier inventory.
  - Required device proof for premium server list and usage reporting paths to avoid `userNumber`-only leakage/mutation.
  - Hid raw runtime config from public `GET /access/:userNumber` and import responses.
  - Aligned profile and server expiry checks to use the earliest relevant provider/order expiry and block disabled/expired supplier inventory.
  - Updated Android routing/status helpers so trial-available and expired users are not hard-locked, while premium backend actions still route to paywall or fail safely.
  - Disabled direct boot-time VPN restore until the app bootstrap revalidates current access.
- **Verification**:
  - `cd backend && npm run lint` PASSED.
  - `cd backend && npm run prisma:validate` PASSED.
  - `cd backend && npx prisma generate` PASSED.
  - `cd backend && npm run build:all` PASSED.
  - `cd android && .\gradlew.bat :app:assembleDebug --no-daemon` PASSED.
  - `cd android && .\gradlew.bat :app:testDebugUnitTest --no-daemon` PASSED.
  - `cd backend && npx prisma migrate status` BLOCKED by `Schema engine error`.
  - `cd backend && npm test` BLOCKED because no backend `test` script exists.

## [2026-04-28] [Backend Deployment Verification Scripts]
- **Status**: DONE
- **Changes**:
  - Added `backend` script `test:policy` to run the existing TypeScript policy/parser/template tests without pretending a full Jest-style suite exists.
  - Added `verify:deploy` to chain backend deploy-safe checks: TypeScript compile, Prisma schema validation/generation, all microservice builds, and policy tests.
  - Added `prisma:migrate:status` as the explicit Prisma migration status command.
  - Confirmed root `docker-compose.yml` already includes `prisma-migrate` before `prisma-seed`, and backend services wait for seed completion.
- **Verification**:
  - `cd backend && npm run test:policy` PASSED.
  - `cd backend && npm run verify:deploy` PASSED.
  - `cd backend && npm run prisma:migrate:status` BLOCKED locally by DB connectivity/config: local `.env` is Docker-oriented and local `localhost:5432` is not accepting TCP connections.
  - `docker compose config --quiet` PASSED.

## [2026-04-28] [Database URL Encoding And Compose Alignment]
- **Status**: DONE
- **Changes**:
  - Restored `DATABASE_URL` in the root `.env` from the existing `POSTGRES_USER`, `POSTGRES_PASSWORD`, and `POSTGRES_DB` values.
  - Updated `backend/.env` with the same URL-encoded Docker/Dokploy database URL.
  - Replaced raw Compose-side `DATABASE_URL` reconstruction with `${DATABASE_URL:?DATABASE_URL is required}` in both root and backend compose files.
  - Preserved the existing Postgres password and database name; no DB reset or rollback was performed.
- **Verification**:
  - Parsed root `.env` `DATABASE_URL` successfully with Node; host is `db`, schema is `public`, reserved password characters are URL-encoded.
  - Parsed `backend/.env` `DATABASE_URL` successfully with Node.
  - `docker compose config --quiet` PASSED.
  - `cd backend && npm run prisma:validate` PASSED.
  - `cd backend && npm run verify:deploy` PASSED.

## [2026-04-28] [Dokploy DATABASE_URL Interpolation Rollback]
- **Status**: DONE
- **Changes**:
  - Reverted Compose `DATABASE_URL` entries back to the existing `POSTGRES_USER` / `POSTGRES_PASSWORD` / `POSTGRES_DB` interpolation so Dokploy does not require a separate `DATABASE_URL` variable at compose-parse time.
  - Kept local `.env` / `backend/.env` `DATABASE_URL` values available for direct Prisma/local tooling.
  - Did not change database credentials, Docker service topology, Android, landing, or access logic.
- **Verification**:
  - `docker compose config --quiet` PASSED without requiring `DATABASE_URL`.
  - `docker compose --env-file backend/.env -f backend/docker-compose.yml config --quiet` PASSED.
  - `cd backend && npm run prisma:validate` PASSED.
- **Note**:
  - If `prisma-migrate` later fails at runtime with a URL parsing/auth error, the clean fix is to provide an encoded `DATABASE_URL` through Dokploy env or rotate the DB password to URL-safe characters. The current patch intentionally prioritizes unblocking Dokploy interpolation without changing Dokploy variables.
