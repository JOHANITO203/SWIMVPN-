# DECISIONS

## [2026-04-29] [Manual Card Proof Uses AdminEvent Recovery For MVP]
- **Decision**: Keep manual card payment proof state on the existing `AdminEvent` audit trail for this MVP batch instead of introducing a new Prisma `PaymentProof` table.
- **Why**: The production regression was caused by volatile Telegram memory, not by missing order/payment records. Recovering recent `CARD_PAYMENT_FLOW_OPENED` events gives the bot a durable fallback without a migration or broad payment refactor.
- **Impact**:
  - Telegram remains a transport/review layer, not payment truth.
  - PostgreSQL remains the recovery source through `AdminEvent`.
  - A dedicated payment-proof model can still be added later if admin reporting needs richer querying.

## [2026-04-29] [Trial Activation Must Be Device-Bound]
- **Decision**: Trial activation must include the Android `deviceId` and the backend must verify it against the persisted customer device before creating a trial order.
- **Why**: Bootstrap, premium server access, encrypted config resolution, and usage reporting already rely on the `userNumber + deviceId` boundary. Leaving activation bound only to `userNumber/email/phone` allowed stale or restored local state to behave differently between debug and release.
- **Impact**:
  - Android no longer sends the sentinel `unknown_device_id` fallback to backend access calls.
  - Backend rejects blank/sentinel device IDs and mismatched activation attempts.
  - `TRIAL_AVAILABLE` remains an app-shell state, with activation exposed from the profile card instead of forcing a dead-end activation-only route.
  - Release builds no longer log HTTP bodies for access calls.


## [2026-04-23] [Connectivity UI Direction - Proxy Recommended, Tunnel System Routing]
- **Decision**: Expose the runtime modes with short product labels: `Proxy` and `Tunnel`.
- **Why**: ADB measurements showed local proxy is faster than full tunnel on the tested imported VLESS node, while full tunnel remains useful for full-device routing.
- **Impact**:
  - `Proxy` is presented as the recommended mode.
  - `Tunnel` is presented as the full-device tunneling mode.
  - The visible latency remains the user-facing quality indicator; no extra quality score is added in the MVP UI.
  - Active routing indicators continue to reflect real runtime state, not selection alone.

## [2026-04-23] [Auto-Connect Must Persist The Last Runnable Payload]
- **Decision**: Persist the last runnable VPN payload locally and use it for boot/package-replace auto-connect restoration.
- **Why**: `autoConnect` should be more than an in-app toggle. The app needs a concrete restart path that does not depend on reconstructing the VPN launch payload from volatile UI state.
- **Impact**:
  - The app now stores the last successful launch payload (`host`, `port`, `protocol`, `runtimeConfig`, `runtimeMode`).
  - A boot receiver can restore the VPN directly after `BOOT_COMPLETED` or `MY_PACKAGE_REPLACED` when `autoConnect` is enabled.
  - Sign-out clears the stored payload so stale sessions are not restored for a different user context.

## [2026-04-23] [Kill Switch Remains Android-System-Managed, But Status Should Be Visible]
- **Decision**: Keep kill switch management in Android system settings, but surface a more honest status chip in-app using Android secure settings when readable.
- **Why**: The user needs a truthful indication of whether SWIMVPN+ is merely available, configured as Always-on, or in Lockdown mode, without pretending the app owns those controls directly.
- **Impact**:
  - The Technical screen now distinguishes `SYSTEM`, `ALWAYS-ON`, and `LOCKDOWN`.
  - The screen still opens Android VPN settings for the actual control surface.

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
## [2026-04-22] [Android tun2socks Must Be JNI-First With tun fd Contract]
- **Decision**: The Android `tun2socks` path must follow the upstream Android model: shared-library/JNI integration with a `tun fd` handoff, while keeping any executable packaging only as a secondary fallback and not as the primary Android truth.
- **Why**: The upstream `hev-socks5-tunnel` Android usage is library-oriented and expects `hev_socks5_tunnel_main(..., tun_fd)`. A pure `ProcessBuilder` story would keep us on the wrong architecture and delay the final full-tunnel wiring.
- **Impact**:
  - Android build packaging now supports a packaged shared library per ABI in addition to the optional executable fallback.
  - `SwimVpnService` now prepares a stable `tun fd` + config-file contract for the future JNI handoff.
  - The remaining blocker for closing Phase 2B is narrower and explicit:
    - package a repo-owned JNI shim or equivalent binding
    - link it to the packaged `hev-socks5-tunnel` library
    - then execute the full-tunnel data plane for real on device.
## [2026-04-22] [Auto-Build Upstream tun2socks Shared Libraries During Android Build]
- **Decision**: For Phase 2B, Android should auto-build upstream `hev-socks5-tunnel` shared libraries during `assembleDebug` when no local prebuilt `.so` is explicitly supplied.
- **Why**: We already have a working NDK/CMake toolchain on this workstation, and keeping Phase 2B blocked on manually curated local `.so` files would slow down integration and make the build less reproducible for our current development flow.
- **Impact**:
  - `prepareTun2SocksRuntimeAssets` now clones the upstream source, repairs Windows symlink placeholders, runs `ndk-build`, and packages the resulting `libhev-socks5-tunnel.so`.
  - The Android app now owns the JNI shim that binds the packaged shared library to the `tun fd` contract.
  - Phase 2B can now be treated as closed from an implementation/build perspective, with operational signoff deferred to device testing.
## [2026-04-23] [Prisma Must Use Versioned Migrations And App-Owned Customer IDs]
- **Decision**: The backend must move away from `prisma db push` as the normal rollout path and use versioned Prisma migrations plus app-owned `Customer.public_id` generation.
- **Why**: Public endpoints are failing in ways that strongly suggest schema drift between code and production. The previous setup had no versioned migrations and depended on a fragile DB-generated `nanoid(...)` default for `Customer.public_id`, which makes production alignment harder and less portable.
- **Impact**:
  - Production rollout should baseline existing databases once, then use `prisma migrate deploy`.
  - `Customer.public_id` is now generated by application logic in all known creation paths instead of depending on a database default function.
  - Prisma services now fail fast with a clear error if critical columns required by the current backend are missing.
  - The backend now has a versioned initial migration that can serve as the baseline for production and new environments.
## [2026-04-23] [Compose Stack Must Treat Seed As A First-Class Readiness Step]
- **Decision**: The root production stack must explicitly run both Prisma migration and Prisma seed as one-shot services before starting the application services.
- **Why**: Our backend endpoints rely not only on schema correctness but also on baseline catalog data such as active plans. Waiting only for migration success still leaves room for a half-ready backend that is structurally valid but functionally empty or broken.
- **Impact**:
  - `docker-compose.yml` now includes `prisma-seed` as a separate one-shot service.
  - App services wait for `prisma-seed` completion, not only `prisma-migrate`.
  - The production rollout path is now clearer:
    - backup
    - optional baseline
    - migrate
    - seed
    - service startup
## [2026-04-23] [Gateway Must Join Dockploy Network For Public Domains]
- **Decision**: `gateway-service` must join the external `dokploy-network` and expose explicit Traefik labels for `api.swimvpn.pro` and `admin.swimvpn.pro`.
- **Why**: The backend was healthy, DNS was correct, but the global Dockploy Traefik container lived on `dokploy-network` while the application gateway only lived on `swimvpn-private`. Without a shared network and explicit routers, public traffic could not reach the backend.
- **Impact**:
  - `gateway-service` now joins both:
    - `swimvpn-private`
    - `dokploy-network`
  - Public routing is expressed explicitly in compose through Traefik labels.
  - Internal services remain private and are not exposed to the shared reverse-proxy network.
## [2026-04-23] [Android Xray Runtime Must Be Extracted From APK At Session Start]
- **Decision**: The Android app must extract the packaged Xray binary from the installed APK into its runtime session directory before launching it.
- **Why**: On modern Android packaging/device combinations, native libraries packaged in the APK are not guaranteed to exist as plain files in `nativeLibraryDir`, so treating `libxray.so` as already extracted caused false runtime failures on real devices.
- **Impact**:
  - The app still packages Xray per ABI inside the APK.
  - `RuntimeFilePreparer` now falls back to reading `lib/<abi>/libxray.so` from the APK itself when `nativeLibraryDir` does not contain the file.
  - This keeps Phase 2B build/integration intact while unblocking real device execution for the Xray runtime path.
