# Purple-Team Adaptive Shaping — Stage A Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Add a client-side TLS-shaping axis (TLS fragmentation, optional pre-handshake noise) that the adaptive agent can pick per network, bundled with the existing uTLS-fingerprint axis — completing (never overriding) the supplier's REALITY fingerprint, with `DEFAULT = AUTO = no shaping = byte-identical to today`.

**Architecture:** Extend the existing `CamouflageProfile` (today `{id, displayNameRes, fingerprint}`) with optional `fragment`/`noises`. A new `TunnelRuntimeAdapter.applyShaping` post-process injects `streamSettings.sockopt.fragment`/`noises` (mirrors the existing `applyFingerprintOverride`). The value threaded from UI → Service → adapter changes from a fingerprint string to a **profile id**, resolved with `CamouflageProfileRepository.byId(...)` at the application point so one carry drives both fp and shaping. Learning reuses the existing per-network maps keyed `NETWORK|profileId` (no score-store codec change). Default stays AUTO; fragment presets reach the agent's auto-escalation (`fallbackOrder`) only after on-device validation that they compose with REALITY + `xtls-rprx-vision`.

**Tech Stack:** Kotlin / Android (Gson `JsonObject`), xray-core 26.3.27 (`sockopt.fragment` / `sockopt.noises`), JUnit unit tests (`testDebugUnitTest`). RAM-constrained machine → targeted unit tests only; `assembleDebug` only for the device-validation gate; no `assembleRelease` until ship time.

---

## Scope

This plan covers **Stage A only**. Out of scope (own specs/plans later): ALPN override, connection pacing, Stage B (closed-loop autopilot), Stage C (cover traffic), Observer #2 (residential exit / leak prevention). Source spec: `docs/superpowers/specs/2026-06-06-purple-team-shaping-design.md` (commit 67a4fa5).

## File Structure

- `android/app/src/main/java/com/swimvpn/app/config/CamouflageProfile.kt` — **modify**: add `FragmentSpec`/`NoiseSpec`, extend `CamouflageProfile`, add `FRAG_LIGHT`/`FRAG_AGGRESSIVE` presets, list them in `all()` (picker). `fallbackOrder` unchanged until Task 7.
- `android/app/src/main/java/com/swimvpn/app/config/TunnelRuntimeAdapter.kt` — **modify**: rename the threaded `camouflageFingerprint: String?` → `camouflageProfileId: String?` on the two public entry points; resolve `byId`; keep `applyFingerprintOverride`; add `applyShaping`.
- `android/app/src/main/java/com/swimvpn/app/SwimVpnService.kt` — **modify**: rename `EXTRA_CAMOUFLAGE_FP`→`EXTRA_CAMOUFLAGE_PROFILE`, `ActiveSession.camouflageFingerprint`→`camouflageProfileId`, the `startVpn`/`restartVpn` params, reads, and the `prepareRuntimeFromRawConfig` call.
- `android/app/src/main/java/com/swimvpn/app/MainViewModel.kt` — **modify**: pass `camouflage.id` (not `camouflage.fingerprint`) at the three `putExtra(EXTRA_CAMOUFLAGE_*)` sites.
- `android/app/src/main/res/values{,-en,-fr,-ru}/strings.xml` — **modify**: add `camouflage_frag_light` / `camouflage_frag_aggressive`.
- `android/app/src/test/java/com/swimvpn/app/config/CamouflageProfileRepositoryTest.kt` — **modify**: assert the new presets.
- `android/app/src/test/java/com/swimvpn/app/config/XrayCamouflageTest.kt` — **modify**: rename the helper's named arg `camouflageFingerprint`→`camouflageProfileId` (assertions unchanged; browser preset id == fingerprint).
- `android/app/src/test/java/com/swimvpn/app/config/XrayShapingTest.kt` — **create**: assert `applyShaping` injects `sockopt`, AUTO is a no-op, frag presets keep the link fp.

---

## Task 0: Branch

