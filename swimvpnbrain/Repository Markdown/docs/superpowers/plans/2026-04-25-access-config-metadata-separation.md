# Access And Active Config Metadata Separation Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Separate SWIMVPN-managed access metadata from active config metadata so the profile screen can display quota and expiration without conflating backend subscription truth with parser-derived imported-config truth.

**Architecture:** Keep backend access state and config metadata as two distinct UI sources. Extend Android import/config storage with a small active-config metadata view model, then render `SWIMVPN Access` and `Active Config` as separate cards in `ProfileScreen` while leaving the subscription page focused on offers and payment.

**Tech Stack:** Kotlin, Jetpack Compose, existing `ConfigRepository`, existing normalized subscription parser, Android unit tests, Gradle.

---

## File Structure

### Existing files to modify
- `android/app/src/main/java/com/swimvpn/app/config/ConfigRepository.kt`
  - expose active config metadata and source classification derived from imported profiles + parser
- `android/app/src/main/java/com/swimvpn/app/MainViewModel.kt`
  - load active config metadata into app state
- `android/app/src/main/java/com/swimvpn/app/MainActivity.kt`
  - pass active config metadata into `ProfileScreen`
- `android/app/src/main/java/com/swimvpn/app/ui/screens/ProfileScreen.kt`
  - split profile UI into `SWIMVPN Access` and `Active Config`
- `android/app/src/main/java/com/swimvpn/app/data/network/Models.kt`
  - add lightweight UI-facing helpers only if needed for account-card clarity
- `android/app/src/main/res/values/strings.xml`
- `android/app/src/main/res/values-fr/strings.xml`
- `android/app/src/main/res/values-ru/strings.xml`
- `WORKLOG.md`
- `DECISIONS.md`
- `TODO.md`

### New files to create
- `android/app/src/main/java/com/swimvpn/app/config/ActiveConfigMetadata.kt`
  - UI-facing metadata model and source enum for active config
- `android/app/src/test/java/com/swimvpn/app/config/ActiveConfigMetadataMappingTest.kt`
  - repository-level mapping tests for active config source + parser metadata propagation

### Intentionally not changed
- `android/app/src/main/java/com/swimvpn/app/ui/screens/SubscriptionScreen.kt`
  - remains checkout/product oriented
- backend payment/quota services
- parser package behavior itself unless a small bug is discovered during mapping work

---

### Task 1: Define Active Config Metadata Model

**Files:**
- Create: `android/app/src/main/java/com/swimvpn/app/config/ActiveConfigMetadata.kt`
- Test: `android/app/src/test/java/com/swimvpn/app/config/ActiveConfigMetadataMappingTest.kt`

- [ ] **Step 1: Write the failing test**

```kotlin
package com.swimvpn.app.config

import com.swimvpn.app.config.subscriptionparser.ParsedSubscription
import com.swimvpn.app.config.subscriptionparser.ParsedVpnProfile
import org.junit.Assert.assertEquals
import org.junit.Test

class ActiveConfigMetadataMappingTest {

    @Test
    fun `maps imported config metadata with imported source`() {
        val parsed = ParsedSubscription(
            profiles = listOf(
                ParsedVpnProfile(
                    displayName = "NL Edge",
                    protocol = "vless",
                    providerName = "Provider Demo",
                    trafficUsedBytes = 15L,
                    trafficTotalBytes = 100L,
                    expiresAt = "2026-05-15T00:00:00Z",
                    raw = "vless://demo"
                )
            ),
            raw = "payload"
        )

        val metadata = ActiveConfigMetadata.fromParsedSubscription(
            parsed = parsed,
            source = ActiveConfigSource.IMPORTED_CONFIG,
            isActive = true,
        )

        assertEquals(ActiveConfigSource.IMPORTED_CONFIG, metadata?.source)
        assertEquals("NL Edge", metadata?.displayName)
        assertEquals("Provider Demo", metadata?.providerName)
        assertEquals(15L, metadata?.trafficUsedBytes)
        assertEquals(100L, metadata?.trafficTotalBytes)
        assertEquals("2026-05-15T00:00:00Z", metadata?.expiresAt)
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run:
```powershell
cd android
$env:GRADLE_OPTS='-Dkotlin.compiler.execution.strategy=in-process'
./gradlew.bat --no-daemon testDebugUnitTest --tests com.swimvpn.app.config.ActiveConfigMetadataMappingTest
```

Expected: FAIL because `ActiveConfigMetadata` and `ActiveConfigSource` do not exist yet.

- [ ] **Step 3: Write minimal implementation**

```kotlin
package com.swimvpn.app.config

