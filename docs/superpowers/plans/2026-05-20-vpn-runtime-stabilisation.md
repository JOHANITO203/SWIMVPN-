# VPN Runtime Stabilisation Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Reduce spontaneous VPN disconnects by making service-kill recovery, network handoff, diagnostics, and battery/OEM handling deterministic and testable.

**Architecture:** Keep the Android VPN runtime centered on `SwimVpnService`, but move fragile decisions into small pure Kotlin policy classes that can be unit-tested without a device. Preserve raw VPN configs and entitlement checks; this plan changes runtime resilience only.

**Tech Stack:** Android Kotlin, `VpnService`, Kotlin coroutines, DataStore/SharedPreferences, JUnit unit tests, Gradle debug builds.

---

## File Structure

- Modify: `android/app/src/main/java/com/swimvpn/app/SwimVpnService.kt`
  - Owns foreground service lifecycle, Xray/tun2socks runtime, reconnect scheduling, network callback, and persisted runtime state writes.
- Modify: `android/app/src/main/java/com/swimvpn/app/vpn/RuntimeStateStore.kt`
  - Persist enough last-known runtime state to recover after service kill and diagnose why a tunnel stopped.
- Modify: `android/app/src/main/java/com/swimvpn/app/vpn/VpnManager.kt`
  - Expose stable diagnostics to UI without wiping the last failure evidence.
- Modify: `android/app/src/main/java/com/swimvpn/app/vpn/RuntimeModels.kt`
  - Add diagnostic fields only if needed by the runtime store and UI.
- Modify: `android/app/src/main/java/com/swimvpn/app/vpn/StickyReconnectPolicy.kt`
  - Keep existing sticky policy but split "recover an active killed tunnel" from "boot autoconnect".
- Create: `android/app/src/main/java/com/swimvpn/app/vpn/RuntimeRecoveryPolicy.kt`
  - Pure policy deciding whether a killed foreground VPN session may be restored from a stored payload.
- Create: `android/app/src/main/java/com/swimvpn/app/vpn/NetworkHandoffPolicy.kt`
  - Pure policy for delaying reconnect after `onLost()` and cancelling it when `onAvailable()` arrives quickly.
- Modify: `android/app/src/main/java/com/swimvpn/app/MainViewModel.kt`
  - Keep auto-connect semantics aligned with the new recovery contract.
- Modify: `android/app/src/main/java/com/swimvpn/app/data/local/PreferencesManager.kt`
  - Reuse existing `AutoConnectPayload` storage as last runtime payload; do not store secrets in logs.
- Modify: `android/app/src/main/java/com/swimvpn/app/ui/screens/TechnicalSettingsScreen.kt`
  - Add battery/OEM warning and action if Android is not ignoring battery optimizations.
- Modify: `android/app/src/main/AndroidManifest.xml`
  - Add battery optimization intent permission if the UX action is implemented.
- Test: `android/app/src/test/java/com/swimvpn/app/vpn/StickyReconnectPolicyTest.kt`
- Test: `android/app/src/test/java/com/swimvpn/app/vpn/RuntimeRecoveryPolicyTest.kt`
- Test: `android/app/src/test/java/com/swimvpn/app/vpn/NetworkHandoffPolicyTest.kt`
- Test: `android/app/src/test/java/com/swimvpn/app/vpn/RuntimeStateStoreTest.kt`

---

## Phase 0: Baseline Evidence Before Fixes

**Purpose:** Verify the current disconnect signature before changing behavior.

- [ ] Run the current Android unit suite.

Run:

```powershell
cd android
.\gradlew.bat testDebugUnitTest
```

Expected: existing tests pass before runtime changes.

- [ ] During next device run, capture these logcat filters:

```bash
adb logcat -v time SwimVpnService:D MainViewModel:D Tun2SocksJni:D AndroidRuntime:E *:S
```

Expected evidence to classify disconnects:

- `service_destroyed`
- `battery_optimization_detected`
- `underlying_network_lost`
- `network_available`
- `reconnect_scheduled`
- `reconnect_failed`
- `engine_crashed`
- `xray_not_alive_*`
- `tun2socks_not_alive`
- `vpn_revoked`

---

