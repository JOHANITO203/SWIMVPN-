# Architectural Decisions - SWIMVPN

## 16 KB Page Alignment (Android 15)
- **Decision**: Forced page size to 16384 bytes using linker flags.
- **Reason**: Required for Android 15 compatibility on devices with 16 KB page size.
- **Implementation**: Added `-Wl,-z,max-page-size=16384` to CMake and NDK-build.

## Foreground Service Type
- **Decision**: Temporarily using `specialUse`.
- **Reason**: `vpn` type caused AAPT linking errors.
- **Goal**: Revert to `vpn` once Build Tools issues are resolved.

## Native Library Packaging
- **Decision**: `useLegacyPackaging false`.
- **Reason**: Needed to store `.so` files uncompressed and aligned in the APK.

## 2026-04-23: Frontend Landing Page
- **Decision:** Integrate a high-tech brutalist landing page at the React project root using Three.js and Framer Motion.
- **Context:** User requested a premium landing page for SwimVPN+ showcasing a 3D globe and interactive mobile mockup.
- **Consequences:** Adds @react-three/fiber to the frontend bundle. Reuses the existing App.tsx as an embedded 3D component (Mockup Flottant) to ensure accurate UI previewing.


## 2026-05-17: Xray Runtime Readiness Boundary
- **Decision**: Treat the local Xray SOCKS listener as the first readiness boundary before Android marks a VPN/local-proxy runtime as ready.
- **Reason**: A live Xray process alone can still be unusable if the inbound proxy failed to bind or startup is still incomplete.
- **Scope**: Local-only TCP probe on `127.0.0.1`; no external network probe and no entitlement/business-rule changes.

## 2026-05-17: Single Android Xray Runtime Generator
- **Decision**: `TunnelRuntimeAdapter` is the only Android component that generates executable Xray runtime documents from normalized profiles.
- **Reason**: Manual JSON string generation in normalization duplicated runtime behavior and risked invalid JSON or dropped protocol parameters.
- **Scope**: `ConfigNormalizationEngine` now validates/enriches profiles and preserves full imported JSON documents, but does not synthesize protocol runtime JSON.

## 2026-05-17: Explicit Obfuscation Runtime Boundary
- **Decision**: Android runtime blocks unsupported Shadowsocks plugin obfuscation and incomplete Reality configs instead of generating a best-effort Xray document.
- **Reason**: Running a plugin config without its obfuscation layer or a Reality config without a public key can create false connection states and insecure/incorrect behavior.
- **Scope**: Metadata remains preserved for import/audit; runtime support must be verified before enabling plugin or fragment/noise execution.

## 2026-05-17: Bounded Server Latency Probing
- **Decision**: Android latency refresh uses bounded TCP probe concurrency and a short in-memory host:port cache.
- **Reason**: Measuring every server at once can overload mobile networks and slow the UI; repeated refreshes should not reopen identical sockets immediately.
- **Scope**: This only affects client-side latency UX and does not change backend entitlement or premium server exposure rules.

## 2026-05-17: Runtime Contract Snapshot Testing
- **Decision**: Runtime generation tests assert stable Xray contract fields instead of entire pretty-printed JSON documents.
- **Reason**: Field-level contract checks catch protocol regressions while avoiding brittle failures from harmless JSON ordering/formatting changes.
- **Scope**: Tests cover generated Android runtime documents only; real-device provider validation remains separate.