## [2026-04-23] [Android Xray Must Prefer Installer-Extracted Native Libraries]
- **Decision**: The Android app must prefer running Xray from installer-extracted native libraries (`nativeLibraryDir`) and package the APK so those libraries are actually extracted on device.
- **Why**: Real devices rejected execution of a copied Xray binary under the app data directory (`no_backup/runtime/.../bin/xray`), so a runtime copy from the APK is not a reliable primary execution path.
- **Impact**:
  - `useLegacyPackaging` is enabled for `jniLibs`.
  - the app manifest explicitly requests native library extraction.
  - `RuntimeFilePreparer` now uses `nativeLibraryDir` first as the true runtime path, with APK extraction kept only as a secondary fallback.
## [2026-04-23] [Android Parser Must Preserve Runtime-Critical gRPC Parameters]
- **Decision**: The Android config pipeline must preserve runtime-critical `gRPC` fields, especially `serviceName`, inside the canonical profile instead of treating `GRPC` as transport-only metadata.
- **Why**: The device/runtime path had already moved past packaging and execution blockers, but `gRPC` configs were still being parsed into incomplete profiles. That caused `TunnelRuntimeAdapter` to emit Xray runtime documents with `network=grpc` and missing `grpcSettings.serviceName`, which is not a viable runtime payload.
- **Impact**:
  - `ConfigParserEngine` now materializes `grpcSettings` from URL and JSON inputs.
  - `VMess` URL parsing also preserves more runtime-critical fields instead of dropping them silently.
  - Phase 2-C now advances on real parser/runtime truth rather than continuing to debug packaging symptoms that are already resolved.
## [2026-04-23] [Strengthen Existing Link Formats Before Adding New Protocols]
- **Decision**: In Phase 2-C, prioritize hardening the already-supported config ecosystems (`vless://`, `vmess://`, `trojan://`, `ss://`, JSON Xray/V2Ray) before introducing unsupported protocols like `hy2://`.
- **Why**: Real device testing showed that the current pain is not only “missing protocols”, but also brittle handling of real-world variants inside the protocols we already claim to support. It is more honest and more useful to make supported formats robust first.
- **Impact**:
  - The parser now tolerates more transport aliases, Base64 variants, IPv6 forms, and JSON outbound layouts within the supported scope.
  - The app can reject unsupported ecosystems more honestly later without also failing on common valid variants of its declared supported formats.
## [2026-04-23] [Use Tolerant Manual URL Parsing For Real-World VLESS/Trojan Links]
- **Decision**: Parse `vless://` and `trojan://` links with a tolerant manual URL splitter instead of relying on strict JVM URI parsing.
- **Why**: Real subscription/config links frequently contain decorated fragments, emojis, brackets, spaces, or partially encoded tags that are acceptable to VPN clients but brittle under strict generic URI parsing.
- **Impact**:
  - The Android app preserves the meaningful parts of those links:
    - authority
    - userinfo
    - host/port
    - query
    - fragment
  - Import reliability improves without pretending to support new protocols outside the declared scope.
## [2026-04-23] [Imported VPN Payloads Must Become Grouped Selectable Servers]
- **Decision**: Treat imported multi-link VPN payloads as grouped server catalogs, not as one oversized pseudo-profile.
- **Why**: Real-world imports can contain several usable nodes. Keeping them collapsed into one `SwimVpnProfile` blocks the product behavior the app needs: browse, select, pin, and activate one server among several imported nodes.
- **Impact**:
  - Imported profiles now carry bundle metadata so the app can reconstruct source groups.
  - The connection server catalog is now a merge of backend access servers and locally imported groups.
  - Server selection can target imported nodes directly, and runtime launch uses the selected server's raw config when present.
  - The servers page can host multiple groups without inventing new backend services or changing PostgreSQL truth.
## [2026-04-23] [Measure Server Responsiveness Before Tuning Tunnel Throughput]
- **Decision**: Add client-side latency probing and tunnel traffic observability before applying speculative throughput tweaks to the VPN runtime.
- **Why**: The app showed neither reliable ping values nor meaningful traffic metrics, which made every slowdown look like a generic "decryption/performance" issue. Without measurement, blind tuning would hide the true cause.
- **Impact**:
  - The servers page can now receive measured TCP latency values from the client.
  - Full-tunnel sessions now report tun2socks byte deltas into `VpnManager`.
  - We can now distinguish more honestly between a slow remote server, a poor route, and a local data-plane problem.
## [2026-04-23] [Stabilize Full Tunnel With Safer MTU And Explicit DNS Before Deeper Tuning]
- **Decision**: For the first throughput stabilization pass, change only two runtime levers with high signal and low architectural risk: MTU and default DNS.
- **Why**: `FULL_TUNNEL` was clearly more fragile than `LOCAL_PROXY`, but the client had no evidence of intentional throttling. A safer MTU and cleaner DNS are defensible first-order fixes that do not invent speculative performance policy.
- **Impact**:
  - Full-tunnel sessions now use MTU `1280` consistently.
  - Default Xray DNS no longer mixes in `localhost`.
  - Runtime diagnostics are now visible in-app through the technical screen, making later tuning evidence-based instead of guess-based.
## [2026-04-23] [Android Priority Order Must Be Parser Then Engine Then Performance]
- **Decision**: The Android VPN roadmap must be executed in this order:
  - parser coverage and truth
  - engine/runtime completion
  - performance tuning
- **Why**: Too many Android sub-chantiers were opened in parallel after `2-B`, which diluted progress and risked optimizing a stack whose parser and engine truth were still incomplete. The product value is higher if supported links are first recognized/imported correctly, then made connectable reliably, and only then optimized for speed.
- **Impact**:
  - `2-B` remains treated as closed from an implementation/integration standpoint.
  - `2-C` is now explicitly guided by the above priority order.
  - Performance work remains important, but it no longer outranks parser or engine completion.
  - `ANDROID_EXECUTION_STATUS.md` is now the dedicated long-term execution memory for this Android roadmap.
## [2026-04-23] [Preserve Modern Supported Link Metadata Even Before Full Runtime Coverage]
- **Decision**: For already-supported ecosystems, the parser must preserve modern real-world metadata even when runtime support for every variant is not yet complete.
- **Why**: The active roadmap puts parser truth ahead of engine completion. Public `vmess://`, `trojan://`, `vless://`, `ss://`, and JSON documents commonly rely on `grpc`, `http2`-family transport details, and `Shadowsocks` plugin metadata. Dropping those fields during ingest/parsing makes it impossible to truthfully evaluate the next engine gap.
- **Impact**:
  - `VMess gRPC` now keeps a usable `serviceName` via `path` fallback when needed.
  - `HTTP2`-family transports now preserve `path/host` metadata through the canonical profile.
  - `Shadowsocks` plugin metadata is preserved in the canonical profile with an explicit warning when runtime support is still incomplete.
  - VLESS/Reality `flow` is preserved and passed to runtime.
  - DPI-related `fragment/noises` metadata is preserved with a warning until runtime support is verified.
## [2026-04-23] [VPN Stop Must Clean Runtime Resources Even When State Is Desynced]
- **Decision**: Android VPN shutdown must be resource-driven, not only state-driven.
- **Why**: A stop request can arrive while runtime startup is still in flight or after `VpnManager` has already drifted to `IDLE/STOPPING`. In that case, relying only on UI/runtime state allows Xray to survive and keep local ports occupied after disconnect.
- **Impact**:
  - `SwimVpnService` now cancels in-flight startup before cleanup.
  - shutdown no longer early-returns if active runtime resources still exist.
  - orphaned Xray processes are defensively reaped with `stopAll()` during cleanup.
## [2026-04-23] [Recognize Happ Deep Links And Subscription Wrappers Before Fetch Support]
- **Decision**: The Android parser/import layer must explicitly recognize Happ client deep links and plain subscription wrappers even before remote fetch/decryption support is fully implemented.
- **Why**: Happ is a major reference client in the market, and its wrappers are part of the modern config ecosystem users actually paste into the app. Treating them as generic unsupported text blocks parser progress and hides the next real implementation gap.
- **Impact**:
  - `happ://add/...` can now unwrap direct supported node links into the standard parser flow.
  - Happ encrypted subscription links and Happ routing links are now classified honestly with explicit messages.
  - Plain `http(s)` subscription URLs are now recognized as VPN-related wrappers instead of unknown strings.
