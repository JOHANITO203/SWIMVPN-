# DECISIONS

## [2026-04-22] [Expose Only Runtime Modes That Actually Exist]
- **Decision**: In the first VPN core batch, expose only `FULL_TUNNEL` as an honest supported runtime mode in the Android app UI.
- **Why**: The repository already had a persisted `PROXY` preference in settings, but no real proxy runtime behind it. Leaving it visible would continue to mislead the user about what the app can actually execute.
- **Impact**:
  - `TechnicalSettingsScreen` now presents only the truthful full-tunnel runtime for this batch.
  - `LOCAL_PROXY` and `SPLIT_TUNNEL` stay in the typed runtime contract for future phases but are not marketed as active capabilities yet.

## [2026-04-22] [Use The Existing Config Pipeline As The Mandatory Runtime Entry]
- **Decision**: Route VPN startup through the existing config handling pipeline (`parse -> normalize -> validate -> prepare runtime payload`) before establishing the Android tunnel state.
- **Why**: Source-of-truth requires that raw VPN config handling be treated as a real ingest/normalize/runtime-preparation flow, not as vague “decryption” or direct string pass-through.
- **Impact**:
  - `SwimVpnService` now validates the raw config before marking runtime as ready.
  - The app is better positioned for future native `Xray-core / tun2socks` integration without rewriting the config path.

## [2026-04-22] [Technical Settings Must Be Honest About Android System Responsibilities]
- **Decision**: Treat `Kill Switch` as an Android-system-managed capability in the current app surface and present it as a settings shortcut rather than a fake in-app toggle.
- **Why**: The previous switch visually implied app-controlled kill-switch behavior that did not exist.
- **Impact**:
  - The technical screen no longer lies about kill-switch state.
  - Future always-on / lockdown integration can attach to a truthful UI contract instead of replacing a false one.

## [2026-04-20] [Shared Prisma Schema for Microservices]
- **Decision**: Use a single unified `schema.prisma` inside `libs/database`.
- **Why**: Ensures rapid MVP development and strict referential integrity.
- **Impact**: Microservices share the data layer but are decoupled at the logic layer.

## [2026-04-20] [Internal Communication via TCP]
- **Decision**: Use NestJS TCP transport for inter-service communication.
- **Why**: Lower overhead than Redis/RabbitMQ for MVP; deterministic port mapping (3000-3005).
- **Impact**: Synchronous-style internal calls, easy to debug locally.

## [2026-04-20] [Centralized Validation in Contracts Library]
- **Decision**: Put `class-validator` decorators directly on the shared DTOs in `@app/contracts`.
- **Why**: Ensures "Write Once, Validate Everywhere". Both the Gateway and the internal microservices will reject malformed data automatically.
- **Impact**: Strict type safety across the entire monorepo.

## [2026-04-20] [Environment-Based Service Discovery]
- **Decision**: Use `process.env.*_SERVICE_HOST` for TCP client hostnames with a fallback to `127.0.0.1`.
- **Why**: Allows the same code to run natively (localhost) and inside Docker containers (service names).
- **Impact**: Flexible deployment and easy local debugging.

## [2026-04-22] [Persistent UI State & Visual Feedback]
- **Decision**: Persist `selectedServerId` in `DataStore` and show active progress in the "Big Power Button".
- **Why**: Enhances UX by remembering user preference across app restarts and providing clear feedback during slow VPN handshakes.
- **Impact**: Reliable state management and professional feel.

## [2026-04-21] [MVP Deployment Stack Truth Alignment]
- **Decision**: Align deployment stack to PostgreSQL + 6 backend services only; remove WireGuard/OpenVPN runtime infrastructure from compose.
- **Why**: Source-of-truth defines SWIMVPN+ MVP as supplier-config resale/management, not VPN server provisioning.
- **Impact**: Docker stack now represents implemented MVP reality and is compose-valid.