- [ ] **Step 1: Create the working branch**

```bash
git checkout main
git checkout -b feat/purple-team-shaping-a
```

---

## Task 1: Extend `CamouflageProfile` with shaping fields + fragment presets

**Files:**
- Modify: `android/app/src/main/java/com/swimvpn/app/config/CamouflageProfile.kt`
- Test: `android/app/src/test/java/com/swimvpn/app/config/CamouflageProfileRepositoryTest.kt`

- [ ] **Step 1: Write the failing test**

Append to `CamouflageProfileRepositoryTest`:

```kotlin
    @Test
    fun `auto carries no shaping (fragment and noises null)`() {
        assertEquals(null, CamouflageProfileRepository.AUTO.fragment)
        assertEquals(null, CamouflageProfileRepository.AUTO.noises)
    }

    @Test
    fun `fragment presets respect the link fp and carry a fragment spec`() {
        val light = CamouflageProfileRepository.byId("frag_light")
        assertEquals("frag_light", light.id)
        assertEquals("", light.fingerprint) // respect the link's fp — only add fragmentation
        assertEquals("tlshello", light.fragment?.packets)
        val aggressive = CamouflageProfileRepository.byId("frag_aggressive")
        assertEquals("", aggressive.fingerprint)
        assertEquals("1-3", aggressive.fragment?.packets)
    }

    @Test
    fun `all includes the fragment presets for the manual picker`() {
        val ids = CamouflageProfileRepository.all().map { it.id }
        assertTrue(ids.containsAll(listOf("frag_light", "frag_aggressive")))
    }
```

- [ ] **Step 2: Run it to verify it fails**

Run: `cd android && ./gradlew :app:testDebugUnitTest --tests "com.swimvpn.app.config.CamouflageProfileRepositoryTest" --max-workers=1 --no-parallel --no-daemon`
Expected: FAIL — `frag_light`/`fragment` unresolved.

- [ ] **Step 3: Implement**

In `CamouflageProfile.kt`, add the shaping value types above the data class and extend it:

```kotlin
/** xray sockopt.fragment: split outgoing TLS records across TCP segments to blur SNI/size DPI. */
data class FragmentSpec(val packets: String, val length: String, val interval: String)

/** xray sockopt.noises: inject junk packets before the handshake. */
data class NoiseSpec(val type: String, val packet: String, val delay: String)

data class CamouflageProfile(
    val id: String,
    val displayNameRes: Int,
    val fingerprint: String,
    val fragment: FragmentSpec? = null,
    val noises: List<NoiseSpec>? = null,
)
```

In `CamouflageProfileRepository`, add the presets after `RANDOMIZED` and list them in `all()` (NOT in `fallbackOrder` yet — Task 7 gates that on device validation):

```kotlin
    // Fragmentation presets keep the link's own fp ("") and only ADD TLS fragmentation — completing
    // the supplier fingerprint, never overriding it. Picker-only until device-validated (Task 7).
    val FRAG_LIGHT = CamouflageProfile(
        "frag_light", R.string.camouflage_frag_light, "",
        fragment = FragmentSpec("tlshello", "100-200", "10-20"),
    )
    val FRAG_AGGRESSIVE = CamouflageProfile(
        "frag_aggressive", R.string.camouflage_frag_aggressive, "",
        fragment = FragmentSpec("1-3", "40-80", "5-15"),
    )
```

Change `ALL` to include them (leave `fallbackOrder` exactly as is):

```kotlin
    private val ALL: List<CamouflageProfile> =
        listOf(AUTO, CHROME, FIREFOX, SAFARI, IOS, RANDOMIZED, FRAG_LIGHT, FRAG_AGGRESSIVE)
```

- [ ] **Step 4: Run it to verify it passes**

