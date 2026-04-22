# TODO - SWIMVPN Android

## High Priority
- [ ] Resolve HTTP 500 on `/api/v1/access/bootstrap` (Diagnose with new logging).
- [x] Fix AAPT error for `foregroundServiceType="vpn"`.
- [ ] Verify 16 KB page alignment on a real Android 15 device/emulator.

## Maintenance
- [x] Update Build Tools and Gradle if necessary to support modern foreground service types.
- [x] Improve error handling in `MainViewModel` for network failures.
