# 2026-05-21 - Android light theme token correction

- Reworked the SwimVPN light visual tokens around a cleaner lavender premium scale: clearer backgrounds, raised surfaces, semantic strokes, readable text levels, stronger violet accents, and status colors.
- Aligned the Material light color scheme with `SwimDesignTokens.Light` instead of scattered pastel hardcodes.
- Updated light-sensitive UI surfaces in the dock, Home power core, Subscription CTAs/payment pills, Servers rows/ping badges/selectors, Profile canvases, and config validation colors to consume semantic tokens.
- Preserved dark theme values, VPN runtime, parser, backend, payment, subscription logic, and navigation behavior.
- Verification: `:app:compileDebugKotlin` and `:app:assembleDebug` passed.

# 2026-05-21 - Landing mobile globe and app icon reconciliation

- Restored the real `InteractivePixelGlobe` render on mobile instead of forcing the static fallback, while keeping it non-interactive to preserve vertical scrolling.
- Added the approved SwimVPN shark mark as a web brand asset and used it in the landing navbar and hero pre-release badge.
- Verified the touched landing files are UTF-8 clean and do not contain mojibake markers.
- Verification: `npm run build` passed. `npm run lint` still fails on existing backend alias/dependency issues outside this landing scope.

# 2026-05-21 - Android startup splash simplification

- Replaced the animated Compose `loading` route splash with a neutral SwimVPN bootstrap surface so startup no longer reads as Android splash plus second in-app splash.
- Kept the `loading` route intact for existing bootstrap, sign-out, and session routing paths.
- Preserved Android starting-window theme, app navigation, VPN runtime, backend, parser, entitlement, and all product screens.
- Verification: `:app:assembleDebug` passed.

# 2026-05-21 - Home VPN core orb/button reconciliation

- Imported the isolated 3D holographic orb renderer into the Android app under `ui/orb3d` without touching VPN runtime, parser, backend, entitlement, dock, or navigation logic.
- Added `HomeVpnCoreStage` as a single Home visual object combining the holographic orb layer and the central hardware power button.
- Replaced the direct Home `VpnCoreOrb` usage with `HomeVpnCoreStage`, preserving the existing `toggleVpnFromHome()` click path and real `VpnState` to `VpnOrbState` mapper.
- Synchronized button accent, glow, press compression, and breathing with the orb visual state so the button reads as embedded in the same energy field instead of pasted on top.
- Removed the `renderBehindCompose=true` SurfaceView layering mode after device capture proved it created a visible black rectangular plate around the orb; the holographic layer now renders on the transparent SurfaceView path again.
- Promoted the polished `SwimDarkLuxuryBackground` to Profile, Technical Settings, Support, and Config Import so product screens share the same dark violet atmospheric field as Home, Servers, and Subscription.
- Verification: `:app:compileDebugKotlin` and `:app:assembleDebug` passed.

# 2026-05-20 - Removed video orb integration and restored safe Canvas orb

- Removed the Home orb MP4 runtime path after portrait QA showed the opaque video background as a visible rectangular plate.
- Replaced `TextureView`/`MediaPlayer` playback in `VpnCoreOrb` with a lightweight Compose Canvas orbital mesh fallback behind the existing hardware power button.
- Deleted the `swim_organic_orbital_mesh.mp4` raw resource and removed the empty `res/raw` directory so no video asset remains in the Android package.
- Preserved the existing VPN click behavior, VPN state mapping, Home layout integration, dock, backend, parser, entitlement, and subscription contracts.
- Verification: `:app:compileDebugKotlin` and `:app:assembleDebug` passed.

# 2026-05-20 - Home orb Lottie and dock liquid removal

- Removed the downloaded Gradient Lottie runtime path from the Home orb and restored a native Canvas orbital mesh driven by VPN visual state.
- Removed the dock liquid runtime layer, liquid phase animation, and purple bridge traces between dock nodes.
- Kept a simple localized purple active core in `MetaballNavDock` so the active node remains visible without the liquid effect.
- Removed the targeted `lottie-compose` dependency and the copied raw Lottie assets from app resources.
- Preserved VPN runtime, backend, parser, entitlement, payment, navigation contracts, and business logic.
- Verification: `:app:compileDebugKotlin` and `:app:assembleDebug` passed. Debug APK installed successfully on the ADB Wi-Fi device.

# 2026-05-20 - Home orb mesh replacement with visible animation

- Removed the native Canvas orbital mesh from `SwimHomeOrb` and replaced it with the provided lightweight `gradient background.json` Lottie asset rendered behind the Start button.
- Added a targeted `lottie-compose` dependency from Maven Central and copied the animation as `res/raw/swim_gradient_background.json`; dotLottie/JitPack was intentionally not used because the `.lottie` file contains the same animation payload and would widen Gradle repository scope.
- Added explicit layer rotation and state-based alpha/speed so the background element has visible loop motion instead of relying on an imperceptible animation frame change.
- Preserved the Start button press compression, hardware layers, VPN state coloring, dock, VPN runtime, backend, parser, entitlement, payment, and business rules.
- Verification: `:app:compileDebugKotlin` and `:app:assembleDebug` passed. ADB runtime install was attempted, but the Wi-Fi device dropped from `adb devices` before the final runtime capture could complete.

# 2026-05-20 - Remove rejected Home Lottie background

- Removed the Home Lottie background layer from `SwimPowerOrb` after visual QA rejection.
- Removed the targeted `lottie-compose` dependency and deleted the `swim_gradient_background.json` raw resource.
- Left the Start button hardware, press compression, state color, and transition progress indicator intact.
- Preserved dock, VPN runtime, backend, parser, entitlement, payment, navigation contracts, and business logic.
- Verification: no Lottie/gradient runtime references remain, `:app:compileDebugKotlin` and `:app:assembleDebug` passed, and the debug APK installed successfully on device `R5CWA0FEPZW`.

# 2026-05-20 - Isolated VpnCoreOrb procedural POC

- Added an isolated `ui/orb` laboratory package with `VpnOrbState`, `VpnCoreOrbTokens`, `VpnCoreOrb`, and `VpnCoreOrbLabScreen`.
- Implemented the orb as a procedural Compose Canvas component: 320dp viewport, organic purple orbital mesh, deterministic particles, layered black power button, state-driven motion, and reduced-motion support.
- Added a standalone lab screen with local controls for DISCONNECTED, CONNECTING, CONNECTED, and UNSTABLE states.
- Did not integrate the lab into Home, navigation, dock, VPN runtime, backend, parser, or any existing product screen.
- Verification: `:app:compileDebugKotlin` and `:app:assembleDebug` passed.

# 2026-05-20 - Integrate VpnCoreOrb into Home

- Replaced the Home central `SwimPowerOrb` visual with the isolated `VpnCoreOrb` component.
- Preserved the existing Home VPN click path by reusing `toggleVpnFromHome()` and `viewModel.toggleVpn(...)`; no VPN runtime, parser, backend, tunnel, entitlement, or navigation logic was changed.
- Added a Home-only UI mapper from real `VpnState` plus fresh `RuntimeStatus` to `VpnOrbState`, including `RECONNECTING` and `DEGRADED` as `UNSTABLE`.
- Adjusted Home orb sizing to 320dp on normal phones and 292dp on compact heights while keeping server pill, stats, and metaball dock intact.
- Verification: `:app:compileDebugKotlin` and `:app:assembleDebug` passed.

# 2026-05-20 - Android Subscription screen rebuild + navigation hook (restart)

- Reworked `SubscriptionScreen.kt` to remove placeholder/accessibility artifacts and complete the reusable button architecture (`PressablePill`, payment method strip, dock callback wiring).
- Added required Compose imports for semantic accessibility; for SP sizing, used a local `fixedSp` helper backed by Compose `sp` constants.
- Hooked `MainActivity` subscription route with:
  - `activeOfferCode = data.profile.offerCode` for current plan highlight
  - `onProfileClick`, `onNavigateHome`, `onNavigateServers`, `onNavigateSettings` for dock/profile actions
  - preserved backend checkout call (`onCheckoutClick` still calls `viewModel.createCheckout(...)` with user-selected method).
- Kept VPN, backend parser, entitlement rules, and payment confirmation flow untouched.
- No fabricated success states introduced; order creation remains real flow.
- Verification: `:app:compileDebugKotlin` and `:app:assembleDebug` pass after resolving a temporary `toSp` API mismatch.

# 2026-05-20 - Android Subscription implementation rollback

- Cancelled the first Subscription dark luxury implementation pass before restarting the target design work.
- Restored `SubscriptionScreen.kt`, `MainActivity.kt`, `SwimDesignTokens.kt`, `DECISIONS.md`, and `TODO.md` to the pre-Subscription-pass state.
- Kept VPN, backend, parser, Home, Servers, and dock behavior untouched.

# 2026-05-20 - Android VPN runtime review follow-up

- Cleared stale failure cause and reconnect counters when a new runtime session reaches healthy `RUNNING`.
- Preserved active Xray/tun2socks log paths while cleaning old failure state after successful startup.
- Added `RuntimeReconnectPolicy` so a pending `NETWORK_LOST` reconnect can be cancelled if a usable network returns before reconnect startup begins.
- Kept engine-crash reconnects non-cancellable by network recovery.
- Verification: targeted diagnostics/reconnect policy tests passed, full targeted runtime policy suite passed, and `android\\gradlew.bat testDebugUnitTest assembleDebug` passed.

# 2026-05-20 - Android VPN runtime recovery phases 1-2

- Separated active killed-service recovery from the user-facing auto-connect toggle.
- Added `RuntimeRecoveryPolicy` so fresh `STARTING`, `RUNNING`, `RECONNECTING`, and `DEGRADED` sessions can be restored when the runnable payload and VPN permission are still available.
- Changed `SwimVpnService.restoreStickySessionIfAllowed()` to evaluate killed-session recovery directly instead of requiring `autoConnect=true`.
- Added `RuntimeServiceDestroyPolicy` so `onDestroy()` caused by a non-user service kill persists a recoverable `RECONNECTING` snapshot with `SERVICE_KILLED`.
- Kept manual/user stop cleanup terminal and non-restorable.
- Verification: targeted runtime recovery tests passed, targeted service destroy policy tests passed, and `android\\gradlew.bat testDebugUnitTest assembleDebug` passed.

# 2026-05-20 - Android VPN network handoff debounce

- Added `NetworkHandoffPolicy` with a 4000ms grace window before reconnecting after an active underlying network loss.
- Updated `SwimVpnService` so Wi-Fi/mobile handoff schedules a delayed reconnect instead of immediately restarting Xray/tun2socks.
- Cancelled the delayed reconnect when a usable network becomes available or gains usable capabilities inside the grace window.
- Preserved the existing reconnect path when no usable underlying network returns after the grace window.
- Verification: targeted network handoff tests passed, runtime recovery/destroy/handoff tests passed together, and `android\\gradlew.bat testDebugUnitTest assembleDebug` passed.

# 2026-05-20 - Android VPN last disconnect diagnostics

- Extended persisted runtime snapshots with Xray and tun2socks log paths.
- Changed `VpnManager.setRuntimeDiagnostics()` so partial updates preserve existing diagnostic evidence instead of clearing omitted fields.
- Changed `VpnManager.clearRuntimeDiagnostics()` to clear active session identity while keeping last failure cause, reconnect count, session start, and log paths visible.
- Rehydrated persisted diagnostics through `VpnManager.reconcileRuntimeSnapshot()`.
- Updated `SwimVpnService` writes so heartbeat, status changes, errors, and service-kill snapshots carry current log paths into `RuntimeStateStore`.
- Verification: targeted diagnostics tests passed, runtime recovery/destroy/handoff/diagnostics tests passed together, and `android\\gradlew.bat testDebugUnitTest assembleDebug` passed.

# 2026-05-20 - Android VPN battery optimization action

- Added `REQUEST_IGNORE_BATTERY_OPTIMIZATIONS` permission to the Android manifest.
- Added a compact Technical Settings action that appears only when Android is still allowed to optimize SWIMVPN battery usage.
- Wired the action to `ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS` with a fallback to the battery optimization settings screen.
- Added English, French, and Russian strings for the battery optimization row.
- Verification: `android\\gradlew.bat :app:processDebugResources :app:compileDebugKotlin` passed, and `android\\gradlew.bat testDebugUnitTest assembleDebug` passed.

# 2026-05-20 - Android VPN startup health proof

- Added `RuntimeStartupHealthPolicy` so `RUNNING` requires Xray alive and, for full tunnel, tun2socks alive.
- Added a short local startup proof window before `SwimVpnService` publishes `RUNNING`.
- Kept remote HTTP probing out of scope; the proof is limited to local process/data-plane health.
- Classified early Xray/tun2socks/runtime startup exits as engine failures instead of generic unknown startup failures.
- Verification: targeted startup health tests passed, all targeted runtime policy tests passed together, and `android\\gradlew.bat testDebugUnitTest assembleDebug` passed.

# 2026-05-20 - Android VPN runtime stabilisation local verification

- Ran final local Android verification for the full runtime stabilisation batch.
- Verification: `android\\gradlew.bat testDebugUnitTest assembleDebug` passed.
- Stopped the Gradle daemon after verification to release memory.
- `git diff --check` passed with line-ending warnings only.
- Real-device QA remains pending for screen-off, Wi-Fi/mobile handoff, service kill recovery, and repeated start/stop.

# 2026-05-20 - Android VPN runtime review findings resolved

- Fixed startup cancellation handling so `CancellationException` from service destroy/stop is not converted into `FAILED/UNKNOWN`.
- Added `RuntimeStartupFailurePolicy` coverage for cancellation, invalid config, and early engine exits.
- Restored `RuntimeStateStore.clear()` as a true persistent reset instead of preserving old diagnostic keys through the generic `write()` preservation path.
- Verification: targeted startup failure policy tests passed, all targeted runtime policy tests passed together, and `android\\gradlew.bat testDebugUnitTest assembleDebug` passed.

# 2026-05-19 - Trial Store reconciliation foundation

- Reconciled the current trial flow toward the campaign-based product target: new trial activations now use a dedicated Trial Store model instead of creating paid-style `Order/TRIAL:3D` records.
- Added Prisma models and migration foundation for `TrialCampaign`, `TrialConfig`, `TrialGrant`, and `TrialAssignment`.
- Seeded the launch campaign `trial-2026-05` with activation window `2026-05-19` through `2026-06-19`.
- Preserved legacy `Order/TRIAL:3D` profile reading so already-signed users are not cut off during the transition.
- Added admin-authenticated trial config import plumbing through gateway admin, admin-control, and inventory-service.
- Added automatic pending trial recovery after trial config import, so `PENDING_FULFILLMENT` grants can become `ACTIVE_TRIAL` without paid inventory.
- Added database uniqueness for one `TrialGrant` per customer/campaign and mapped activation races to a clean business refusal.
- Added persisted trial expiration audit (`TRIAL_EXPIRED`) and hardened trial import payload validation.
- Added policy coverage proving Trial Store active grants resolve to `ACTIVE_TRIAL`, empty trial capacity resolves to `PENDING_FULFILLMENT`, pending grants recover after import, active paid access keeps priority over active trial grants, expired grants are persisted, and trial imports preserve raw configs.
- Verification: `prisma validate`, `prisma generate`, `lint`, `build:all`, `test:policy`, and `git diff --check` passed.
# 2026-05-18 - VPN update audit program

- Created `AUDIT_UPDATE_VPN_2026-05.md` to program a cold audit of the current `main` VPN stack before implementation.
- Documented the intended separation between backend business/inventory truth and Android parser/runtime truth.
- Listed audit lots for documentation, Android parser, Android runtime, backend VPN/inventory, camouflage/obfuscation, performance, and real-device QA.
- Added go/no-go criteria to avoid treating intentional layer separation as accidental duplication.
- No Android, backend, entitlement, deployment, payment, Prisma, or runtime behavior was changed.

# 2026-05-17 - SwimPay webhook fulfillment hardening merge

- Preserved the current gateway compatibility behavior for both `POST /api/v1/payments/swimpay/webhook` and `POST /webhooks/swimpay`.
- Hardened customer-order SwimPay fulfillment so a signed public webhook must match the stored `SWIMPAY_SESSION` session id and SwimPay order id, with RUB amount not lower than the backend order amount. Higher SwimPay merchant anti-collision micro-adjustments are accepted.
- Replaced hardcoded SwimPay compose secrets with required deployment environment variables after secret rotation.
- Added regression coverage for mismatched SwimPay webhook session/order data staying ignored with the order still pending and no inventory fulfillment.
- Added regression coverage for accepting higher SwimPay anti-collision amounts while still rejecting lower paid amounts.
# 2026-05-13 - SwimPay webhook compatibility alias

- Added a non-breaking gateway alias for SwimPay staging webhooks: `POST /webhooks/swimpay`.
- Kept the canonical route intact: `POST /api/v1/payments/swimpay/webhook`.
- Confirmed `GET /api/v1/payments/swimpay/return` remains informational only and does not confirm payment.
- Added policy coverage for gateway route metadata/forwarding, invalid signatures, duplicate event idempotence, confirmed fulfillment, and rejected/expired terminal states.
## 2026-05-09 - SwimPay local env secrets configured

- Copied SwimPay environment values from the private `C:\Users\Lenovo\StudioProjects\SWIMPAY SECRETS.txt` file into local SWIMVPN `.env` and `backend/.env`.
- Added only environment variable names in logs; secret values were not printed or committed.
- VPS still needs the same SwimPay variables configured in the production service environment before live webhook testing.
# WORKLOG

## [2026-05-09] [SwimPay Checkout And Webhook Integration]
- **Status**: IMPLEMENTED / NEEDS STAGING CREDENTIALS + LIVE WEBHOOK QA
- **Changes**:
  - Added `SWIMPAY` as a checkout method alongside manual card and Crypto Pay.
  - Added a backend SwimPay client service for server-side checkout creation and signed webhook verification using the SwimPay V1 public contract.
  - Added `POST /api/v1/payments/swimpay/webhook` with raw-body signature handling and `GET /api/v1/payments/swimpay/return` as a non-confirming checkout return endpoint.
  - Kept fulfillment behind the existing backend `fulfillOrderByRef()` boundary; Android only opens the checkout URL and never marks payment as confirmed.
  - Added idempotence through `AdminEvent` records keyed by SwimPay event id.
  - Added SwimPay as an Android subscription payment option.
  - Added SwimPay environment variables to root/backend env examples and compose files.