## Phase 1: Separate Killed-Service Recovery From Auto-Connect

**Problem:** `restoreStickySessionIfAllowed()` refuses restore when `autoConnect` is disabled. That makes sense for boot, but not for recovering a tunnel Android killed while it was active.

**Files:**
- Create: `android/app/src/main/java/com/swimvpn/app/vpn/RuntimeRecoveryPolicy.kt`
- Test: `android/app/src/test/java/com/swimvpn/app/vpn/RuntimeRecoveryPolicyTest.kt`
- Modify: `android/app/src/main/java/com/swimvpn/app/SwimVpnService.kt`

- [x] Add failing tests for killed-service recovery.

Test cases:

```kotlin
@Test fun activeRunningSnapshotWithPayloadRestoresEvenWhenAutoConnectIsDisabled()
@Test fun idleSnapshotNeverRestores()
@Test fun staleRunningSnapshotDoesNotRestore()
@Test fun fullTunnelWithoutVpnPermissionDoesNotRestore()
@Test fun missingPayloadDoesNotRestore()
```

Run:

```powershell
cd android
.\gradlew.bat testDebugUnitTest --tests "com.swimvpn.app.vpn.RuntimeRecoveryPolicyTest"
```

Expected: fail because `RuntimeRecoveryPolicy` does not exist yet.

- [x] Implement `RuntimeRecoveryPolicy`.

Contract:

```kotlin
object RuntimeRecoveryPolicy {
    fun shouldRecoverKilledSession(
        snapshot: RuntimeStateSnapshot,
        payloadAvailable: Boolean,
        vpnPermissionAvailable: Boolean,
        nowMs: Long = System.currentTimeMillis(),
    ): Boolean
}
```

Expected behavior:

- restore `STARTING`, `RUNNING`, `RECONNECTING`, `DEGRADED` when fresh;
- require payload;
- require VPN permission for `FULL_TUNNEL`;
- ignore `autoConnect`;
- never restore `IDLE`, `STOPPING`, `FAILED`, `STOPPED_BY_USER`.

- [x] Update `SwimVpnService.restoreStickySessionIfAllowed()`.

Change intent:

- read payload first;
- evaluate `RuntimeRecoveryPolicy`;
- remove the `autoConnectFlow.first()` gate from killed-service restore;
- keep boot/package restore in `MainViewModel.maybeRestoreAutoConnectFromBoot()` gated by `autoConnect`.

- [x] Verify Phase 1.

Run:

```powershell
cd android
.\gradlew.bat testDebugUnitTest --tests "com.swimvpn.app.vpn.RuntimeRecoveryPolicyTest"
.\gradlew.bat testDebugUnitTest
```

Expected: all tests pass.

---

## Phase 2: Stop `onDestroy()` From Converting System Kill Into Clean Stop

**Problem:** `onDestroy()` currently calls `stopVpn()` and can clear runtime state. A system kill can therefore look like a normal stop, preventing recovery.

**Files:**
- Modify: `android/app/src/main/java/com/swimvpn/app/SwimVpnService.kt`
- Test: `android/app/src/test/java/com/swimvpn/app/vpn/RuntimeServiceDestroyPolicyTest.kt`

- [x] Add service destroy recovery policy tests.

Test cases:

```kotlin
@Test fun systemDestroyDuringActiveStatesPersistsRecoverableReconnectingState()
@Test fun userDestroyDoesNotPersistRecoverableState()
@Test fun terminalStatesDoNotPersistRecoverableState()
```

- [x] Change `onDestroy()` semantics.

Expected behavior:

- if `stoppedByUser == true`, keep current manual stop cleanup;
- if not stopped by user and runtime was active, persist a recoverable state with `DisconnectCause.SERVICE_KILLED`;
- do not call `stopForeground(STOP_FOREGROUND_REMOVE)` as a way to erase active recovery evidence for system kills;
- still stop native resources defensively to avoid leaked Xray/tun2socks processes.

- [x] Verify Phase 2.

Run:

```powershell
cd android
.\gradlew.bat testDebugUnitTest --tests "com.swimvpn.app.vpn.RuntimeServiceDestroyPolicyTest"
.\gradlew.bat testDebugUnitTest
```

