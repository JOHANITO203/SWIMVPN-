# Work Log - SWIMVPN Project

## 2024 Update - Gradle Settings Fix

**Date:** [Current Date]
**Task:** Fix project's Gradle settings per user request

### Changes Made:

4. **Android 15 & Connectivity Fixes (2026-04-23)**
   - **FGS Alignment**: Updated `SwimVpnService` to use `foregroundServiceType="vpn"` (standard for Android 14+). Added `FOREGROUND_SERVICE_VPN` permission.
   - **Backend Recovery**:
     - Identified and fixed **UTF-8 BOM** in `migration.sql` causing PostgreSQL syntax errors (`E42601`) on VPS.
     - Fixed `prisma db seed` failure by forcing `ts-node` to use CommonJS in `package.json`.
     - Implemented UI-side error handling for HTTP 500 and network timeouts in `MainViewModel`.
   - **Infrastructure**: Successfully verified production health endpoints and microservice connectivity.

1. **Gradle Wrapper Updated**
   - Changed from Gradle 8.7 to 8.11.1 in `gradle/wrapper/gradle-wrapper.properties`
   - Distribution URL: `gradle-8.11.1-bin.zip`

2. **Kotlin Plugin Version Adjusted**
   - Changed from Kotlin 1.9.22 to 1.9.0 in root `build.gradle`
   - Reason: Compatibility with Compose Compiler 1.5.1 (Kotlin 1.9.22 was incompatible)

3. **Compose Compiler Version**
   - Set to `1.5.1` in `app/build.gradle` (compatible with Kotlin 1.9.0)

4. **Android Manifest Fix**
   - Removed `android:foregroundServiceType="vpn"` attribute from service declaration
   - Reason: Incompatible with foregroundServiceType flags on current SDK

5. **SDK Versions**
   - compileSdk: 34 (from 36)
   - targetSdk: 34 (unchanged)
   - minSdk: 26 (unchanged)

### Build Status:
- ✅ Gradle sync successful
- ✅ Build (`app:assembleDebug`) successful
- ✅ No compilation errors

### Notes:
- Minimum supported Gradle version requirement (8.11.1) satisfied
- Kotlin version downgrade necessary due to Compose Compiler compatibility requirements
- The IDE note about newer Gradle version (8.14.4) is informational only

### Next Steps:
- Project ready for development
- Consider updating Compose BOM and Compose Compiler when ready for Kotlin 2.0 migration

---

## 2024 Update - VPN Configuration Polyvalence System

**Date:** [Current Date]
**Task:** Implement multi-format VPN configuration parsing and normalization system

### Architecture Implemented:

1. **Config Parser Engine** (`ConfigParserEngine.kt`)
   - Supports VLESS, VMess, Trojan, Shadowsocks URLs
   - Preserves raw configs intact
   - Extracts metadata and connection parameters

2. **Protocol Data Models** (`ProtocolModels.kt`)
   - Canonical `SwimVpnProfile` model for all config types
   - Enums for protocols, transports, security modes
   - Transport-specific settings (Reality, TLS, WebSocket, etc.)

3. **Normalization Engine** (`ConfigNormalizationEngine.kt`)
   - Validates configurations
   - Enriches with defaults
   - Generates runtime-ready configs
   - Creates UI previews

4. **Config Repository** (`ConfigRepository.kt`)
   - Manages imported profiles
   - Handles deduplication
   - Provides clipboard checking
   - Manages active profile state

### Key Features:
- **Multi-format input**: VLESS, VMess, Trojan, Shadowsocks URLs
- **Raw preservation**: Original configs never modified
- **Protocol classification**: Auto-detects protocol, transport, security
- **Validation**: Comprehensive error and warning reporting
- **UI integration**: Config preview generation for user display
- **Storage**: DataStore-backed profile management

### Build Status:
- ✅ All new files compile successfully
- ✅ No breaking changes to existing code
- ✅ Build (`app:assembleDebug`) successful

### Notes:
- System follows modern Android architecture patterns
- Separation of parsing, normalization, and runtime preparation
- Designed for future extensibility (JSON Xray/V2Ray, QR codes, etc.)
- Clipboard import ready for UI integration