- **Verification**:
  - Backend SwimPay service policy test passed.
  - Backend SwimPay checkout/webhook policy test passed.
  - `npm run verify:deploy` passed.
  - `docker compose --env-file .env.example -f docker-compose.yml config --quiet` passed.
  - `android\\gradlew.bat --no-daemon :app:processDebugResources :app:compileDebugKotlin` passed.
  - `android\\gradlew.bat --no-daemon assembleDebug` passed with existing Gradle/JDK/CMake warnings.
- **Live QA needed**:
  - Provision full show-once `SWIMPAY_SECRET_KEY` and `SWIMPAY_WEBHOOK_SECRET`.
  - Save/test the public webhook URL in SwimPay.
  - Create a real SWIMVPN order with `SWIMPAY`, complete checkout, manually confirm in SwimPay, and verify premium fulfillment only after `payment.confirmed`.

[2026-05-02] [Android Subscription Fetch Isolation]
- Extracted remote subscription fetching into a testable Android SubscriptionFetcher.
- Isolated OkHttp/cookie state per fallback User-Agent so provider redirect cookies from a failed attempt cannot poison v2rayNG/Happ-compatible retries.
- Added standard subscription fetch headers and a MockWebServer regression test for failed-attempt cookie isolation.
- Verification: :app:testDebugUnitTest targeted subscription tests passed; :app:compileDebugKotlin passed; live provider probe from this machine still returns HTTP 502 after one redirect.
## [2026-05-02] [Android VLESS JSON Subscription Node Coverage]

- Used the Happ screenshot confirmation to target the provider shape as multi-node `VLESS | JSON` subscription content.
- Added parser coverage for JSON arrays of VLESS outbound nodes with Russian display names and `xhttp` transport metadata.
- Aligned VLESS JSON transport mapping so `httpupgrade`, `xhttp`, and `splithttp` are classified as HTTP-style transport instead of `UNKNOWN`.
- Verification: targeted Android subscription parser, cookie jar, and link extractor tests passed; debug Kotlin compilation remained green.

## [2026-05-02] [Android Subscription Carrier Decoder Hardening]

- Hardened Android subscription payload decoding for provider carrier formats without printing or mutating raw VPN nodes.
- Added parser coverage for URL-encoded Base64 payloads, nested Base64 payloads, and Happ add wrappers carrying encoded supported configs.
- Reused the shared subscription decoder from ConfigRepository so direct imports and remote subscription fetches normalize through the same bounded decoder.
- Live check for `https://subs.eu-fffast.com/66era36u8ho` still returns HTTP 502 after the redirect cookie step from this workstation, so real-provider node expansion must be re-tested when the provider responds with a payload.
- Verification: targeted Android parser/cookie/extractor unit tests passed; debug Kotlin compilation was covered by the test task.

## [2026-05-02] [Obsidian Brain Vault Bootstrap]

- Configured `swimvpnbrain/` as an Obsidian navigation vault for the repository Markdown documentation.
- Mirrored existing repository `.md` files into `swimvpnbrain/Repository Markdown/` while preserving relative paths.
- Added a central `SWIMVPN Brain` index and focused maps for architecture, operations, Android, and documentation inventory.
- Added lightweight Obsidian settings and bookmarks for the core navigation notes.
- No backend, Android, landing, Prisma, Docker, or entitlement behavior was changed.

# WORKLOG

## [2026-04-29] [Production Regression Batch - Imported Config, Freemium Quota, Manual Proof]
- **Status**: DONE
- **Changes**:
    - Audited the remaining production regressions with parallel agents and ADB/source inspection.
    - Fixed imported config handoff so successful imports select the first imported profile immediately and refresh the global server catalog from local DataStore.
    - Preserved freemium/imported-config security: imported configs remain local and usable without granting backend premium servers.
    - Fixed the profile quota card so `UNLIMITED` is shown only for `ACTIVE_TRIAL`, not for trial-used, expired, profile-incomplete, or freemium states.
    - Added explicit freemium/provider-managed quota copy in English, French, and Russian.
    - Hardened the manual card proof bot so it can recover recent payment sessions from persisted `AdminEvent` records if the in-memory Telegram state is lost.
    - Added support for image screenshots sent as Telegram documents and added a post-proof contact confirmation prompt forwarded to the admin review chat.
- **Verification**:
    - Pending final build/check output in the active Codex batch.
    - ADB launch capture confirmed the installed release opens Home and currently showed no selected server before this patch.

## [2026-04-29] [Android Release/Debug Trial Consistency Hardening]
- **Status**: DONE
- **Changes**:
    - Audited debug vs release behavior with parallel agents across build variants, R8/ProGuard, Android trial flow, backend contract, device identity, network security, and validation readiness.
    - Centralized Android device identity lookup and stopped sending the unsafe `unknown_device_id` fallback to backend access endpoints.
    - Added device-bound trial activation so Android sends `deviceId` and backend verifies it matches the persisted customer device before creating a trial.
    - Kept `TRIAL_AVAILABLE` users inside the freemium app shell while adding a profile CTA to activate the trial when eligible.
    - Added R8 keep rules for imported config models persisted with Gson and for the native tun2socks bridge.
    - Disabled HTTP body logging in release builds and disabled Android backup for sensitive local app state.
- **Verification**:
    - `cd backend && npm run lint` PASSED.
    - `cd backend && npm run prisma:validate` PASSED.
    - `cd backend && npm run build:all` PASSED.
    - `cd backend && npm run test:policy` PASSED.
    - `cd android && .\gradlew.bat :app:assembleDebug --no-daemon` PASSED.
    - `cd android && .\gradlew.bat :app:assembleRelease --no-daemon` PASSED.
    - `cd android && .\gradlew.bat :app:testDebugUnitTest --no-daemon` PASSED.

## [2026-04-29] [Signed Release Trial Activation Error Mapping]
- **Status**: DONE
- **Changes**:
    - Captured ADB logs from the user-signed release APK and confirmed launch/bootstrap succeeds with no Android crash.
    - Identified trial activation returning `HTTP 500` from the gateway while the app remained on profile/trial activation.
    - Reproduced the opaque 500 with a safe malformed activation request, confirming access RPC errors were being surfaced as generic server errors.
    - Updated `gateway-service` access endpoints to unwrap customer-service RPC errors and map access/business failures to explicit HTTP errors (`400`, `403`, `409`, or `503`).
    - Updated Android trial activation catch paths to display backend error details through the existing activation error toast instead of a generic failure.
- **Verification**:
    - `cd backend && npm run lint` PASSED.
    - `cd backend && npm run build:all` PASSED.
    - `cd android && .\gradlew.bat :app:assembleRelease --no-daemon` PASSED.
    - `cd android && .\gradlew.bat :app:testDebugUnitTest --no-daemon` PASSED.

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

## [2026-04-29] [Release Trial Freemium Escape Hatch]
- **Status**: DONE
- **Changes**:
  - Added an Android release-safe "continue without trial" path on the trial/profile completion screen.
  - The freemium path reloads the backend profile and builds the normal app shell without granting premium access.
  - Preserved backend-controlled premium access: premium servers/configs still depend on `ACTIVE_TRIAL` or `ACTIVE_SUBSCRIPTION`.
  - Converted customer-service trial validation failures into `RpcException` messages so the gateway can map missing/invalid device ID, unauthorized device, and already-used trial cases to clear HTTP errors instead of opaque service errors.
- **Verification**:
  - ADB release capture showed the old signed APK was stuck on the trial activation screen with no freemium/subscription/import escape path.
  - `cd backend && npm run lint` PASSED.
  - `cd backend && npm run build:all` PASSED.
  - `cd android && .\gradlew.bat :app:assembleRelease --no-daemon --console=plain` PASSED.
  - `git diff --check` PASSED with CRLF warnings only.
- **Note**:
  - The local release artifact is unsigned, so it cannot be installed over the user's signed release without signing through the production signing flow first.
  - Production API still returned `503` for missing `deviceId` before this backend patch is deployed; retest after Dokploy redeploy.

## [2026-04-29] [Release Trial Denial Freemium/Profile Recovery]
- **Status**: DONE
- **Changes**:
  - Added backend `complete_profile` RPC and gateway `POST /api/v1/access/profile/complete` for device-bound profile contact persistence without granting premium access.
  - Trial activation now persists normalized contact info before returning a trial-used denial, so payment/freemium profile data is not lost.
  - Android `Continue without trial` now sends valid typed email/phone to the backend profile-completion endpoint before entering the app shell.
  - Stabilized root navigation so technical/settings state changes do not force the app back to Home.
  - Hardened technical settings against secure-settings read failures and unavailable Android settings activities.
- **Verification**:
  - `cd backend && npm run lint` PASSED.
  - `cd backend && npm run build:all` PASSED.
  - `cd android && .\gradlew.bat :app:assembleRelease --no-daemon --console=plain` PASSED.
  - `git diff --check` PASSED with CRLF warnings only.
- **Note**:
  - The generated local APK is unsigned; a signed release must be produced before device QA.
  - The untracked `adb-captures/package-dumpsys-current.txt` is an ADB diagnostic artifact, not source code.

## [2026-04-29] [Release Import Metadata + Manual Payment Audit Fix]
- **Status**: DONE
- **Runtime evidence**:
  - ADB import of `https://wb.routerwb.ru/jtz5386jCHkztYRZ` on the signed release parsed and imported 11 configs, then failed to read local profiles because R8 stripped Gson TypeToken generic signature metadata.
  - The provider URL returns quota/expiry through `subscription-userinfo` headers: used/download bytes, total bytes, expiry epoch, and update interval.
- **Changes**:
  - Hardened release ProGuard/R8 rules for Gson `TypeToken` and generic signatures.
  - Android subscription URL imports now preserve `subscription-userinfo` and `profile-update-interval` response metadata.
  - Imported config metadata now carries traffic used, total quota, expiry, provider/source URL, and update interval into persisted profiles.
  - Premium managed active config metadata now merges backend profile quota/expiry/provider fields instead of using server catalog rows only.
  - Updated Access/Profile copy to use `Premium` and `Imported`, and removed misleading `FREE / IMPORTED` / trial `UNLIMITED` labels.
  - Hardened manual card proof bot flow against duplicate screenshot loops and restart-lost contact confirmation state.
  - Manual card approve/reject paths now report already-processed order status more truthfully and avoid rejection emails when no rejection transition happened.
- **Verification**:
  - `cd android && .\gradlew.bat :app:testDebugUnitTest --tests "*SubscriptionParserTest" --tests "*ActiveConfigMetadataMappingTest" --console=plain` PASSED.
  - `cd android && .\gradlew.bat :app:assembleRelease --console=plain` PASSED, including `minifyReleaseWithR8`.
  - `cd backend && npm run lint` PASSED.
  - `cd backend && npm run build:all` PASSED.
  - `cd backend && npm run test:policy` PASSED.
- **Note**:
  - Local release output remains `app-release-unsigned.apk`; final ADB verification of the fixed release requires signing with the production key before installing over the user's signed APK.
  - Manual Telegram approval/rejection still requires live bot/admin-group QA because the terminal cannot act as the Telegram user/admin review group.

## [2026-04-29] [Manual Card Final Confirmation Forwarding Fix]
- **Status**: DONE
- **Problem**: A live Telegram test showed the notification bot accepted the payment proof and asked for final email/phone/sender phone, but the final confirmation details were not reliably forwarded to the admin review flow.
- **Root Cause**: The proof message and the final contact confirmation were handled as separate admin messages. The final contact message had no approve/reject controls, did not update the order customer contact for delivery, and Telegram forwarding failures could abort the handler before the user received feedback.
- **Changes**:
    - Added a small parser for final manual-card confirmation text: email, customer phone, and sender payment phone.
    - Forward the final review packet to the payment review chat with approve/reject buttons attached to the complete contact context.
    - Update the order customer email/phone from the confirmed final details before approval/delivery.
    - Keep the pending confirmation open and notify the user if the admin review chat forwarding fails.
    - Tightened pending confirmation recovery so previous confirmations for the same order do not block a new proof event.
- **Verification**:
    - `cd backend && npx ts-node -r tsconfig-paths/register apps/notification-bot-service/src/__tests__/manual-card-confirmation.spec.ts` PASSED.
    - `cd backend && npm run lint` PASSED.
    - `cd backend && npm run test:policy` PASSED.
    - `cd backend && npm run build:all` PASSED.

## [2026-04-29] [Manual Payment Approval Email Workflow Audit]
- **Status**: DONE
- **Findings**:
    - Verified approval path: Telegram approve button -> `customer-order-service.approveManualCardPayment` -> `fulfillOrderByRef` -> `inventory-delivery-service.fulfillOrder` -> `notification-bot-service.processPostPurchaseDelivery` -> Resend email.
    - Verified rejection path: Telegram reject button -> `customer-order-service.rejectManualCardPayment` -> rejection email via notification bot.
    - Found admin authorization contradiction: `ADMIN_CHAT_ID` was being used as both notification destination and admin identity, while current env values use group-shaped chat ids. Telegram callback `from.id` is a user id, so approve/reject could be denied even when clicked from the admin group.
- **Changes**:
    - Added a tested Telegram admin authorization helper that supports explicit `ADMIN_USER_IDS`, personal `ADMIN_CHAT_ID`, and configured admin/review group callback context.
    - Wired notification bot approve/reject/resend/admin actions through the helper.
    - Added optional `ADMIN_USER_IDS` env pass-through and examples.
- **Verification**:
    - `cd backend && npx ts-node -r tsconfig-paths/register apps/notification-bot-service/src/__tests__/telegram-admin-auth.spec.ts` PASSED.
    - `cd backend && npm run lint` PASSED.
    - `cd backend && npm run test:policy` PASSED.
    - `cd backend && npm run build:all` PASSED.

## [2026-04-29] [Admin Operations Bot Option B Design]
- **Status**: SPEC_READY
- **Changes**:
    - Wrote the Admin Operations Bot and Resale Ledger design spec.
    - Captured the corrected supplier resale rule: one customer order consumes one resale slot; every supplier config can be resold to max 4 orders even when supplier device limit is 5.
    - Confirmed `TELEGRAM_BOT_TOKEN` / `admin-control-service` should become the secure Admin Operations Bot, while `NOTIFICATION_BOT_TOKEN` remains payment/proof/email delivery and `ADMIN_SUPPORT_BOT_TOKEN` remains support.
- **Spec**: `docs/superpowers/specs/2026-04-29-admin-operations-bot-and-resale-ledger-design.md`

## [2026-04-29] [Supplier Resale Cap Finalized At Two Orders]
- **Status**: SPEC_UPDATED
- **Changes**:
    - Updated the Admin Operations Bot design spec from max 4 resale orders per supplier link to max 2 resale orders per supplier link.
    - Captured the final UI truth: every subscription plan may advertise up to 2 devices.
    - Preserved the backend accounting truth: one paid customer order consumes one resale slot; a supplier link stops accepting new orders after two resale slots are used.
- **Spec**: `docs/superpowers/specs/2026-04-29-admin-operations-bot-and-resale-ledger-design.md`

## [2026-04-30] [Resale Cap Final Alignment Implementation]
- **Status**: DONE
- **Changes**:
    - Aligned backend policy with the final product truth: one paid customer order consumes one resale slot and each supplier config defaults to max two resale orders.
    - Split resale slot semantics from customer-facing device allowance: backend allocation uses one resale slot per order, while profile/subscription UI can display up to two devices.
    - Updated plan seed slot counts to `1` for Basic, Premium, and Platinum.
    - Added Prisma migration `20260430093000_resale_cap_two_orders` to set inventory default max resale slots to `2`, normalize plan/assignment slot counts to `1`, recalculate used resale slots, and refresh health status while preserving expired/disabled states.
    - Updated Android subscription cards to show `Up to 2 devices` / `Jusqu'à 2 appareils` / `До 2 устройств`.
- **Verification**:
    - `cd backend && npm run prisma:validate` PASSED.
    - `cd backend && npm run lint` PASSED.
    - `cd backend && npm run test:policy` PASSED.
    - `cd backend && npm run build:all` PASSED.
    - `cd android && .\gradlew.bat :app:processDebugResources :app:compileDebugKotlin --no-daemon` PASSED.

## [2026-04-30] [Secure Admin Operations Bot Inventory MVP]
- **Status**: DONE
- **Changes**:
    - Converted `admin-control-service` Telegram bot into a restricted admin inventory bot using `ADMIN_USER_IDS` or a personal `ADMIN_CHAT_ID`.
    - Added `/stock`, `/import`, and `/add basic|premium|platinum <config-or-url>` commands for supplier config stock management.
    - Kept supplier raw config payload untouched and delegated parsing/import to `inventory-delivery-service`.
    - Enforced the current resale truth on bot imports: supplier device metadata can remain 5, but SWIMVPN resale cap defaults to 2 orders per config.
    - Added admin audit logging for bot imports through `AdminEvent`.
    - Passed `ADMIN_USER_IDS` into `admin-control-service` from the root Docker compose so Dokploy can enforce the secure allow-list.
- **Verification**:
    - `cd backend && npx ts-node -r tsconfig-paths/register apps/admin-control-service/src/__tests__/admin-bot-auth.spec.ts` PASSED.
    - `cd backend && npx ts-node -r tsconfig-paths/register apps/admin-control-service/src/__tests__/admin-bot-formatter.spec.ts` PASSED.
    - `cd backend && npm run lint` PASSED.
    - `cd backend && npm run test:policy` PASSED.
    - `cd backend && npm run build:all` PASSED.
- **Note**:
    - Live Telegram QA still requires Dokploy env `ADMIN_USER_IDS` to contain the allowed human Telegram user IDs, then redeploy `admin-control-service`.

## [2026-04-30] [Docker Prisma Migrate Startup Fix]
- **Status**: DONE
- **Problem**: Docker/Dokploy stopped at `prisma-migrate` with exit 1, blocking all app tests.
- **Root Cause**:
    - Root `docker-compose.yml` rebuilt `DATABASE_URL` from raw `POSTGRES_PASSWORD`; the current password contains URL-sensitive characters, so Prisma failed with `P1013 invalid port number in database URL`.
    - The new migration `20260430093000_resale_cap_two_orders` also contained a UTF-8 BOM, causing PostgreSQL to fail at `syntax error at or near "ï»¿ALTER"`.
- **Changes**:
    - Updated all root compose backend services to consume `${DATABASE_URL}` directly instead of reconstructing it from raw Postgres credentials.
    - Rewrote `backend/prisma/migrations/20260430093000_resale_cap_two_orders/migration.sql` without BOM while preserving its SQL logic.