import com.swimvpn.app.config.subscriptionparser.ParsedSubscription

enum class ActiveConfigSource {
    SWIMVPN_MANAGED,
    IMPORTED_CONFIG,
}

data class ActiveConfigMetadata(
    val source: ActiveConfigSource,
    val isActive: Boolean,
    val displayName: String,
    val providerName: String? = null,
    val protocol: String? = null,
    val serverHost: String? = null,
    val trafficUsedBytes: Long? = null,
    val trafficTotalBytes: Long? = null,
    val expiresAt: String? = null,
    val warnings: List<String> = emptyList(),
) {
    companion object {
        fun fromParsedSubscription(
            parsed: ParsedSubscription,
            source: ActiveConfigSource,
            isActive: Boolean,
        ): ActiveConfigMetadata? {
            val profile = parsed.profiles.firstOrNull() ?: return null
            return ActiveConfigMetadata(
                source = source,
                isActive = isActive,
                displayName = profile.displayName,
                providerName = profile.providerName ?: parsed.providerName,
                protocol = profile.protocol,
                serverHost = profile.serverHost,
                trafficUsedBytes = profile.trafficUsedBytes ?: parsed.trafficUsedBytes,
                trafficTotalBytes = profile.trafficTotalBytes ?: parsed.trafficTotalBytes,
                expiresAt = profile.expiresAt ?: parsed.expiresAt,
                warnings = (parsed.warnings + profile.warnings).distinct(),
            )
        }
    }
}
```

- [ ] **Step 4: Run test to verify it passes**

Run the same command from Step 2.
Expected: PASS.

- [ ] **Step 5: Commit**

```powershell
git add android/app/src/main/java/com/swimvpn/app/config/ActiveConfigMetadata.kt android/app/src/test/java/com/swimvpn/app/config/ActiveConfigMetadataMappingTest.kt
git commit -m "feat(android): add active config metadata model"
```

### Task 2: Expose Active Config Metadata From ConfigRepository

**Files:**
- Modify: `android/app/src/main/java/com/swimvpn/app/config/ConfigRepository.kt`
- Modify: `android/app/src/test/java/com/swimvpn/app/config/ActiveConfigMetadataMappingTest.kt`

- [ ] **Step 1: Write the failing repository test**

```kotlin
@Test
fun `returns imported active config metadata from active profile raw config`() {
    val raw = """
        Provider Demo
        15.3GB/1000.0GB
        Expires: 15.05.2026
        vless://11111111-1111-1111-1111-111111111111@example.com:443?security=tls&type=tcp#Node
    """.trimIndent()

    val metadata = ActiveConfigMetadata.fromRawConfig(
        rawConfig = raw,
        source = ActiveConfigSource.IMPORTED_CONFIG,
        displayNameFallback = "Node"
    )

    assertEquals(ActiveConfigSource.IMPORTED_CONFIG, metadata?.source)
    assertEquals("Provider Demo", metadata?.providerName)
    assertEquals(15.3 * 1024 * 1024 * 1024, metadata?.trafficUsedBytes?.toDouble(), 1024.0)
}
```

- [ ] **Step 2: Run test to verify it fails**

Run:
```powershell
cd android
$env:GRADLE_OPTS='-Dkotlin.compiler.execution.strategy=in-process'
./gradlew.bat --no-daemon testDebugUnitTest --tests com.swimvpn.app.config.ActiveConfigMetadataMappingTest
```

Expected: FAIL because `fromRawConfig` does not exist.

- [ ] **Step 3: Extend the model and repository with minimal implementation**

```kotlin
companion object {
    fun fromRawConfig(
        rawConfig: String,
        source: ActiveConfigSource,
        displayNameFallback: String,
        isActive: Boolean = true,
    ): ActiveConfigMetadata? {
        val parsed = com.swimvpn.app.config.subscriptionparser.SubscriptionParser.parse(rawConfig)
        val firstProfile = parsed.profiles.firstOrNull()
        return if (firstProfile == null) {
            ActiveConfigMetadata(
                source = source,
                isActive = isActive,
                displayName = displayNameFallback,
                warnings = parsed.warnings,
            )
        } else {
            fromParsedSubscription(parsed, source, isActive)
                ?.copy(displayName = firstProfile.displayName.ifBlank { displayNameFallback })
        }
    }
}
```

And in `ConfigRepository.kt`, add a focused method:

```kotlin
suspend fun getActiveConfigMetadata(): ActiveConfigMetadata? {
    val profile = getActiveProfile() ?: return null
    val source = when {
        profile.sourceType == SourceType.SUBSCRIPTION_URL || profile.sourceType == SourceType.MANUAL_ENTRY -> ActiveConfigSource.IMPORTED_CONFIG
        else -> ActiveConfigSource.SWIMVPN_MANAGED
    }
    return ActiveConfigMetadata.fromRawConfig(
        rawConfig = profile.rawConfig,
        source = source,
        displayNameFallback = profile.displayName,
        isActive = true,
    )
}
```

- [ ] **Step 4: Run tests to verify repository mapping passes**

Run:
```powershell
cd android
$env:GRADLE_OPTS='-Dkotlin.compiler.execution.strategy=in-process'
./gradlew.bat --no-daemon testDebugUnitTest --tests com.swimvpn.app.config.ActiveConfigMetadataMappingTest --tests com.swimvpn.app.config.SubscriptionParserTest
```

Expected: PASS.

- [ ] **Step 5: Commit**

```powershell
git add android/app/src/main/java/com/swimvpn/app/config/ActiveConfigMetadata.kt android/app/src/main/java/com/swimvpn/app/config/ConfigRepository.kt android/app/src/test/java/com/swimvpn/app/config/ActiveConfigMetadataMappingTest.kt
git commit -m "feat(android): expose active config metadata from repository"
```

### Task 3: Thread Active Config Metadata Through App State

**Files:**
- Modify: `android/app/src/main/java/com/swimvpn/app/MainViewModel.kt`
- Modify: `android/app/src/main/java/com/swimvpn/app/MainActivity.kt`

- [ ] **Step 1: Write the failing state contract change**

Add the target property to both success and profile rendering call sites first:

```kotlin
data class Success(
    val profile: AccessProfileResponse,
    val activeConfigMetadata: com.swimvpn.app.config.ActiveConfigMetadata? = null,
    // existing fields...
)
```

and in `MainActivity.kt`:

```kotlin
ProfileScreen(
    profile = currentState.profile,
    activeConfigMetadata = currentState.activeConfigMetadata,
    bytesIn = bytesIn,
    bytesOut = bytesOut,
    // existing callbacks...
)
```

- [ ] **Step 2: Run compile to verify it fails where state builders are incomplete**

Run:
```powershell
cd android
./gradlew.bat --no-daemon :app:processDebugResources :app:compileDebugKotlin
```

Expected: FAIL until `MainViewModel` fills the new state property.

- [ ] **Step 3: Implement minimal state wiring**

In `MainViewModel.kt`, when building success state:

```kotlin
private suspend fun resolveActiveConfigMetadata(): com.swimvpn.app.config.ActiveConfigMetadata? {
    return runCatching { configRepository.getActiveConfigMetadata() }.getOrNull()
}
```

and inside the success-state construction:

```kotlin
val activeConfigMetadata = resolveActiveConfigMetadata()
return AppState.Success(
    profile = profile,
    servers = servers,
    serverGroups = groupedServers,
    plans = plans,
    isOnboardingDone = isOnboardingDone,
    routingMode = routingMode,
    autoConnect = autoConnect,
    language = language,
    themeMode = themeMode,
    activeServer = selectedServer,
    activeConfigMetadata = activeConfigMetadata,
)
```

Also refresh this metadata after imported profile selection.

- [ ] **Step 4: Run compile to verify it passes**

Run the same command from Step 2.
Expected: PASS.

- [ ] **Step 5: Commit**

```powershell
git add android/app/src/main/java/com/swimvpn/app/MainViewModel.kt android/app/src/main/java/com/swimvpn/app/MainActivity.kt
git commit -m "feat(android): thread active config metadata through app state"
```

### Task 4: Refactor ProfileScreen Into Two Truth Cards

**Files:**
- Modify: `android/app/src/main/java/com/swimvpn/app/ui/screens/ProfileScreen.kt`
- Modify: `android/app/src/main/res/values/strings.xml`
- Modify: `android/app/src/main/res/values-fr/strings.xml`
- Modify: `android/app/src/main/res/values-ru/strings.xml`

- [ ] **Step 1: Write the UI contract change first**

Change the signature:

```kotlin
fun ProfileScreen(
    profile: AccessProfileResponse,
    activeConfigMetadata: com.swimvpn.app.config.ActiveConfigMetadata? = null,
    bytesIn: Long = 0,
    bytesOut: Long = 0,
    // existing callbacks...
)
```

and replace the single analytics section with two sections:
- `SWIMVPN Access`
- `Active Config`

- [ ] **Step 2: Run compile to verify it fails until new helper composables and strings exist**

Run:
```powershell
cd android
./gradlew.bat --no-daemon :app:processDebugResources :app:compileDebugKotlin
```

Expected: FAIL on missing strings/helpers.

- [ ] **Step 3: Implement the minimal UI split**

Add helper composables like:

```kotlin
@Composable
private fun SwimVpnAccessCard(profile: AccessProfileResponse) { /* existing quota/expiry logic moved here */ }