## [2026-04-23] [Remote Subscriptions Feed The Same Grouped Import Pipeline]
- **Decision**: Standard remote subscription URLs should be fetched and normalized into the existing grouped multi-link import pipeline instead of creating a separate subscription system.
- **Why**: The app already has a useful local catalog model for imported groups, selectable nodes, and pins. Creating a second system for fetched subscriptions would duplicate behavior and weaken parser truth.
- **Impact**:
  - `http(s)` subscriptions are downloaded with bounded timeouts and size limits.
  - Base64 subscription payloads are decoded before splitting into node links.
  - Fetched nodes become normal imported grouped servers.
  - Encrypted Happ subscriptions remain classified separately until their crypto format is implemented.
## [2026-04-23] [Modern Protocol Schemes Should Be Recognized Before Runtime Support]
- **Decision**: The parser should recognize modern subscription schemes such as Hysteria2, TUIC, SOCKS, and WireGuard, but must mark them unsupported until the engine can actually execute them.
- **Why**: Public subscriptions frequently mix supported and unsupported protocols. If the parser cannot identify unsupported schemes, it either hides useful feedback or pollutes neighboring supported entries. If it pretends they are connectable, it misleads the product.
- **Impact**:
  - Mixed subscriptions are split more accurately.
  - Users receive protocol-specific unsupported messages.
  - Engine/runtime work remains a separate, explicit future milestone.
## [2026-04-23] [Happ Crypt Links Require Authorized Format Before Import]
- **Decision**: Happ `crypt5` is classified as the current/preferred encrypted Happ subscription format, while `crypt3` and `crypt4` are classified as legacy encrypted formats. The app must not attempt to import encrypted Happ links without an authorized provider key/format.
- **Why**: Parser truth should recognize what the user pasted, but encrypted third-party subscription wrappers are access-control boundaries. Supporting them safely requires a legitimate format/key path, not reverse-engineering or bypassing another client.
- **Impact**:
  - Happ encrypted links now produce version-specific warnings and actionable import guidance.
  - Standard subscription URLs and `happ://add/https://...` wrappers remain the supported import path.
  - Future encrypted import work must be implemented as an authorized provider adapter.
  - Happ encrypted links are not treated as previewable/importable while their payload cannot be resolved.
## [2026-04-23] [SWIMVPN Encrypted Imports Start With Versioned Crypt1]
- **Decision**: SWIMVPN-owned encrypted imports start with `swimvpn://crypt1/<payload>`, using AES-256-GCM and a 12-byte nonce prefixed to ciphertext.
- **Why**: We need a versioned format we control before revisiting third-party encrypted wrappers. Versioning lets us rotate or replace the format later without breaking old import handling.
- **Impact**:
  - Android can recognize and decode SWIMVPN `crypt1` payloads when `SWIMVPN_CRYPT1_KEY_BASE64` is configured.
  - Decrypted plaintext is optionally GZIP-unpacked and then passed through the existing parser/import pipeline.
  - This is an MVP import-protection mechanism; hardcoded app keys are not treated as perfect DRM and should be rotated through release/build channels later.
## [2026-04-23] [Backend Owns SWIMVPN Crypt Link Generation]
- **Decision**: SWIMVPN `crypt1` link generation belongs in `vpn-config-engine-service`, exposed through the authenticated admin gateway route `POST /api/v1/admin/crypt-import`.
- **Why**: The backend is the controlled authority for inventory/config handling. Keeping encryption generation server-side avoids distributing generation logic broadly and lets the admin/backend layer control keys, compression, and future rotation.
- **Impact**:
  - The backend generates `swimvpn://crypt1/...` links with AES-256-GCM and random per-link nonce.
  - `SWIMVPN_CRYPT1_KEY_BASE64` is configured through environment variables, not committed source.
  - Android remains a decoder/importer for generated links, not the authority that mints them.
## [2026-04-23] [Do Not Ship SWIMVPN Crypt1 Decryption Keys In Android APK]
- **Decision**: Android must recognize `swimvpn://crypt1/...` but must not include the `crypt1` AES key or perform offline decryption.
- **Why**: Any symmetric key embedded in an APK can be extracted. To improve protection, crypt payload resolution must move server-side where keys can be protected, rotated, and audited.
- **Impact**:
  - Android no longer exposes `SWIMVPN_CRYPT1_KEY_BASE64` through BuildConfig.
  - Offline `crypt1` import is intentionally blocked.
  - The backend generator remains valid, and Android must use backend-side resolution rather than APK-side decrypt.
## [2026-04-23] [SWIMVPN Crypt1 Resolution Is Device-Bound Server-Side]
- **Decision**: `swimvpn://crypt1/...` payloads are resolved through `POST /api/v1/subscription/resolve-crypt`, which validates `userNumber`, `deviceId`, and active access before decrypting.
- **Why**: This keeps cryptographic keys out of the APK while still allowing legitimate users to import protected SWIMVPN payloads.
- **Impact**:
  - Customer service owns access/device validation.
  - VPN config engine owns crypt1 decrypt/decompress logic.
  - Android receives only the resolved raw config payload after backend authorization.
## [2026-04-23] [Mixed Imports Prefer Positive Supported Results]
- **Decision**: When a pasted payload contains both supported servers and recognized-but-unsupported modern formats, the app should import the supported servers and keep unsupported-format details as silent diagnostics.
- **Why**: Users need a positive, understandable result when useful servers were imported. Unsupported Hysteria2/TUIC/SOCKS/WireGuard-style entries are product/runtime gaps, not client-facing failures when other entries succeeded.
- **Impact**:
  - Supported entries remain importable from mixed subscriptions.
  - Unsupported recognized entries are retained for diagnostics without polluting the success UI.
  - If no supported server is importable, the import still fails clearly.
## [2026-04-23] [Provider Link Extraction Is A Dedicated Parser Boundary]
- **Decision**: VPN scheme extraction is centralized in `VpnConfigLinkExtractor` instead of ad-hoc regex splitting inside repository logic.
- **Why**: Provider payloads often contain multiple links, Base64-decoded subscriptions, JSON/string wrappers, and mixed protocols. Extraction must avoid false positives such as detecting `ss://` inside `vless://`.
- **Impact**:
  - `ConfigRepository` delegates grouped entry extraction to a testable parser boundary.
  - Direct links embedded in JSON/string provider payloads can be extracted without bypassing existing Xray/V2Ray JSON parsing.
  - Regression tests now protect the tokenizer behavior before future parser expansion.
## [2026-04-23] [Tun2Socks Runtime Config Must Match Upstream Hev Schema]
- **Decision**: Android full-tunnel runtime generation must emit the upstream `hev-socks5-tunnel` YAML schema instead of a SWIMVPN-specific JSON wrapper.
- **Why**: The JNI bridge calls `hev_socks5_tunnel_main_from_file` directly. That parser understands `tunnel`, `socks5`, optional `mapdns`, and `misc`; unknown JSON sections can let the native bridge start without a correctly wired data plane.
- **Impact**:
  - Full tunnel now writes `tun2socks-main.yml`.
  - Xray local SOCKS remains the handoff point at `127.0.0.1:10808`.
  - Future tun2socks tuning must be expressed through documented upstream keys, not custom wrapper metadata.
## [2026-04-23] [Runtime UI State Uses Persisted Heartbeat Snapshot]
- **Decision**: Android runtime UI state must reconcile against a persisted, timestamped service snapshot instead of relying only on the in-memory singleton bridge.
- **Why**: A foreground VPN service can remain active while Compose/accessibility state drifts or misses an in-memory update. The user-facing power button must reflect whether the actual runtime is active, not just the latest UI memory value.
- **Impact**:
  - `SwimVpnService` writes runtime status and heartbeat data to `RuntimeStateStore`.
  - `HomeScreen` reads the snapshot and treats stale active snapshots as disconnected.
  - Runtime control remains local-only; no backend dependency is introduced.

## [2026-04-23] [Android UI Text Must Resolve From Resources, Not Mixed Hardcodes]
- **Decision**: Critical user-facing Android text must come from string resources with complete key parity across `values`, `values-fr`, and `values-ru`.
- **Why**: The app had corrupted FR/RU resource files plus visible text still hardcoded in Kotlin. That combination made locale switching unstable and difficult to verify.
- **Impact**:
  - Home/runtime/import status text now localizes through Android resources.
  - FR and RU resource files are treated as full first-class translations, not partial overlays.
  - Future locale work should add keys to all three language files in the same batch.