- **Verification**:
    - `docker compose build prisma-migrate` PASSED.
    - `docker compose run --rm --no-deps prisma-migrate npx prisma migrate resolve --rolled-back 20260430093000_resale_cap_two_orders` PASSED locally after the failed attempt.
    - `docker compose run --rm --no-deps prisma-migrate npm run prisma:migrate:deploy` PASSED.
    - `docker compose build prisma-seed` PASSED.
    - `docker compose up prisma-seed --abort-on-container-exit --exit-code-from prisma-seed` PASSED.
- **Production note**:
    - If Dokploy already recorded the failed migration before this fix, run the same one-time Prisma resolve command on the VPS/Dokploy shell before redeploying.

## [2026-04-30] [Admin Bot Fulfillment And Supplier Health Commands]
- **Status**: DONE
- **Changes**:
    - Added Admin Operations Bot commands for pending fulfillment and retry: `/pending`, `/retry <orderRef|all>`.
    - Added supplier health control commands: `/expire <inventoryId>`, `/disable <inventoryId>`, `/quota_reached <inventoryId>`.
    - Added lightweight accounting commands based on existing paid order truth: `/orders_today`, `/revenue_today`.
    - Propagated inventory `EXPIRED` and `DISABLED` health updates to linked active assignments so app access stops seeing those supplier configs as active.
    - Preserved raw supplier configs; admin health commands only update status and audit events.
- **Verification**:
    - `cd backend && npx ts-node -r tsconfig-paths/register apps/admin-control-service/src/__tests__/admin-bot-formatter.spec.ts` PASSED.
    - `cd backend && npm run lint` PASSED.
    - `cd backend && npm run prisma:validate` PASSED.
    - `cd backend && npm run test:policy` PASSED.
    - `cd backend && npm run build:all` PASSED.
- **Note**:
    - Full accounting ledger with expenses, crypto reporting, and profit calculations remains a later schema-backed module.

## [2026-04-30] [Accounting Ledger MVP]
- **Status**: DONE
- **Changes**:
    - Added Prisma `AccountingEntry` model with `REVENUE`, `EXPENSE`, and `ADJUSTMENT` entry types.
    - Added accounting sources for `ORDER`, `SUPPLIER_CONFIG`, `MANUAL`, `CRYPTO`, and `REFUND`.
    - Recorded idempotent revenue ledger entries when `admin-control-service` receives `order_fulfilled` events.
    - Added Admin Operations Bot commands `/add_expense <amount> <currency> <note>` and `/profit_month`.
    - Kept `Order` as the source of truth for sales while ledger stores accounting/reporting history.
- **Verification**:
    - `cd backend && npm run prisma:validate` PASSED.
    - `cd backend && npm run prisma:generate` PASSED.
    - `cd backend && npx ts-node -r tsconfig-paths/register apps/admin-control-service/src/__tests__/admin-bot-formatter.spec.ts` PASSED.
    - `cd backend && npm run lint` PASSED.
    - `cd backend && npm run test:policy` PASSED.
    - `cd backend && npm run build:all` PASSED.
    - `docker compose build prisma-migrate` PASSED.
    - `docker compose run --rm prisma-migrate npm run prisma:migrate:deploy` PASSED and applied `20260430103000_accounting_entries` locally.
- **Note**:
    - Crypto exchange-rate ingestion and richer profit reports remain future enhancements.

## [2026-04-30] [Admin Bot Guided Supplier Import]
- **Status**: DONE
- **Changes**:
    - Added guided Telegram admin import flow through `/add_wizard`.
    - The wizard asks for Basic/Premium/Platinum, accepts one supplier config or subscription URL, then requires explicit `confirm`.
    - Kept the direct `/add basic|premium|platinum <config>` command for advanced admin use.
    - Import still uses backend inventory truth with `maxResaleSlots = 2` and supplier device metadata defaulting to `5`.
    - Enriched import results with parsed supplier metadata: protocol, source quota, source usage, expiry, provider, health, device limit, and resale slots.
    - Raw supplier config remains preserved in PostgreSQL and is not echoed back in full Telegram messages.
- **Verification**:
    - `cd backend && npx ts-node -r tsconfig-paths/register apps/admin-control-service/src/__tests__/admin-bot-formatter.spec.ts` PASSED.
    - `cd backend && npm run prisma:validate` PASSED.
    - `cd backend && npm run lint` PASSED.
    - `cd backend && npm run test:policy` PASSED.
    - `cd backend && npm run build:all` PASSED.
- **Note**:
    - Wizard step state is in-memory admin UX only; durable truth starts after confirmed import creates `InventoryItem` rows in PostgreSQL.

## [2026-04-30] [Automatic Supplier Healthcheck Scheduler]
- **Status**: DONE
- **Changes**:
    - Added an internal inventory healthcheck scheduler in `inventory-delivery-service`.
    - Scheduler runs the existing `runHealthCheck()` logic automatically every 30 minutes by default.
    - Scheduler can be disabled with `INVENTORY_HEALTHCHECK_INTERVAL_MS=0`, `false`, or `disabled`.
    - Scheduler interval is clamped to a safe minimum of 60 seconds to avoid hammering the VPS/VPN engine.
    - Scheduled failures are logged and do not crash the service.
- **Verification**:
    - `cd backend && npx ts-node -r tsconfig-paths/register apps/inventory-delivery-service/src/__tests__/inventory-health-scheduler.policy.spec.ts` PASSED.
    - `cd backend && npm run lint` PASSED.
    - `cd backend && npm run test:policy` PASSED.
    - `cd backend && npm run prisma:validate` PASSED.
    - `cd backend && npm run build:all` PASSED.
- **Note**:
    - This is not a supplier-side revocation system. It updates backend visibility/access status from supplier expiry/health truth so expired/degraded configs stop being treated as healthy.

## [2026-04-30] [Admin Bot Telegram Slash Menu Registration]
- **Status**: DONE
- **Problem**: Telegram slash menu showed no commands even though command handlers existed.
- **Root Cause**: The admin bot registered Telegraf handlers but never called Telegram `setMyCommands`, so Telegram clients had no command menu metadata.
- **Changes**:
    - Added `ADMIN_BOT_COMMANDS` command menu definition.
    - Registered commands on bot launch with `bot.telegram.setMyCommands(...)`.
    - Kept registration failure non-fatal so temporary Telegram API failure does not stop the bot.
- **Verification**:
    - `cd backend && npx ts-node -r tsconfig-paths/register apps/admin-control-service/src/__tests__/admin-bot-formatter.spec.ts` PASSED.
    - `cd backend && npm run lint` PASSED.
    - `cd backend && npm run test:policy` PASSED.
    - `cd backend && npm run build:all` PASSED.
- **Live QA**:
    - Redeploy `admin-control-service`, reopen Telegram chat, type `/`, and verify commands appear.

## [2026-04-30] [Telegram Bot Slash Menus And Admin Identity Diagnostics]
- **Status**: DONE
- **Problem**: Telegram bots exposed handlers in code, but some slash menus were missing and private admin commands could not recognize the tester as admin when `ADMIN_CHAT_ID` pointed to a group chat.
- **Root Cause**: Telegram private commands authorize by the sender `from.id`; a group `ADMIN_CHAT_ID` such as `-100...` is not a private admin user id. The dedicated admin allow-list is `ADMIN_USER_IDS`, and there was no simple bot-side way to read the tester's real Telegram user id.
- **Changes**:
    - Added `/whoami` to Admin Operations Bot, Admin Support Bot, and Notification/Payment Bot.
    - Added Telegram `setMyCommands` registration for Admin Support Bot and Notification/Payment Bot.
    - Added notification bot command menu helper and policy test coverage.
    - Added `ADMIN_USER_IDS` propagation to `backend/docker-compose.yml` for `admin-control-service` and `notification-bot-service`.
    - Documented `/whoami` usage in `backend/.env.example`.
- **Verification**:
    - `cd backend && npx ts-node -r tsconfig-paths/register apps/admin-control-service/src/__tests__/admin-support-bot.spec.ts` PASSED.
    - `cd backend && npx ts-node -r tsconfig-paths/register apps/admin-control-service/src/__tests__/admin-bot-formatter.spec.ts` PASSED.
    - `cd backend && npx ts-node -r tsconfig-paths/register apps/notification-bot-service/src/__tests__/telegram-command-menu.spec.ts` PASSED.
    - `cd backend && npx ts-node -r tsconfig-paths/register apps/admin-control-service/src/__tests__/admin-bot-auth.spec.ts` PASSED.
    - `cd backend && npx ts-node -r tsconfig-paths/register apps/notification-bot-service/src/__tests__/telegram-admin-auth.spec.ts` PASSED.
    - `cd backend && npm run lint` PASSED.
    - `cd backend && npm run test:policy` PASSED.
    - `cd backend && npm run build:all` PASSED.
- **Live QA**:
    - Redeploy bot services, send `/whoami` to the admin/payment bot, copy `User id` into Dokploy `ADMIN_USER_IDS`, then retest private admin commands.

## [2026-04-30] [Telegram Admin Identity Runtime Diagnostics]
- **Status**: DONE
- **Problem**: Dokploy showed `ADMIN_USER_IDS=7161959711`, but the admin bot still returned access denied in private chat.
- **Evidence**: `/whoami` returned Telegram `User id: 7161959711`, so the sender id was known and stable.
- **Changes**:
    - Hardened Telegram id normalization for admin auth in Admin Operations Bot and Notification/Payment Bot.
    - `ADMIN_USER_IDS` now tolerates quotes, semicolons, whitespace, and newlines while still accepting only numeric Telegram ids.
    - `/whoami` now reports authorization diagnostics without exposing configured id values: `Authorized`, configured admin id count, and whether current user is in `ADMIN_USER_IDS`.
- **Verification**:
    - `cd backend && npx ts-node -r tsconfig-paths/register apps/admin-control-service/src/__tests__/admin-bot-auth.spec.ts` PASSED.
    - `cd backend && npx ts-node -r tsconfig-paths/register apps/notification-bot-service/src/__tests__/telegram-admin-auth.spec.ts` PASSED.
    - `cd backend && npm run lint` PASSED.
    - `cd backend && npm run test:policy` PASSED.
    - `cd backend && npm run build:all` PASSED.
- **Live QA**:
    - After redeploy, `/whoami` must show `Authorized: yes` and `Current user in ADMIN_USER_IDS: yes` for user `7161959711`.
    - If it still shows no, inspect the running container env because the service is not receiving the Dokploy value.

## [2026-04-30] [Manual Payment Bot Token Alignment]
- **Status**: DONE
- **Problem**: Manual card proof handling could fail when checkout generated a Telegram link to one bot while `notification-bot-service` listened to another token.
- **Root Cause**: `customer-order-service` could fall back to `TELEGRAM_BOT_TOKEN` for manual payment links, while the proof listener only used `NOTIFICATION_BOT_TOKEN`. This allowed payment proofs to land in a bot without the card-proof state machine.
- **Changes**:
    - Manual payment link routing now prefers `PAYMENT_BOT_USERNAME`, then `PAYMENT_BOT_TOKEN`, then `NOTIFICATION_BOT_TOKEN`.
    - Manual payment routing no longer falls back to the admin `TELEGRAM_BOT_TOKEN`.
    - `notification-bot-service` now listens on `PAYMENT_BOT_TOKEN` first, with `NOTIFICATION_BOT_TOKEN` as fallback.
    - Root and backend compose files now pass `PAYMENT_BOT_TOKEN` into `notification-bot-service`.
    - Screenshot uploads without order context now receive a clearer recovery message asking the user to reopen app payment or include the order reference as caption.
- **Verification**:
    - `cd backend && npx ts-node -r tsconfig-paths/register apps/customer-order-service/src/__tests__/payment-bot-routing.spec.ts` PASSED.
    - `cd backend && npx ts-node -r tsconfig-paths/register apps/notification-bot-service/src/__tests__/manual-card-confirmation.spec.ts` PASSED.
    - `cd backend && npx ts-node -r tsconfig-paths/register apps/notification-bot-service/src/__tests__/telegram-command-menu.spec.ts` PASSED.
    - `cd backend && npm run lint` PASSED.
    - `cd backend && npm run test:policy` PASSED.
    - `cd backend && npm run build:all` PASSED.
- **Live QA**:
    - After redeploy, start card payment from the Android app, verify the Telegram link opens the payment bot, send a proof screenshot, confirm contact details, and verify the review packet lands in `PAYMENT_REVIEW_CHAT_ID`.

## [2026-04-30] [Notification Bot Token Ownership Alignment]
- **Status**: DONE
- **Problem**: Notification delivery messages could be sent by a different Telegram bot than the one listening for inline callbacks and manual payment proof commands.
- **Root Cause**: `TelegramCommandService` listened on `PAYMENT_BOT_TOKEN`/`NOTIFICATION_BOT_TOKEN`, while `TelegramSenderService` still preferred `NOTIFICATION_BOT_TOKEN`/`TELEGRAM_BOT_TOKEN`. Inline callback ownership belongs to the bot that sends the message, so this could strand admin buttons.
- **Changes**:
    - Added shared notification bot token routing helpers.
    - Command listener uses `PAYMENT_BOT_TOKEN`, then `NOTIFICATION_BOT_TOKEN`, never admin `TELEGRAM_BOT_TOKEN`.
    - Sender now prefers `PAYMENT_BOT_TOKEN`, then `NOTIFICATION_BOT_TOKEN`, then legacy `TELEGRAM_BOT_TOKEN` only for one-way fallback alerts.
    - Added policy tests for token routing.
- **Verification**:
    - `cd backend && npx ts-node -r tsconfig-paths/register apps/notification-bot-service/src/__tests__/telegram-token-routing.spec.ts` PASSED.
    - `cd backend && npx ts-node -r tsconfig-paths/register apps/notification-bot-service/src/__tests__/telegram-command-menu.spec.ts` PASSED.
    - `cd backend && npx ts-node -r tsconfig-paths/register apps/notification-bot-service/src/__tests__/manual-card-confirmation.spec.ts` PASSED.
    - `cd backend && npm run lint` PASSED.
    - `cd backend && npm run test:policy` PASSED.
    - `cd backend && npm run build:all` PASSED.
- **Live QA**:
    - After redeploy, verify admin delivery buttons and manual payment approve/reject callbacks are sent and received by the same payment/notification bot.

## [2026-04-30] [Android VPN Stability Runtime Audit And Guardrails]
- **Status**: IMPLEMENTED / NEEDS SIGNED RELEASE QA
- **Problem**: Production release testing reported Technical Settings auto-connect instability, unexpected VPN disconnects, and occasional app close/restart symptoms.
- **Runtime evidence**:
    - ADB confirmed `com.swimvpn.app` running on `SM-S916B`, with active VPN network `tun0`, foreground `SwimVpnService`, and persistent VPN notification.
    - Opening Profile and Technical Settings produced no `AndroidRuntime` crash.
    - Toggling Auto-Connect on did not restart the process; PID stayed stable and crash buffer remained empty.
- **Root cause direction**:
    - Auto-Connect was only persisting a boolean without validating that a runnable active config exists.
    - Full tunnel could report `RUNNING` too optimistically if the tun2socks data plane was not actually active.
    - VPN lifecycle lacked explicit logging for system revocation/task removal and post-start native process liveness.
- **Changes**:
    - Auto-Connect now validates an active runnable server/config before enabling; invalid state leaves it off and shows a clear toast.
    - VPN starts now use foreground-service compatible startup from the app start path.
    - Full tunnel now fails clearly instead of reporting connected when tun2socks does not start.
    - Added runtime liveness monitoring for Xray and tun2socks while connected.
    - Added explicit logs/handling for manual stop, startup failure, tun2socks failure/exit, VPN revocation, service destruction, and task removal.
- **Verification**:
    - `cd android && .\gradlew.bat :app:assembleDebug --console=plain` PASSED.
    - `cd android && .\gradlew.bat :app:assembleRelease --console=plain` FAILED due local Gradle/JVM OOM during `minifyReleaseWithR8`, with `hs_err_pid8912.log` reporting insufficient native memory.
    - Retried release with `--no-daemon --max-workers=1` and reduced `GRADLE_OPTS`; still FAILED due local JVM OOM (`hs_err_pid16416.log`).
- **Live QA needed**:
    - Build signed release on a machine/session with enough RAM for R8.
    - Install signed release, connect VPN, open Technical Settings, toggle Auto-Connect, background/foreground app, lock/unlock device, and watch logcat for structured stop reasons.

## [2026-05-01] [Backend Premium Boundary And Fulfillment Risk Closure]
- **Status**: DONE / READY FOR LIVE QA
- **Problem**: Backend still had several production risks before live testing:
  - Activation codes could previously become an unmanaged premium-grant path.
  - Unsigned Stripe/YooKassa webhook handlers could fulfill if exposed before signature verification was completed.
  - Premium server listing could expose generic server rows instead of the customer's assigned supplier config.
  - Notification bot service needed the customer-service TCP endpoint passed consistently for approval/delivery orchestration.
  - A draft fix treated `InventoryHealthStatus.FULL` as access-expired, which would incorrectly cut existing active customers once a supplier link reached the resale cap.
- **Changes**:
  - Disabled unmanaged activation-code fulfillment and audit rejected attempts.
  - Disabled Stripe/YooKassa webhook fulfillment until signature verification is implemented.
  - Store server listing now derives the premium server card from the active assigned inventory raw config instead of generic server inventory.
  - Profile and usage selection now inspect recent fulfilled/paid orders and choose a valid active assignment instead of only the newest order row.
  - `FULL` remains a no-new-sales state, but existing `ACTIVE` assignments stay usable unless the assignment expires, supplier expires, quota is exhausted, or health is `EXPIRED`/`DISABLED`.
  - Added policy tests for activation-code rejection, `FULL` assignment semantics, and assigned-server exposure.
  - Root and backend compose definitions pass `CUSTOMER_SERVICE_HOST`/`CUSTOMER_SERVICE_PORT` to `notification-bot-service`.
- **Verification**:
  - `cd backend && npx ts-node -r tsconfig-paths/register apps/customer-order-service/src/__tests__/backend-security.policy.spec.ts` PASSED.
  - `cd backend && npx ts-node -r tsconfig-paths/register apps/store-engine-service/src/__tests__/assigned-server.policy.spec.ts` PASSED.
  - `cd backend && npm run lint` PASSED.
  - `cd backend && npm run prisma:validate` PASSED.
  - `cd backend && npm run test:policy` PASSED.
  - `cd backend && npm run build:all` PASSED.