## [2026-04-21] [Admin Session Minimum Compliance]
- **Decision**: Implement minimal dmin_sessions lifecycle now (create on login, validate token against active session, revoke on logout).
- **Why**: Source-of-truth requires admin sessions, but full refresh-token architecture is intentionally deferred.
- **Impact**: Admin auth now uses persisted session checks without broad auth rewrite.


## [2026-04-22] [Traefik-Only Public Exposure in Production]
- **Decision**: Publish only 80/443 via Traefik; keep backend and database services private on Docker networks.
- **Why**: Matches MVP security posture and VPS deployment constraints while preserving domain-based TLS routing.
- **Impact**: Internal services are reachable only by Docker service name; no direct public exposure of service ports.

## [2026-04-22] [Dedicated Prisma Migration Job]
- **Decision**: Run prisma migrate deploy through a dedicated one-shot compose service (prisma-migrate) before app startup.
- **Why**: Prevents race conditions and avoids migration logic duplication across runtime services.
- **Impact**: App services are gated by migration completion using depends_on: service_completed_successfully.


## [2026-04-22] [Add notification-bot-service as Controlled Utility Service]
- **Decision**: Implement post-purchase delivery as a new isolated microservice (
otification-bot-service) instead of extending dmin-control-service.
- **Why**: Keeps responsibilities narrow (Telegram + email delivery only), avoids coupling admin auth/control concerns with delivery operations, and remains easy to plug into current flow.
- **Impact**: Backend keeps core 6 services plus one utility service justified by explicit MVP delivery requirements.

## [2026-04-22] [Dedicated Notification Bot Token Optionality]
- **Decision**: Use NOTIFICATION_BOT_TOKEN (optional) for command polling to avoid collision with existing admin bot polling token; fallback sender can still use TELEGRAM_BOT_TOKEN for outbound notifications.
- **Why**: Running two pollers on the same Telegram token is unstable.
- **Impact**: Reliable command mode when dedicated token is provided; deterministic notification sending remains available without it.


## [2026-04-22] [Admin Support Bot Embedded in admin-control-service]
- **Decision**: Implement the admin support bot as a focused deterministic module/service inside dmin-control-service for MVP.
- **Why**: Smallest viable change with least operational overhead; reuses existing admin Telegram context while keeping strict non-LLM menu-based behavior.
- **Impact**: No additional standalone support microservice needed now; escalation handling is operational with static RU/EN templates and support-group relay.

## [2026-04-22] [Use Resend as Notification Mail Provider]
- **Decision**: Replace SMTP transport in 
otification-bot-service with Resend API for transactional delivery emails.
- **Why**: Lower operational overhead, simpler deterministic integration, and cleaner production setup with one API key.
- **Impact**: Email sending now depends on RESEND_API_KEY; sender identity is controlled by MAILER_FROM_EMAIL and MAILER_FROM_NAME.

## [2026-04-22] [Explicit Admin JWT Secret]
- **Decision**: Introduce ADMIN_JWT_SECRET as a distinct required secret for dmin-control-service JWT signing.
- **Why**: Removes hardcoded fallback and separates admin token scope from generic service JWT usage.
- **Impact**: Deployment env must include ADMIN_JWT_SECRET (while JWT_SECRET remains required for other services).

## [2026-04-22] [Dockploy Owns Reverse Proxy Layer]
- **Decision**: In Dockploy deployment mode, the app compose must not include its own Traefik instance.
- **Why**: VPS already has Dockploy-managed proxy on 80/443; embedded Traefik caused port allocation failure.
- **Impact**: Domain/TLS routing is configured at Dockploy layer; app compose runs backend services only.

## [2026-04-22] [Prisma Runtime Dependency Baseline on Alpine]
- **Decision**: Install OpenSSL userland libs in backend images (`builder` + `runtime`) for Prisma stability.
- **Why**: Dockploy deployment showed `prisma-migrate` exit 1 with schema engine parse errors on Alpine.
- **Impact**: Backend image gains minimal system deps; migrations should run reliably in production containers.