## [2026-04-23] [Apply App Locale Outside Compose Recomposition]
- **Decision**: Android app locale changes must be applied once at activity startup and on explicit user language changes, not from a Compose LaunchedEffect tied to a continuously collected preference flow.
- **Why**: The previous approach let DataStore locale state and AppCompatDelegate locale state race during activity recreation, causing repeated MainActivity restart loops and black-screen flicker.
- **Impact**:
  - MainActivity now applies the persisted locale before setContent.
  - Technical settings still change language immediately, but through an explicit callback instead of a recomposition loop.
  - Future locale work should keep a single source-of-truth flow and avoid calling setApplicationLocales(...) from reactive UI loops.
## [2026-04-23] [Technical Screen External Actions Must Be Debounced On Entry]
- **Decision**: External actions from the Android technical screen must be briefly disabled during screen entry.
- **Why**: Investigation showed no app crash, but repeated involvement of com.android.settings after entering the technical screen. The most credible cause is an accidental click-through into the kill-switch/settings shortcut during navigation.
- **Impact**:
  - The kill-switch shortcut remains available, but only after a short entry delay.
  - Screen-entry navigation becomes safer without changing the screen architecture.
  - If more click-through cases appear later, the same pattern can be extended to other external-action tiles.
## [2026-04-23] [Prefer Explicit IPv4 DNS For The Current Android Tunnel Path]
- **Decision**: Use an explicit shared IPv4 resolver set and prefer IPv4 resolution in the current Android tunnel runtime.
- **Why**: The app's present full-tunnel path is validated mainly as an IPv4 data plane (`VpnService` + `tun2socks` + Xray). Leaving DNS/routing too generic increases the risk of "connected but page never finishes loading" cases when resolution or fallback drifts toward paths the tunnel is not consistently carrying.
- **Impact**:
  - Xray DNS now uses `UseIPv4`.
  - Routing now uses `IPIfNonMatch` instead of `AsIs`.
  - Android `VpnService` and Xray share one explicit IPv4 resolver baseline.
  - This is a stabilization decision, not a claim of full split-tunnel or advanced dual-stack support.
## [2026-04-23] [Tunnel Is The Normal User Mode; Proxy Is Advanced/Manual]
- **Decision**: Present `Tunnel` as the normal recommended user mode and `Proxy` as an advanced/manual mode.
- **Why**: Live ADB auditing confirmed that `LOCAL_PROXY` works as a real local proxy path, but that does not make it the default whole-device browsing experience for ordinary users.
- **Impact**:
  - Product wording now favors `Tunnel` for normal usage.
  - `Proxy` remains available, but is described more honestly as a manual or proxy-aware path.
  - The runtime engine is preserved; only the product contract and guidance are corrected in this batch.
## [2026-04-23] [Proxy Keeps Its Earlier Xray DNS/Routing Policy]
- **Decision**: Keep `LOCAL_PROXY` on its earlier Xray DNS/routing profile while allowing `FULL_TUNNEL` to keep the newer tunnel-oriented hardening policy.
- **Why**: The user's positive proxy validation happened before the shared DNS/routing hardening batch. Since that batch changed the shared Xray runtime for both modes, restoring proxy confidence requires a proxy-specific rollback instead of a global tunnel rollback.
- **Impact**:
  - `LOCAL_PROXY` now uses:
    - `UseIP`
    - `AsIs`
    - DNS `1.1.1.1` + `8.8.8.8`
  - `FULL_TUNNEL` keeps the newer policy for tunnel behavior.
  - The shared runtime generator is now mode-aware rather than forcing one policy onto both paths.
## [2026-04-24] [Freeze Proxy Runtime After Recovery]
- **Decision**: Freeze the current `LOCAL_PROXY` runtime configuration after the reinstall-and-audit recovery.
- **Why**: The latest live audits confirmed the intended rollback policy is active, Xray is healthy, proxy traffic exits correctly, and DuckDuckGo plus the user's real app usage recovered. Further runtime edits would add risk without a reproducible regression.
- **Impact**:
  - `LOCAL_PROXY` stays on the rollbacked DNS/routing profile.
  - Future proxy work should only resume if a new reproducible regression appears.
  - The next implementation focus can safely move away from proxy stabilization.
## [2026-04-24] [Use Backend-Driven Checkout With Crypto Pay And Manual Card Review]
- **Decision**: Subscription checkout now branches into two explicit MVP payment methods: `CRYPTO` through Crypto Pay invoices, and `CARD_MANUAL` through Telegram proof review.
- **Why**: There is no card PSP yet, but the product still needs a real payment path. Crypto Pay offers a documented invoice/webhook seam, while manual card review keeps the card flow operational without pretending to automate bank processing.
- **Impact**:
  - The mobile app no longer invents a checkout step; it asks the backend for a payment-specific redirect.
  - Checkout pricing now comes from PostgreSQL plan truth, not the Android client.
  - Manual payment proof and review are logged through `AdminEvent` while `Order` remains the payment truth anchor.
  - Existing inventory fulfillment and delivery email flows remain the final post-payment path.
## [2026-04-24] [Do Not Pretend Session Traffic Equals Subscription-Wide Analytics]
- **Decision**: The profile analytics UI must separate quota truth and current-session traffic instead of presenting them as one global subscription-usage metric.
- **Why**: The backend profile still returns `dataUsedBytes = 0`, so the app cannot honestly claim that it knows full subscription consumption across time/devices. The only reliable traffic counter currently available in the product is the local runtime session on this device.
- **Impact**:
  - The profile screen now presents:
    - backend plan quota
    - current device session usage
    - synced access status/expiry
  - Product wording no longer overstates analytics maturity.
  - Future server-side usage analytics can be added later without undoing this UI truthfulness baseline.
## [2026-04-24] [Subscription Progress Must Follow Measured Backend Usage Only]
- **Decision**: The subscription progress bar must be driven only by measured backend usage (`dataUsedBytes`) against the sold plan quota, never by ad-hoc local session traffic.
- **Why**: This bar is intended to become a business control surface for cut-off, renewal decisions, and quota enforcement. Mixing runtime session counters into it would make the value visually active but operationally false.
- **Impact**:
  - The profile screen now shows a quota progress bar that is reserved for real measured subscription consumption.
  - Until backend usage collection exists, the progress stays at zero instead of inventing fake depletion.
  - The next backend batch must introduce strict usage and device-allocation rules before this bar becomes an enforcement tool.
## [2026-04-24] [Shared Supplier Links Are Source-Level Assets With Strict User Caps]
- **Decision**: Supplier links with large pooled traffic must be modeled as source-level backend assets that can be allocated to multiple distinct customers up to a hard cap, instead of being treated as one-order one-config forever.
- **Why**: Your supplier links are not ordinary single-user configs. They carry large shared traffic pools (typically `1000 GB`) and are allowed to serve at most `5` different users in SWIMVPN. That rule must live in PostgreSQL and inventory fulfillment, not in Telegram or in the Android client.
- **Impact**:
  - Inventory now has source-level quota fields and a strict max distinct-customer allocation count.
  - Order assignments now carry measured usage bytes per customer allocation.
  - Profile usage can now come from backend assignment truth instead of a fake hardcoded zero.
  - Quota exhaustion is now part of backend access-state resolution.

## [2026-04-24] [First Usage Producer Must Stay Explicit And Low-Frequency]
- **Decision**: The first measured-usage producer will report bytes only on explicit manual VPN stop, not as a background sync loop.
- **Why**: We need real quota truth to start feeding the subscription progress bar, but we do not yet have a durable continuous telemetry design. Reporting at manual stop gives a first business-safe signal without inventing a noisy or fragile background system.
- **Impact**:
  - Backend `dataUsedBytes` can start moving from Android runtime truth.
  - Profile refresh after stop keeps the UI aligned with PostgreSQL truth.
  - Continuous usage synchronization remains a follow-up batch, not an accidental side effect of this implementation.