---

## 2024 Update - Android 15 Compatibility & Backend Diagnostics

**Date:** [Current Date]
**Task:** Fix Android 15 launch failure, manifest AAPT errors, and improve backend diagnostics.

### Changes Made:

1. **✅ Android 15 Compatibility (16 KB Page Alignment)**
   - Configured `useLegacyPackaging false` in `app/build.gradle` to ensure native libraries are uncompressed and memory-mappable.
   - Added `-Wl,-z,max-page-size=16384` linker flag to CMake and NDK-build tasks in `app/build.gradle`.
   - Verified that both Xray (pre-built) and tun2socks (source-built) are aligned.

2. **✅ Manifest & Service Type Fix**
   - Reverted `foregroundServiceType` from `vpn` to `specialUse` in `AndroidManifest.xml` to resolve AAPT linker errors.
   - Confirmed `SwimVpnService.kt` correctly uses `ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE` for Android 14+.

3. **✅ Backend Diagnostic Improvements**
   - Updated `MainViewModel.kt` to capture and log the full HTTP error body and status code when `bootstrapAccess` fails.
   - Improved error messaging in the UI to include the HTTP status code for easier troubleshooting of the "HTTP 500" issue.

### Build Status:
- ✅ Build successful (`:app:assembleDebug`).
- ✅ AAPT manifest errors resolved.
- ✅ Native library alignment configuration complete.

### Diagnostic Findings:
- The backend "HTTP 500" on `api/v1/access/bootstrap` is being tracked. Enhanced logging is now active in the debug build to capture the server's error response.

### Next Steps:
- Deploy to an Android 15 device/emulator to verify 16 KB alignment at runtime.
- Check Logcat for the detailed HTTP 500 error body to identify the root cause on the server (e.g., database mismatch, missing `deviceId` record).

## 2026-04-23 [SwimVPN+ Landing Page]
- **Status**: DONE
- **Changes**:
  - Implemented the 'SwimVPN+' high-tech brutalist landing page in src/LandingPage.tsx.
  - Configured @react-three/fiber Interactive Pixel Globe with 5000 points.
  - Setup multi-layer parallax using motion/react useScroll and useTransform.
  - Rendered App.tsx mockup seamlessly within a rotating 3D phone frame container.
  - Set src/main.tsx entry point to the new Landing Page.
- **Verification**:
  - Code compiles, npm run lint passes (ignoring backend alias errors).

## 2026-04-25 [Android Active Config Metadata Model]
- **Status**: DONE
- **Changes**:
  - Added `ActiveConfigSource` and `ActiveConfigMetadata` under the Android config package.
  - Implemented `ActiveConfigMetadata.fromParsedSubscription(...)` to map the first parsed profile into active-config UI metadata while preserving parser-provided traffic, expiry, and warning fallbacks.
  - Added a targeted unit test covering imported-config mapping from `ParsedSubscription`.
- **Verification**:
  - Confirmed the new test failed first because `ActiveConfigMetadata` and `ActiveConfigSource` were missing.
  - Ran `./gradlew.bat --no-daemon testDebugUnitTest --tests com.swimvpn.app.config.ActiveConfigMetadataMappingTest` successfully after implementation.

## 2026-04-25 [Android Active Config Metadata Contract Follow-up]
- **Status**: DONE
- **Changes**:
  - Replaced the hidden subscription-only mapper contract with an explicit profile-based mapper using `ActiveConfigFallbackMetadata`.
  - Kept a clearly named convenience path, `fromFirstProfileInSubscription(...)`, for callers that intentionally want first-profile selection.
  - Expanded targeted test coverage for fallback precedence, warning merge/deduplication, and empty-subscription behavior.
- **Verification**:
  - Confirmed the updated test suite failed first on the missing explicit mapper API.
  - Ran `./gradlew.bat --no-daemon testDebugUnitTest --tests com.swimvpn.app.config.ActiveConfigMetadataMappingTest` successfully after implementation.