Run: `cd android && ./gradlew :app:testDebugUnitTest --tests "com.swimvpn.app.config.CamouflageProfileRepositoryTest" --max-workers=1 --no-parallel --no-daemon`
Expected: PASS. (The existing `all presets present...` test asserts an exact ordered list — update its expected list to `listOf("auto","chrome","firefox","safari","ios","randomized","frag_light","frag_aggressive")` and keep its "browser profiles match their id" filter excluding `auto`/`frag_*`.)

- [ ] **Step 5: Commit**

```bash
git add android/app/src/main/java/com/swimvpn/app/config/CamouflageProfile.kt android/app/src/test/java/com/swimvpn/app/config/CamouflageProfileRepositoryTest.kt
git commit -m "feat(shaping): extend CamouflageProfile with fragment/noises + frag presets (picker-only)"
```

---

## Task 2: `applyShaping` in `TunnelRuntimeAdapter` + resolve profile by id

**Files:**
- Modify: `android/app/src/main/java/com/swimvpn/app/config/TunnelRuntimeAdapter.kt` (entry points at lines 65-85 `prepareRuntimeFromRawConfig`, 103-134 `generateXrayRuntimeDocument`, and near `applyFingerprintOverride`)
- Modify: `android/app/src/test/java/com/swimvpn/app/config/XrayCamouflageTest.kt`
- Create: `android/app/src/test/java/com/swimvpn/app/config/XrayShapingTest.kt`

- [ ] **Step 1: Write the failing test**

Create `XrayShapingTest.kt` (fixtures copied from `XrayCamouflageTest` — the engineer may read tasks out of order, so the full fixture is repeated):

```kotlin
package com.swimvpn.app.config

import com.swimvpn.app.vpn.RuntimeMode
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** Stage A: TLS-shaping (sockopt.fragment/noises) is applied post-build; AUTO is a no-op. */
class XrayShapingTest {

    private fun streamOf(profileId: String?) =
        TunnelRuntimeAdapter.generateXrayRuntimeDocument(realityProfile(), RuntimeMode.FULL_TUNNEL, camouflageProfileId = profileId)
            ?.getAsJsonArray("outbounds")?.first()?.asJsonObject?.getAsJsonObject("streamSettings")
            ?: error("runtime document / streamSettings must exist")

    @Test
    fun `auto adds no sockopt (byte-identical regression guard)`() {
        assertFalse(streamOf("auto").has("sockopt"))
    }

    @Test
    fun `frag_light injects sockopt fragment and keeps the link reality fp`() {
        val stream = streamOf("frag_light")
        val fragment = stream.getAsJsonObject("sockopt").getAsJsonObject("fragment")
        assertEquals("tlshello", fragment["packets"].asString)
        assertEquals("100-200", fragment["length"].asString)
        // shaping must NOT touch the fingerprint — link default stays "chrome"
        assertEquals("chrome", stream.getAsJsonObject("realitySettings")["fingerprint"].asString)
    }

    @Test
    fun `null profile adds no sockopt`() {
        assertFalse(streamOf(null).has("sockopt"))
    }

    private fun base(security: SecurityMode, transport: Transport) = SwimVpnProfile(
        sourceType = SourceType.MANUAL_ENTRY,
        rawConfig = "x",
        sourceFormat = SourceFormat.VLESS_URL,
        protocol = Protocol.VLESS,
        transport = transport,
        securityMode = security,
        address = "example.com",
        port = 443,
        userId = "00000000-0000-0000-0000-000000000000",
        displayName = "n",
        displaySubtitle = "s",
    )

    private fun realityProfile() = base(SecurityMode.REALITY, Transport.TCP).copy(
        realitySettings = RealitySettings("pk", "sid", "/"),
        tlsSettings = TlsSettings(sni = "www.microsoft.com"),
        tcpSettings = TcpSettings(),
    )
}
```

- [ ] **Step 2: Run it to verify it fails**

Run: `cd android && ./gradlew :app:testDebugUnitTest --tests "com.swimvpn.app.config.XrayShapingTest" --max-workers=1 --no-parallel --no-daemon`
Expected: FAIL — `camouflageProfileId` is not a parameter of `generateXrayRuntimeDocument` yet.