## [2026-04-22] [Build All Nest Services for Monorepo Runtime Images]
- **Decision**: Build each service explicitly in Docker build pipeline rather than relying on default `nest build` target.
- **Why**: Default build produced only gateway output, causing runtime `MODULE_NOT_FOUND` for other service entrypoints.
- **Impact**: Slightly longer build time; deterministic multi-service runtime correctness.

## [2026-04-22] [Support Escalation Requires Email Before Closing Ticket]
- **Decision**: Support bot now collects a valid customer email after escalation message and only then forwards the case.
- **Why**: Support handoff policy requires clear callback channel and explicit expectation of email response.
- **Impact**: User receives deterministic confirmation with ticket id + target email.

## [2026-04-22] [Optional Personal Support Report Destination]
- **Decision**: Add optional `ADMIN_SUPPORT_REPORT_CHAT_ID` for parallel admin report notifications.
- **Why**: Owner requested visibility in personal Telegram account while keeping primary support group destination.
- **Impact**: Primary escalation remains `ADMIN_SUPPORT_CHAT_ID`; personal relay activates only when report chat id is set.

## [2026-04-22] [Server Ops Scripts as Minimal Repo Standard]
- **Decision**: Introduce a minimal `scripts/ops` toolbox in-repo for deployment and incident response.
- **Why**: Reduce manual mistakes and speed up routine VPS operations under Dockploy/Compose.
- **Impact**: Team now has repeatable commands for deploy, health checks, DB backup/restore, and diagnostics.

## [2026-04-22] [Use Play Services ML Kit Barcode Variant]
- **Decision**: Switch Android barcode scanning dependency to `com.google.android.gms:play-services-mlkit-barcode-scanning`.
- **Why**: Reduce risk from bundled JNI payload alignment issues while keeping same ML Kit scanning API in app code.
- **Impact**: Requires Google Play Services availability on device; app code remains mostly unchanged.

## [2026-04-22] [Use Google Code Scanner Instead Of Local CameraX/ML Kit Processing]
- **Decision**: Replace the Android QR scanner implementation with Google Play Services Code Scanner.
- **Why**: The APK still shipped `libimage_processing_util_jni.so` after the previous mitigation, so the local image-processing scanner path remained incompatible with 16 KB page-size requirements.
- **Impact**: The scanner UX remains `open scanner -> scan QR -> return content`, but the camera UI is now provided by Play Services instead of a custom in-app preview. This removes the blocking native library from the APK.

## [2026-04-22] [Keep Retrofit Base URL Valid Even In Local Emulator Mode]
- **Decision**: Ensure Android `RetrofitClient` base URL always ends with a trailing slash.
- **Why**: `Retrofit.Builder().baseUrl(...)` throws immediately when the base URL is missing `/`, which can crash app startup before UI recovery paths run.
- **Impact**: No backend contract change. This only prevents a fatal Android-side initialization error.

## [2026-04-22] [Match MainActivity With An AppCompat Theme]
- **Decision**: Keep `MainActivity` on `AppCompatActivity` and align `Theme.SwimVpn` to an AppCompat parent theme.
- **Why**: `AppCompatActivity` with a framework `android:Theme.Material...` parent can crash on launch before Compose UI is shown.
- **Impact**: No UI redesign and no backend impact. This is a strict startup-stability fix.

## [2026-04-22] [Trial Contract Uses Bootstrap + Explicit Activation]
- **Decision**: Replace Android auto-trial startup with a backend-controlled bootstrap + explicit trial activation contract.
- **Why**: The product now requires onboarding-linked trial activation, prospect capture, anti-abuse checks, and a public user number that is safe to display without exposing raw device identifiers.
- **Impact**:
  - Backend creates or recovers a prospect profile by device id.
  - Backend generates a public user number in `SW-XXXXXX` format.
  - Trial activates only after onboarding completion and after the user provides email + phone.
  - Trial eligibility is enforced by backend checks on device id, email, and phone.
  - Android keeps its premium UI but now reflects real backend truth instead of auto-granting trial locally.

