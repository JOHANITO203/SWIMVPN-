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