- [ ] **Step 3: Implement — rename the carry to a profile id + add `applyShaping`**

In `TunnelRuntimeAdapter.kt`:

(a) `prepareRuntimeFromRawConfig` (line ~70): rename the param and pass it through:

```kotlin
        camouflageProfileId: String? = null,
```
```kotlin
        val runtimeDocument = generateXrayRuntimeDocument(normalized, runtimeMode, routingOptions, camouflageProfileId)
```

(b) `generateXrayRuntimeDocument` (line ~110): rename the param and resolve the profile, applying fp (unchanged) then shaping. Replace the existing fingerprint line:

```kotlin
        camouflageProfileId: String? = null,
```
```kotlin
            val camouflageProfile = camouflageProfileId?.let { CamouflageProfileRepository.byId(it) }
            camouflageProfile?.fingerprint?.takeIf { it.isNotBlank() }?.let { applyFingerprintOverride(document, it) }
            camouflageProfile?.let { applyShaping(document, it) }
```

(c) Add `applyShaping` next to `applyFingerprintOverride`:

```kotlin
    /**
     * Inject client-side TLS shaping (xray sockopt.fragment / sockopt.noises) into every outbound that
     * has a streamSettings block. No-op when the profile carries neither (AUTO) ⇒ byte-identical output.
     * Operates below TLS, so it composes with REALITY/vision without touching fp/SNI (device-validated).
     */
    private fun applyShaping(document: JsonObject, profile: CamouflageProfile) {
        if (profile.fragment == null && profile.noises.isNullOrEmpty()) return
        val outbounds = document.getAsJsonArray("outbounds") ?: return
        outbounds.forEach { element ->
            val stream = element.takeIf { it.isJsonObject }
                ?.asJsonObject?.getAsJsonObject("streamSettings") ?: return@forEach
            val sockopt = stream.getAsJsonObject("sockopt") ?: JsonObject().also { stream.add("sockopt", it) }
            profile.fragment?.let { f ->
                sockopt.add("fragment", JsonObject().apply {
                    addProperty("packets", f.packets)
                    addProperty("length", f.length)
                    addProperty("interval", f.interval)
                })
            }
            profile.noises?.takeIf { it.isNotEmpty() }?.let { list ->
                sockopt.add("noises", JsonArray().apply {
                    list.forEach { n ->
                        add(JsonObject().apply {
                            addProperty("type", n.type)
                            addProperty("packet", n.packet)
                            addProperty("delay", n.delay)
                        })
                    }
                })
            }
        }
    }
```

(d) Update `XrayCamouflageTest.kt` helper to the new param name (assertions unchanged — `byId("firefox")` resolves to fp `firefox`, `null` → reality default `chrome`):

```kotlin
    private fun streamOf(profile: SwimVpnProfile, fingerprint: String?) =
        TunnelRuntimeAdapter.generateXrayRuntimeDocument(profile, RuntimeMode.FULL_TUNNEL, camouflageProfileId = fingerprint)
            ?.getAsJsonArray("outbounds")?.first()?.asJsonObject?.getAsJsonObject("streamSettings")
            ?: error("runtime document / streamSettings must exist")
```

- [ ] **Step 4: Run both adapter tests to verify they pass**

Run: `cd android && ./gradlew :app:testDebugUnitTest --tests "com.swimvpn.app.config.XrayShapingTest" --tests "com.swimvpn.app.config.XrayCamouflageTest" --max-workers=1 --no-parallel --no-daemon`
Expected: PASS (both classes).

- [ ] **Step 5: Commit**

```bash
git add android/app/src/main/java/com/swimvpn/app/config/TunnelRuntimeAdapter.kt android/app/src/test/java/com/swimvpn/app/config/XrayShapingTest.kt android/app/src/test/java/com/swimvpn/app/config/XrayCamouflageTest.kt
git commit -m "feat(shaping): applyShaping (sockopt.fragment/noises) + carry profile id through the adapter"
```