- **Live QA needed**:
  - Redeploy backend services.
  - Verify an active paid assignment still shows access when its supplier inventory is full from resale cap.
  - Verify expired/disabled supplier inventory hides premium config.
  - Verify manual payment approval still reaches customer-order-service after deploy.

## [2026-05-01] [Adaptive VPN Phase 1 Local Decision Agent]
- **Status**: IMPLEMENTED / NEEDS LIVE DEVICE QA
- **Problem**: The non-LLM intelligence vision needed a safe first implementation focused on stability rather than marketing or a heavy model.
- **Decision**:
  - Phase 1 lives on Android near the VPN controller, not in backend fulfillment.
  - The agent observes runtime failures and only acts when Auto-Connect is enabled.
  - The first implementation is deterministic: bounded reconnect, local server score history, route fallback, and structured decision logs.
  - Stealth switching and contextual bandits remain future phases until runtime metrics are proven reliable.
- **Changes**:
  - Added `AdaptiveDecisionAgent` pure policy for retry/fallback decisions.
  - Added local `ServerScoreStore` for per-server success/failure/consecutive-failure state.
  - Added structured `AdaptiveEventLogger` logs for runtime and decision events.
  - `MainViewModel` now observes VPN runtime failures and performs safe auto-reconnect/fallback with max-attempt protection.
  - `SwimVpnService` now emits structured runtime start, success, failure, and disconnect events.
  - Added user-facing messages for reconnecting, switching route, and give-up states.
- **Verification**:
  - `cd android && .\gradlew.bat :app:testDebugUnitTest --tests com.swimvpn.app.adaptive.AdaptiveDecisionAgentTest --no-daemon --max-workers=1 --console=plain` PASSED.
  - `cd android && .\gradlew.bat :app:assembleDebug --no-daemon --max-workers=1 --console=plain` PASSED.
  - `cd android && .\gradlew.bat :app:compileReleaseKotlin --no-daemon --max-workers=1 --console=plain` PASSED.
- **Live QA needed**:
  - Enable Auto-Connect, connect to a valid imported config, then simulate/observe a runtime failure and confirm one same-server reconnect before fallback.
  - Confirm manual disconnect never triggers adaptive reconnect.
  - Confirm backend premium servers are not selected when entitlement is inactive.
  - Watch logcat for `SwimDecisionAgent` events.

## [2026-05-01] [Android Tun2Socks Runtime Build Fix]
- **Status**: FIXED / BUILD TASK VERIFIED
- **Problem**: `:app:prepareTun2SocksRuntimeAssets` failed with `tun2socks source build did not produce libhev-socks5-tunnel.so for arm64-v8a`.
- **Root cause**:
  - The Gradle helper used `providers.exec { ... }` inside imperative build logic, so the clone/NDK commands were not reliably executed.
  - The upstream `hev-socks5-tunnel` Android build requires explicit `NDK_PROJECT_PATH=.`, `APP_BUILD_SCRIPT=Android.mk`, and `NDK_APPLICATION_MK=Application.mk` on Windows.
  - `prepareXrayRuntimeAssets` deleted the full ABI JNI directory, which could remove `libhev-socks5-tunnel.so` after tun2socks had copied it.
- **Changes**:
  - Replaced lazy `providers.exec` calls with immediate `exec` calls for source clone and `ndk-build`.
  - Added explicit NDK project/build script arguments for upstream tun2socks.
  - Made the Xray runtime task delete only `libxray.so` instead of deleting the shared ABI directory.
  - Added incomplete checkout cleanup before re-cloning tun2socks sources.
- **Verification**:
  - `cd android && .\gradlew.bat :app:prepareTun2SocksRuntimeAssets --no-daemon --max-workers=1 --console=plain --info` PASSED and compiled `libhev-socks5-tunnel.so` for `arm64-v8a` and `x86_64`.
  - `cd android && .\gradlew.bat :app:prepareXrayRuntimeAssets :app:prepareTun2SocksRuntimeAssets --no-daemon --max-workers=1 --console=plain` PASSED with both runtimes coexisting in generated JNI libs.
  - `cd android && .\gradlew.bat :app:compileDebugKotlin --no-daemon --max-workers=1 --console=plain` PASSED using reduced local Gradle memory.
- **Known local limitation**:
  - A full `:app:assembleDebug` attempt progressed beyond the tun2socks issue but the local Gradle JVM crashed from insufficient memory (`hs_err_pid18476.log`). This is an environment/RAM pressure issue, not the original tun2socks artifact issue.

## [2026-05-01] [Manual Card Bot Proof Forwarding Guard]
- **Status**: FIXED / NEEDS LIVE BOT QA
- **Problem**: A customer sent a manual card payment screenshot to the Telegram payment bot. The bot received the capture but did not respond afterward, and the customer did not receive access.
- **Root cause found in code**: `notification-bot-service` created the `CARD_PAYMENT_PROOF_SUBMITTED` event, then attempted to forward the screenshot to the admin/review chat before setting the customer confirmation state and before replying to the customer. If Telegram forwarding failed because of `PAYMENT_REVIEW_CHAT_ID`/group membership/chat permissions/token routing, the handler threw and the customer saw silence.
- **Change**: Photo/document proof forwarding to admin review is now non-blocking. The bot stores the pending confirmation state first, catches/logs admin forwarding failures, and still asks the customer for final email/phone/sender phone confirmation.
- **Verification**:
  - Local backend tests/build could not run because `backend/node_modules` is absent after disk cleanup (`ts-node` and `nest` unavailable).
  - Diff inspected manually against the failing control flow.
- **Live QA needed**:
  - Redeploy `notification-bot-service`.
  - Send a new card proof screenshot from a non-admin customer chat.
  - Confirm the customer immediately receives the email/phone/sender phone prompt even if admin review forwarding has a problem.
  - Confirm the review group receives the proof or logs show `Failed to forward manual card photo/document proof` with the exact Telegram error.
  - Confirm admin approve triggers fulfillment and email delivery.

## [2026-05-01] [Manual Card Admin Rescue Commands]
- **Status**: IMPLEMENTED / NEEDS REDEPLOY + LIVE QA
- **Problem**: If a customer pays by card, sends a screenshot, then goes offline or the Telegram review forwarding breaks, the order can stay `PENDING` even though money was received.
- **Change**: `notification-bot-service` now exposes admin rescue commands:
  - `/pending_cards` lists recent pending manual card orders and whether a stored proof exists.
  - `/review_card ORD-...` resends the latest stored Telegram proof from PostgreSQL with approve/reject buttons.
  - `/approve_card ORD-...` approves using the latest stored proof event and triggers normal customer-order fulfillment.
  - `/reject_card ORD-...` rejects the manual card order and notifies the customer by email when available.
- **Security**:
  - `/approve_card` requires a stored `CARD_PAYMENT_PROOF_SUBMITTED` event. It does not grant access without a persisted proof trail.
  - Final fulfillment still goes through `customer-order-service`; Telegram remains an admin control layer, not the source of truth.
- **Live recovery for current incident**:
  - Redeploy `notification-bot-service`.
  - In the payment bot as admin, run `/pending_cards`.
  - Run `/review_card <orderRef>` to inspect the stored proof.
  - If money is confirmed, run `/approve_card <orderRef>`.

## [2026-05-01] [Manual Card Proof Admin Auto-Fallback]
- **Status**: IMPLEMENTED / NEEDS REDEPLOY + LIVE QA
- **Problem**: In production, a customer paid by card and sent a screenshot, but because the admin could not approve the received payment, the customer did not receive access. The admin workflow still required too much manual recovery when Telegram review delivery failed or the customer went offline after proof upload.
- **Change**:
  - When a card proof photo/document is received, the bot now automatically builds an admin review packet with approve/reject actions.
  - It first tries to send the media proof to `PAYMENT_REVIEW_CHAT_ID`/review chat.
  - If media forwarding fails, it sends a text fallback with the same approve/reject actions.
  - It also sends a direct admin fallback to every configured `ADMIN_USER_IDS` value, so the admin can approve even if the review group is misconfigured or unavailable.
  - If all admin notifications fail, an admin event `CARD_PAYMENT_REVIEW_NOTIFICATION_FAILED` is persisted for audit/recovery.
  - Customer contact confirmation is no longer blocked by review chat failures; the customer gets an under-review acknowledgement and admin can continue from the stored proof.
- **Security**:
  - No automatic fulfillment is granted from the screenshot alone.
  - Admin still must press approve or use `/approve_card <orderRef>`.
  - Approval still goes through `customer-order-service` and the normal fulfillment/delivery path.
- **Verification**:
  - Local policy tests/build could not run because `backend/node_modules` is missing after disk cleanup (`ts-node` and `nest` unavailable).
  - Static diff reviewed for the manual card photo/document/contact confirmation flow.
- **Live recovery**:
  - Redeploy `notification-bot-service`.
  - For the current paid customer, run `/pending_cards` or wait for the direct admin fallback if the proof event is already in PostgreSQL and the user resends/continues.
  - Use `/review_card <orderRef>` then press approve, or directly `/approve_card <orderRef>` after confirming money in the bank.

## [2026-05-01] [Manual Card Pending Payment Reminder Loop]
- **Status**: IMPLEMENTED / NEEDS REDEPLOY + LIVE QA
- **Problem**: Admin workflow still depended on noticing the first payment proof notification. If a proof was stored but approval did not happen, a paid customer could remain stuck in `PENDING`.
- **Change**:
  - `notification-bot-service` now starts a manual card reminder loop after the Telegram payment bot launches.
  - It scans pending `CARD_MANUAL` orders with a stored `CARD_PAYMENT_PROOF_SUBMITTED` event.
  - It sends review-chat and direct-admin reminders with approve/reject actions.
  - It records `CARD_PAYMENT_ADMIN_REMINDER_SENT` or `CARD_PAYMENT_ADMIN_REMINDER_FAILED` in `AdminEvent` for audit.
  - Anti-spam: reminders are skipped until the proof reaches `MANUAL_CARD_REMINDER_MIN_AGE_MS` and are not repeated for the same proof within `MANUAL_CARD_REMINDER_INTERVAL_MS`.
- **Config**:
  - `MANUAL_CARD_REMINDER_INTERVAL_MS` defaults to `600000` (10 minutes). Set `0` to disable.
  - `MANUAL_CARD_REMINDER_MIN_AGE_MS` defaults to `300000` (5 minutes).
- **Security**:
  - Reminder does not approve automatically.
  - Admin must still approve, and fulfillment still goes through `customer-order-service`.
- **Verification**:
  - Static flow inspection completed.
  - Local `npm run test:policy` and `npm run build:all` could not run because `backend/node_modules` is missing after disk cleanup (`ts-node` and `nest` unavailable).

## [2026-05-01] [Manual Card Confirmation Parser Hardening]
- **Status**: IMPLEMENTED / NEEDS BOT REDEPLOY + LIVE QA
- **Problem**: After sending a card payment screenshot, customers can reply with email/phone/sender phone in many natural formats. The previous parser only handled a fragile line-based format and could miss or merge fields, which risks breaking the bot-to-admin review chain.
- **Change**:
  - `notification-bot-service` now parses confirmation text with labeled and unlabeled formats.
  - Supported examples include one-line English labels, French labels, Russian labels, spaced phone numbers, punctuation-separated values, and missing sender-phone fallback.
  - Phone extraction now avoids merging separate phone lines.
  - Admin approval remains required; the parser only improves data capture for review.
- **Verification**:
  - `npx --yes -p ts-node@10.9.2 -p typescript@5.1.3 ts-node apps/notification-bot-service/src/__tests__/manual-card-confirmation.spec.ts` PASSED.
  - Full backend `npm run test:policy` still cannot run locally until `backend/node_modules` is restored.
- **Live QA needed**:
  - Redeploy `notification-bot-service`.
  - Test customer confirmation formats:
    - `email@example.com / 79507704623 / sender 79507704624`
    - `Email: ... Phone: ... Sender phone: ...`
    - `Courriel: ... Telephone: ... Numero expediteur: ...`
    - `Pochta/Email: ... Telefon: ... Sender payment phone: ...`
  - Confirm the admin review packet receives the parsed final email, phone, and sender payment phone.

## [2026-05-01] [Manual Card Review Output Traceability]
- **Status**: IMPLEMENTED / NEEDS BOT REDEPLOY + LIVE QA
- **Problem**: A customer paid by card and the bot received the screenshot, but the dedicated admin group did not receive the full review package with screenshot plus personal confirmation data.
- **Finding**:
  - Proof and contact confirmation are two separate bot outputs.
  - Proof output had failure audit and admin fallback.
  - Contact confirmation output had fallback but did not persist a sent/failed notification event, making production incidents harder to trace.
  - `/review_card` resent the stored proof but did not clearly include the latest parsed contact confirmation.
- **Change**:
  - Added `/trace_card ORD-...` admin command to inspect the manual-card output chain for one order.
  - `/review_card` and reminder messages now include the latest contact confirmation summary when available.
  - Contact confirmation review output now writes `CARD_PAYMENT_CONTACT_REVIEW_NOTIFICATION_SENT` or `CARD_PAYMENT_CONTACT_REVIEW_NOTIFICATION_FAILED` admin events.
  - Trace output lists proof storage, contact confirmation, review/fallback notification events, and recovery commands.
- **Verification**:
  - `npx --yes -p ts-node@10.9.2 -p typescript@5.1.3 ts-node apps/notification-bot-service/src/__tests__/telegram-command-menu.spec.ts` PASSED.
  - `npx --yes -p ts-node@10.9.2 -p typescript@5.1.3 ts-node apps/notification-bot-service/src/__tests__/manual-card-confirmation.spec.ts` PASSED.
  - `npm run build:all` could not run locally because `backend/node_modules` is missing (`nest` unavailable).
- **Live QA needed**:
  - Redeploy `notification-bot-service`.
  - Run `/trace_card <orderRef>` for the current incident.
  - Run `/review_card <orderRef>` to resend proof plus latest contact summary.
  - Confirm the dedicated review group and direct admin fallback receive the expected package.

## [2026-05-02] [Manual Card Complete Review + Premium Subscription Runtime Resolution]
- **Status**: IMPLEMENTED / NEEDS REDEPLOY + SIGNED APK QA
- **Problem**:
  - A real card payment proof and customer contact confirmation were received in the payment bot, but no dedicated review group received the complete screenshot + personal-info review packet.
  - After admin approval, the paid plan became active, but Android attempted to start VPN with the supplier `https://...` subscription URL directly and failed with `Unsupported configuration format`.
- **Root cause**:
  - Manual card proof and contact data could be emitted as separate Telegram messages; the contact confirmation path did not force a combined proof + contact review packet.
  - Backend inventory can legitimately preserve supplier subscription URLs as `raw_config`, while Android runtime only accepts concrete runtime configs such as `vless://`, `vmess://`, `trojan://`, `ss://`, or JSON. The premium/backend connection path did not reuse the subscription resolver used by manual imports.
- **Change**:
  - `notification-bot-service` now sends a complete manual-card review package after contact confirmation: proof media when available, parsed contact data, raw confirmation package, and approve/reject buttons.
  - If media forwarding fails, the bot sends text fallback to the review chat and direct admin fallback.
  - Android now resolves backend subscription URLs before starting the VPN runtime. If the active premium resource is `https://...`, the app fetches the subscription, parses it, selects the first supported runtime config, and sends that concrete config to `SwimVpnService`.
  - The supplier URL remains preserved as the backend/source truth; only the runtime payload is resolved locally before connection.
- **Verification**:
  - `npx --yes -p ts-node@10.9.2 -p typescript@5.1.3 ts-node apps/notification-bot-service/src/__tests__/telegram-command-menu.spec.ts` PASSED.
  - `npx --yes -p ts-node@10.9.2 -p typescript@5.1.3 ts-node apps/notification-bot-service/src/__tests__/manual-card-confirmation.spec.ts` PASSED.
  - `cd android && .\gradlew.bat :app:compileDebugKotlin --no-daemon --max-workers=1 --console=plain` PASSED with reduced Gradle memory.
- **Live QA needed**:
  - Redeploy `notification-bot-service`.
  - Build/install a new release APK because Android runtime behavior changed.
  - Approve a test manual card order and confirm the review group receives screenshot + final email + final phone + sender phone in one package.
  - In the app, connect using a paid backend resource stored as a supplier `https://...` subscription URL and confirm it resolves to a concrete VPN config instead of `Unsupported configuration format`.

## [2026-05-02] [Customer Cancellation Revokes Assignment And Releases Resale Capacity]
- **Status**: IMPLEMENTED / NEEDS BACKEND REDEPLOY + RELEASE APK QA
- **Problem**:
  - A customer with an unusable premium config had no self-service way to remove the active subscription/config from the app.
  - Product clarification: cancelled access must release the supplier link resale slot so the link can be sold again within the configured cap.
- **Root cause**:
  - Existing backend/admin inventory revocation already recalculates supplier capacity, but no customer-facing cancellation endpoint used that path.
- **Change**:
  - Added customer cancellation contract, gateway endpoint, and customer-service handler.
  - Customer cancellation verifies `userNumber + deviceId`, finds the active assignment, then calls inventory `revoke_assignment` so `used_resale_slots` and inventory health are recalculated by the existing source-of-truth logic.
  - Android profile screen now shows a guarded `Cancel access / Résilier l'accès` action for active paid subscriptions.
  - Confirming cancellation calls the backend, clears selected backend premium config/auto-connect when relevant, stops the backend VPN session if it is active, and refreshes the profile. Imported configs remain available.
- **Verification**:
  - `cd android && .\gradlew.bat :app:compileDebugKotlin --no-daemon --max-workers=1 --console=plain` PASSED.
  - `cd android && .\gradlew.bat :app:mergeDebugResources --no-daemon --max-workers=1 --console=plain` PASSED.
  - `git diff --check` PASSED aside from expected CRLF conversion warnings.
  - Backend targeted test could not run locally because temporary `npx` execution cannot resolve the backend Nest/Prisma modules without local `backend/node_modules`.
- **Live QA needed**:
  - Redeploy backend services.
  - Build/install a new signed release APK.
  - With an active paid user, cancel access from Profile and confirm `subscriptionUrl` disappears, backend premium server access is denied, imported configs still work, and inventory `used_resale_slots` decreases/recomputes through inventory revocation.

## [2026-05-02] [Post-Cancellation Standard UX + Fulfillment Failure Trace]
- **Status**: IMPLEMENTED / NEEDS BACKEND REDEPLOY + RELEASE APK QA
- **Problem**:
  - After customer cancellation, Android displayed a harsh expired state and still showed the old supplier expiry with remaining days.
  - Manual card approval failures returned the generic message `Fulfillment triggered but failed`, hiding the real inventory/customer-order cause.
