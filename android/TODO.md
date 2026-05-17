# TODO - SWIMVPN Android

## High Priority
- [ ] Resolve HTTP 500 on `/api/v1/access/bootstrap` (Diagnose with new logging).
- [x] Fix AAPT error for `foregroundServiceType="vpn"`.
- [ ] Verify 16 KB page alignment on a real Android 15 device/emulator.

## Maintenance
- [x] Update Build Tools and Gradle if necessary to support modern foreground service types.
- [x] Improve error handling in `MainViewModel` for network failures.

## 2026-05-07 Follow-ups
- [ ] Validate service reconnect on real Wi-Fi to cellular handoff.
- [ ] Confirm tun2socks JNI fd ownership on device.
- [ ] Add battery optimization guidance UI only after OEM behavior is verified.

## 2026-05-07 Provider Sample Follow-up
- [ ] Real-device test for the provider VLESS Reality node after parser normalization fix.

## 2026-05-17 Phase 1 Runtime Stability Follow-ups
- [ ] Validate the Xray local SOCKS readiness probe on a real Android device with VLESS Reality.
- [ ] Add a tun2socks readiness/data-plane probe after JNI startup if device testing exposes false RUNNING states.
- [ ] Add runtime readiness unit tests if Android test harness setup allows service-level coroutine testing.

## 2026-05-17 Phase 2 Runtime Generation Follow-ups
- [ ] Add TunnelRuntimeAdapter snapshot tests for VLESS Reality TCP/gRPC, VMess WS TLS, Trojan WS TLS, and Shadowsocks.
- [ ] Verify imported full JSON Xray/V2Ray documents retain custom routing/inbounds after TunnelRuntimeAdapter augmentation.
- [ ] Decide whether Shadowsocks plugin runtime support should be implemented or blocked with a clear unsupported-runtime error.

## 2026-05-17 Phase 4 Obfuscation Follow-ups
- [ ] Validate complete VLESS Reality configs on a real device after runtime guard changes.
- [ ] Decide whether to package/support a verified Shadowsocks SIP003 plugin runtime or keep plugin configs blocked with a clear message.
- [ ] Keep fragment/noise/noises as preserved metadata only until Xray runtime support is verified with real provider fixtures.

## 2026-05-17 Phase 5 Speed Follow-ups
- [ ] Validate latency refresh behavior on a large production server list over Wi-Fi and cellular.
- [ ] Add UX affordance for manual forced latency refresh if cache TTL feels stale.
- [ ] Consider feeding measured handshake success/failure into server ordering after real-device metrics are collected.

## 2026-05-17 Phase 6 Runtime Contract Follow-ups
- [ ] Run `TunnelRuntimeAdapterSnapshotTest` in the Android CI/tooling environment where Gradle wrapper and JetBrains JDK toolchain are available.
- [ ] Add anonymized provider fixture snapshots after real VLESS Reality, VMess, Trojan, and Shadowsocks configs are collected.
