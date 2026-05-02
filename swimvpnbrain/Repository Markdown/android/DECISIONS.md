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