@Composable
private fun ActiveConfigCard(metadata: com.swimvpn.app.config.ActiveConfigMetadata?) {
    if (metadata == null) return

    val sourceLabel = when (metadata.source) {
        com.swimvpn.app.config.ActiveConfigSource.SWIMVPN_MANAGED -> stringResource(R.string.active_config_source_managed)
        com.swimvpn.app.config.ActiveConfigSource.IMPORTED_CONFIG -> stringResource(R.string.active_config_source_imported)
    }

    // render display name, provider, protocol, parsed quota, parsed expiry
}
```

Add string keys:

```xml
<string name="profile_section_swimvpn_access">SWIMVPN Access</string>
<string name="profile_section_active_config">Active Config</string>
<string name="active_config_source_managed">SWIMVPN Managed</string>
<string name="active_config_source_imported">Imported Config</string>
<string name="active_config_protocol">Protocol</string>
<string name="active_config_provider">Provider</string>
<string name="active_config_expiration">Config expiration</string>
<string name="active_config_quota">Config quota</string>
<string name="active_config_no_metadata">No config metadata available</string>
```

- [ ] **Step 4: Run compile to verify it passes**

Run:
```powershell
cd android
./gradlew.bat --no-daemon :app:processDebugResources :app:compileDebugKotlin
```

Expected: PASS.

- [ ] **Step 5: Commit**

```powershell
git add android/app/src/main/java/com/swimvpn/app/ui/screens/ProfileScreen.kt android/app/src/main/res/values/strings.xml android/app/src/main/res/values-fr/strings.xml android/app/src/main/res/values-ru/strings.xml
git commit -m "feat(android): separate access and active config cards"
```

### Task 5: Verify Scenarios And Update Repo Memory

**Files:**
- Modify: `WORKLOG.md`
- Modify: `DECISIONS.md`
- Modify: `TODO.md`

- [ ] **Step 1: Run targeted verification commands**

Run:
```powershell
cd android
$env:GRADLE_OPTS='-Dkotlin.compiler.execution.strategy=in-process'
./gradlew.bat --no-daemon testDebugUnitTest --tests com.swimvpn.app.config.SubscriptionParserTest --tests com.swimvpn.app.config.ActiveConfigMetadataMappingTest
./gradlew.bat --no-daemon :app:processDebugResources :app:compileDebugKotlin
```

Expected: PASS.

- [ ] **Step 2: Perform logical scenario review**

Checklist:
- trial profile shows only SWIMVPN trial truth in `SWIMVPN Access`
- imported config shows `Imported Config` badge in `Active Config`
- parser quota/expiration only appear inside `Active Config`
- no UI field claims imported quota is backend-enforced by SWIMVPN

- [ ] **Step 3: Update repo memory files**

Add concise entries for:
- new UI separation decision
- implementation status
- follow-up work for richer active-config metadata presentation

- [ ] **Step 4: Commit**

```powershell
git add WORKLOG.md DECISIONS.md TODO.md
git commit -m "docs: record access and active config separation"
```

## Self-Review
- Spec coverage checked:
  - source differentiation: covered in Tasks 1, 2, 4
  - two-card UI split: covered in Task 4
  - parser metadata display without backend confusion: covered in Tasks 2 and 4
  - keep subscription screen product-focused: explicitly preserved in file structure and non-goals
- Placeholder scan: no TBD/TODO placeholders left in task steps
- Type consistency checked:
  - `ActiveConfigMetadata`
  - `ActiveConfigSource`
  - `getActiveConfigMetadata()`
  - `activeConfigMetadata` state property
  are named consistently across tasks