## [2026-04-24] [Apply Dependent Prisma Migrations Sequentially On Fresh Local Databases]
- **Decision**: On a fresh local database, the initial schema and follow-up quota migration must be executed and resolved strictly in sequence, never in parallel.
- **Why**: The quota migration depends on base tables such as `InventoryItem`. Parallel execution can produce false negatives and an incoherent `_prisma_migrations` history.
- **Impact**:
  - Local environment setup is now deterministic.
  - Future migration recovery on blank databases should start with `202604230001_init_schema` before any follow-up migration.
  - This avoids a misleading state where Prisma history says �applied� while SQL never actually ran.

## [2026-04-24] [Trial Must Stay Outside The Public Subscription Catalog]
- **Decision**: The trial remains an access-state feature and badge, not a purchasable store plan shown on the subscription page.
- **Why**: The trial is operationally separate from paid offers and has different rules (`3 days`, abuse controls, activation logic). Showing it as the `WEEK` store offer creates false product meaning and confuses checkout.
- **Impact**:
  - Public plan listing now exposes only paid offers.
  - Trial access stays visible through profile/home status only.
  - A future paid weekly offer can be introduced explicitly instead of piggybacking on the internal trial record.

## [2026-04-24] [Checkout Must Confirm Payment Email Explicitly]
- **Decision**: Checkout now requires an explicit email confirmation modal immediately before payment instead of assuming the stored profile email is implicitly accepted.
- **Why**: Payment follow-up depends on the email, and users need a clear confirmation point before redirecting into Telegram or Crypto Pay. This also reduces confusion when checkout fails because contact data is incomplete or stale.
- **Impact**:
  - The app shows a lightweight confirm/cancel modal before creating checkout.
  - Backend checkout errors for missing email are now surfaced more honestly.
  - The payment contract is explicit at the moment of action, not hidden in prior onboarding state.

## [2026-04-24] [Trial Profiles Must Not Masquerade As Weekly Paid Offers]
- **Decision**: Trial profiles must not expose a paid-offer code in API/UI state, even if the internal plan category uses `WEEK` for inventory grouping.
- **Why**: The user-facing meaning of a trial is different from a paid weekly offer. Reusing `WEEK` at the profile surface creates false status, confusing analytics, and misleading subscription labels.
- **Impact**:
  - Trial account cards stop showing `Offer � WEEK`.
  - A future paid weekly offer can still exist independently.
  - Internal inventory grouping can remain separate from public subscription meaning.
## [2026-04-25] [Weekly Paid Offer And Trial Must Share Category But Not Product Meaning]
- **Decision**: `WEEK` remains the commercial weekly offer in PostgreSQL, while trial keeps its own runtime rules (`3 days`, `5 GB`, hidden from store) even if it still uses the `WEEK` category internally for assignment grouping.
- **Why**: The repository truth requires public offers `week`, `month`, `quarter`. The previous seed collapsed `WEEK` into the trial itself, which removed the weekly offer from the store and confused profile meaning.
- **Impact**:
  - The store can expose all three paid offers again.
  - Trial stays outside the purchasable catalog.
  - Backend quota and expiry logic must explicitly override trial values instead of inheriting the paid weekly plan labels.

## [2026-04-25] [Checkout Errors Must Survive The Microservice Boundary]
- **Decision**: Checkout failures in `customer-order-service` must throw `RpcException` payloads, and the gateway must extract nested message shapes before returning HTTP errors.
- **Why**: Plain `Error` exceptions crossing the Nest microservice boundary were collapsing into opaque `Internal server error` responses, which hid the actual payment problem from the user.
- **Impact**:
  - Missing email, missing payment bot config, and similar checkout failures can now surface as actionable HTTP messages after redeploy.
  - Future payment debugging should start from the preserved gateway message before adding more instrumentation.
## [2026-04-25] [Subscription Parsing Must Be Normalized Before Runtime Normalization]
- **Decision**: Subscription parsing now has its own Kotlin layer with normalized parser models, separate from UI and separate from the runtime-oriented SwimVpnProfile normalization path.
- **Why**: The previous parser logic already handled core protocol links, but metadata extraction and subscription expansion were too dispersed across import code. Strengthening robustness without breaking imports required a dedicated parser layer instead of piling more heuristics directly into UI/import logic.
- **Impact**:
  - The app can parse more subscription metadata without mixing parser concerns with business logic.
  - Raw payloads remain preserved intact.
  - ConfigRepository can consume normalized entries first, then keep using the existing runtime parser/normalizer for importable configs.
  - Future parser work should extend the subscription parser package, not push raw-text parsing into screens.

## [2026-04-25] [Profile UI Must Keep Backend Access Truth Separate From Imported Config Truth]
- **Decision**: The profile screen keeps `SWIMVPN Access` as backend access truth and `Active Config` as parser/runtime truth for the currently active config.
- **Why**: Imported configs can carry useful provider, quota, and expiry metadata, but that metadata is not the same thing as SWIMVPN-managed subscription enforcement. Showing both in one card risks falsely presenting imported parser values as backend-controlled product truth.
- **Impact**:
  - Trial and paid access state remain anchored to backend profile fields only.
  - Imported config metadata is surfaced with an explicit source badge in a separate card.
  - Parser-derived quota/expiration stays descriptive and local to `Active Config`, not product-enforcement wording.
## [2026-04-25] [Supplier Config Capacity Must Be Enforced As Shared Resale Slots]
- **Decision**: Supplier configs are now treated as shared backend resources with an explicit resale cap of `4` slots, regardless of the provider's hidden technical allowance of `5`.
- **Why**: Product truth requires us to preserve speed and reliability by under-selling supplier device capacity instead of promising isolated per-user resources that do not exist.
- **Impact**:
  - Fulfillment now allocates by slot consumption, not by distinct-customer count alone.
  - Paid orders can enter `PENDING_FULFILLMENT` when payment succeeds but no supplier config has enough remaining healthy capacity.
  - Admin tooling and audit logs can reason about `used_resale_slots`, config health, assignment status, and manual reassignment/revocation.
  - Backend profile payloads now expose commercial allowance (`devicesAllowed`) separately from supplier-managed quota/expiration truth.

## [2026-04-25] [Backend Import Must Parse Supplier Message Bundles, Not Just Bare Config Links]
- **Decision**: The backend import path now accepts a supplier message bundle and extracts the deliverable subscription URL plus metadata, instead of requiring admins to manually strip the text down to a bare link first.
- **Why**: Real supplier resources often arrive as rich Telegram/bot messages containing the usable subscription URL, quota, expiry, provider name, and current connected-device count in one block. Requiring manual cleanup loses critical truth and creates empty inventory metadata.
- **Impact**:
  - Inventory imports can preserve the meaningful supplier metadata needed for product truth.
  - The backend now seeds `used_resale_slots` from already-connected supplier devices when detected.
  - The app can later display provider-driven traffic and expiry more honestly once the backend resource exists.

## [2026-04-25] [Paid Profile UI Must Show Public Plan Names And Exact Expiry]
- **Decision**: The paid-user profile path must surface the public subscription label (`Basic / Premium / Platinum`) and the exact expiration date, while leaving provider/site details out of the primary purchased-plan presentation.
- **Why**: Internal backend categories like `WEEK / MONTH / QUARTER` are implementation details. The user-facing truth that matters is what they bought, how much shared supplier traffic is already used, and when the access actually expires.
- **Impact**:
  - Backend profile payloads now expose `planDisplayName`.
  - Android home/profile surfaces no longer need to show internal commercial codes for paid access.
  - `SWIMVPN Access` now prioritizes exact expiry date and real consumption over supplier-brand metadata.

## [2026-04-25] [Paid Access Pending Fulfillment Must Stay Explicit In UI]
- **Decision**: `PENDING_FULFILLMENT` must remain a first-class user-visible state in Android instead of being shown as either `ACTIVE` or `INACTIVE`.
- **Why**: A paid order that has been confirmed but not yet assigned is neither active nor absent. Hiding this state causes contradictory UI and breaks the truth of the fulfillment model.
- **Impact**:
  - Home badge and profile status now stay aligned with backend fulfillment truth.
  - Users can distinguish �paid and being prepared� from �active� and �expired�.
  - Support/debugging becomes easier because the UI no longer masks pending delivery as inactivity.

