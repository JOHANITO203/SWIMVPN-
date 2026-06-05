# Purple-Team Shaping — Stage B Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Close the loop during a live session — detect "connected but unhealthy" via a through-tunnel probe and morph the shaping profile (then switch server) to restore connectivity, learning per network.

**Architecture:** A pure debounce core (`TunnelHealthSentinel`) decides "degraded" from a stream of probe outcomes; a thin scheduler in `MainViewModel` probes through xray's local SOCKS while connected and routes "degraded" into the EXISTING failure pipeline (`handleAdaptiveRuntimeFailure` → `planAfterFailure`). `planAfterFailure` gains a `MORPH_PROFILE` step (try the next untried shaping profile on the same server, executed via the existing `ACTION_RESTART`) inserted before `SWITCH_SERVER`. Everything is gated by the AI toggle.

**Tech Stack:** Kotlin/Android, coroutines, Java SOCKS proxy (`java.net.Proxy`), JUnit. RAM-constrained machine → targeted `testDebugUnitTest` with `--rerun-tasks` (this machine's incremental cache corrupts → spurious `Unresolved reference`/`NoClassDefFoundError`); `assembleDebug` only for the device-validation gate.

---

## Scope

Stage B only. Source spec: `docs/superpowers/specs/2026-06-06-purple-team-shaping-stage-b-design.md`. Balanced posture: degraded after **3** consecutive failed probes; **SHAPING_MORPH_LIMIT = 2** morphs/incident. Out of scope: Stage C (cover), Observer #2, server control, ALPN/pacing, xray-log parsing.

## File Structure

- `android/app/src/main/java/com/swimvpn/app/adaptive/TunnelHealthSentinel.kt` — **new**: pure debounce state machine (probe-outcome stream → HEALTHY/DEGRADED). One responsibility, no Android deps, unit-tested.
- `android/app/src/main/java/com/swimvpn/app/adaptive/AdaptiveDecisionAgent.kt` — **modify**: `MORPH_PROFILE` action type, `targetProfileId` on `DecisionAction`, `SHAPING_MORPH_LIMIT`, `nextUntriedProfile`, and the cascade branch in `planAfterFailure`.
- `android/app/src/main/java/com/swimvpn/app/adaptive/AgentDisabledFailurePolicy.kt` — **modify**: also downgrade `MORPH_PROFILE` when AI off.
- `android/app/src/main/java/com/swimvpn/app/MainViewModel.kt` — **modify**: sentinel lifecycle + probe I/O, route degraded into `handleAdaptiveRuntimeFailure`, execute `MORPH_PROFILE` via `ACTION_RESTART` with a forced profile id, incident tried-profile state.
- tests: `TunnelHealthSentinelTest` (new), `AdaptiveDecisionAgentTest`/`CamouflageAdaptiveTest` (extend), `AgentDisabledFailurePolicyTest` (extend).

---

## Task 0: Branch

- [ ] **Step 1**

```bash
git checkout main
git checkout -b feat/purple-team-shaping-b
```

---

## Task 1: `TunnelHealthSentinel` — pure debounce core

**Files:**
- Create: `android/app/src/main/java/com/swimvpn/app/adaptive/TunnelHealthSentinel.kt`
- Test: `android/app/src/test/java/com/swimvpn/app/adaptive/TunnelHealthSentinelTest.kt`

- [ ] **Step 1: Write the failing test**

```kotlin
package com.swimvpn.app.adaptive

import org.junit.Assert.assertEquals
import org.junit.Test

class TunnelHealthSentinelTest {

    @Test
    fun `stays healthy until the threshold of consecutive failures`() {
        val s = TunnelHealthSentinel(degradedThreshold = 3)
        assertEquals(HealthVerdict.HEALTHY, s.onProbe(success = false)) // 1
        assertEquals(HealthVerdict.HEALTHY, s.onProbe(success = false)) // 2
        assertEquals(HealthVerdict.DEGRADED, s.onProbe(success = false)) // 3 -> degraded
    }

    @Test
    fun `a single success resets the failure streak`() {
        val s = TunnelHealthSentinel(degradedThreshold = 3)
        s.onProbe(false); s.onProbe(false)
        assertEquals(HealthVerdict.HEALTHY, s.onProbe(success = true)) // reset
        assertEquals(HealthVerdict.HEALTHY, s.onProbe(success = false)) // 1 again
        assertEquals(HealthVerdict.HEALTHY, s.onProbe(success = false)) // 2
        assertEquals(HealthVerdict.DEGRADED, s.onProbe(success = false)) // 3
    }

    @Test
    fun `reset clears the streak`() {
        val s = TunnelHealthSentinel(degradedThreshold = 3)
        s.onProbe(false); s.onProbe(false)
        s.reset()
        assertEquals(HealthVerdict.HEALTHY, s.onProbe(success = false)) // back to 1
    }

    @Test
    fun `degraded latches only once until reset (no repeated firing)`() {
        val s = TunnelHealthSentinel(degradedThreshold = 2)
        assertEquals(HealthVerdict.HEALTHY, s.onProbe(false))
        assertEquals(HealthVerdict.DEGRADED, s.onProbe(false)) // fires
        assertEquals(HealthVerdict.ALREADY_DEGRADED, s.onProbe(false)) // does not re-fire
    }
}
```

- [ ] **Step 2: Run, verify FAIL**

`cd android && ./gradlew :app:testDebugUnitTest --tests "com.swimvpn.app.adaptive.TunnelHealthSentinelTest" --max-workers=1 --no-parallel --no-daemon`
Expected: FAIL (unresolved `TunnelHealthSentinel`/`HealthVerdict`).

- [ ] **Step 3: Implement**

```kotlin
package com.swimvpn.app.adaptive

/** Verdict returned per probe. DEGRADED fires exactly once per streak (then ALREADY_DEGRADED) so the
 *  caller triggers one recovery action, not one per probe. */
enum class HealthVerdict { HEALTHY, DEGRADED, ALREADY_DEGRADED }

/**
 * Pure debounce for "connected but unhealthy": feed it the outcome of each through-tunnel probe; it
 * declares DEGRADED after [degradedThreshold] consecutive failures and latches until a success or
 * [reset]. No Android deps — the probe I/O and scheduling live in the caller.
 */
class TunnelHealthSentinel(private val degradedThreshold: Int = 3) {
    private var consecutiveFailures = 0
    private var latched = false

    fun onProbe(success: Boolean): HealthVerdict {
        if (success) {
            consecutiveFailures = 0
            latched = false
            return HealthVerdict.HEALTHY
        }
        consecutiveFailures += 1
        if (consecutiveFailures < degradedThreshold) return HealthVerdict.HEALTHY
        if (latched) return HealthVerdict.ALREADY_DEGRADED
        latched = true
        return HealthVerdict.DEGRADED
    }

    fun reset() {
        consecutiveFailures = 0
        latched = false
    }
}
```

- [ ] **Step 4: Run, verify PASS** (append `--rerun-tasks` if stale-cache symptom)

`cd android && ./gradlew :app:testDebugUnitTest --tests "com.swimvpn.app.adaptive.TunnelHealthSentinelTest" --rerun-tasks --max-workers=1 --no-parallel --no-daemon`

- [ ] **Step 5: Commit**

```bash
git add android/app/src/main/java/com/swimvpn/app/adaptive/TunnelHealthSentinel.kt android/app/src/test/java/com/swimvpn/app/adaptive/TunnelHealthSentinelTest.kt
git commit -m "feat(autopilot): TunnelHealthSentinel — pure degraded-detection debounce"
```

---

## Task 2: `MORPH_PROFILE` decision + next-untried-profile selection

**Files:**
- Modify: `android/app/src/main/java/com/swimvpn/app/adaptive/AdaptiveDecisionAgent.kt`
- Test: `android/app/src/test/java/com/swimvpn/app/adaptive/CamouflageAdaptiveTest.kt`

Read first (exact current lines): `enum class DecisionActionType` (~55, has RECONNECT_SAME/SWITCH_SERVER/GIVE_UP), `data class DecisionAction` (~75, fields type/targetServerId/delayMs/reason), `object AdaptiveDecisionAgent { const MAX_RECONNECT_ATTEMPTS=5; SAME_SERVER_RETRY_LIMIT=2; ... }` (~83), `fun planAfterFailure(...)` (~267), `fun selectBestCamouflageProfile(score, networkType, profiles)` (returns a `CamouflageProfile`), `CamouflageProfileRepository.fallbackOrder`.

**Key design:** morphs do NOT consume the `reconnectAttempt` budget (the caller does not increment it for a morph) — the morph budget is the size of the tried-profile set. This keeps `MAX_RECONNECT_ATTEMPTS` from cutting off the later `SWITCH_SERVER` phase.

- [ ] **Step 1: Write the failing test** — append to `CamouflageAdaptiveTest`:

```kotlin
    // --- MORPH_PROFILE cascade (Stage B) ---

    @Test
    fun `morph is chosen after same-server retries when an untried profile exists`() {
        val action = AdaptiveDecisionAgent.planAfterFailure(
            currentServerId = "s",
            candidates = listOf(),
            scores = emptyMap(),
            reconnectAttempt = 2, // past SAME_SERVER_RETRY_LIMIT
            nowMs = 1_000L,
            networkType = NetworkType.WIFI,
            currentProfileId = "auto",
            triedProfileIds = setOf("auto"),
        )
        assertEquals(DecisionActionType.MORPH_PROFILE, action.type)
        assertEquals("s", action.targetServerId)
        assertEquals("chrome", action.targetProfileId) // next untried in fallbackOrder after auto
    }

    @Test
    fun `morph stops after the limit and falls through to switch-or-retry`() {
        // 1 initial + SHAPING_MORPH_LIMIT(2) morphs already tried -> no more morphs.
        val action = AdaptiveDecisionAgent.planAfterFailure(
            currentServerId = "s",
            candidates = listOf(),            // no fallback candidate
            scores = emptyMap(),
            reconnectAttempt = 2,
            nowMs = 1_000L,
            networkType = NetworkType.WIFI,
            currentProfileId = "firefox",
            triedProfileIds = setOf("auto", "chrome", "firefox"),
        )
        assertEquals(DecisionActionType.RECONNECT_SAME, action.type) // no fallback -> retry current
    }

    @Test
    fun `same-server retry still precedes any morph`() {
        val action = AdaptiveDecisionAgent.planAfterFailure(
            currentServerId = "s", candidates = listOf(), scores = emptyMap(),
            reconnectAttempt = 0, nowMs = 1_000L, networkType = NetworkType.WIFI,
            currentProfileId = "auto", triedProfileIds = setOf("auto"),
        )
        assertEquals(DecisionActionType.RECONNECT_SAME, action.type)
    }
```

- [ ] **Step 2: Run, verify FAIL** (`planAfterFailure` has no `currentProfileId`/`triedProfileIds` params, no `MORPH_PROFILE`, no `targetProfileId`).

`cd android && ./gradlew :app:testDebugUnitTest --tests "com.swimvpn.app.adaptive.CamouflageAdaptiveTest" --max-workers=1 --no-parallel --no-daemon`

- [ ] **Step 3: Implement** in `AdaptiveDecisionAgent.kt`:

(a) Add to `DecisionActionType`: `MORPH_PROFILE,` (after `SWITCH_SERVER`).

(b) Add a field to `DecisionAction` (default null keeps every existing constructor call valid):
```kotlin
data class DecisionAction(
    val type: DecisionActionType,
    val targetServerId: String?,
    val delayMs: Long,
    val reason: String,
    val targetProfileId: String? = null,
)
```

(c) Add the constant near `SAME_SERVER_RETRY_LIMIT`: `private const val SHAPING_MORPH_LIMIT = 2`.

(d) Add the next-untried-profile helper (uses the agent's existing preference order; skips already-tried; null when exhausted):
```kotlin
    /** The most-preferred shaping profile not yet tried this incident, or null if all are exhausted. */
    fun nextUntriedProfile(triedProfileIds: Set<String>): CamouflageProfile? =
        CamouflageProfileRepository.fallbackOrder.firstOrNull { it.id !in triedProfileIds }
```

(e) Extend `planAfterFailure` signature with two params (append, defaulted so non-Stage-B callers/tests still compile):
```kotlin
    fun planAfterFailure(
        currentServerId: String?,
        candidates: List<ServerDecisionCandidate>,
        scores: Map<String, ServerQualityScore>,
        reconnectAttempt: Int,
        nowMs: Long,
        networkType: NetworkType = NetworkType.UNKNOWN,
        currentProfileId: String? = null,
        triedProfileIds: Set<String> = emptySet(),
    ): DecisionAction {
```
Insert the MORPH phase BETWEEN the existing `RECONNECT_SAME` block (the `if (reconnectAttempt < SAME_SERVER_RETRY_LIMIT ...)` return) and the `selectBestServer` fallback block:
```kotlin
        // Stage B: before abandoning the (premium) server, try the next untried shaping profile on it.
        // Morphs are bounded by SHAPING_MORPH_LIMIT and do NOT consume the reconnect-attempt budget
        // (the caller does not increment reconnectAttempt for a morph).
        val morph = if (!scores[currentServerId].isAvoided(nowMs) && triedProfileIds.size <= SHAPING_MORPH_LIMIT) {
            nextUntriedProfile(triedProfileIds)
        } else null
        if (morph != null) {
            return DecisionAction(
                type = DecisionActionType.MORPH_PROFILE,
                targetServerId = currentServerId,
                delayMs = backoffFor(reconnectAttempt),
                reason = "try_next_shaping_profile_before_switching_server",
                targetProfileId = morph.id,
            )
        }
```
(`backoffFor` and `isAvoided` already exist.)

- [ ] **Step 4: Run, verify PASS** (`--rerun-tasks`). Also run the existing `AdaptiveDecisionAgentTest` to confirm **non-regression** of the old cascade (those calls omit the new params → `triedProfileIds` empty → `nextUntriedProfile(emptySet())` returns AUTO, so a morph WOULD be chosen for old callers!).

  **Non-regression fix:** old `AdaptiveDecisionAgentTest` cases that expect `SWITCH_SERVER`/`RECONNECT_SAME` at `reconnectAttempt>=2` would now get `MORPH_PROFILE`. To preserve their intent, the morph phase must be a NO-OP when the caller opted out. Make the morph conditional on the caller actually using Stage B: only morph when `currentProfileId != null`. Update the guard:
```kotlin
        val morph = if (currentProfileId != null && !scores[currentServerId].isAvoided(nowMs) && triedProfileIds.size <= SHAPING_MORPH_LIMIT) {
            nextUntriedProfile(triedProfileIds)
        } else null
```
  Re-run `CamouflageAdaptiveTest` (passes `currentProfileId`) AND `AdaptiveDecisionAgentTest` (omits it → null → no morph → old behavior). Both green.

`cd android && ./gradlew :app:testDebugUnitTest --tests "com.swimvpn.app.adaptive.CamouflageAdaptiveTest" --tests "com.swimvpn.app.adaptive.AdaptiveDecisionAgentTest" --rerun-tasks --max-workers=1 --no-parallel --no-daemon`

- [ ] **Step 5: Commit**

```bash
git add android/app/src/main/java/com/swimvpn/app/adaptive/AdaptiveDecisionAgent.kt android/app/src/test/java/com/swimvpn/app/adaptive/CamouflageAdaptiveTest.kt
git commit -m "feat(autopilot): MORPH_PROFILE decision step (try next shaping profile before server switch)"
```

---

## Task 3: AI-off gating of `MORPH_PROFILE`

**Files:**
- Modify: `android/app/src/main/java/com/swimvpn/app/adaptive/AgentDisabledFailurePolicy.kt`
- Test: `android/app/src/test/java/com/swimvpn/app/adaptive/AgentDisabledFailurePolicyTest.kt`

- [ ] **Step 1: Write the failing test** — append to `AgentDisabledFailurePolicyTest`:

```kotlin
    @Test
    fun `agent off downgrades MORPH_PROFILE to same-server reconnect`() {
        val planned = DecisionAction(
            type = DecisionActionType.MORPH_PROFILE,
            targetServerId = "s",
            delayMs = 0L,
            reason = "x",
            targetProfileId = "chrome",
        )
        val resolved = AgentDisabledFailurePolicy.resolve(agentEnabled = false, currentServerId = "s", plannedAction = planned)
        assertEquals(DecisionActionType.RECONNECT_SAME, resolved.type)
        assertEquals(null, resolved.targetProfileId)
    }

    @Test
    fun `agent on leaves MORPH_PROFILE unchanged`() {
        val planned = DecisionAction(DecisionActionType.MORPH_PROFILE, "s", 0L, "x", "chrome")
        assertEquals(planned, AgentDisabledFailurePolicy.resolve(true, "s", planned))
    }
```

- [ ] **Step 2: Run, verify FAIL**

`cd android && ./gradlew :app:testDebugUnitTest --tests "com.swimvpn.app.adaptive.AgentDisabledFailurePolicyTest" --max-workers=1 --no-parallel --no-daemon`

- [ ] **Step 3: Implement** — broaden the policy to cover both auto-actions and clear the morph target:

```kotlin
    fun resolve(
        agentEnabled: Boolean,
        currentServerId: String,
        plannedAction: DecisionAction,
    ): DecisionAction {
        val isAutoAction = plannedAction.type == DecisionActionType.SWITCH_SERVER ||
            plannedAction.type == DecisionActionType.MORPH_PROFILE
        if (agentEnabled || !isAutoAction) {
            return plannedAction
        }
        return plannedAction.copy(
            type = DecisionActionType.RECONNECT_SAME,
            targetServerId = currentServerId,
            targetProfileId = null,
            reason = "agent_disabled_reconnect_same_server",
        )
    }
```

- [ ] **Step 4: Run, verify PASS** (`--rerun-tasks`); the existing SWITCH_SERVER-downgrade test must still pass.

`cd android && ./gradlew :app:testDebugUnitTest --tests "com.swimvpn.app.adaptive.AgentDisabledFailurePolicyTest" --rerun-tasks --max-workers=1 --no-parallel --no-daemon`

- [ ] **Step 5: Commit**

```bash
git add android/app/src/main/java/com/swimvpn/app/adaptive/AgentDisabledFailurePolicy.kt android/app/src/test/java/com/swimvpn/app/adaptive/AgentDisabledFailurePolicyTest.kt
git commit -m "feat(autopilot): AI-off suppresses MORPH_PROFILE (same-server retry, manual profile honored)"
```

---

## Task 4: `MainViewModel` — sentinel lifecycle, probe, morph executor

**Files:**
- Modify: `android/app/src/main/java/com/swimvpn/app/MainViewModel.kt`

No unit test (Android/coroutine integration; verified by compile + device validation Task 6). Read first: `handleAdaptiveRuntimeFailure` (~255-347, note the `handlingAdaptiveFailure` guard and the `when (action.type)` block + `adaptiveReconnectAttempt += 1`), `restartRuntimeForModeChangeIfNeeded` (~513-556) and `switchActiveServerIfConnected` (the ACTION_RESTART emission pattern: `Intent(...).apply { action = ACTION_RESTART; putExtra(EXTRA_SERVER_HOST/PORT/PROTOCOL/URL/RUNTIME_MODE/CAMOUFLAGE_PROFILE...) }; context.startService(...)`), `resolveCamouflageProfile` (sets `_activeCamouflageProfileId.value`), `premiumUsageReportJob` (the `viewModelScope.launch { while(true){ delay(...); if (state != CONNECTED) break; ... } }` periodic-loop template), `VpnManager.state`. SOCKS port is `127.0.0.1:10808`.

- [ ] **Step 1: Add incident + sentinel state fields** (near `adaptiveReconnectAttempt`):

```kotlin
    private val healthSentinel = TunnelHealthSentinel(degradedThreshold = 3)
    private var healthSentinelJob: Job? = null
    private var incidentTriedProfiles: Set<String> = emptySet()
```

- [ ] **Step 2: The through-tunnel probe** (private suspend helper):

```kotlin
    /** True iff a small request succeeds THROUGH xray's local SOCKS (the real tunnelled path). */
    private suspend fun probeTunnelHealthy(): Boolean = withContext(Dispatchers.IO) {
        runCatching {
            val proxy = java.net.Proxy(java.net.Proxy.Type.SOCKS, java.net.InetSocketAddress("127.0.0.1", 10808))
            // Unresolved host => the SOCKS proxy resolves it remotely (DNS rides the tunnel).
            val url = java.net.URL("https://www.gstatic.com/generate_204")
            val conn = (url.openConnection(proxy) as java.net.HttpURLConnection).apply {
                connectTimeout = 6000
                readTimeout = 6000
                instanceFollowRedirects = false
                requestMethod = "GET"
            }
            try {
                conn.responseCode in 200..399
            } finally {
                conn.disconnect()
            }
        }.getOrDefault(false)
    }
```

- [ ] **Step 3: Sentinel lifecycle** — start on a confirmed connect, stop on disconnect/manual-stop. Add a `startHealthSentinel()` / `stopHealthSentinel()` and call them where the session starts/ends (start it right after a successful connect in the same place premium-usage reporting starts; stop it in `toggleVpn`'s stop branch and wherever `manualStopRequested`/teardown happens):

```kotlin
    private fun startHealthSentinel() {
        healthSentinelJob?.cancel()
        healthSentinel.reset()
        healthSentinelJob = viewModelScope.launch {
            while (true) {
                delay(HEALTH_PROBE_INTERVAL_MS) // 15_000L — ~3 misses ≈ 45s (balanced)
                if (VpnManager.state.value != VpnState.CONNECTED) continue
                if (!screenOnAndNotPowerSave()) continue // reuse the same gate as the latency refresh
                val healthy = probeTunnelHealthy()
                when (healthSentinel.onProbe(healthy)) {
                    HealthVerdict.DEGRADED -> handleAdaptiveRuntimeFailure() // route into the one pipeline
                    HealthVerdict.HEALTHY, HealthVerdict.ALREADY_DEGRADED -> { /* keep probing */ }
                }
            }
        }
    }

    private fun stopHealthSentinel() {
        healthSentinelJob?.cancel()
        healthSentinelJob = null
        healthSentinel.reset()
    }
```
Add `private const val HEALTH_PROBE_INTERVAL_MS = 15_000L` to the companion. For `screenOnAndNotPowerSave()`, reuse the exact battery/screen check the periodic latency refresh already uses (find it near `latencyRefreshJob`); if it is inline there, extract it to a private helper and call it from both. Do NOT invent a new gate.

- [ ] **Step 4: Reset incident state on healthy / fresh connect** — in `handleAdaptiveRuntimeFailure`, seed/extend the tried-profile set and pass it to `planAfterFailure`; reset it on a fresh user connect (in `toggleVpn`'s connect branch set `incidentTriedProfiles = emptySet()`), and clear it when the sentinel sees health return (on `HealthVerdict.HEALTHY` after a degraded streak — simplest: clear it whenever a probe succeeds: add `incidentTriedProfiles = emptySet()` in the HEALTHY branch).

  In `handleAdaptiveRuntimeFailure`, before calling `planAfterFailure`, ensure the active profile is in the set:
```kotlin
            if (incidentTriedProfiles.isEmpty()) incidentTriedProfiles = setOf(_activeCamouflageProfileId.value)
```
  and pass the new params:
```kotlin
            val plannedAction = AdaptiveDecisionAgent.planAfterFailure(
                currentServerId = activeServer.id,
                candidates = current.servers.map { it.toDecisionCandidate(current.profile) },
                scores = scores,
                reconnectAttempt = adaptiveReconnectAttempt,
                nowMs = now,
                networkType = currentNetworkType(),
                currentProfileId = _activeCamouflageProfileId.value,
                triedProfileIds = incidentTriedProfiles,
            )
```

- [ ] **Step 5: Execute `MORPH_PROFILE`** — add a branch to the `when (action.type)` in `handleAdaptiveRuntimeFailure`. Unlike RECONNECT/SWITCH, a morph does NOT increment `adaptiveReconnectAttempt` (move the `adaptiveReconnectAttempt += 1` so it runs only for the non-morph branches, OR subtract it back for morph). Cleanest: guard the increment:
```kotlin
            if (action.type != DecisionActionType.MORPH_PROFILE) adaptiveReconnectAttempt += 1

            when (action.type) {
                DecisionActionType.GIVE_UP -> { /* existing toast */ }
                DecisionActionType.MORPH_PROFILE -> {
                    val target = action.targetProfileId ?: return
                    incidentTriedProfiles = incidentTriedProfiles + target
                    _activeCamouflageProfileId.value = target
                    _effect.emit(AppSideEffect.ShowToast(s(R.string.adaptive_optimizing)))
                    delay(action.delayMs)
                    restartActiveServerWithProfile(activeServer, current, target)
                }
                DecisionActionType.RECONNECT_SAME,
                DecisionActionType.SWITCH_SERVER -> { /* existing block unchanged */ }
            }
```

- [ ] **Step 6: The morph executor** — mirror `switchActiveServerIfConnected`/`restartRuntimeForModeChangeIfNeeded` but force the profile id and keep the same server:
```kotlin
    private suspend fun restartActiveServerWithProfile(server: ServerNode, current: AppState.Success, profileId: String) {
        val rawInput = server.rawConfig ?: current.profile.subscriptionUrl
        val resolved = configRepository.resolveRuntimeConfigForConnection(
            input = rawInput ?: return,
            sourceType = if (server.source == "backend") SourceType.BACKEND_API else SourceType.MANUAL_ENTRY,
        ).getOrNull()?.rawConfig ?: return
        val context = app.applicationContext
        val intent = Intent(context, SwimVpnService::class.java).apply {
            action = SwimVpnService.ACTION_RESTART
            putExtra(SwimVpnService.EXTRA_SERVER_HOST, server.host)
            putExtra(SwimVpnService.EXTRA_SERVER_PORT, server.port)
            putExtra(SwimVpnService.EXTRA_PROTOCOL, server.protocol)
            putExtra(SwimVpnService.EXTRA_URL, resolved)
            putExtra(SwimVpnService.EXTRA_RUNTIME_MODE, currentStateRoutingModeName())
            putExtra(SwimVpnService.EXTRA_CAMOUFLAGE_PROFILE, profileId) // FORCED morph profile
        }
        context.startService(intent)
    }
```

- [ ] **Step 7: Add the toast string** in all 4 locales (after `adaptive_reconnecting`):
  - `values/`,`values-fr/`: `<string name="adaptive_optimizing">Optimisation de la connexion…</string>`
  - `values-en/`: `<string name="adaptive_optimizing">Optimising connection…</string>`
  - `values-ru/`: `<string name="adaptive_optimizing">Оптимизация соединения…</string>`

- [ ] **Step 8: Compile**

`cd android && ./gradlew :app:compileDebugKotlin --rerun-tasks --max-workers=1 --no-parallel --no-daemon`
Expected: BUILD SUCCESSFUL.

- [ ] **Step 9: Commit**

```bash
git add android/app/src/main/java/com/swimvpn/app/MainViewModel.kt android/app/src/main/res/values/strings.xml android/app/src/main/res/values-en/strings.xml android/app/src/main/res/values-fr/strings.xml android/app/src/main/res/values-ru/strings.xml
git commit -m "feat(autopilot): wire health sentinel + in-session profile morph into the failure pipeline"
```

---

## Task 5: Full targeted regression suite

- [ ] **Step 1**

```bash
cd android && ./gradlew :app:testDebugUnitTest \
  --tests "com.swimvpn.app.adaptive.TunnelHealthSentinelTest" \
  --tests "com.swimvpn.app.adaptive.CamouflageAdaptiveTest" \
  --tests "com.swimvpn.app.adaptive.AdaptiveDecisionAgentTest" \
  --tests "com.swimvpn.app.adaptive.AgentDisabledFailurePolicyTest" \
  --tests "com.swimvpn.app.config.CamouflageProfileRepositoryTest" \
  --rerun-tasks --max-workers=1 --no-parallel --no-daemon
```
Expected: BUILD SUCCESSFUL, all green. Fix any failure at root; never weaken a test to pass.

---

## Task 6: Device-validation gate (manual — STOP for user)

- [ ] **Step 1: Build + install coexisting debug** (re-add `debug { applicationIdSuffix ".debug" }` temporarily, build `assembleDebug`, install, then `git checkout android/app/build.gradle` — do NOT commit the suffix).
- [ ] **Step 2: Validate** — AI toggle ON; connect a premium REALITY+vision node. (a) On a healthy session, confirm NO morph fires over several minutes (the probe keeps succeeding). (b) Induce degradation (e.g., a node/route that connects but stalls) and confirm: the "Optimisation de la connexion…" toast appears, a brief blip, a different shaping profile is tried (check the generated `xray-config.json` `sockopt`), and connectivity recovers — and that it switches server only after the morphs are exhausted. **STOP and report; do not invent the result.**

---

## Task 7: Final review (no merge)

- [ ] **Step 1:** Re-run the Task 5 suite — green.
- [ ] **Step 2:** `git diff --stat main...HEAD` — only intended files; no `build.gradle` debug suffix, no `loglevel=debug`.
- [ ] **Step 3: STOP.** Report status + the Task-6 result. Merge to main+production requires explicit user validation (prod = image of main).

---

## Notes for the implementer

- **Morph budget ≠ reconnect budget:** a morph must not increment `adaptiveReconnectAttempt`, else `MAX_RECONNECT_ATTEMPTS` cuts off the later server-switch. The tried-profile set is the morph counter.
- **`planAfterFailure` non-regression:** the morph phase is a no-op when `currentProfileId == null`, so every existing caller/test keeps its old behavior. New Stage-B callers pass the profile id.
- **Honesty:** the toast says "optimising connection", never "anti-detection/stealth". The loop optimises observed connectivity, not (unmeasurable) stealth.
- **Probe cost:** one ~0-byte 204 fetch every 15 s while screen-on and connected; gated exactly like the existing latency refresh.