---

## Task 3: Thread the profile id through the service + view-model

**Files:**
- Modify: `android/app/src/main/java/com/swimvpn/app/SwimVpnService.kt` (lines 142, 166-177, 214, 235, 365, 408-414, 439, 500-505)
- Modify: `android/app/src/main/java/com/swimvpn/app/MainViewModel.kt` (the three `putExtra(SwimVpnService.EXTRA_CAMOUFLAGE_FP, camouflage.fingerprint)` sites: ~553, ~1186, ~1507)

No unit test (cross-process wiring; covered by compilation + the existing adapter tests + the device-validation gate). This is a mechanical rename — change every site, do not change behaviour for AUTO.

- [ ] **Step 1: Rename in `SwimVpnService.kt`**

- Const (line 142): `const val EXTRA_CAMOUFLAGE_PROFILE = "CAMOUFLAGE_PROFILE"`
- `ActiveSession` field (line 177): `val camouflageProfileId: String? = null,`
- Reads (lines 214, 235): `intent.getStringExtra(EXTRA_CAMOUFLAGE_PROFILE)` (line 235 keeps the `?: activeSession?.camouflageProfileId` fallback).
- `restartVpn` param (line 365) and `startVpn` param (line 414): `camouflageProfileId: String? = null,`
- Line 398 (restartVpn → startVpn call) and 439 (`effectiveCamouflage...`): rename the local/argument to `camouflageProfileId`.
- `ActiveSession(...)` construction (line ~440) and the `prepareRuntimeFromRawConfig` call (line 505): `camouflageProfileId = activeSession?.camouflageProfileId`.

- [ ] **Step 2: Update the three call sites in `MainViewModel.kt`**

At each `putExtra(SwimVpnService.EXTRA_CAMOUFLAGE_FP, camouflage.fingerprint)` (connect path ~553, server-switch path `switchActiveServerIfConnected` ~1186, reconnect path ~1507), change to:

```kotlin
                    putExtra(SwimVpnService.EXTRA_CAMOUFLAGE_PROFILE, camouflage.id)
```

(`resolveCamouflageProfile` already returns the `CamouflageProfile`; we now carry its `.id` instead of `.fingerprint`. AUTO → id `"auto"` → adapter resolves AUTO → fp `""` + no shaping → byte-identical.)

- [ ] **Step 3: Compile to verify the rename is complete**

Run: `cd android && ./gradlew :app:compileDebugKotlin --max-workers=1 --no-parallel --no-daemon`
Expected: BUILD SUCCESSFUL (no unresolved `EXTRA_CAMOUFLAGE_FP` / `camouflageFingerprint`).

- [ ] **Step 4: Commit**

```bash
git add android/app/src/main/java/com/swimvpn/app/SwimVpnService.kt android/app/src/main/java/com/swimvpn/app/MainViewModel.kt
git commit -m "refactor(shaping): carry the camouflage PROFILE ID (not just fp) UI->service->adapter"
```

---

## Task 4: UI strings for the fragment presets (×4 locales)

**Files:**
- Modify: `android/app/src/main/res/values/strings.xml`, `values-en/strings.xml`, `values-fr/strings.xml`, `values-ru/strings.xml`

The picker iterates `CamouflageProfileRepository.all()` (`TechnicalSettingsScreen.kt:350`), so the presets appear automatically once their `displayNameRes` strings exist. Wording = compatibility/profile, never "stealth/invisible".

- [ ] **Step 1: Add the two strings after `camouflage_randomized` in each file**