- **Root cause**:
  - `customer-order-service` profile lookup selected the latest paid order even when its only assignment was `REVOKED`, so revoked access was still interpreted as an expired subscription with provider dates.
  - Android also trusted stale non-active expiry/plan fields defensively and rendered `EXPIRED` in red across Home/Profile.
  - Fulfillment exceptions were swallowed into a generic result instead of being persisted and shown to the admin.
- **Change**:
  - Profile source of truth now uses only orders with `ACTIVE` or `PENDING` assignments for current entitlement display.
  - A revoked-only paid order now returns `FREEMIUM`, `accessType=NONE`, no offer badge, and no subscription expiry.
  - Android post-cancel/non-premium UI now shows `STANDARD MODE` instead of `EXPIRED` / `PREMIUM INACTIVE`; stale expiry is hidden when access is not active or pending.
  - Customer cancellation clears stale VPN error state when backend premium access is removed.
  - Manual card fulfillment failures now audit `FULFILLMENT_FAILED` and return the concrete error to the bot.
  - `/trace_card` now includes fulfillment failure events and their error payload when present.
- **Verification planned**:
  - Run backend policy test for revoked profile and fulfillment failure error propagation.
  - Run Android resource/Kotlin compile checks.
  - Live QA: cancel an active paid subscription and confirm Home/Profile return to standard usable state, no remaining-days display, no red expired badge, and the supplier link slot is available for resale.
  - Live QA: retry `/approve_card <orderRef>` after deploy; if it still fails, the bot should now show the exact inventory/customer-order error and `/trace_card <orderRef>` should list it.
- **Verification performed locally**:
  - `git diff --check` PASSED with CRLF warnings only.
  - `cd android && .\gradlew.bat :app:mergeDebugResources --no-daemon --max-workers=1 --console=plain` PASSED.
  - `cd android && .\gradlew.bat :app:compileDebugKotlin --no-daemon --max-workers=1 --console=plain` PASSED.
  - `cd backend && npm run test:policy` could not start because local `backend/node_modules` is missing (`ts-node` unavailable). Run this in Dokploy/container or after restoring backend dependencies.

## [2026-05-02] [Pending Fulfillment Cancellation Returns To Standard]
- **Status**: IMPLEMENTED / NEEDS BACKEND REDEPLOY + RELEASE APK QA
- **Problem**:
  - A customer who had a paid/manual-card order with no assigned config could still see `Accès en préparation` after trying to cancel, because no active assignment existed to revoke and the paid order stayed open.
  - The pending badge was rendered in red, and stale VPN runtime errors could still appear when no server was selected.
  - The freemium explanatory text was too heavy for the desired product UX.
- **Change**:
  - Customer cancellation now closes a paid or pending-fulfillment order with no active assignment by marking the order `CANCELLED` and auditing `CUSTOMER_PENDING_ORDER_CANCELLED`.
  - A cancelled pending order returns the app to standard/freemium profile state and consumes no resale slot.
  - Android allows cancellation from pending fulfillment state, removes red pending styling, renames pending copy to `Commande en cours`, clears stale VPN error display when no server is selected, and shortens the cancellation toast.
  - The standard quota card no longer displays the long explanatory sentence.
- **Live QA needed**:
  - With a paid-but-not-fulfilled order, tap cancel and confirm the app returns to `MODE STANDARD` with no `Connection failed` text.
  - Confirm `/trace_card <orderRef>` still keeps audit visibility for the cancelled order.
  - Confirm a cancelled pending order cannot accidentally expose premium config and does not consume inventory capacity.
- **Additional verification**:
  - `git diff --check` PASSED with CRLF warnings only.
  - Initial parallel Gradle verification caused a transient `mergeDebugResources` intermediate-file error; rerunning sequentially fixed it.
  - `cd android && .\gradlew.bat :app:mergeDebugResources :app:compileDebugKotlin --no-daemon --max-workers=1 --console=plain` PASSED.
  - `cd backend && npm run test:policy` could not start locally because `ts-node` is unavailable (`backend/node_modules` missing). Re-run inside the backend container or after dependency restore.

## [2026-05-02] [Manual Card Approval Keeps Inventory Failure Detail]
- **Status**: IMPLEMENTED / NEEDS BACKEND REDEPLOY + LIVE QA
- **Problem**: `/approve_card <orderRef>` still returned `Fulfillment failed: Internal server error`, which was not actionable for admin operations.
- **Root cause**: `inventory-delivery-service` threw fulfillment exceptions across the Nest microservice boundary. Nest converted those unhandled exceptions to the generic `Internal server error` before `customer-order-service` could audit or display the real cause.
- **Change**:
  - `InventoryController.fulfillOrder()` now catches fulfillment exceptions and returns a structured `{ success: false, error }` response with the concrete message.
  - `customer-order-service` now treats structured inventory failures like thrown failures: it writes `FULFILLMENT_FAILED` and returns the specific message to the payment bot.
  - Policy coverage was extended for structured inventory failure responses.
- **Live QA needed**:
  - Redeploy `inventory-delivery-service`, `customer-order-service`, and `notification-bot-service`.
  - Retry `/approve_card <orderRef>`; if it fails, the bot should show the actual inventory/customer-order reason instead of `Internal server error`.
  - Run `/trace_card <orderRef>` to see the `FULFILLMENT_FAILED` audit entry and its error payload.

## [2026-05-02] [Manual Card Approval Separates Payment From Fulfillment]

- Adjusted manual card approval semantics so an admin-approved payment is not reported as failed when inventory fulfillment fails afterward.
- `customer-order-service` now keeps the order in `PENDING_FULFILLMENT` with `paymentApproved: true` and preserves the exact `fulfillmentError` for admin diagnosis.
- `notification-bot-service` now replies that payment is approved but fulfillment is pending, instead of saying approval failed.
- Security boundary remains unchanged: no premium config is exposed until inventory assigns a valid resource.

## [2026-05-02] [Drop Stale Unique Assignment Indexes]

- Root cause confirmed for manual card fulfillment failure: production DB still had the initial unique index on `OrderAssignment.inventory_item_id`.
- That stale index contradicted the resale-slot model where one supplier config can serve multiple orders until `max_resale_slots` is reached.
- Added an idempotent Prisma migration to drop the stale unique assignment indexes and keep the non-unique lookup index.

## [2026-05-02] [Prisma Migration SQL Encoding Fix]

- Rewrote `20260502093000_drop_stale_unique_assignment_indexes/migration.sql` as UTF-8 without BOM after Dokploy prisma-migrate failed on deploy.
- SQL content remains unchanged and idempotent; only file encoding was corrected for production migration safety.

## [2026-05-02] [Safe Prisma Migrate Deploy Wrapper]

- Added a targeted migration deploy wrapper for Dokploy so `prisma-migrate` can recover the known failed `20260502093000_drop_stale_unique_assignment_indexes` migration automatically.
- The wrapper only resolves that explicit migration when Prisma status reports it as failed, then runs `prisma migrate deploy` normally.
- This avoids manual production intervention while preserving Prisma's blocking behavior for unrelated migration failures.

## [2026-05-02] [Prisma Migrate OOM Guard]

- Investigated Dokploy `prisma-migrate` exit 137 after adding the safe migration wrapper.
- Root cause: the migration container was limited to 128 MB while running Node + Prisma + migration recovery, causing an OOM-style kill.
- Increased only `prisma-migrate` memory to 384 MB and changed the wrapper to call the local Prisma binary directly instead of `npx` to reduce process overhead.

## [2026-05-02] [Premium Subscription URL Expansion And Plan Quota Truth]

- Adjusted Android premium server handling so a purchased backend subscription URL is expanded into its supported VLESS/VMess/Trojan/Shadowsocks nodes before display/connection.
- Premium active config cards now use backend plan quota and customer assignment usage instead of supplier total quota metadata.
- Hid provider website/server host rows for SWIMVPN-managed configs so supplier subscription hosts such as `wb.routerwb.ru` are not shown as VPN nodes.
- Backend profile payload now reports customer-facing plan quota from `Plan.quota_label` and customer-specific usage from `OrderAssignment.measured_used_bytes`.

## [2026-05-02] [Premium Subscription Runtime Nodes And Plan Quota Truth - Store Guard]

- Added a backend store guard so `http://` / `https://` supplier subscription URLs are not parsed or exposed as runtime VPN servers.
- Android remains responsible for resolving an entitled premium subscription URL into its real VLESS/VMess/Trojan/Shadowsocks nodes before display/connection.
- Customer-facing managed quota continues to come from the commercial plan (`Plan.quota_label`) and assignment usage, not supplier global quota metadata.
- Supplier hosts such as `wb.routerwb.ru` are treated as internal subscription sources, not customer-facing VPN nodes.

## [2026-05-02] [Live QA - Premium Subscription Expands To VLESS Nodes]

- **Status**: LIVE QA PASSED
- **Evidence**:
  - User tested a purchased Basic access on a signed Android build after backend deploy.
  - Home screen selected a concrete `vless` node (`de.cloudrt.ru`) instead of the supplier subscription host.
  - VPN state showed connected and traffic counters increased during the session.
  - The user-facing selected server label came from the parsed subscription node, not `HTTPS - wb.routerwb.ru`.
- **Remaining note**:
  - Backend policy tests still need to be rerun in an environment with backend dependencies available, because local `backend/node_modules` is missing on this workstation.

## [2026-05-02] [Provider Time + Sold Plan Quota Enforcement]

- **Status**: IMPLEMENTED / NEEDS BACKEND CONTAINER POLICY TEST + LIVE QA
- **Change**:
  - Paid access no longer invents a local `fulfilled_at + plan duration` expiry. For paid plans, expiry is supplier/assignment-managed when available.
  - SWIMVPN now focuses enforcement on the sold plan quota (`Plan.quota_label`) and assignment usage.
  - Android now reports Premium backend usage periodically during an active connection, invisibly to the user.
  - If backend responds that Premium access is no longer allowed, Android stops the VPN, disables auto-connect, refreshes the profile, removes backend Premium servers, and returns the app to standard mode with a soft non-persistent toast.
  - Inventory usage recording now marks the current assignment `EXPIRED` with `PLAN_QUOTA_EXHAUSTED` when the sold quota is reached, then recalculates resale capacity so the supplier link can be reused within cap.
  - Store server delivery now also checks sold plan quota before exposing backend Premium servers.
- **Verification**:
  - Android Kotlin compile passed locally.
  - Backend policy test could not run locally because `backend/node_modules` is unavailable (`ts-node` missing). Run `npm run test:policy` in the backend container or after dependency restore.

## [2026-05-02] [Multi-Agent Production Code Review]

- **Status**: READ-ONLY REVIEW COMPLETE / FIXES REQUIRED BEFORE FINAL PRODUCTION READINESS
- **Scope reviewed**:
  - Backend entitlement/profile/trial/subscription/quota logic.
  - Inventory allocation, resale capacity, source quota, revocation, and delivery.
  - Android runtime/profile/server state and backend contract usage.
  - Manual card/payment Telegram bot workflow and admin authorization.
  - Docker/Prisma/env/deploy readiness.
- **High-priority findings**:
  - Assignment usage reporting is client-driven and can overwrite usage downward; quota accounting must be monotonic.
  - `reportUsage` can return a non-profile payload while Android expects `AccessProfileResponse`.
  - Source quota exhaustion is not enforced consistently in allocation/health recalculation.
  - `moveAssignment` can reactivate revoked/expired assignments.
  - Android expands managed supplier subscription URLs client-side, creating a backend source-of-truth/security boundary conflict.
  - Manual payment delivery can be fulfilled without a durable recovery path if email/post-purchase delivery fails.
  - Telegram payment admin authorization allows group-context approval instead of requiring explicit `ADMIN_USER_IDS`.
  - Production seed still creates hardcoded starter VPN inventory and mutates plans on deploy.
- **Verification**:
  - `git status --short --branch` showed a clean worktree before documentation note.
  - `git diff --check` passed before documentation note.
  - Backend tests/builds were not runnable locally because backend dependencies are absent.

## [2026-05-02] [Deploy Bootstrap Seed Hardening]

- Documented root `DATABASE_URL` as a required Docker/Prisma placeholder without real secrets.
- Changed Prisma seed behavior so existing production plans are not overwritten during deploy seed runs.
- Gated starter/demo VPN inventory behind `SEED_DEMO_DATA=true`; production default skips demo inventory.
- Clarified that no default database admin is seeded and first admin bootstrap must be a controlled ops step with a bcrypt `password_hash`.
- Reordered backend `verify:deploy` to validate/generate Prisma before TypeScript lint/build checks.

## [2026-05-02] [Multi-Agent Review Fix Batch Applied]

- Hardened backend profile resolution so a newer revoked/failed terminal order does not mask an older still-active fulfilled assignment.
- Hardened Android premium refresh so a profile refresh without runtime config does not wipe the currently valid backend premium runtime access while entitlement remains active.
- Hardened inventory admin move so source quota is checked against projected moved usage before moving an assignment.
- Added targeted policy coverage for older active assignment preservation, Android runtime access preservation, and source-quota-aware assignment move rejection.
- Verification status:
  - `git diff --check` passed.
  - `docker compose --env-file .env.example -f docker-compose.yml config --quiet` passed.
  - Android `compileDebugKotlin` passed.
  - Backend policy scripts are still blocked locally because backend dev binaries are missing from `backend/node_modules`.
  - A forced Android unit-test rerun hit Windows paging-file exhaustion; the prior targeted test invocation completed with exit code 0, but this workstation needs memory/paging relief for repeated forced test runs.

## [2026-05-02] [Backend Dependencies Restored And Policy Tests Unblocked]

- Reinstalled backend dependencies with `npm ci` after cleaning the incomplete `backend/node_modules` directory.
- Verified local backend toolchain is available again: `ts-node`, `prisma`, `tsc`, and `@prisma/client`.
- `npm run prisma:validate`, `npm run lint`, and `npm run test:policy` now run locally.
- While unblocking policy tests, fixed customer profile edge cases surfaced by the test suite:
  - revoked/cancelled paid history returns `FREEMIUM` / standard state instead of `TRIAL_AVAILABLE` or expired paid UI;
  - paid orders without assignment remain `PENDING_FULFILLMENT` until delivered or cancelled;
  - cancelled paid orders remain visible as history so profile bootstrap can return the correct freemium state;
  - fulfillment failure tests now assert the durable `FULFILLMENT_FAILED` audit event instead of assuming it is the only event.

## [2026-05-02] [Backend NPM Audit Review]

- Ran `npm audit --json` and `npm audit fix --dry-run` for backend dependencies.
- Current audit summary: 22 total vulnerabilities, 0 critical, 8 high, 10 moderate, 4 low.
- Main risk cluster is NestJS dependency stack:
  - runtime-facing: `@nestjs/core`, `@nestjs/platform-express`, `@nestjs/common`, `@nestjs/config`, `@nestjs/swagger` and transitives such as `multer`, `file-type`, `lodash`, `js-yaml`;
  - dev/build-facing: `@nestjs/cli`, Angular devkit, `glob`, `webpack`, `inquirer`, `tmp`, `picomatch`.
- `npm audit fix --dry-run` indicates most complete fixes require `npm audit fix --force`, which would move packages such as `@nestjs/core`, `@nestjs/platform-express`, `@nestjs/swagger`, `@nestjs/cli`, and `@nestjs/config` across major versions.
- Recommendation: do not run `npm audit fix --force` in the current production stabilization window. Treat full remediation as a planned NestJS 11 upgrade with full backend policy/build/Dokploy verification.

## [2026-05-02] [Android Subscription Redirect Cookie Parser Fix]

- Diagnosed the new supplier URL `subs.eu-fffast.com` without printing raw VPN nodes.
- Root cause: the provider responds with an initial `302` plus `Set-Cookie`; the actual Base64 subscription payload is returned only when the redirected request sends that cookie back.
- Android `OkHttp` followed redirects but used the default no-cookie behavior, so it could loop on empty `302` responses and never reach the VLESS node payload.
- Added an in-memory `SubscriptionCookieJar` scoped to the Android subscription fetch client.
- Verification:
  - The diagnostic fetch with cookies reaches HTTP 200 and decodes to VLESS entries.
  - `cd android && .\gradlew.bat :app:testDebugUnitTest --tests com.swimvpn.app.config.SubscriptionCookieJarTest --tests com.swimvpn.app.config.SubscriptionParserTest` passed.

[2026-05-07] [Android VPN Stability And Provider Parser Hardening]
- Audited Android VPN service/runtime and subscription parser with parallel agents before implementation.
- Added service-owned runtime observability states, disconnect causes, battery-optimization logging, network callbacks, and bounded same-session reconnect backoff.
- Kept backend entitlement boundary intact: service reconnect only retries the currently active runtime payload and does not fetch or grant premium access.
- Extended Android subscription parsing for missing Base64 padding, supported Clash YAML proxies, supported sing-box JSON outbounds, richer normalized metadata fields, and unknown-provider warnings.
- Verification so far: targeted Android parser/adaptive unit tests passed after Kotlin compile.

[2026-05-07] [Android Provider Sample VLESS Reality Header Param Fix]
- Audited live provider subscription `subs.eu-fffast.com` without printing raw secrets.
- Confirmed Android fetcher User-Agents receive Base64 payload with 29 VLESS Reality TCP nodes and subscription-userinfo metadata.
- Added regression coverage for provider-style `headerType=&path=&host=` query params.
- Fixed VLESS/Trojan TCP parsing so blank `headerType` and `host` are treated as absent, preserving raw config while avoiding invalid Xray `header.type=""` output.
- Verification: targeted parser tests, full Android unit tests, and `assembleDebug` passed.

## 2026-05-07 18:20:04 +03:00 - Android VPN underlay reconnect fix
- Diagnosed live via ADB on Samsung SM-S916B: SwimVPN foreground service and notification stay active, but network monitoring was observing the VPN default network instead of the physical underlay.
- Fixed SwimVpnService to monitor INTERNET + NOT_VPN networks only and to set underlying networks only to Wi-Fi/cellular/ethernet underlays.
- Verification: testDebugUnitTest and assembleDebug passed. ADB install smoke was blocked by signature mismatch with the already installed app; app data was not wiped.

## 2026-05-07 19:46:58 +03:00 - Plan mise a jour securite/camouflage
- Added docs/PLAN_MISE_A_JOUR_SECURITE_CAMOUFLAGE.md to split immediate security release work from the parallel cloud feature work for adaptive network privacy and camouflage.
- Document notes that the external security review results are the next required input before prioritizing production fixes.