## [2026-04-25] [Card Checkout Redirect Must Follow The Real Command Bot]
- **Decision**: The card-payment redirect must be derived from the bot token that actually polls `/start card_<orderRef>` commands, not from a manually typed username string treated as a separate source of truth.
- **Why**: The notification/payment command bot is the runtime owner of the manual card flow. If the redirect username points to a different bot, the customer lands in the wrong chat and the payment flow appears broken even when Telegram and backend services are healthy.
- **Impact**:
  - `NOTIFICATION_BOT_TOKEN` / `TELEGRAM_BOT_TOKEN` now anchor the redirect target.
  - `PAYMENT_BOT_USERNAME` becomes an optional fallback rather than a fragile mandatory field.
  - Mis-entering a bot token in `PAYMENT_BOT_USERNAME` no longer breaks checkout because the backend can resolve the username automatically.

## [2026-04-25] [Trial Must Stay Separate From Paid Analytics]
- **Decision**: Trial access must not present quota-limited analytics in `SWIMVPN Access`; it is shown as unlimited while active, and imported-config analytics remain a separate truth in `Active Config`.
- **Why**: The product trial is not a paid offer and should not visually compete with a supplier-backed paid plan or an imported config�s parsed metadata. Showing the historical `5 GB` backend fallback made the profile look like trial was a normal limited plan, which created hidden confusion.
- **Impact**:
  - Backend profile payloads no longer expose a measured quota limit for trial.
  - Android trial UI now shows `Unlimited` instead of a quota progress bar.
  - Paid subscription analytics continue to dominate whenever the current access truth is paid.
  - Imported config analytics remain sourced from `ActiveConfigMetadata` only, instead of being blended into `SWIMVPN Access`.

## [2026-04-25] [Trial Bootstrap And Labels Must Not Pretend There Is Paid Access]
- **Decision**: When the user has no active access yet, bootstrap/profile-incomplete responses must expose `devicesAllowed = 0`, and trial UI/backend fallback labels must not reuse paid-plan quota semantics.
- **Why**: Returning a non-zero device allowance before access exists and keeping a hidden `5 GB` trial fallback created small but real contradictions that could resurface in UI, admin tools, or future endpoints.
- **Impact**:
  - No-access states no longer imply a phantom device entitlement.
  - Trial wording is less likely to be mistaken for a paid plan.
  - Hidden fallback paths are less likely to reintroduce the old trial-cap confusion later.

## [2026-04-25] [Supplier Bundle Parsing Must Support Real Russian Provider Messages]
- **Decision**: The Android parser must recognize supplier metadata in the same Russian-language bundle formats users actually import, including `??` units and textual month names.
- **Why**: The UI for `Active Config` was already capable of showing quota bars and expiry, but the parser was silently dropping the metadata for real supplier messages, which made the UI look broken when the truth was actually missing upstream.
- **Impact**:
  - Imported configs can now surface the real traffic bar and expiry from richer supplier messages.
  - Parser verification now includes a realistic provider bundle instead of only sanitized English metadata examples.

## [2026-04-25] [Payment Runtime Must Accept Real Deployment Variable Names]
- **Decision**: `customer-order-service` must accept `PAYMENT_BOT_TOKEN` for card-bot resolution and `CRYPTO_PAY_API_KEY` as a fallback alias for crypto configuration, while Docker must pass those values into the service environment.
- **Why**: The live runtime was failing with `main bot not configured` and `crypto api not configured` even though the operator had provided usable credentials, because the service only trusted a narrower variable set than the deployment actually used.
- **Impact**:
  - Card checkout can resolve the payment bot from the real token used in deployment.
  - Crypto checkout is more tolerant of deployment naming drift.
  - Docker-based runtime configuration is now closer to the operator's real environment.

## [2026-04-25] [The VPS Root Compose Must Match The Backend Payment Contract]
- **Decision**: The root `docker-compose.yml` used by VPS deployments must explicitly carry the same payment-runtime variables required by `customer-order-service` and `notification-bot-service`, instead of relying on the backend-only compose file.
- **Why**: The live environment was reading the root compose, which was missing the payment bot and crypto variables even though the backend code had already been updated. That created a false impression that the code was still broken while the real drift was in deployment wiring.
- **Impact**:
  - The deployment compose at the repo root now reflects the real backend runtime contract for card and crypto checkout.
  - Future VPS redeploys can pick up payment variables without depending on the backend-local compose file.

## [2026-04-25] [Landing Must Be Exposed As Its Own Web Service]
- **Decision**: The landing page is deployed as a separate root-level web service with Traefik labels on `app.swimvpn.pro`, instead of pretending it is part of a backend microservice.
- **Why**: The landing lives at the repository root and is a standalone frontend concern. DNS alone is not enough; Dokploy needs a real containerized service behind the domain.
- **Impact**:
  - `app.swimvpn.pro` now has a dedicated deployment target in the main compose file.
  - Backend microservices stay isolated from frontend hosting concerns.

## [2026-04-28] [Backend Entitlement State Is The Access Truth]
- **Decision**: Backend profile responses now expose an explicit `entitlementState` (`ACTIVE_TRIAL`, `ACTIVE_SUBSCRIPTION`, `EXPIRED_TRIAL`, `EXPIRED_SUBSCRIPTION`, `TRIAL_AVAILABLE`, `PROFILE_INCOMPLETE`, `PENDING_FULFILLMENT`) while keeping legacy `status`/`accessType` for compatibility.
- **Why**: Android and backend were mixing trial, paid, expired, and profile-incomplete states through coarse `ACTIVE`/`EXPIRED` flags, creating contradictory routing and premium-resource checks.
- **Impact**:
  - Android normalizes all access decisions from backend state instead of inventing its own truth.
  - Premium backend resources are limited to active trial or active subscription.
  - Expired users keep app shell/imported-config access without receiving managed servers/configs.

## [2026-04-28] [Runtime Config Exposure Must Be Device-Bound]
- **Decision**: Raw managed config delivery must be opt-in and device-bound; public profile/import paths must not expose `subscriptionUrl`, and premium server/usage endpoints must verify the customer device.
- **Why**: A public user number is not strong enough to authorize premium server metadata, usage mutation, or raw config delivery.
- **Impact**:
  - `GET /access/:userNumber` and local import sync return safe profile payloads without raw runtime config.
  - `/servers` requires both `x-user-number` and `x-device-id`.
  - Usage reporting validates the device before recording shared supplier usage.
  - Encrypted `crypt1` resolution is bound to the assigned raw config.

## [2026-04-29] [Trial Activation Must Not Trap Freemium Users]
- **Decision**: The trial/profile completion screen must offer a freemium escape path that loads the backend profile and enters the normal app shell without granting managed premium resources.
- **Why**: ADB release validation showed a profile-incomplete user could be stuck on trial activation with no route to import configs, view offers, or continue without trial.
- **Impact**:
  - Trial activation remains backend-enforced and abuse-protected.
  - Users who cannot or do not want to activate trial can still enter the app shell.
  - Premium servers/configs remain restricted to backend `ACTIVE_TRIAL` or `ACTIVE_SUBSCRIPTION`.

## [2026-04-29] [Profile Completion Is Separate From Trial Entitlement]
- **Decision**: Completing or updating profile contact data is allowed without granting trial or premium access.
- **Why**: A user whose trial is denied must still be able to enter freemium, import configs, and subscribe. Coupling contact persistence to successful trial activation created a dead-end and blocked checkout.
- **Impact**:
  - `POST /api/v1/access/profile/complete` updates only the device-bound customer's contact data.
  - Trial anti-abuse remains enforced; trial denial does not become premium access.
  - Existing paid access is not recovered by unauthenticated email/phone lookup, avoiding account takeover risk.

## [2026-04-29] [Provider Metadata Comes From Subscription Headers When Available]
- **Decision**: For imported subscription URLs, Android treats HTTP `subscription-userinfo` and `profile-update-interval` headers as provider metadata inputs while preserving raw VPN config body unchanged.
- **Why**: Runtime ADB testing of the real supplier URL showed traffic, quota, expiry, and refresh interval are supplied in headers, not in the Base64 profile body.
- **Impact**:
  - Imported config UI can show real traffic/quota/expiry for providers that follow the common subscription header convention.
  - Parser remains data-only and does not decide entitlement or premium access.
  - Missing headers remain safe: UI shows unknown/inactive states rather than fake unlimited quota.

