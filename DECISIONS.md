# DECISIONS

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