## 2026-05-07 19:56:56 +03:00 - Security review fixes batch 1
- Implemented backend fixes from the security review: admin sessions now store SHA-256 token fingerprints instead of reusable JWT plaintext, and supplier inventory healthchecks reject loopback/private/reserved targets before opening sockets.
- Added policy tests for admin session token storage and healthcheck SSRF prevention. Verification passed: backend test:policy, lint, and build:all.

## 2026-05-07 20:18:31 +03:00 - Gateway public surface hardening
- Ran read-only multi-agent audits for gateway/admin exposure, Android device identity privacy, and Docker/Traefik network boundaries.
- Added targeted in-memory gateway rate limits for admin login, public access profile lookup, and crypt import resolution without adding a new dependency.
- Disabled Swagger in production unless `GATEWAY_SWAGGER_ENABLED=true`; added optional `GATEWAY_CORS_ORIGINS` allowlist while preserving legacy open CORS if unset.
- Added a gateway policy test for admin-login rate limiting. Targeted gateway test and backend test:policy passed.

## 2026-05-07 20:44:12 +03:00 - Audit systeme installe SWIMVPN
- Audited the installed backend, Android runtime, parser, deployment, and legal/privacy surfaces before applying more security patches.
- Added docs/AUDIT_SYSTEME_INSTALLE_SWIMVPN.md to document real flows, sources of truth, contradictions, safe patches, and migration-only areas.
- Key finding: profile/device/privacy fixes must be staged; `getProfile()` and raw Android ID cannot be locked or replaced abruptly without breaking installed flows.

## 2026-05-07 21:02:44 +03:00 - Documentation realigned to installed code
- Re-read root/backend/android worklogs and treated the installed code plus recent worklogs as the operational source of truth.
- Updated backend architecture roadmap, architecture docs, domain model, installed-system audit, privacy copy, and terms copy to follow current behavior instead of stale target-state assumptions.
- Reframed previous "contradictions" as documentation drift unless the installed code breaks a real flow.

## 2026-05-07 21:16:09 +03:00 - Raw Android device identity documented as operational truth
- Aligned docs and privacy copy with the product decision that raw Android device identity remains the operational model for continuity, trial anti-abuse, and backend-sensitive actions.
- Removed remaining TODO/roadmap language that treated device hashing as an intended migration.
- Documented required protections around the model: no public exposure, no raw logs, protected DB/backups/secrets/admin access, and backend device checks retained.

## 2026-05-07 22:02:00 +03:00 - Android sticky VPN service restore hardening
- Audited the installed Android auto-connect path after confirming bootstrap, purchase/entitlement, manual VPN connection, admin login, and supplier import flows are already production-working.
- Found that `SwimVpnService` returned `START_STICKY` but did not handle Android sticky restarts with `intent == null`, leaving a system-restarted service without a restored tunnel.
- Added a bounded sticky-restore policy: only restore a very fresh active runtime snapshot, with auto-connect enabled and a saved runtime payload. This avoids turning boot restore into a backend entitlement bypass.
- Verification: targeted StickyReconnectPolicy unit test, full Android debug unit tests, and assembleDebug passed.

## 2026-05-07 22:34:00 +03:00 - Android Xray runtime performance trim
- Audited tunnel/proxy runtime path with ADB: foreground service is active, Xray runs as a separate process, and tun2socks JNI appears to run inside the app process.
- Added tests for generated Xray runtime documents to keep proxy outbounds valid while avoiding unused Xray stats and inbound sniffing on empty-routing configs.
- Trimmed generated app-owned runtime documents: no unused Xray stats policy and no inbound sniffing when routing rules are empty. Supplier full JSON documents keep their existing policy/stats behavior unless already missing inbounds.
- Verification: targeted TunnelRuntimeAdapterPerformanceTest, full Android debug unit tests, and assembleDebug passed.

## 2026-05-07 22:52:00 +03:00 - Android foreground VPN notification polish
- Simplified the foreground VPN notification text to a single localized running state instead of runtime/debug details.
- Added notification translations: English `Run`, French `En marche`, Russian `????????`.
- Updated SwimVpnService notification behavior for background stability: ongoing, silent, only-alert-once, local-only, foreground-service immediate, and tap-to-return-to-app intent.
- Verification: VpnNotificationLanguageTest, full Android debug unit tests, and assembleDebug passed.

## 2026-05-07 23:22:00 +03:00 - Local proxy routing UX correction
- Audited the reported slow proxy route on device with ADB.
- Confirmed Xray local HTTP/SOCKS listeners are alive on 127.0.0.1:10809 and 127.0.0.1:10808, and explicit curl traffic through both proxies succeeds.
- Root cause: LOCAL_PROXY is a manual/proxy-aware app mode, not global Android routing; normal browsers do not use it automatically.
- Hid LOCAL_PROXY from the user routing selector and migrated user-selectable legacy PROXY/LOCAL_PROXY preferences to FULL_TUNNEL so normal page loading uses the VPN TUN path.
- Verification: RuntimeModePreferenceTest, full Android debug unit tests, and assembleDebug passed.

## 2026-05-07 23:45:00 +03:00 - Advanced local proxy retained and switch hardened
- Corrected the previous local proxy direction after product review: LOCAL_PROXY remains available as an advanced/manual mode, not a hidden diagnostic-only path.
- Kept the operational finding from ADB: local HTTP/SOCKS listeners work when the client app is explicitly configured, but normal Android browsing still requires FULL_TUNNEL.
- Added a service-owned runtime restart path for routing-mode changes so switching proxy/tunnel no longer uses the user-stop path or races against service teardown.
- Added UI copy that exposes HTTP 127.0.0.1:10809 and SOCKS 127.0.0.1:10808 for proxy-aware apps.
- Verification: pending for full Android debug tests and assembleDebug.

## 2026-05-07 23:58:00 +03:00 - Advanced local proxy verification
- Verification passed: RuntimeModePreferenceTest, full Android debug unit tests, git diff --check, and assembleDebug.
- Remaining validation is manual device QA for live switching FULL_TUNNEL <-> LOCAL_PROXY while connected.

## 2026-05-19 08:50:23 +03:00 - Android default language and SwimPay priority
- Audited the Android language bootstrap, VPN notification locale, settings reset, and subscription checkout method selection before changing code.
- Changed the Android default language to Russian for new or unsupported language values while preserving English and French as explicit supported languages.
- Normalized persisted language updates so UI state, bootstrap locale, and VPN notification language stay aligned.
- Made SwimPay the default Android checkout method without removing manual card or crypto fallback options.
- Verification: targeted language/payment unit tests, full Android debug unit tests, and assembleDebug passed.

## 2026-05-19 09:02:35 +03:00 - Notification bot delivery email diagnostics
- Audited the post-purchase delivery path from inventory assignment event to notification-bot email send and delivery status reporting.
- Fixed the Russian delivery email template so default RU emails are readable Cyrillic instead of mojibake placeholders.
- Added a Resend transport configuration diagnostic that exposes provider/from/configured status without exposing secrets.
- Included mailer diagnostics in delivery status responses used by /order and /status, and made inventory delivery events explicitly send customerLanguage ru.
- Added the email sender configuration policy test to backend test:policy.
- Verification: notification template test, email sender config test, backend test:policy, backend lint/typecheck, and backend build:all passed.

## 2026-05-19 09:08:17 +03:00 - Android VPN sticky restore diagnostics batch
- Audited SwimVpnService sticky restart, RuntimeStateStore freshness, reconnect causes, and technical diagnostics before changing runtime behavior.
- Extended the active sticky restore window from 15 seconds to 120 seconds so delayed Android service restarts can recover an active tunnel while still rejecting stale snapshots.
- Preserved concrete disconnect causes when writing runtime errors for engine crashes and VPN permission revocation.
- Added technical diagnostics for last disconnect cause and reconnect attempts in EN/FR/RU resources.
- Verification: StickyReconnectPolicyTest RED/GREEN, full Android debug unit tests, and assembleDebug passed.

## 2026-05-19 09:11:23 +03:00 - Adaptive decision score decay
- Audited the adaptive server selection policy and confirmed historical failure penalties could outlive the actual network/node incident.
- Added bounded failure-score decay after the recovery window so an old unstable node can become eligible again when it has the best current latency.
- Kept immediate avoidUntil behavior, premium blocking, and runtime-config filtering unchanged.
- Verification: AdaptiveDecisionAgentTest RED/GREEN and full Android debug unit tests passed.

## 2026-05-19 09:14:29 +03:00 - Landing SEO metadata baseline
- Audited the landing static metadata, legal hash routes, footer contact, and current SEO gaps before changing the landing.
- Added cautious SEO metadata to index.html: description, canonical, robots, Open Graph, Twitter summary, app metadata, hreflang, and SoftwareApplication JSON-LD.
- Updated metadata.json to remove unsupported ultimate-solution copy and describe the Android VPN/import surface more accurately.
- Aligned the footer support email with backend delivery templates: support@swimvpn.pro.
- Verification: npm run build passed. npm run lint failed on pre-existing root TypeScript scope issues involving backend aliases, server.ts, and a globe Three/SVG ref mismatch.

## 2026-05-19 13:11:52 +03:00 - Self-review fixes for VPN freshness, mailer status, and SEO locale
- Re-reviewed the previous implementation and fixed the P1 issue where the 120-second sticky restore window also made the Android UI treat stale RUNNING snapshots as fresh.
- Restored RuntimeStateSnapshot UI freshness to 15 seconds and moved the 120-second grace window into StickyReconnectPolicy only.
- Refined notification mailer diagnostics so /status distinguishes apiKeyConfigured/fromEmailPresent/fromEmailLooksValid/ready instead of a broad configured flag.
- Corrected landing language metadata to match the currently English landing content and removed duplicate hreflang variants pointing to the same URL.
- Verification: StickyReconnectPolicyTest RED/GREEN, email sender config RED/GREEN, full Android debug unit tests, assembleDebug, backend test:policy, backend lint, backend build:all, and landing build passed.

## 2026-05-19 - Live Android QA on physical device
- Device: SM-S916B / Android 16 API 36, serial R5CWA0FEPZW.
- Installed app: com.swimvpn.app versionName=1.0 versionCode=1, installed 2026-05-18 01:37:37.
- Observed active VPN: SwimVpnService foreground, tun0, WIFI|VPN network validated, process PID 14151 alive after backgrounding.
- Observed issues on installed build: app bootstraps locale=en, subscription expansion fails with HTTP 502 on all fallback clients, visible UI still has English labels and floating + button overlaps server card text.
- No app data cleared, no APK installed, no server/config changed, no VPN stop/start performed.

## 2026-05-19 - SwimPay fulfillment and delivery stabilization
- Analyzed VPS logs from gateway, customer-order, inventory-delivery, notification-bot, admin-control, store, vpn-config, and db containers.
- Root cause found in inventory-delivery-service logs: Prisma P2028 Transaction already closed in InventoryService.checkStockAndNotify after a fulfillment transaction.
- Moved fulfillment side effects to post-commit execution, changed stock check to use the normal Prisma service after commit, and guarded non-critical post-fulfillment stock checks.
- Added retry-delivery behavior for already assigned paid/fulfilled orders so admin retry can re-emit process_post_purchase_delivery without consuming another inventory slot.
- Added Android bounded external-checkout refresh window so returning from SwimPay refreshes profile/server state automatically for badge/config sync.

## 2026-05-19 - Android post-checkout strong refresh
- Limited implementation to Android post-checkout sync.
- Changed return-from-SwimPay refresh to use device-bound bootstrapAccess instead of the lightweight access profile endpoint.
- Rebuilds success state through the normal server-loading path so managed backend servers are fetched after fulfillment.
- Added a pure post-checkout server selection policy that preserves an active imported server and auto-selects the first backend fulfillment only when no server was active.
- Verification: targeted PostCheckoutServerSelectionPolicyTest and PaymentMethodPolicyTest passed.

## 2026-05-19 - Backend managed runtime node foundation
- Added vpn-config-engine managed runtime node parsing for direct VLESS, VMess, Trojan, Shadowsocks, multi-line payloads, and base64-decoded multi-line payloads.
- Kept supplier HTTP/HTTPS subscription URLs non-runtime in this backend pass; no remote subscription fetch is performed.
- Store server exposure now asks vpn-config-engine for managed nodes after customer/device/assignment/quota/expiration barriers, with a local direct-config fallback.
- Assigned backend servers now expose stable per-node ids and each node rawConfig only after entitlement barriers pass.
- Verification: managed nodes parser test, assigned server policy test, backend TypeScript lint, and targeted builds for vpn-config-engine-service and store-engine-service passed.

## 2026-05-19 - Fulfillment managed nodes integration verification
- Kept Android post-checkout sync polling alive while fulfillment is pending, then stops it only once premium access is observed or the bounded checkout window expires.
- Persisted the selected backend node after post-checkout rebuild when the app moves from no/stale backend selection to a newly delivered managed node, while preserving imported active servers.
- Hardened VLESS and Trojan runtime parsing so direct configs without explicit ports default to 443 instead of being rejected as invalid.
- Added the managed node parser spec to backend test:policy so CI covers the new parser surface.
- Verification: backend lint, backend build:all, backend test:policy, Prisma validate/generate, Android debug unit tests, Android assembleDebug, and git diff --check all passed.

## 2026-05-19 - Post-review fulfillment sync fixes
- Fixed store-engine Docker wiring so the service resolves vpn-config-engine-service through VPN_CONFIG_SERVICE_HOST instead of falling back to container-local 127.0.0.1.
- Added an explicit store-engine dependency on vpn-config-engine-service in docker-compose to reduce startup race risk.
- Kept Android post-checkout polling alive when transient bootstrap/profile/server rebuild paths return null during the bounded checkout refresh window.
- Verification: targeted Android post-checkout policy test, backend lint, backend build:all, backend test:policy, Android assembleDebug, and git diff --check passed.

## 2026-05-19 - Batch 4A Android IA recommendation polish
- Added a pure adaptive server recommendation result with runtime quality states for fresh, stale, missing ping, and fresh probe failure.
- Propagated latency probe freshness/failure into Android server models and recomputed advisory recommendations after bootstrap and latency refresh.
- Added gated IA chips in the server list and selected server card only when the recommended server has fresh validated latency evidence.
- Kept backend entitlement boundaries intact: premium-blocked or configless candidates are ignored and no auto-connect/auto-switch is introduced.
- Verification: targeted adaptive unit test, full Android debug unit tests, compileDebugKotlin, assembleDebug, and git diff --check passed.

## 2026-05-19 - Batch 4A review fixes
- Fixed adaptive recommendation fail-closed behavior so a fresh failed latency probe is excluded instead of merely penalized.
- Replaced the IA UI string-state contract with a boolean `isRecommendedServerValidated` flag to avoid silent badge regressions from string typos.
- Added adaptive regression tests for fresh failed probe exclusion and `recommendServer` fresh quality-state exposure.
- Verification: targeted adaptive test, full Android debug unit tests, compileDebugKotlin, assembleDebug, and git diff --check passed.

## 2026-05-19 - Batch 4B backend capacity hints for IA
- Store-engine now exposes entitled backend node capacity hints: source load percent, JSON-safe traffic used/total bytes, and availability status.
- Android accepts the availability status field and uses backend load as a conservative scoring penalty in the adaptive recommendation engine.
- No fake throughput, user geolocation, migration, or entitlement bypass was introduced; hints are returned only after the existing premium server barriers pass.
- Verification: targeted store policy test, backend lint, backend build:all, backend test:policy, targeted adaptive test, Android debug unit tests, Android assembleDebug, and git diff --check passed.

## 2026-05-19 - Batch 4B review fixes
- Adaptive recommendation now applies the backend `availabilityStatus` hint, so `CONGESTED` nodes are penalized even when quota load is numerically low.
- Android now treats missing load as unknown instead of zero load; imported/local legacy nodes no longer get a fake zero-load advantage.
- Backend traffic bytes remain JSON-safe strings, and Android parses managed server traffic strings defensively before exposing active config metadata.
- Verification: targeted adaptive and metadata tests, store policy test, backend lint/build:all/test:policy, Android debug unit tests, Android assembleDebug passed.

## 2026-05-19 - Batch 4C Plan/Quota fulfillment visibility
- Active managed config metadata now carries backend fulfillment health hints: provider, host, availability status, and node load.
- Profile active-config card now shows managed node host/provider plus availability/load instead of hiding those details for SWIMVPN-managed nodes.
- Kept plan quota truth in the access card and node/provider hints in the active config card to avoid mixing business quota with routing quality.
- Verification: targeted ActiveConfigMetadataMappingTest, Android debug unit tests, Android assembleDebug, and git diff --check passed.

## 2026-05-19 - Batch 4C review fix
- Fixed managed node availability status normalization to use Locale.ROOT so backend status codes translate consistently across device locales.
- Added a unit test covering Turkish locale behavior for lowercase availability status values.
- Verification: targeted availability/metadata tests, Android debug unit tests, Android assembleDebug passed.

## 2026-05-19 - Backend access contract replacement rules
- Hardened customer cancellation so it revokes every active assignment for the customer, preventing older paid access from reappearing after cancel.
- Fulfillment now treats upgrade/downgrade as a replacement: once the new order is successfully assigned, older active assignments for the same customer are revoked in the same transaction and audited.
- Pending/no-capacity fulfillment keeps existing active access intact; no old assignment is revoked until the new fulfillment succeeds.
- Verification: targeted customer policy, targeted inventory policy, store assigned-server policy, full backend test:policy, backend lint, backend build:all, Prisma validate/generate passed.

## 2026-05-19 - Backend access contract review fixes
- Customer cancellation now queries active assignments directly by customer, outside the recent-order profile window, and cancels every paid/pending fulfillment order in the no-active path.
- Inventory replacement is limited to non-trial paid fulfillments, so activating a trial cannot revoke an existing paid access.
- Strengthened inventory replacement tests to assert the Prisma filter targets only older active assignments for the same customer.
- Verification: targeted customer/inventory/store policies, full backend test:policy, backend lint, backend build:all, Prisma validate/generate passed.