## [2026-04-29] [Telegram Admin Authorization Supports Review Group Context]
- **Decision**: Telegram payment admin actions may be authorized either by explicit `ADMIN_USER_IDS`, by a personal `ADMIN_CHAT_ID`, or by callback/message context inside the configured admin/review group chat.
- **Why**: The existing implementation compared `ctx.from.id` to `ADMIN_CHAT_ID`, but production env commonly uses group chat ids for admin/review destinations. A group chat id cannot equal a human Telegram user id, which blocks approve/reject callbacks.
- **Impact**:
  - `PAYMENT_REVIEW_CHAT_ID` can remain the group that receives proof packets.
  - `ADMIN_USER_IDS` can be added for stricter personal admin allow-listing.
  - Ordinary private chats remain unauthorized.

## [2026-04-29] [Resale Slot Means One Customer Order]
- **Decision**: Supplier resale capacity is counted per customer order, not by commercial plan tier. Basic, Premium, and Platinum each consume exactly one resale slot on one supplier config link.
- **Why**: A supplier link can technically work on up to 5 devices, but SWIMVPN intentionally resells it to at most 4 customer orders and keeps one supplier device capacity unused as a quality buffer.
- **Impact**:
  - Superseded on 2026-04-29 by the final two-order resale cap: `max_resale_slots` should be `2` for supplier configs.
  - `supplier_device_limit` may remain `5` as provider metadata.
  - Superseded on 2026-04-30: Basic, Premium, and Platinum each consume one resale slot per paid order.
  - Plan category continues to select the inventory bucket and commercial offer, not the number of resale slots consumed.

## [2026-04-29] [Supplier Link Resale Cap Is Two Orders]
- **Decision**: Every supplier config link may be resold to at most two customer orders, regardless of whether the provider technically supports up to five devices.
- **Why**: The product should not be greedy with shared provider capacity. Limiting resale to two customer orders preserves performance and makes the customer-facing promise `up to 2 devices` easier to reason about.
- **Impact**:
  - `max_resale_slots` should become `2` for supplier configs.
  - Every paid order still consumes one resale slot.
  - Basic, Premium, and Platinum remain commercial/inventory categories, not different resale-slot multipliers.
  - The subscription UI should advertise `up to 2 devices` for each plan.

## [2026-04-30] [Admin Operations Bot Is A Restricted Inventory Control Surface]
- **Decision**: The `TELEGRAM_BOT_TOKEN` / `admin-control-service` bot is now the secure Admin Operations Bot for supplier inventory imports, while payment proof handling remains in `notification-bot-service`.
- **Why**: Telegram can help operate the shop, but it must not become an unauthenticated source of truth. Admin imports must be restricted to explicit human admin user IDs and recorded in PostgreSQL audit events.
- **Impact**:
  - `ADMIN_USER_IDS` is the preferred production allow-list for admin bot commands.
  - `/add basic|premium|platinum <config-or-url>` imports configs into the matching inventory category with the current max resale cap of two customer orders.
  - Raw supplier config content remains preserved and parsing/business allocation stays in backend services.

## [2026-04-30] Admin bot guided imports use in-memory conversation state

Decision: The `/add_wizard` Telegram admin import flow keeps only the temporary step state in memory. Confirmed imports are persisted through the existing inventory service into PostgreSQL.

Reason: Telegram is an admin control layer, not the source of truth. Persisting unfinished wizard drafts would add schema and cleanup complexity without improving customer delivery safety for this MVP. If the bot restarts mid-wizard, the admin can restart `/add_wizard`; no supplier config is stored until explicit `confirm` succeeds.

Consequence: Live ops must treat `/add_wizard` as a convenience workflow. Durable inventory, slot caps, supplier quota, expiration, and assignment truth remain in PostgreSQL.

## [2026-04-30] Inventory supplier healthcheck runs inside inventory-delivery-service

Decision: The MVP uses a lightweight in-process scheduler in `inventory-delivery-service` instead of introducing a new cron service or external queue.

Reason: The service already owns supplier inventory state and already exposes `runHealthCheck()`. Adding a new service would increase Dokploy complexity and another deployment surface. The scheduler is configurable and can be disabled through `INVENTORY_HEALTHCHECK_INTERVAL_MS` if VPS load or multi-instance deployment changes later.

Consequence: In the current single-instance Dokploy deployment this gives automatic expiry/health enforcement. If the platform later runs multiple inventory instances, this should move to a single-worker cron/queue to avoid duplicate health checks.

## [2026-05-01] FULL Inventory Means Sold Out, Not Existing Access Revoked

Decision: `InventoryHealthStatus.FULL` means a supplier config has no remaining resale capacity for new orders. It must not by itself revoke or hide access for customers who already have an `ACTIVE` assignment to that config.

Reason: SWIMVPN intentionally caps resale per supplier link. When the cap is reached, the link is full for new sales, but the already assigned customers are the reason the link is full and must continue to receive the access they bought unless the supplier expires, quota is exhausted, the assignment is revoked/expired, or the inventory is explicitly disabled.

Consequence: Allocation must reject `FULL` inventory, but profile/access/server delivery should keep serving an active assignment on `FULL` inventory. True access stoppage remains driven by assignment status, supplier expiration, source quota exhaustion, or disabled/expired health.

## [2026-05-01] Adaptive VPN Intelligence Starts As Local Deterministic Control

Decision: The non-LLM intelligence starts as a local Android deterministic decision agent. It observes VPN runtime state, records per-server success/failure history, retries with bounded backoff, and may fallback to another already-authorized visible server. It does not run inside Xray/tun2socks and does not make backend entitlement decisions.

Reason: The product promise is adaptive stability, but the safest first step is reliable observability and bounded recovery. A contextual bandit or automatic stealth switching would be premature without trustworthy runtime metrics and config-mode classification.

Consequence: Phase 1 can be tested and explained through logs. Future Phase 2 scoring and Phase 3 bandit logic must build on this local history and must remain disabled or conservative until live device QA proves the base reconnect behavior is stable.

## [2026-05-02] Customer cancellation releases resale capacity through inventory revocation

Decision: User-initiated cancellation/revocation must call the inventory `revoke_assignment` path instead of only marking the customer entitlement inactive in `customer-order-service`.

Reason: The product rule is that a supplier link can be resold up to the configured cap. Reusing the inventory revocation path keeps `OrderAssignment.access_status`, `InventoryItem.used_resale_slots`, `InventoryItem.health_status`, and audit events aligned with the existing admin revocation behavior.

Consequence: Customer cancellation removes premium access from the cancelling device and makes capacity available again when the active assignment is revoked. Orders remain traceable for accounting; refunds are not implied by cancellation and remain an admin/business process.

## [2026-05-02] Cancelled Paid Access Returns To Standard Freemium State

Decision: A customer-initiated cancellation or revoked paid assignment must not be represented to Android as an expired subscription. If no active or pending assignment remains, the current access state is `FREEMIUM` / `NONE`, with no paid offer badge and no paid expiry shown.

Reason: Cancellation is a voluntary removal of one premium assignment, not an app lockout. The user can still use the app shell, import external configs, and buy a new plan. Showing `EXPIRED` plus supplier remaining days is contradictory and hurts trust.

Consequence: Backend profile bootstrap only treats orders with `ACTIVE` or `PENDING` assignments as current paid entitlement. Historical fulfilled/revoked orders remain traceable for accounting/audit, but they do not drive active plan UI or subscription expiry. Android also defensively hides paid plan/expiry fields unless access is active or pending fulfillment.

## [2026-05-02] Premium Subscription URLs Are Sources, Not Runtime Servers

Decision: A purchased supplier `http://` or `https://` subscription URL is an internal source document. It must not be exposed to Android as an `HTTPS` VPN server. After entitlement is confirmed, Android resolves that source into concrete runtime nodes such as VLESS, VMess, Trojan, or Shadowsocks and only those nodes are selectable/connectable.

Reason: Showing `wb.routerwb.ru` as a server made the app look connected while no usable VPN route existed. The provider domain is not the thing the customer connects through; it is the source that lists the real nodes. The customer-facing quota is also the sold plan quota, while supplier quota remains internal/provider metadata.

Consequence: Store server responses ignore plain HTTP(S) subscription URLs as runtime endpoints. Managed UI hides supplier host/provider details and displays commercial plan quota/usage. Raw supplier config remains preserved for entitlement-backed resolution and future parser refresh.

## [2026-05-02] Paid Time Is Supplier-Managed, SWIMVPN Enforces Sold Quota