## [2026-04-22] [Home Screen Keeps Premium Grammar But Only Surfaces Honest Access and Server State]
- **Decision**: Realign the Android home screen by keeping its premium visual language while making status, selected server, and quick actions reflect backend/runtime truth.
- **Why**: The home screen is the daily usage hub. It must stay visually strong, but it cannot continue to hide server actions or present inferred states that the backend/runtime do not actually support.
- **Impact**:
  - Profile entry remains on the home header.
  - Selected server is now represented as a premium card instead of a low-information text line.
  - The floating `+` action returns on the home screen as the quick access point for config import / QR / code actions.
  - Status badge and subtitle now reflect access truth and VPN runtime state more honestly.

## [2026-04-22] [ConfigImportScreen Becomes The Single Import Hub]
- **Decision**: Use `ConfigImportScreen` as the single Android entry point for access/config import actions, and stop driving the main UX through the legacy import bottom sheet.
- **Why**: The repository already contains a richer config-first screen aligned with the product grammar (`ingest -> parse -> validate -> preview -> preserve raw config`). Keeping both the sheet and the screen as first-class flows created duplication and user confusion.
- **Impact**:
  - Home `+` and profile import actions now converge on the same screen.
  - Coupon/code activation is removed from the main Android UX for now because it is not an active product partnership flow.
  - Local config import stays primary, with backend profile sync attempted through the existing import endpoint when possible.
  - Import sync failures no longer collapse the whole app into a blocking error state if the local import already succeeded.

## [2026-04-22] [Android Repo Must Carry Its Own Gradle Wrapper]
- **Decision**: The Android module must version its Gradle wrapper files inside `android/` and not rely on Android Studio-only or machine-global Gradle availability.
- **Why**: The project was buildable in Studio but not reproducible from CLI because the wrapper scripts and JAR were missing from the repository.
- **Impact**:
  - `android/gradlew`, `android/gradlew.bat`, and `android/gradle/wrapper/gradle-wrapper.jar` are now part of the repo contract.
  - Future Android verification can start from the repository itself before depending on IDE behavior.

## [2026-04-22] [Prioritize Safe Developer Artifact Cleanup Over Windows System Cleanup]
- **Decision**: Free critical disk space first by removing development artifacts, caches, dumps, and obsolete project directories instead of touching Windows system files.
- **Why**: The immediate stability risk came from a nearly full `C:` drive, and there were multiple large non-essential development files that could be removed safely with far lower system risk.
- **Impact**:
  - Disk recovery actions should start with build outputs, Gradle caches, JVM crash dumps, replay logs, and abandoned projects.
  - System-level cleanup remains optional and secondary.

## [2026-04-22] [Shell Startup Must Respect Invocation Directory]
- **Decision**: Remove repository-agnostic `Set-Location` behavior from the user PowerShell profile so shells open in the directory where they are invoked.
- **Why**: Forcing every PowerShell session to jump to `D:\Dev` was creating repeated context errors and pulling work into the wrong repository even when commands were launched elsewhere.
- **Impact**:
  - PowerShell now respects the caller's working directory by default.
  - Future repo work is less likely to drift into the wrong project because of a global shell override.
  - No matching `cmd` autorun override was found, so no `cmd` startup correction was needed in this batch.

## [2026-04-22] [Ignore Android Local Gradle User Home]
- **Decision**: Treat `android/.gradle-user-home/` as a strictly local cache and ignore it in Git.
- **Why**: GUI/CLI commit flows that stage all files were failing on Windows path-length limits when they tried to add Gradle cache files from that directory.
- **Impact**:
  - Android local build caches no longer block repository commits.
  - The repo keeps only reproducible Android wrapper/build configuration, not transient cache internals.

## [2026-04-22] [ProfileScreen Must Render Backend Truth, Not Legacy Mock Semantics]
- **Decision**: Align the Android profile screen around backend-driven identity and access truth: `userNumber`, `email`, `phone`, `accessType`, `offerCode`, `status`, `dataLimitGB`, `dataUsedBytes`, and effective expiry.
- **Why**: The premium profile UI was already strong visually, but it still mixed legacy `planType`/mock semantics with the newer backend contract. That created ambiguity around what the user actually has, what is expiring, and how much traffic remains.
- **Impact**:
  - `SW-XXXXXX` remains the primary visible account identity.
  - `email` and `phone` are displayed as secondary identity fields when present.
  - profile badges and analytics now derive from backend truth first, with Android session bytes only augmenting consumption display.
  - management actions remain limited to validated product flows and do not reintroduce coupon behavior.