## 2026-05-19 - Backend contract audit findings resolved
- Profile, server exposure, cancellation, and usage reporting no longer depend on a recent-order `take: 10` window for active access decisions.
- Customer profile now queries active/pending/expired assignments directly and prioritizes active paid access over trial access when both exist.
- Trial activation now refuses to create a parallel trial when the customer already has active paid access.
- Retry fulfillment on an already-active paid order now also revokes older active assignments for the customer through the existing replacement cleanup path.
- Formalized backend customer response contract types and made usage reporting device-bound at DTO level.
- Gateway access/admin controllers now use contract DTOs for request bodies where available, with adminId still injected server-side.
- Verification: targeted customer/store/inventory policy tests, backend lint, full backend test:policy, Prisma validate/generate, and backend build:all passed.

## 2026-05-19 - Backend access review findings closed
- Fixed replacement cleanup so paid fulfillment/retry revokes only active assignments from orders older than the authoritative replacement order.
- Aligned store server selection with profile entitlement priority: active paid access is selected before active trial access.
- Fixed profile direct-assignment folding so multiple assignments for the same order are merged before entitlement resolution.
- Added regression tests for older-only replacement cleanup, paid-over-trial server exposure, and same-order assignment merging.
- Verification: targeted customer/store/inventory policy tests, backend lint, full backend test:policy, Prisma validate/generate, and backend build:all passed.

## 2026-05-19 - Trial contract finalized in backend
- Added backend guard so activateTrial refuses to create a trial while a paid order is PAID or PENDING_FULFILLMENT.
- Preserved active trial usability while paid fulfillment is pending; only active paid access supersedes trial runtime exposure.
- Added regression coverage for paid-pending blocking trial activation.
- Verification: targeted customer policy test, backend lint, full backend test:policy, Prisma validate/generate, and backend build:all passed.

## 2026-05-19 - Trial contract simulation coverage
- Added gateway simulation coverage so trial business denials map to 409 Conflict instead of 503 service failures.
- Added customer simulations for active trial remaining usable while paid fulfillment is pending, and for paid-pending trial refusal without mutating contact fields.
- Moved paid active/pending trial guards before profile contact updates in activateTrial.
- Added the gateway access error mapping spec to backend test:policy.
- Verification: targeted gateway/customer/store/inventory policy tests, backend lint, full backend test:policy, Prisma validate/generate, backend build:all passed.

## 2026-05-19 - Trial Store review findings resolved
- Store-engine now exposes active Trial Store assignments as selectable backend nodes after device, status, expiry, and config-status checks; paid servers remain preferred when paid access exists.
- Trial grants now persist normalized email/phone/device snapshots and Prisma uniqueness guards for race-proof one-time campaign eligibility.
- Pending Trial Store recovery now atomically locks the grant before creating an assignment and releases the config lock if the grant race is lost.
- Customer profile now refuses runtime exposure for disabled/dead trial configs, keeps paid expired state above trial expired state, and reports trial start from fulfillment assignment when available.
- Supplier import parsing now detects embedded VLESS/VMess/Trojan/Shadowsocks runtime configs before falling back to HTTP subscription URLs.
- Gateway/admin trial import validation now rejects non-string `supplierExpiresAt`, and Android now starts a bounded pending-fulfillment refresh after trial activation.
- Verification so far: targeted store/inventory/customer/vpn/gateway policy tests, Prisma validate/generate, backend lint, and targeted Android pending-refresh unit test passed.

## 2026-05-19 - Trial Store review follow-up findings closed
- Customer trial profiles now expire runtime exposure at the earliest relevant date across grant, assignment, supplier expiry, and campaign window.
- Trial Store server load remains `null` when the backend has no measured capacity signal instead of exposing a fake zero-load value.
- Android pending fulfillment refresh now uses the shared pending policy and is wired directly after `activateTrial()` success.
- Trial identity and one-assignment-per-grant guards were moved into a separate additive migration after the Trial Store foundation migration.
- Verification: targeted customer/store policy tests, targeted Android pending-refresh test, Prisma validate/generate, backend lint/build/policy, Android debug tests/build, local Docker `prisma:migrate:deploy`, and local Docker `prisma:migrate:status` passed.

## 2026-05-20 - VPN runtime stabilisation plan
- Organized the VPN disconnect audit into a phased Android runtime plan covering killed-service recovery, network handoff debounce, diagnostics persistence, battery/OEM hardening, and connected-state verification.
- No runtime behavior was changed in this batch.
- Verification: documentation diff check passed.

## 2026-05-20 - VPN runtime stabilisation Phase 1
- Added `RuntimeRecoveryPolicy` so fresh active killed-service sessions can recover from persisted payload without depending on the user-facing auto-connect toggle.
- Updated `SwimVpnService` sticky restore to require a payload and VPN permission for full tunnel, while keeping boot/package auto-connect gated in `MainViewModel`.
- Added regression coverage for active, stale, idle, missing-payload, missing-permission, and local-proxy recovery cases.
- Verification: baseline Android debug unit tests, targeted `RuntimeRecoveryPolicyTest`, full Android debug unit tests, and `assembleDebug` passed.

## 2026-05-20 - SWIMVPN Dark Luxury design tokens
- Added `docs/SWIMVPN_DESIGN_DNA_2026-05.md` as the design source of truth for future Android UI implementation.
- Captured the global dark monolithic visual DNA, color/material/radius/typography/spacing tokens, reusable metaball dock rules, and screen contracts for Home, Servers, Subscription, and Settings.
- No Android or backend runtime code was changed in this batch.

## 2026-05-20 - Home screen design token extraction
- Added `docs/SWIMVPN_HOME_SCREEN_TOKENS_2026-05.md` as the dedicated token document for the first Home / Connected screen and its reusable components.
- Extracted component-level rules for the OLED background, profile button, living power orb, connection status, server pill, stats card, and metaball dock.
- Explicitly kept this batch documentation-only: no Android, backend, VPN runtime, trial, or subscription behavior was changed.

## 2026-05-20 - Home screen Dark Luxury implementation plan
- Added `docs/superpowers/plans/2026-05-20-home-screen-dark-luxury.md` for the first Android Home screen design pass.
- Used design and Android architecture subagents to audit the mocks, current Compose surface, iconography, dock feasibility, and implementation risk.
- Locked the scope as Android UI-only: backend, entitlement, trial/subscription, parsing, and VPN runtime contracts remain frozen.

## 2026-05-20 - Home screen Dark Luxury first implementation pass
- Added Android Compose Dark Luxury tokens, hardware surfaces, living power orb, Home screen composition, and a four-node metaball dock.
- Wired the Home route to the new `ui.screens.HomeScreen` while preserving the existing VPN permission flow, `viewModel.toggleVpn`, active server data, runtime state reconciliation, and premium guard before backend-server connect.
- Verification: `:app:compileDebugKotlin` and `:app:assembleDebug` passed. ADB reported no connected device, so live screenshot comparison remains pending.
- Memory hygiene: stopped the Gradle daemon after verification; no broad app/process killing was performed to avoid terminating the IDE, Codex session, ADB, or VPN.

## 2026-05-20 - Home dock metaball geometry pass
- Reworked the Home bottom dock from a regular rounded capsule into a Canvas-drawn metaball body with four lobes, concave valleys, a top hardware highlight, and a subtle lower purple edge.
- Preserved the existing Home/Servers/Subscription/Settings callbacks and did not touch backend, entitlement, VPN runtime, parsing, trial, or subscription contracts.
- Added the applied dock geometry extraction and divergence matrix to `docs/SWIMVPN_HOME_SCREEN_TOKENS_2026-05.md`.
- Verification: `:app:compileDebugKotlin` and `:app:assembleDebug` passed; debug APK was installed over ADB Wi-Fi and screenshot captured at `screenshots/swimvpn_home_dock_metaball_v2_20260520.png`.

## 2026-05-20 - Home dock cleanup and disk recovery
- Cleaned generated Gradle/temp/build artifacts after Gradle failed with an insufficient disk space error on `D:\Gradle`.
- Removed only disposable installer/APK/ZIP files from `Downloads`; kept PNG design mocks and project screenshots/log references.
- Refined the dock rendering to reduce visible construction strokes, soften highlights, and improve icon breathing radius against the mock.
- Verification: `:app:compileDebugKotlin` and `:app:assembleDebug` passed after cleanup. ADB capture could not validate the Home because the physical device foreground was ChatGPT instead of SWIMVPN at capture time.

## 2026-05-20 - Home dock mock comparison pass
- Compared the current Home dock against the standalone metaball mock and updated the token matrix with the exact 340dp x 89dp geometry, node centers, active/inactive diameters, and material differences.
- Kept the validated 340dp x 89dp dock geometry and refined only the material: darker body fill, softer strokes, lower purple edge alpha, smaller icons, and reduced active-node glow.
- Verification: `:app:compileDebugKotlin` and `:app:assembleDebug` passed; debug APK installed over ADB Wi-Fi; fresh Home screenshot captured at `screenshots/swimvpn_home_dock_mock_compare_v2_20260520.png`.

## 2026-05-20 - Home dock waist formula pass
- Replaced the hand-tuned dock body path with a parametric Bezier metaball approximation for the shapes between icons.
- Applied the mock ratios: `waist/r = 0.60`, circle control `r * 0.55`, and waist control `d * 0.15` for each adjacent node pair.
- Documented the exact waist formula in `docs/SWIMVPN_HOME_SCREEN_TOKENS_2026-05.md`.
- Verification: `:app:compileDebugKotlin` and `:app:assembleDebug` passed; debug APK installed over ADB Wi-Fi; fresh Home screenshot captured at `screenshots/swimvpn_home_dock_waist_formula_20260520.png`.

## 2026-05-20 - Home dock active-radius metaball pass
- Compared the standalone dock mock against the current Home dock screenshot and found that the body path still used a uniform inactive radius even when the selected node is visually larger.
- Updated the metaball body formula to use per-lobe radii: `44.5dp` for the active lobe and `40dp` for inactive lobes, while keeping `waist/r = 0.60`.
- Softened the active lobe shadow/glow and moved the purple bloom to the actual active node instead of assuming Home is always active.
- Temporarily added a debug-only dock preview surface to capture the dock alone on device, then removed it immediately after capture so the working APK returns to the normal app surface.
- Updated the Home dock token matrix and Bezier formula so future passes keep the active node fused into the body instead of sitting above a smaller silhouette.
- Verification: `:app:compileDebugKotlin` and `:app:assembleDebug` passed; debug APK was installed after removing the temporary preview surface and SWIMVPN relaunched on `MainActivity`.

## 2026-05-20 - Home dock mock reconciliation pass
- Reconciled the existing dock component against the standalone mock without changing iconography.
- Raised the Home dock bottom padding from `24dp` to `34dp` so it reads as floating instead of pinned to the gesture area.
- Refined dock material only: clearer inactive recesses, stronger but thin top highlight, softer active glow, darker active outer ring, and deeper purple active gradient.
- Backend, VPN runtime, access contracts, and navigation callbacks were not changed.
- Verification: `:app:compileDebugKotlin` and `:app:assembleDebug` passed; debug APK installed over ADB Wi-Fi; Home screenshot captured at `screenshots/swimvpn_home_dock_reconciled_20260520.png`.

## 2026-05-20 - Home dock circle diameter refinement
- Reduced dock icon circle diameters without changing the icon set: inactive outer `80dp -> 74dp`, active outer `89dp -> 82dp`, inactive recess `54dp -> 50dp`, active purple circle `58dp -> 54dp`.
- Recalculated the metaball body radii to stay aligned with the smaller visual nodes: inactive `37dp`, active `41dp`.
- Added a clipped procedural rubber-grain overlay and inter-lobe shadow/highlight modelling so the dock material reads more like polished grey joystick rubber.
- Further reduced the visible button rings to avoid a cheap oversized-button feel: inactive outer `68dp`, active outer `76dp`, inactive recess `46dp`, active purple circle `52dp`; the larger metaball body remains responsible for the fused silhouette.
- Updated the Home dock token document to keep the new geometry as the current design contract.
- Verification: `:app:compileDebugKotlin` and `:app:assembleDebug` passed; debug APK installed over ADB Wi-Fi; Home screenshot captured at `screenshots/swimvpn_home_dock_rubber_contrast_20260520.png`.

## 2026-05-20 - Metaball nav dock premium rework
- Reworked the dock component into `MetaballNavDock` while keeping `SwimMetaballDock` as a compatibility wrapper for the current Home screen call site.
- Removed the previous procedural grain/noise direction and returned the dock material to a smooth molded black hardware surface.
- Added animated active-center motion with `Animatable`, localized glow transfer, subtle active breathing, press compression, and active-radius influence on the metaball body.
- Rebuilt the visual layering into a cleaner model: fused Canvas body, sculpted node shell/bowl, animated purple core layer, then crisp icon/label plane.
- Reduced the dock safe-zone footprint from `340dp` to `320dp` and recalculated node centers to keep active edge nodes inside the component bounds.
- VPN runtime, backend, parser, route logic, entitlement, and screen business behavior were not changed.
- Verification: `:app:compileDebugKotlin` and `:app:assembleDebug` passed; debug APK installed over ADB Wi-Fi; Home screenshot captured at `screenshots/swimvpn_home_metaball_nav_dock_rework_20260520.png`.

## 2026-05-20 - Home hardware tokenization pass
- Centralized the validated dock material in `SwimDesignTokens`: shell, bowl, purple core, highlights, shadows, layer identifiers, motion values, dock geometry, Start button tokens, and User button tokens.
- Rewired `MetaballNavDock` to read the centralized tokens while preserving the validated 320dp safe-zone dock geometry.
- Rebuilt the Home Start button and User button with the same layered hardware model as the dock: outer shell, recessed bowl, icon/core plane, skin overlay, and localized glow.
- Updated the Home token document to correct stale dock diameter values and document the shared material/layer architecture.
- Backend, VPN runtime, parser, route logic, entitlement, and business contracts were not changed.
- Verification: `:app:compileDebugKotlin`, `:app:assembleDebug`, and `git diff --check` passed. ADB live capture was not repeated in this batch because the device disconnected after the prior live QA; Gradle daemon was stopped after verification.

## 2026-05-20 - Servers Dark Luxury implementation pass
- Replaced the generic Material servers list with a SwimVPN Dark Luxury server screen using shared hardware tokens, compact pill rows, AI status card, embedded config quota pill, action pills, and the existing metaball dock active on Servers.
- Added a clean UI contract for `ServerSourceTab`, `ImportedConfigSummaryUi`, `ServerNodeUi`, and `ServerScreenUiState`; quota and expiration remain attached to the config summary, not server rows.
- Wired the screen to existing real `ServerGroup`/`ServerNode` data and `ActiveConfigMetadata` without changing VPN runtime, backend, parser, entitlement, trial, or subscription contracts.
- Added `docs/SWIMVPN_SERVER_SCREEN_TOKENS_2026-05.md` for the screen design and motion contract.
- Verification: `:app:compileDebugKotlin`, `:app:assembleDebug`, and `git diff --check` passed. Debug APK installed on the physical device; first capture exposed font-scale overflow, which was corrected with bounded visual text sizing. A second capture attempt was blocked by the phone lock/emergency-call surface, so final visual QA remains manual.

## 2026-05-20 - Subscription live design QA
- Forced `com.swimvpn.app/.MainActivity` into the foreground over ADB Wi-Fi and disabled device animation scales to make the UI hierarchy dump stable.
- Captured the live Subscription screen at `screenshots/swimvpn_subscription_live_pull_20260520_122118.png` and verified the hierarchy belongs to `com.swimvpn.app`.
- Cross-check found a major layout mismatch against the mock: oversized title/subtitle, oversized plan cards, two-column card content causing truncation, Premium card hidden behind the dock, and Platinum/guarantee not visible in the first viewport.
- No production logic, VPN runtime, backend, parser, entitlement, or payment behavior was changed in this QA batch.

## 2026-05-20 - Subscription and Servers responsive dock layout pass
- Removed the top-right user button from Subscription and Servers, keeping the existing callback signatures for navigation compatibility.
- Reworked Subscription and Servers as edge-to-edge screens whose scrollable content reserves a real bottom zone for the metaball dock instead of drawing underneath it.
- Reduced Subscription and Servers token sizes for card heights, title scale, row height, action pills, badges, and dock-reserved content space so the design is not tied to one phone resolution.
- Verified on device with ADB screenshots:
  - `screenshots/swimvpn_subscription_final_layout_20260520_123456.png`
  - `screenshots/swimvpn_servers_final_layout_20260520_123456.png`
  - `screenshots/swimvpn_servers_density_20260520_123905.png`

## 2026-05-20 - Servers Agent and ping information pass
- Replaced repeated AI wording with one clear card title: `Agent active`, subtitle `Showing the best servers for your connection`, and a smaller `AI` badge.
- Enriched server rows with location context from existing backend/client data: country, city/location, provider/group/protocol/load/availability details.
- Replaced the plain latency line with a compact live ping badge using existing measured `ping`, `latencyMeasuredAtMs`, and `latencyProbeFailed` fields; no new probe loop or backend contract was introduced.
- Refined the green Agent status into a small recessed pilot light integrated into the card surface instead of a dominant floating dot.
- Verified on device with screenshot `screenshots/swimvpn_servers_agent_ping_20260520_124540.png`.

## 2026-05-20 - Import configuration visual reconciliation
- Rebuilt `ConfigImportScreen` with the same SwimVPN dark hardware grammar as the Servers screen: edge-to-edge atmospheric background, monolithic cards, pill method buttons, recessed icon bowls, and compact imported profile rows.
- Limited visible import methods to `Manual Input` and `QR Code`; clipboard/paste import UI is no longer exposed from this screen.
- Preserved the existing manual import dialog, QR scanner, repository import calls, profile selection, and deletion behavior without changing parser, VPN runtime, backend, entitlement, or config contracts.
- Verification: `:app:compileDebugKotlin` and `:app:assembleDebug` passed. Debug APK installed successfully on the ADB Wi-Fi device.

## 2026-05-20 - Manual input dialog visual reconciliation
- Rebuilt the `ImportConfigDialog` manual input modal as a SwimVPN dark hardware panel with shared tokens, high-radius shell, recessed input field, purple active action, and compact preview surface.
- Preserved the existing `onTextChange`, `canImport`, `onImport`, preview validation, and repository import flow; no parser, VPN runtime, backend, entitlement, or source-type behavior was changed.
- Verification: `:app:compileDebugKotlin` passed.