Expected: killed-service state remains restorable; user stop stays non-restorable.

---

## Phase 3: Debounce Network Handoff Reconnects

**Problem:** `onLost()` immediately schedules reconnect. During Wi-Fi/mobile handoff, Android may emit `onLost()` just before `onAvailable()`, causing unnecessary process restarts and visible disconnects.

**Files:**
- Create: `android/app/src/main/java/com/swimvpn/app/vpn/NetworkHandoffPolicy.kt`
- Test: `android/app/src/test/java/com/swimvpn/app/vpn/NetworkHandoffPolicyTest.kt`
- Modify: `android/app/src/main/java/com/swimvpn/app/SwimVpnService.kt`

- [x] Add failing network policy tests.

Test cases:

```kotlin
@Test fun onLostSchedulesDebouncedReconnect()
@Test fun onAvailableInsideGraceWindowCancelsReconnect()
@Test fun noAvailableAfterGraceWindowAllowsReconnect()
```

Expected constants:

```kotlin
const val NETWORK_HANDOFF_GRACE_MS = 4_000L
```

- [x] Implement debounced handoff behavior.

Expected service behavior:

- on `onLost(activeUnderlyingNetwork)`: mark `DEGRADED`, schedule a delayed reconnect job;
- if `onAvailable()` or usable `onCapabilitiesChanged()` arrives before delay expires: cancel the delayed reconnect and set underlying network;
- if no usable network arrives: call existing `scheduleReconnect(DisconnectCause.NETWORK_LOST, "underlying_network_lost")`.

- [x] Verify Phase 3.

Run:

```powershell
cd android
.\gradlew.bat testDebugUnitTest --tests "com.swimvpn.app.vpn.NetworkHandoffPolicyTest"
.\gradlew.bat testDebugUnitTest
```

Expected: no immediate reconnect on normal handoff.

---

## Phase 4: Preserve Last Disconnect Diagnostics

**Problem:** `stopVpn()` clears runtime diagnostics quickly, so the UI loses log paths and cause after a failure.

**Files:**
- Modify: `android/app/src/main/java/com/swimvpn/app/vpn/RuntimeModels.kt`
- Modify: `android/app/src/main/java/com/swimvpn/app/vpn/RuntimeStateStore.kt`
- Modify: `android/app/src/main/java/com/swimvpn/app/vpn/VpnManager.kt`
- Modify: `android/app/src/main/java/com/swimvpn/app/SwimVpnService.kt`
- Test: `android/app/src/test/java/com/swimvpn/app/vpn/VpnManagerDiagnosticsTest.kt`

- [x] Add failing diagnostics persistence tests.

Test cases:

```kotlin
@Test fun stoppedRuntimePreservesLastDisconnectCauseAndLogPaths()
@Test fun activeHeartbeatDoesNotEraseLastFailureEvidence()
```

Diagnostic fields to preserve:

- `lastDisconnectCause`
- `reconnectCount`
- `sessionStartedAt`
- `xrayLogPath`
- `tun2SocksLogPath`
- `lastRuntimeEventAt`

- [x] Implement persistence.

Expected behavior:

- `clearRuntimeDiagnostics()` clears active session fields only;
- last failure fields survive until a new successful `RUNNING` session is established;
- UI diagnostic card can still show the last cause and log path after disconnect.

- [x] Verify Phase 4.

Run:

```powershell
cd android
.\gradlew.bat testDebugUnitTest --tests "com.swimvpn.app.vpn.VpnManagerDiagnosticsTest"
.\gradlew.bat testDebugUnitTest
```

Expected: failure evidence survives cleanup.

---

## Phase 5: Add Battery/OEM Hardening UX

**Problem:** App only logs `battery_optimization_detected`; it does not help the user remove the kill source.

**Files:**
- Modify: `android/app/src/main/AndroidManifest.xml`
- Modify: `android/app/src/main/java/com/swimvpn/app/ui/screens/TechnicalSettingsScreen.kt`
- Modify: `android/app/src/main/res/values/strings.xml`
- Modify: `android/app/src/main/res/values-fr/strings.xml`
- Modify: `android/app/src/main/res/values-ru/strings.xml`

- [x] Add manifest permission.

Permission:

```xml
<uses-permission android:name="android.permission.REQUEST_IGNORE_BATTERY_OPTIMIZATIONS" />
```

- [x] Add Technical Settings action.

Expected behavior:

- if `PowerManager.isIgnoringBatteryOptimizations(packageName)` is false, show a warning row;
- action opens `Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS` with `package:` URI;
- if request action fails, open `Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS`.

- [x] Verify Phase 5.

Run:

```powershell
cd android
.\gradlew.bat testDebugUnitTest
.\gradlew.bat assembleDebug
```

Manual QA:

- open Technical Settings;
- confirm battery warning appears when optimization is active;
- tap action and confirm Android settings opens.

---

## Phase 6: Make Connected State Less Optimistic

**Problem:** `RUNNING` is set after process start, not after a real data-path proof. If Xray starts but cannot reach server, the app can show connected until a later crash/failure.

**Files:**
- Modify: `android/app/src/main/java/com/swimvpn/app/SwimVpnService.kt`
- Modify: `android/app/src/main/java/com/swimvpn/app/vpn/RuntimeModels.kt` only if a new `VERIFYING` status is chosen.
- Test: targeted pure policy tests if a new health policy is extracted.

- [x] Add a small post-start verification window.

Conservative target:

- keep `STARTING` while Xray and tun2socks are armed;
- mark `RUNNING` after runtime survives the first short monitor interval;
- if Xray/tun2socks exits before that, classify as startup failure, not successful connection.

- [x] Do not add remote HTTP probing in the first pass.

Reason: probing external domains through the tunnel can add false failures, privacy questions, and device-dependent behavior. Start with process/data-plane proof first.

- [x] Verify Phase 6.

Run:

```powershell
cd android
.\gradlew.bat testDebugUnitTest
.\gradlew.bat assembleDebug
```

Manual QA:

- connect to a valid node;
- connect to a deliberately invalid config;
- confirm invalid config does not show stable connected state.

---

## Phase 7: Full Verification And Live Diagnostics

**Files:**
- Modify: `WORKLOG.md`
- Modify: `TODO.md`
- Modify: `DECISIONS.md` if the recovery contract is changed.

- [x] Run full local verification.

```powershell
cd android
.\gradlew.bat testDebugUnitTest assembleDebug
.\gradlew.bat --stop
cd ..
git diff --check
git status --short --branch
```

- [ ] Run live device QA.

Scenarios:

- connect and keep screen off for 20 minutes;
- switch Wi-Fi to mobile;
- switch mobile to Wi-Fi;
- kill app task from recents;
- simulate service process death if possible;
- revoke VPN permission and confirm clean failure;
- use valid backend managed config;
- use imported config.

- [ ] Capture logcat.

```bash
adb logcat -v time SwimVpnService:D MainViewModel:D Tun2SocksJni:D AndroidRuntime:E *:S
```

Expected after fixes:

- network handoff should show `network_lost`, then `network_available`, without unnecessary `reconnect_started` when handoff is fast;
- system kill should show recoverable state and sticky restore;
- battery optimization warning should be visible when relevant;
- diagnostics should preserve last cause and log paths after disconnect.

Review follow-up:

- [x] Do not convert startup coroutine cancellation during service destroy into `FAILED/UNKNOWN`.
- [x] Keep `RuntimeStateStore.clear()` as a true persistent reset while preserving diagnostics through normal runtime writes.

---

## Execution Order

1. Phase 1: killed-service recovery independent from auto-connect.
2. Phase 2: safe `onDestroy()` semantics.
3. Phase 3: network handoff debounce.
4. Phase 4: persistent diagnostics.
5. Phase 5: battery/OEM hardening UX.
6. Phase 6: less optimistic connected state.
7. Phase 7: full verification and live QA.

This order fixes the highest-probability spontaneous disconnect causes first, then improves observability, then refines connection truth.

---

## Deployment Notes

- No backend contract change is expected.
- No entitlement logic should be touched.
- No raw VPN config mutation is allowed.
- Release build should be checked after runtime changes because minify/shrink can affect service/native integration.
- If native crashes persist after Phases 1-4, inspect `xray.stderr.log`, `tun2socks.exit`, and `AndroidRuntime:E` before changing the runtime engine.