Decision: For paid access, SWIMVPN does not invent a separate local expiration from the commercial plan duration. The supplier/assignment expiry is the time truth when present. SWIMVPN enforces the sold plan quota from `Plan.quota_label` and ends Premium access when the measured assignment usage reaches that quota.

Reason: The supplier controls the actual subscription lifetime. The product responsibility we keep locally is the commercial quota sold to the customer and the app experience after that quota is reached.

Consequence: Plan quota exhaustion marks the assignment ended, recalculates inventory resale capacity, blocks backend Premium server delivery, stops the Android VPN on the next usage report, and returns the user to standard mode without locking the app. Imported configs remain independent and usable.

## [2026-05-02] Production Seed Is Reference-Only By Default

Decision: Prisma production seed must only ensure missing reference plans/servers and must not create starter VPN inventory unless `SEED_DEMO_DATA=true` is explicitly set.

Reason: Production deploy/bootstrap must not expose hardcoded VPN configs or overwrite live pricing/plans. Real inventory must enter through controlled admin/import flows, and the first database admin must be created through an explicit ops step with a bcrypt password hash.

Consequence: Fresh production databases get minimal catalog references, but no usable demo VPN access and no default admin credentials. Existing plan pricing/copy remains database truth after the first creation.

## [2026-05-02] Active Entitlement Selection Prefers Usable Access Over Newer Terminal Orders

Decision: Customer profile resolution must prefer the newest usable active assignment, then pending fulfillment, then expired assignment. Revoked or failed terminal assignments must not mask an older still-active assignment.

Reason: A failed or revoked later order is not proof that an older valid paid access should be removed. Backend entitlement remains the source of truth, but the selection rule must preserve usable access when it exists.

Consequence: Android receives active entitlement when any recent active assignment is still valid, while customers with only revoked/failed terminal assignments return to standard/freemium state.

## [2026-05-02] Cancelled/Pending Paid Orders Stay Visible For Profile State Resolution

Decision: `getProfile()` may read cancelled paid order history, but cancelled/revoked orders do not become active entitlement. They are used only to avoid misleading trial/premium UI after cancellation and to return the customer to standard freemium state.

Reason: A paid customer who cancels or has a pending order cancelled should not see an expired premium state or a fresh trial prompt. A paid-but-undelivered order must remain `PENDING_FULFILLMENT` so admins can complete delivery instead of losing the order into freemium.

Consequence: Active assignments are still preferred first, pending paid orders remain visible, expired assignments represent expired access, and terminal/cancelled paid history falls back to `FREEMIUM` / standard mode.

## [2026-05-02] NPM Audit Force Fix Requires Dedicated NestJS Upgrade

Decision: Do not run `npm audit fix --force` during production stabilization.

Reason: The audit force path upgrades core NestJS packages across major versions, affecting runtime services, Swagger, platform-express/multer handling, CLI/build behavior, and microservice compatibility. That is too broad to mix with entitlement/payment/VPN stabilization fixes.

Consequence: Non-breaking audit fixes may be tested separately, but complete audit remediation is a dedicated NestJS 11 migration with full backend build, policy tests, Docker/Dokploy deploy, and live smoke verification.

## [2026-05-02] Subscription Fetch Supports Redirect Cookies In Memory

Decision: Android subscription downloads may keep provider cookies in memory while resolving a remote subscription URL, but those cookies are not persisted and are not part of entitlement truth.

Reason: Some subscription providers gate the actual Base64/VLESS payload behind an HTTP redirect that requires returning a short-lived cookie. Without a cookie jar, the app can follow redirects forever and never receive the importable nodes.

Consequence: The subscription fetcher can interoperate with redirect-cookie providers while preserving the existing parser and raw config handling. Cookies remain host/path scoped by OkHttp matching rules and disappear with the app process; premium authorization remains enforced by backend entitlement, not by cookies.

## [2026-05-07] [Android Service Reconnect Is Same-Session Only]
- **Decision**: Let `SwimVpnService` perform bounded reconnect attempts for the already active runtime payload when the network changes or the local engine exits unexpectedly.
- **Why**: VPN stability cannot depend only on `MainViewModel`, because the UI may be closed while the foreground VPN service still owns the tunnel.
- **Impact**:
  - Reconnect uses the same raw runtime config that was already selected and authorized before the service started.
  - The service does not fetch backend premium servers, expand subscriptions, or bypass entitlement checks.
  - Manual stop maps to `STOPPED_BY_USER` and must not auto-reconnect.
  - Wider fallback to another server remains in the UI/adaptive layer where the server list and entitlement state are available.

## 2026-05-07 18:20:04 +03:00 - VPN network monitoring source of truth
- Decision: SwimVpnService reconnection logic must monitor NOT_VPN physical underlay networks, not registerDefaultNetworkCallback after the VPN is established.
- Reason: Android's default network can become the VPN itself; using it as setUnderlyingNetworks creates a self-referential underlay and hides Wi-Fi/mobile changes from reconnect logic.

## 2026-05-07 19:56:56 +03:00 - Backend security batch 1
- Decision: keep the existing AdminSession.refresh_token_hash column but store SHA-256 fingerprints of JWTs rather than plaintext tokens.
- Decision: inventory healthchecks must only probe public supplier endpoints; loopback, private, link-local, multicast/reserved, localhost, and unresolvable hosts return unhealthy without opening a socket.

## 2026-05-07 20:18:31 +03:00 - Gateway public security defaults
- Decision: keep gateway rate limiting local and dependency-free for this stabilization release.
- Reason: the immediate production risk is brute force/enumeration pressure on a small set of public routes; a minimal middleware avoids a broad dependency/config migration before release.
- Decision: Swagger is disabled by default in production and can be explicitly re-enabled with `GATEWAY_SWAGGER_ENABLED=true`.
- Decision: CORS can be restricted with `GATEWAY_CORS_ORIGINS`, but remains open if unset to avoid breaking unknown current landing/admin origins during this batch.

## 2026-05-07 21:02:44 +03:00 - Installed code is the operational truth
- Decision: when stale documentation conflicts with current code and recent worklogs, the documentation must be corrected first.
- Reason: the installed system reflects field-driven changes around freemium, supplier subscriptions, quota enforcement, Android runtime parsing, manual/Crypto payment flows, and VPN stability.
- Consequence: future security patches must start from the current flows and should not rewrite working business logic just to match older roadmap text.

## 2026-05-07 21:16:09 +03:00 - Raw Android device identity remains the product model
- Decision: keep the normalized Android device identifier as the operational `Customer.device_id` model.
- Reason: the installed app relies on it for stable customer continuity, trial anti-abuse, premium server exposure, crypt1 resolution, cancellation, and usage reporting.
- Consequence: do not plan or implement raw-to-hash migration unless the product decision changes. Security work should protect this identifier through logging discipline, API non-exposure, database/backups/secrets protection, and backend device checks.

## 2026-05-07 22:02:00 +03:00 - Sticky service restore is fresh-session only
- Decision: `SwimVpnService` may restore from `START_STICKY` only when `RuntimeStateStore` shows a fresh active state (`STARTING`, `RUNNING`, `RECONNECTING`, or `DEGRADED`).
- Reason: Android/OEM can kill and relaunch a foreground VPN service with a null intent; without this path, the service restart does not actually restore the tunnel.
- Consequence: normal boot/package-replaced restore still depends on app bootstrap revalidation. Stale snapshots, stopped states, missing VPN permission, disabled auto-connect, or missing payload do not restore.

## 2026-05-07 22:34:00 +03:00 - Runtime speed tuning must be measured and conservative
- Decision: begin speed work by removing app-generated Xray overhead that is not used by current business logic, instead of changing MTU, tun2socks buffers, or provider configs first.
- Reason: usage/quota comes from tun2socks stats, while generated Xray stats and sniffing on empty routing rules add work without clear value in the standard full-tunnel path.
- Consequence: MTU and tun2socks buffer tuning remain measurement-gated ADB tasks. Raw supplier configs and entitlement flows are unchanged.

## 2026-05-07 22:52:00 +03:00 - VPN notification is user-facing, not runtime diagnostics
- Decision: the foreground VPN notification should show only the localized running state while the tunnel/proxy is active.
- Reason: detailed runtime text belongs in logs/debug UI, not in the Android notification shade. A stable short notification is clearer and less noisy while preserving the foreground service signal.
- Consequence: service logs still keep runtime details; notification content remains minimal and translated from the user's saved language preference.