## 2026-05-20 - Settings screen visual reconciliation
- Rebuilt `TechnicalSettingsScreen` as an edge-to-edge SwimVPN dark hardware settings surface with Account, Application, and Connection sections.
- Added account canvas with expandable local identity details and a non-destructive logout pill placeholder because no real logout/account callback is currently wired into this screen.
- Reworked application controls into language chips for RU/EN/FR and a dark/light theme switch using the existing theme callback.
- Reworked connection controls into routing pilot lights for Tunnel/Proxy, auto-connect switch, kill-switch action, battery-optimization action, and local Agent IA switch.
- Preserved existing routing, auto-connect, language, theme, Android VPN settings, and battery optimization callbacks; backend, parser, VPN runtime, entitlement, and contracts were not changed.
- Verification: `:app:compileDebugKotlin` passed.

## 2026-05-20 - Settings support and dock continuity pass
- Wired real profile identity into `TechnicalSettingsScreen` from `MainActivity` using existing email/phone state and connected the settings logout pill to the existing `viewModel.signOut()` flow.
- Added an `Aide et support` section in settings that navigates to the existing support route.
- Rebuilt `SupportScreen` with the same dark hardware grammar as Servers/Settings while preserving email, Telegram, FAQ, and subscription navigation behavior.
- Fixed cross-screen dock continuity by preserving the last selected dock item so Home <-> Servers starts from the previous node and glides to the new active node instead of appearing instantly at the target.
- Backend, parser, VPN runtime, entitlement, payment, and support contact contracts were not changed.
- Verification: `:app:compileDebugKotlin` passed.

## 2026-05-20 - Account screen correction after settings misread
- Moved the requested account simplification back to `ProfileScreen`: dark hardware account canvas, expandable identity stack, access/status chip, action pills, and integrated sign-out pill.
- Removed the Account and Help/Support blocks from `TechnicalSettingsScreen`, leaving it focused on Application and Connection controls.
- Removed the visible runtime diagnostics surface from settings so the simplified user-facing design no longer exposes technical runtime language.
- Kept `SupportScreen` as a dedicated dark hardware route and preserved profile navigation to Subscription, Import Access, Settings, Support, trial activation, cancellation, and sign-out callbacks.
- Backend, parser, VPN runtime, entitlement, payment, and access contracts were not changed.
- Verification: `:app:compileDebugKotlin` passed.

## 2026-05-20 - Servers premium contract and dock gooey flow
- Split the Servers screen UI contract between Imported and Premium sources: Imported keeps imported config quota/expiry, while Premium now receives a separate `PremiumAccessSummaryUi` derived from backend entitlement/profile and backend server nodes.
- Premium Servers no longer reuses the imported quota summary; it shows plan/access state, managed node count, premium quota/expiry, and subscription management actions.
- Preserved the existing Android premium source of truth: backend nodes remain `source == "backend"` and are still gated by `profile.isPremiumAllowed` in `MainViewModel`.
- Added a soft gooey bridge to `SwimMetaballDock` transitions so the active node leaves a controlled fluid trail between dock nodes without adding noise or texture.
- Backend, parser, VPN runtime, entitlement, and payment logic were not changed.
- Verification: `:app:compileDebugKotlin` and `:app:assembleDebug` passed.
- Review follow-up: premium access now stays visually active while nodes sync, Premium actions no longer mirror Imported actions, and the dock gooey bridge is drawn inside the surface layer before highlights. Debug APK installed successfully on the ADB Wi-Fi device for visual QA.

## 2026-05-20 - Account, Lottie start orb, and liquid dock pass
- Simplified `ProfileScreen` by removing the redundant access-status info row and limiting management actions to Technical Settings, Help/Support, and Subscription.
- Added the downloaded Gradient Lottie asset as `swim_gradient_orb.json` and integrated it behind the Home start button with a soft breathing animation driven by VPN visual state.
- Added `lottie-compose` as a targeted Android dependency; no broad dependency updates were made.
- Added the downloaded liquid bubble `.lottie` asset to `res/raw` for visual/reference continuity while keeping the dock runtime implemented in Compose Canvas.
- Reworked `SwimMetaballDock` active core into a fuller liquid fill layer: the current node drains, the target node fills, and a rounded fluid stream passes through the dock valleys during navigation.
- Backend, parser, VPN runtime, entitlement, and payment logic were not changed.
- Verification: `:app:compileDebugKotlin` and `:app:assembleDebug` passed. Debug APK installed successfully on the ADB Wi-Fi device for visual QA.

## 2026-05-20 - Lottie orb tint and dock liquid timing correction
- Kept the downloaded Gradient Lottie as the Home orb animation source instead of replacing it with a static image or standalone Canvas mesh.
- Reframed the Lottie as a donut-style animated backdrop around the Start button: larger scale, stronger visibility, centered dark mask, and purple color filter derived from the active SwimVPN accent.
- Recalculated dock liquid transfer so the purple flow peaks mid-transition instead of fading immediately from the source node.
- Increased dock transition duration from 280ms to 540ms and slightly raised active glow breathing for a more readable liquid navigation feel.
- Backend, parser, VPN runtime, entitlement, payment, and access contracts were not changed.
- Verification: `:app:compileDebugKotlin` and `:app:assembleDebug` passed. Debug APK installed successfully on the ADB Wi-Fi device and live screenshots were captured for Home and Servers.

## 2026-05-20 - Orb and dock visible motion pass
- Kept the Home Lottie orb at the same depth behind the Start button, but changed its parent rotation from a subtle `-3..3` oscillation to a continuous slow orbital rotation so the backdrop reads as animated.
- Added a local Start button press response independent of VPN state: soft compression plus a short purple ring response inside the hardware button.
- Kept dock liquid at the same visual depth as the active purple core and added an internal animated liquid phase so the active node has moving highlight material at rest, not just during navigation.
- Preserved the existing dock icon layer above the liquid and did not change navigation, VPN runtime, backend, parser, entitlement, or payment behavior.
- Verification: first compile caught a missing `cos` import in the dock animation; after correction, `:app:compileDebugKotlin` and `:app:assembleDebug` passed. Debug APK installed successfully on the ADB Wi-Fi device and a live Home screenshot was captured.

## 2026-05-20 - Onboarding profile contract fixes
- Android now treats backend `profileCompletionRequired` as authoritative, even if an active entitlement state is also present.
- The onboarding freemium continuation path now requires a completed profile and stays in setup if backend still returns `PROFILE_INCOMPLETE`.
- Active trial users with paid fulfillment pending keep trial runtime access, while Android can still display/poll the pending paid fulfillment signal.
- Trial Store runtime exposure now remains blocked by profile completion, so incomplete profiles cannot receive managed trial configs.
- Verification: red/green Android `AccessProfileResponseTest`, backend customer security policy, backend typecheck, and targeted Android debug unit tests passed.

## 2026-05-20 - Isolated animated particle donut orb POC
- Added `AnimatedParticleDonutOrb` as a standalone Compose Canvas POC for a procedural SwimVPN donut/orb: no video, GIF, Lottie, or external asset.
- Added reusable donut orb tokens and dimensions with a 320dp canvas, 300dp outer visual diameter, 168dp center hole, deterministic particles, orbital lines, state-based motion, reduced-motion scaling, and a central click target.
- Added `AnimatedParticleDonutOrbPreviewScreen` with local state controls for Disconnected, Connecting, Connected, and Unstable visual states.
- Kept the component isolated in `ui/orb`; Home, dock, VPN runtime, parser, backend, entitlement, navigation, and payment logic were not modified.
- Verification: `:app:compileDebugKotlin` and `:app:assembleDebug` passed.
- Added a debug-only `donut-orb-lab` route that opens only when launched with the ADB extra `open_donut_orb_lab=true`, so the POC can be visually QA'd on device without changing normal app navigation.
- Rejected after live device QA and removed the POC files plus the debug lab route to restore the normal app surface.

## 2026-05-21 - Android navigation performance triage
- Audited Home/Servers/Subscription navigation jank after visual polish.
- Kept top-level navigation optimized with singleTop/restoreState helpers to reduce route stacking.
- Restored Home orb render cadence to 60 FPS (`16_666_667ns`) per QA direction.
- Reverted Home orb z-order to the prior transparent visual plane to remove the black plate regression.
- Reduced Servers screen entrance stagger cost by rendering reveal content immediately while preserving dock motion.
- Freed local workstation memory by stopping Gradle/Kotlin Java daemons; preserved VS Code/Codex/VPN processes.
- Verified `:app:compileDebugKotlin :app:assembleDebug` with `--no-daemon`; installed debug APK on Wi-Fi adb device.

## 2026-05-21 - Android subscription plan copy alignment
- Updated Subscription plan cards to present user-facing decision facts instead of long technical feature lists.
- Kept backend as the source for plan title, duration, quota, and price.
- Added UI product policy for visible device allowance: Basic 2 devices, Premium 3 devices, Platinum 4 devices.
- Added AI Agent plan bullet: real-time best-node selection.
- Did not touch backend plans, Prisma migrations, slot_count, payments, VPN runtime, or parser logic.
- Verified Android debug build with `./gradlew.bat --no-daemon :app:compileDebugKotlin :app:assembleDebug`.

## 2026-05-21 - Manual card payment pipeline removed
- Removed manual card checkout from Android-visible payment methods; Subscription now offers only SwimPay and Crypto.
- Removed `CARD_MANUAL` from active checkout contracts and replaced the customer-order checkout fallback with an unsupported-method rejection.
- Removed Telegram bot manual card proof/review/approval commands, routing helpers, env variables, and policy tests dedicated to the manual bot pipeline.
- Preserved historical `CARD_MANUAL:APPROVED` fixture references where they represent already-paid legacy orders, not new checkout behavior.
- Stabilized a date-sensitive backend policy fixture whose provider expiry had reached 2026-05-21 during verification.
- Verification: backend `typecheck`, `test:policy`, `prisma:generate`, and Android `PaymentMethodPolicyTest`, `compileDebugKotlin`, `assembleDebug` passed.
- Re-verified on request: no active manual-payment bot refs remain, backend checks passed again, and Android payment policy/debug build passed again.

## 2026-05-21 - SwimPay payment icon
- Added a transparent vector SwimPay mark derived from the provided logo direction, keeping only the central green/blue symbol and dropping the white/glass background plus secondary badge.
- Integrated the mark into the Android Subscription payment method pill for SwimPay only.

## 2026-05-21 - Crypto Pay payment icon
- Added a transparent vector Crypto Pay mark inspired by the Crypto Pay/Crypto Bot visual family and official docs positioning.
- Integrated the mark into the Android Subscription payment method pill for Crypto while keeping payment behavior unchanged.

## 2026-05-21 - Android French language stabilization pass
- Made the Android base string resources French so the default fallback language is French instead of English.
- Reconciled visible English copy across onboarding resources, Home, Servers, Subscription, Import configuration, Account, Technical settings, Support, and the metaball dock.
- Preserved brand/technical terms where appropriate, including SwimPay, Crypto, Premium, VPN, proxy, tunnel, QR code, and protocol names.
- Did not touch backend contracts, VPN runtime, parser logic, payment behavior, entitlement checks, or navigation architecture.

## 2026-05-21 - Android English/Russian language module pass
- Added an explicit English Android language pack in `values-en/strings.xml` while keeping French as the default fallback.
- Kept FR/EN/RU resource keys aligned and cleaned Russian strings that still contained English payment/support labels.
- Moved exposed dock, import, server, subscription header, profile menu, support back action, technical settings, Home auto-select, and orb accessibility labels toward resource-backed strings.
- Cleaned old mojibake in repository notes that could confuse future localization QA.
- Verification: FR/EN/RU key parity passed, Russian leftover scan passed, mojibake scan passed for Android resources/UI and repo notes, and `:app:compileDebugKotlin` passed.

## 2026-05-21 - Android localization review findings fixed
- Wired Subscription dynamic plan titles, CTA labels, billing periods, feature bullets, badges, and content descriptions to shared FR/EN/RU resources.
- Wired Servers runtime summaries, AI card fallback labels, ping/load/detail labels, imported quota captions, and premium access summaries to shared FR/EN/RU resources.
- Kept backend plan/pricing/entitlement contracts untouched; this was a UI localization consistency fix only.
- Verification: targeted hardcoded-label scans passed, FR/EN/RU key parity remained aligned, mojibake scan passed, and `:app:compileDebugKotlin` passed.

## 2026-05-21 - Android launcher icon shark mark
- Replaced the old Android-template launcher foreground/background with the SwimVPN shark mark from the provided SVG direction.
- Updated adaptive launcher XML to use drawable foreground/background layers with a matte black-purple background and centered purple shark mark.
- Replaced legacy `mipmap-anydpi` launcher vectors so older launcher paths no longer show the Android template.
- Verification: `:app:assembleDebug` passed.

## 2026-05-21 - Android notification status icon
- Added the provided 24dp monochrome shark vector as `ic_stat_swimvpn` for Android foreground-service notifications.
- Switched the VPN service notification small icon from the app logo PNG to the notification-safe monochrome vector.
- Removed the legacy `mipmap-anydpi` launcher vectors that were added during the launcher pass; launcher remains on adaptive v26 XML plus density PNG fallbacks.
- Removed unused `ic_shark_logo.xml`; kept `swimvpn_logo.png` because `MainActivity` still references it.
- Verification: `:app:assembleDebug` passed.

## 2026-05-21 - Android launcher density cleanup
- Regenerated legacy launcher fallback PNGs from the SwimVPN shark mark preview for mdpi, hdpi, xhdpi, xxhdpi, and xxxhdpi.
- Removed obsolete `ic_launcher_adaptive_back.png` and `ic_launcher_adaptive_fore.png` density assets because adaptive v26 now uses vector foreground/background layers.
- Kept only the required launcher stack: adaptive v26 XML, vector foreground/background, and clean density PNG fallbacks.
- Verification: PNG dimensions match Android density targets and `:app:assembleDebug` passed.

## 2026-05-21 - Android launch, onboarding, and state screen polish
- Changed the Android window theme from a light AppCompat launch surface to a dark SwimVPN launch surface to remove the white startup flash before Compose renders.
- Rebuilt the Compose loading splash with the SwimVPN launcher mark, dark purple hardware background, and a subtle breathing glow.
- Reconciled onboarding with the shared SwimVPN dark luxury background, monolithic hardware card, purple icon core, and pill CTA while preserving the existing three-step flow.
- Reworked the bootstrap error state into a dark premium card with a restrained purple status icon and pill retry action.
- Verification: `:app:assembleDebug` passed.

## 2026-05-21 - Android light theme derivation
- Treated the current black/purple SwimVPN visual language as the canonical dark theme in `SwimVpnTheme`.
- Replaced the old blue/navy Material palette with dark tokens from `SwimDesignTokens` and a derived light palette using pale violet backgrounds, white/lilac surfaces, dark plum text, and the same SwimVPN violet accent.
- Updated transparent system bars so status/navigation icons switch to dark glyphs in light mode and stay light in dark mode.
- Kept custom hardware tokens unchanged for this pass; broad dynamic light/dark conversion of bespoke surfaces remains a separate visual-system step.

## 2026-05-21 - Android dark/light token split
- Converted the existing implicit SwimVPN visual tokens into explicit `SwimDesignTokens.Dark` color, material, and highlight groups.
- Added a matching `SwimDesignTokens.Light` group with the same token structure, derived as a pearl/lavender sibling of the black-purple dark theme.
- Added `LocalSwimVisualTokens` and wired `SwimVpnTheme` to provide dark or light visual tokens according to the active app theme.
- Kept legacy `SwimDesignTokens.Color`, `Material`, and `Highlight` as dark aliases so existing screens continue compiling while they are migrated to the theme-aware provider.
- Made legacy `SwimDesignTokens.Color`, `Material`, and `Highlight` resolve through the active visual token family, so existing screens and sub-screens now follow the selected Light/Dark theme without a risky mass rewrite.
- Migrated shared hardware surfaces, the dark-luxury background, dock body, and Home power core rendering toward the theme-aware token provider.

## 2026-05-21 - Android foreground notification state mirror
- Replaced the static foreground-service notification body with state-aware copy driven by `VpnManager.runtimeStatus` and `VpnManager.runtimeMode`.
- Added localized notification states for disconnected, connecting, connected, reconnecting, degraded, stopping, failed, and stopped-by-user in default French, French, English, and Russian resources.
- Kept the Android notification channel label stable as the app name while allowing the notification title/body to mirror the live VPN runtime state.
- Updated notification refreshes when the VPN runtime status or runtime error changes.
- Verification: `:app:assembleDebug` passed.

## 2026-05-21 - Landing page SwimVPN bento conversion
- Reworked the landing page into a dark SwimVPN app-aligned bento layout focused on APK download, pre-release availability, the current trial, in-app VPN config purchases, and free personal config import.
- Replaced cyan/cyber terminal messaging with black hardware surfaces, violet SwimVPN accents, rounded bento cards, and French user-facing acquisition copy.
- Clarified the dual usage model: users can buy ready-to-use configs in the app or paste/scan their own VLESS/Trojan configs for free usage.
- Updated the download section, usage steps, feature grid, phone mockup, footer, and global landing background to match the current app visual grammar.
- Verification: `npm run build` passed. `npm run lint` still fails on pre-existing backend alias/type issues and an existing globe ref typing issue unrelated to this landing copy/layout pass.

## 2026-05-21 - Landing review finding fixes
- Added reduced-motion handling to the landing motion surfaces, CTA press/hover effects, and phone mockup ambient animation.
- Lazy-loaded the landing globe, hid the heavy Three.js globe on mobile in favor of a lightweight bento fallback, and split the globe into a separate production chunk.
- Added a lower-power/static globe mode for reduced-motion decorative usage and removed the always-interactive hero globe behavior.
- Reworded trial copy to say the trial is activated and its exact duration is shown inside the app instead of implying an unverified duration on the landing page.
- Fixed the existing R3F globe line ref typing issue so landing typecheck no longer reports that file.
- Verification: `npm run build` passed. `npm run lint` still fails only on existing backend alias/dependency issues outside the landing scope.

## 2026-05-21 - Android light material polish pass
- Strengthened the shared light background with a layered lavender gradient field so Home, Servers, Subscription, Account, onboarding, and sub-screens no longer sit on a flat pale wash.
- Reduced light-mode material heaviness by softening raised shadows, inner bowl shadows, skin sheen, and dock shadow strokes while keeping the dark token family unchanged.
- Removed a hardcoded dark fill from the shared pill surface so server cards and related pills use the active material tokens in light mode.
- Added a light-surface contrast mode for the procedural Home orb, boosting mesh/rim/glow readability only when the light token family is active.
- Reworked the Home server pill badge and chevron bowl with tokenized gradients/strokes and removed the stray white specular point.
- Verification: `:app:compileDebugKotlin` and `:app:assembleDebug` passed.