`values/` (FR base):
```xml
    <string name="camouflage_frag_light">Fragmenté (léger)</string>
    <string name="camouflage_frag_aggressive">Fragmenté (fort)</string>
```
`values-en/`:
```xml
    <string name="camouflage_frag_light">Fragmented (light)</string>
    <string name="camouflage_frag_aggressive">Fragmented (strong)</string>
```
`values-fr/`:
```xml
    <string name="camouflage_frag_light">Fragmenté (léger)</string>
    <string name="camouflage_frag_aggressive">Fragmenté (fort)</string>
```
`values-ru/`:
```xml
    <string name="camouflage_frag_light">Фрагментация (лёгкая)</string>
    <string name="camouflage_frag_aggressive">Фрагментация (сильная)</string>
```

- [ ] **Step 2: Verify resources compile**

Run: `cd android && ./gradlew :app:compileDebugKotlin --max-workers=1 --no-parallel --no-daemon`
Expected: BUILD SUCCESSFUL (no missing-string reference from `CamouflageProfile`).

- [ ] **Step 3: Commit**

```bash
git add android/app/src/main/res/values/strings.xml android/app/src/main/res/values-en/strings.xml android/app/src/main/res/values-fr/strings.xml android/app/src/main/res/values-ru/strings.xml
git commit -m "feat(shaping): i18n labels for the fragment presets (compatibility wording)"
```

---

## Task 5: Full targeted unit-test pass (regression gate)

**Files:** none (verification only)

- [ ] **Step 1: Run the shaping + camouflage + adaptive suites**

Run:
```bash
cd android && ./gradlew :app:testDebugUnitTest \
  --tests "com.swimvpn.app.config.CamouflageProfileRepositoryTest" \
  --tests "com.swimvpn.app.config.XrayCamouflageTest" \
  --tests "com.swimvpn.app.config.XrayShapingTest" \
  --tests "com.swimvpn.app.adaptive.CamouflageAdaptiveTest" \
  --tests "com.swimvpn.app.config.XrayStreamSettingsBuilderTest" \
  --max-workers=1 --no-parallel --no-daemon
```
Expected: BUILD SUCCESSFUL, all green. (`CamouflageAdaptiveTest` is unaffected — `fallbackOrder` is unchanged, so the agent still defaults to AUTO with no history.)

- [ ] **Step 2: Commit (no-op marker if nothing changed)** — skip if the suite was already green; otherwise fix the failing assertion at its root and re-run before continuing.

---

## Task 6: Device-validation gate (manual — REQUIRED before Task 7)

**Files:** none (manual on-device verification). This is the hard gate from the spec: `fragment` must be proven to compose with REALITY + `xtls-rprx-vision` without breaking the handshake (this stack is fragile — a forced chrome fp alone broke premium on 2026-06-05).

- [ ] **Step 1: Build + install the coexisting debug build**

```bash
cd android && ./gradlew :app:assembleDebug --max-workers=1 --no-parallel --no-daemon
# install app-debug.apk on the device (com.swimvpn.app.debug)
```
(If the `.debug` suffix is not present, temporarily re-add the `debug { applicationIdSuffix ".debug" }` block, build, then revert it — do NOT commit it.)

- [ ] **Step 2: Manually validate `frag_light` against a premium REALITY+vision node**

In the debug app: AI toggle OFF → pick profile **Fragmenté (léger)** → connect a premium (REALITY+vision) server. Then pull the xray log:
```bash
adb -s <serial> shell run-as com.swimvpn.app.debug \
  sh -c 'cat no_backup/runtime/sessions/$(ls -t no_backup/runtime/sessions | head -1)/logs/xray.stdout.log' | tail -40
```
PASS criteria: the log shows `proxy/vless/outbound: tunneling request to ... via <server>:443` (successful tunnel), **no** `dial ...: operation was canceled` storm; and `https://example.com` loads through the tunnel. Repeat for `frag_aggressive`.

- [ ] **Step 3: Record the result**

If both presets PASS → proceed to Task 7. If a preset FAILS (handshake breaks / dial-cancel storm) → it stays **picker-only** (do NOT add it to `fallbackOrder`); note it in the commit message and skip it in Task 7. AUTO remains the default regardless, so non-frag users are never affected.

---

