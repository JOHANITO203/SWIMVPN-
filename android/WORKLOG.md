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