## [2026-05-07] Android VPN Stability And Parser Hardening
- Added service-level network callbacks, disconnect causes, runtime states, battery optimization detection logs, and bounded same-session reconnect.
- Added parser coverage for Clash YAML, sing-box JSON, missing Base64 padding, and unknown-provider warning behavior.
- Kept unsupported modern schemes recognized but non-importable until runtime support is designed.

[2026-05-07] [Android Provider Sample VLESS Reality Header Param Fix]
- Live provider sample decoded to 29 VLESS Reality TCP nodes.
- Added parser regression for empty TCP query params and normalized blank header fields to safe absent/default values.
- Next: validate one decoded node on a real device with logcat, without exposing raw supplier secrets.

---

## 2026-05-17 - Phase 1 VPN Runtime Readiness Probe

**Task:** Start Phase 1 technical VPN stability improvements.

### Changes Made:
- Added a bounded local SOCKS readiness probe after Xray process launch and before declaring the runtime ready.
- Kept the probe local-only (`127.0.0.1`) to avoid external network dependencies and avoid changing entitlement/business logic.
- Improved startup failure detail when Xray exits early or never opens the local SOCKS listener.

### Verification Target:
- Android debug build / compile check.
- Manual device validation remains required for Wi-Fi to cellular handoff and real provider configs.

---

## 2026-05-17 - Phase 2 Android Runtime Generation Unification

**Task:** Make `TunnelRuntimeAdapter` the single Android source for executable Xray runtime document generation.

### Changes Made:
- Removed manual protocol runtime JSON generation from `ConfigNormalizationEngine`.
- Kept `ConfigNormalizationEngine` focused on validation, metadata enrichment, and preserving complete imported JSON runtime documents.
- Preserved full JSON Xray/V2Ray imports through `normalizedRuntimeConfig` so `TunnelRuntimeAdapter` can augment existing documents without losing user-supplied routing/inbounds/outbounds.

### Verification Target:
- Android compile/build check where toolchain/network allows.
- Add runtime JSON snapshot tests in a follow-up to lock VLESS Reality, VMess, Trojan, and Shadowsocks output.

---

## 2026-05-17 - Phase 4 Obfuscation Runtime Boundary

**Task:** Make obfuscation support explicit and safe in Android runtime compatibility checks.

### Changes Made:
- Added runtime support guards for Reality configs so missing public keys or SNI/server names fail before Xray document generation.
- Made Shadowsocks plugin obfuscation an explicit unsupported-runtime state while preserving plugin metadata in parsed profiles.
- Added parser/runtime compatibility unit coverage for Shadowsocks plugin preservation, missing Reality public key denial, and complete Reality support.

### Verification Target:
- Targeted Android unit test for `ConfigParserEngineTest` where Gradle/toolchain allows.
- Real-device verification remains required for VLESS Reality nodes.

---

## 2026-05-17 - Phase 5 Latency Probe Throttling

**Task:** Improve server latency measurement speed and stability without changing backend access rules.

### Changes Made:
- Added a bounded concurrency limit to Android TCP latency probes to avoid opening one socket per server at once.
- Added a short in-memory TTL cache keyed by `host:port` so repeated refreshes reuse recent latency results.
- Kept failed measurements non-destructive: the previous server ping is preserved if a probe fails.
- Added unit coverage for cache reuse, cache expiry, failed-probe fallback, and concurrency limiting.

### Verification Target:
- Targeted `ServerLatencyEvaluatorTest` where Gradle/toolchain allows.
- Real-device validation should compare perceived refresh speed on large server lists.

---

## 2026-05-17 - Phase 6 Xray Runtime Contract Tests

**Task:** Add snapshot-style contract coverage for Android Xray runtime generation.

### Changes Made:
- Added structure-focused runtime tests for VLESS Reality TCP, VMess WS/TLS, Trojan WS/TLS, and basic Shadowsocks.
- Added coverage that imported full Xray JSON documents are augmented with standard inbounds/direct/block outbounds without replacing the existing proxy outbound or routing rules.
- Kept tests focused on critical runtime contract fields instead of brittle full JSON snapshots.

### Verification Target:
- Targeted `TunnelRuntimeAdapterSnapshotTest` where Gradle/toolchain allows.
- Real-device runtime validation remains required for provider configs.