## Task 7: Promote validated presets to the agent's escalation order (`fallbackOrder`)

**Files:**
- Modify: `android/app/src/main/java/com/swimvpn/app/config/CamouflageProfile.kt`
- Test: `android/app/src/test/java/com/swimvpn/app/adaptive/CamouflageAdaptiveTest.kt`

Only for presets that PASSED Task 6. This lets the agent (AI ON) auto-escalate to fragmentation when AUTO/browser profiles keep failing on a network.

- [ ] **Step 1: Write the failing test**

Append to `CamouflageAdaptiveTest`:

```kotlin
    @Test
    fun `agent can escalate to a fragment profile that has positive margin`() {
        val score = ServerQualityScore(
            "s",
            profileSuccesses = mapOf("WIFI|frag_light" to 3),
            profileFailures = mapOf("WIFI|auto" to 2, "WIFI|chrome" to 2),
        )
        assertEquals("frag_light", AdaptiveDecisionAgent.selectBestCamouflageProfile(score, NetworkType.WIFI).id)
    }
```

- [ ] **Step 2: Run it to verify it fails**

Run: `cd android && ./gradlew :app:testDebugUnitTest --tests "com.swimvpn.app.adaptive.CamouflageAdaptiveTest" --max-workers=1 --no-parallel --no-daemon`
Expected: FAIL — `frag_light` is not in `fallbackOrder`, so the agent never considers it (returns `auto`).

- [ ] **Step 3: Add the validated presets to `fallbackOrder`**

In `CamouflageProfileRepository`, append the presets that passed Task 6 (shown here with both passing; drop any that failed):

```kotlin
    val fallbackOrder: List<CamouflageProfile> =
        listOf(AUTO, CHROME, FIREFOX, SAFARI, IOS, RANDOMIZED, FRAG_LIGHT, FRAG_AGGRESSIVE)
```

- [ ] **Step 4: Run it to verify it passes**

Run: `cd android && ./gradlew :app:testDebugUnitTest --tests "com.swimvpn.app.adaptive.CamouflageAdaptiveTest" --tests "com.swimvpn.app.config.CamouflageProfileRepositoryTest" --max-workers=1 --no-parallel --no-daemon`
Expected: PASS. (`fallback order starts with auto and contains every profile` still holds — `containsAll(all())` is satisfied.)

- [ ] **Step 5: Commit**

```bash
git add android/app/src/main/java/com/swimvpn/app/config/CamouflageProfile.kt android/app/src/test/java/com/swimvpn/app/adaptive/CamouflageAdaptiveTest.kt
git commit -m "feat(shaping): agent auto-escalates to device-validated fragment presets"
```

---

## Task 8: Final review & hand-off (no merge)

- [ ] **Step 1: Re-run the full targeted suite (Task 5 command)** — confirm green.
- [ ] **Step 2: `git diff --stat main...HEAD`** — confirm only the intended files changed; no `build.gradle` debug suffix, no `loglevel=debug` left behind.
- [ ] **Step 3: STOP.** Report status and the Task-6 device-validation result. Merge to main + production requires explicit user validation (prod = image of main). Release build (`assembleRelease` heap workaround) happens at ship time, not here.

---

## Notes for the implementer

- **Zero-regression invariant:** AUTO (default) must keep producing a byte-identical xray document — the `XrayShapingTest."auto adds no sockopt"` and the existing `XrayCamouflageTest."no override keeps reality default"` are the guards. Never let shaping touch fp/SNI.
- **xray schema:** `streamSettings.sockopt.fragment = {packets, length, interval}` (strings; `packets`=`"tlshello"` or `"N-M"`); `sockopt.noises = [{type, packet, delay}]`. Verified shape for xray 26.3.27 — if a future xray rejects a field, the device-validation gate (Task 6) catches it before it reaches users.
- **Honesty:** UI wording stays compatibility/profile; never claim stealth. The agent learns connection reliability per `(profileId × network)`, not undetectability.