## [2026-04-22] [SupportScreen Must Point To Official Support Channels]
- **Decision**: The Android support screen must expose the production support email and the official Telegram support bot instead of legacy placeholder/community links.
- **Why**: The support UI is a customer-facing trust surface. It has to match the deterministic support path already defined in the backend/admin-control layer and send users to the right contact immediately.
- **Impact**:
  - `SupportScreen` now uses `support@swimvpn.pro` as the mail target.
  - Telegram support now opens `@SWIMVPNSUPPORTADMINBOT` with native Telegram deep-link first and web fallback.
  - FAQ/help copy is aligned with current Android flows rather than legacy import wording.
## [2026-04-22] [SubscriptionScreen Must Render Backend Plans And Stop Faking Checkout]
- **Decision**: The Android subscription screen must render the real backend plan contract (`name`, `code`, `durationLabel`, `quotaLabel`, `priceRub`) and must not invent a client checkout URL when the backend does not provide one.
- **Why**: The old screen mixed a premium visual language with stale marketing tiers (`BRONZE / SILVER / GOLD`) and a fake Stripe URL, which broke product truth.
- **Impact**:
  - Subscription cards are now backend-driven while keeping the premium look.
  - Android order creation now reuses known profile contact data when available.
  - The app no longer pretends to have a finished PSP checkout flow; it creates the order and tells the user honestly that payment is not yet enabled in-app.
## [2026-04-22] [Package Official Xray At Build Time Instead Of Versioning Large Binaries]
- **Decision**: Android must download and package official `Xray-core` artifacts during build time instead of storing heavyweight runtime binaries directly in Git.
- **Why**: The native runtime now needs a real Xray executable, but checking large ABI binaries into the repository would bloat history and make routine frontend/backend work much heavier.
- **Impact**:
  - `android/app/build.gradle` prepares official Android Xray artifacts during `preBuild`.
  - The repository stays source-focused while native runtime packaging remains reproducible.
  - ABI support in this batch is intentionally limited to the artifacts officially available and useful for MVP:
    - `arm64-v8a`
    - `x86_64`
## [2026-04-22] [Use Native Xray Process Bridge Before The tun2socks Data Plane]
- **Decision**: Introduce a native Xray process bridge now, and use it to unlock a real `LOCAL_PROXY` runtime path before the `tun2socks`-based full tunnel data plane is finished.
- **Why**: Official Android Xray artifacts are available immediately, while `tun2socks` packaging still needs its own dedicated batch. Shipping the bridge now creates real native execution and observability without pretending the full tunnel is already complete.
- **Impact**:
  - Android now has a concrete native runtime layer:
    - runtime preparation
    - config file emission
    - Xray process start/stop
    - stdout/stderr capture
    - exit tracking
  - `LOCAL_PROXY` becomes the first real native execution mode in the app.
  - `FULL_TUNNEL` remains transitional until the `tun2socks` batch is complete.
## [2026-04-22] [tun2socks Must Be Optional Until A Real Android Binary Exists]
- **Decision**: Treat `tun2socks` packaging as optional and non-blocking in the Android build until a real Android executable is available for the target ABI set.
- **Why**: There is no ready-to-embed official Android `tun2socks` artifact in the current workflow, and this machine does not currently provide a guaranteed local build path (`go` absent, Docker daemon unavailable). Breaking `assembleDebug` on that dependency would stall the whole app.
- **Impact**:
  - Android build stays green even with no packaged `tun2socks` binary.
  - The repo now exposes a proper `tun2socks` runtime contract and availability detection.
  - `FULL_TUNNEL` remains explicitly transitional until the data plane binary is supplied and wired.
