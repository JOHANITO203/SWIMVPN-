# SwimVPN Startup Reorganization Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Remove the visible blank black/white startup gap and reorganize app launch so native boot, app shell, bootstrap, and heavy visuals each happen in the right order.

**Architecture:** Keep Android's unavoidable native starting window, but make it visually consistent and extremely short. Render a real SwimVPN app shell immediately, then hydrate it with cached/local state and refresh backend data asynchronously. Defer heavy OpenGL orb rendering until after the first useful frame is visible.

**Tech Stack:** Android, Kotlin, Jetpack Compose, AppCompat theme resources, StateFlow, Gradle, ADB visual verification.

---

## File Structure

- Modify: `android/app/src/main/res/values/themes.xml`
  - Owns default native window background before Compose draws.
- Modify: `android/app/src/main/res/values-night/themes.xml`
  - Owns dark native window background before Compose draws.
- Modify: `android/app/src/main/res/values-v31/themes.xml`
  - Owns Android 12+ splash background/icon for light/system variants.
- Modify: `android/app/src/main/res/values-night-v31/themes.xml`
  - Owns Android 12+ splash background/icon for dark variants.
- Modify: `android/app/src/main/res/values/colors.xml`
  - Defines theme-safe boot colors. Keep this small and native-resource only.
- Modify: `android/app/src/main/java/com/swimvpn/app/MainActivity.kt`
  - Owns first Compose frame, locale/theme application, and initial navigation shell.
- Modify: `android/app/src/main/java/com/swimvpn/app/MainViewModel.kt`
  - Owns bootstrap sequencing and state hydration.
- Modify: `android/app/src/main/java/com/swimvpn/app/ui/components/HomeVpnCoreStage.kt`
  - Owns delayed/heavy orb rendering on Home.
- Create: `android/app/src/main/java/com/swimvpn/app/ui/screens/StartupShell.kt`
  - A lightweight, immediate SwimVPN visual shell used while bootstrap refreshes.
- Update: `WORKLOG.md`
  - Record implementation and verification.
- Update: `DECISIONS.md`
  - Record architectural decision: cached shell before network bootstrap.
- Update: `TODO.md`
  - Track follow-up tuning if ADB captures still show delay.

---

### Task 1: Make Native Boot Theme Deterministic And Theme-Aware

**Files:**
- Modify: `android/app/src/main/res/values/colors.xml`
- Modify: `android/app/src/main/res/values/themes.xml`
- Modify: `android/app/src/main/res/values-night/themes.xml`
- Modify: `android/app/src/main/res/values-v31/themes.xml`
- Modify: `android/app/src/main/res/values-night-v31/themes.xml`

- [ ] **Step 1: Audit current theme values**

Run:
```powershell
rg -n "windowBackground|windowSplashScreen|swim_background_boot|windowDisablePreview|navigationBarColor|statusBarColor" android/app/src/main/res -S
```

Expected:
- No `windowDisablePreview`.
- `windowBackground` and `windowSplashScreenBackground` currently point to boot background colors.

- [ ] **Step 2: Define native boot colors**

In `android/app/src/main/res/values/colors.xml`, keep or add:
```xml
<color name="swim_boot_dark">#FF07070B</color>
<color name="swim_boot_light">#FFF8F6FF</color>
<color name="swim_splash_transparent">#00000000</color>
```

- [ ] **Step 3: Apply light native boot**

In `android/app/src/main/res/values/themes.xml`, use:
```xml
<item name="android:windowBackground">@color/swim_boot_light</item>
<item name="android:colorBackground">@color/swim_boot_light</item>
<item name="android:windowLightStatusBar">true</item>
<item name="android:windowLightNavigationBar">true</item>
<item name="android:navigationBarColor">@color/swim_boot_light</item>
```

Do not add:
```xml
<item name="android:windowDisablePreview">true</item>
```

- [ ] **Step 4: Apply dark native boot**

In `android/app/src/main/res/values-night/themes.xml`, use:
```xml
<item name="android:windowBackground">@color/swim_boot_dark</item>
<item name="android:colorBackground">@color/swim_boot_dark</item>
<item name="android:windowLightStatusBar">false</item>
<item name="android:windowLightNavigationBar">false</item>
<item name="android:navigationBarColor">@color/swim_boot_dark</item>
```

- [ ] **Step 5: Apply Android 12+ splash resources**

In `values-v31/themes.xml`, add:
```xml
<item name="android:windowSplashScreenBackground">@color/swim_boot_light</item>
<item name="android:windowSplashScreenAnimatedIcon">@drawable/ic_splash_transparent</item>
<item name="android:windowSplashScreenIconBackgroundColor">@android:color/transparent</item>
```

In `values-night-v31/themes.xml`, add:
```xml
<item name="android:windowSplashScreenBackground">@color/swim_boot_dark</item>
<item name="android:windowSplashScreenAnimatedIcon">@drawable/ic_splash_transparent</item>
<item name="android:windowSplashScreenIconBackgroundColor">@android:color/transparent</item>
```

- [ ] **Step 6: Verify resources compile**

Run:
```powershell
cd android
.\gradlew.bat --no-daemon :app:compileDebugKotlin
```

Expected:
- `BUILD SUCCESSFUL`.

---

### Task 2: Remove Blocking Preference Reads From MainActivity First Draw

**Files:**
- Modify: `android/app/src/main/java/com/swimvpn/app/MainActivity.kt`

- [ ] **Step 1: Identify blocking reads**

Check `onCreate()` for:
```kotlin
runBlocking {
    persistedLanguage = prefs.languageFlow.first()
    persistedThemeMode = prefs.themeModeFlow.first()
}
```

- [ ] **Step 2: Replace blocking reads with fast defaults plus Compose collection**

Target behavior:
- Use existing process/default theme immediately.
- Let Compose collect `themeModeFlow`.
- Apply locale after content is available, not before first draw.

Implementation pattern:
```kotlin
override fun onCreate(savedInstanceState: Bundle?) {
    val prefs = PreferencesManager(this)
    super.onCreate(savedInstanceState)

    lifecycleScope.launch {
        val persistedLanguage = prefs.languageFlow.first()
        applyLocale(persistedLanguage)
    }

    setContent {
        val themeMode by prefs.themeModeFlow.collectAsState(initial = ThemeMode.SYSTEM)
        val systemDark = isSystemInDarkTheme()
        val darkTheme = when (themeMode) {
            ThemeMode.SYSTEM -> systemDark
            ThemeMode.DARK -> true
            ThemeMode.LIGHT -> false
        }

        SwimVpnTheme(darkTheme = darkTheme) {
            AppNavigation(
                viewModel = viewModel,
                onApplyLocale = ::applyLocale,
            )
        }
    }
}
```

Do not reintroduce synchronous disk reads before `super.onCreate()` or `setContent`.

- [ ] **Step 3: Keep theme switching explicit**

When user changes theme inside settings, keep existing `viewModel.setThemeMode(...)`. Do not call `AppCompatDelegate.setDefaultNightMode(...)` during every cold start unless required by an existing setting action.

- [ ] **Step 4: Compile**

Run:
```powershell
cd android
.\gradlew.bat --no-daemon :app:compileDebugKotlin
```

Expected:
- `BUILD SUCCESSFUL`.

---

### Task 3: Add A Real Lightweight Startup Shell

**Files:**
- Create: `android/app/src/main/java/com/swimvpn/app/ui/screens/StartupShell.kt`
- Modify: `android/app/src/main/java/com/swimvpn/app/MainActivity.kt`

- [ ] **Step 1: Create StartupShell composable**

Create `StartupShell.kt`:
```kotlin
package com.swimvpn.app.ui.screens

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.swimvpn.app.ui.components.SwimDarkLuxuryBackground

@Composable
fun StartupShell(
    modifier: Modifier = Modifier,
) {
    SwimDarkLuxuryBackground {
        Box(modifier = modifier.fillMaxSize())
    }
}
```

Purpose:
- This is not a route.
- This is not a loading screen with text.
- This is a visually coherent first app surface while state resolves.

- [ ] **Step 2: Replace StartupBridgeSurface body**

In `MainActivity.kt`, replace:
```kotlin
fun StartupBridgeSurface() {
    SwimDarkLuxuryBackground {
        Box(modifier = Modifier.fillMaxSize())
    }
}
```

with:
```kotlin
fun StartupBridgeSurface() {
    StartupShell()
}
```

Add import:
```kotlin
import com.swimvpn.app.ui.screens.StartupShell
```

- [ ] **Step 3: Confirm no loading route is restored**

Run:
```powershell
rg -n 'composable\("loading"\)|navigate\("loading"\)|startDestination = "loading"' android/app/src/main/java/com/swimvpn/app -S
```

Expected:
- No results.

---

### Task 4: Split Bootstrap Into Immediate Shell State And Async Refresh

**Files:**
- Modify: `android/app/src/main/java/com/swimvpn/app/MainViewModel.kt`

- [ ] **Step 1: Keep AppState.Loading short-lived**

Current issue:
```kotlin
_state.value = AppState.Loading
// then prefs + deviceId + bootstrap + servers + plans before Success
```

Target:
- Read local prefs quickly.
- If enough cached identity/profile/server state exists in existing storage, emit a `Success` shell first.
- Refresh backend after first shell.

If no cached profile exists, keep `StartupShell` but avoid expensive nonessential work before first destination.

- [ ] **Step 2: Move noncritical calls after first Success**

In `initApp()`, keep these before first destination:
```kotlin
val isOnboardingDone = prefs.onboardingDoneFlow.first()
val routingMode = prefs.runtimeModeFlow.first()
val autoConnect = prefs.autoConnectFlow.first()
val deviceId = getDeviceId()
val language = prefs.languageFlow.first()
val themeMode = prefs.themeModeFlow.first()
val bootstrap = api.bootstrapAccess(...)
```

Move these out of the first critical path where safe:
```kotlin
api.getPlans()
refreshServerLatency()
```

Acceptable result:
- App shows the first real destination as soon as bootstrap profile is known.
- Plans and latency can hydrate after the first render.

- [ ] **Step 3: Avoid full Loading during post-checkout refresh**

Ensure `refreshAfterExternalCheckoutIfNeeded()` does not set:
```kotlin
_state.value = AppState.Loading
```

Expected:
- User returning from external payment keeps current screen visible while refresh runs.

- [ ] **Step 4: Compile**

Run:
```powershell
cd android
.\gradlew.bat --no-daemon :app:compileDebugKotlin
```

Expected:
- `BUILD SUCCESSFUL`.

---

### Task 5: Defer Heavy Orb Rendering Until After First Useful Frame

**Files:**
- Modify: `android/app/src/main/java/com/swimvpn/app/ui/components/HomeVpnCoreStage.kt`

- [ ] **Step 1: Add a delayed render flag**

Inside `HomeVpnCoreStage`, add:
```kotlin
var renderOrb by remember { mutableStateOf(false) }

LaunchedEffect(Unit) {
    withFrameNanos { }
    withFrameNanos { }
    renderOrb = true
}
```

Required imports:
```kotlin
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
```

- [ ] **Step 2: Render a lightweight placeholder before the GL orb**

Wrap `SwimHolographicOrb3D`:
```kotlin
if (renderOrb) {
    SwimHolographicOrb3D(
        state = state.toHolographicState(),
        isReducedMotionEnabled = isReducedMotionEnabled,
        quality = if (isReducedMotionEnabled) OrbRenderQuality.Low else OrbRenderQuality.Auto,
        interactionEnabled = false,
        renderBehindCompose = false,
        lightSurfaceMode = lightTheme,
        modifier = Modifier
            .matchParentSize()
            .scale(stageBreath),
    )
} else {
    Canvas(
        modifier = Modifier
            .matchParentSize()
            .scale(stageBreath),
    ) {
        val center = androidx.compose.ui.geometry.Offset(size.width / 2f, size.height / 2f)
        drawCircle(
            brush = Brush.radialGradient(
                listOf(
                    accent.copy(alpha = 0.14f),
                    Color.Transparent,
                ),
                center = center,
                radius = size.minDimension * 0.48f,
            ),
            radius = size.minDimension * 0.48f,
            center = center,
        )
    }
}
```

Purpose:
- Home text/buttons can appear before the EGL surface stabilizes.
- The orb no longer controls perceived app entry.

- [ ] **Step 3: Verify no visible black plate**

Confirm `OrbGLSurfaceView` keeps:
```kotlin
holder.setFormat(PixelFormat.TRANSLUCENT)
setBackgroundColor(android.graphics.Color.TRANSPARENT)
```

Do not add opaque backgrounds to the GL view.

---

### Task 6: Verify Cold Start And Resume Behavior Over ADB

**Files:**
- No code files.
- Output captures to `screenshots/startup-seq-final/`.

- [ ] **Step 1: Build debug APK**

Run:
```powershell
cd android
.\gradlew.bat --no-daemon :app:assembleDebug
```

Expected:
- `BUILD SUCCESSFUL`.

- [ ] **Step 2: Install on device**

Run:
```powershell
$adb="$env:LOCALAPPDATA\Android\Sdk\platform-tools\adb.exe"
$serial="R5CWA0FEPZW"
$apk="c:\Users\Lenovo\StudioProjects\SWIMVPN-\android\app\build\outputs\apk\debug\app-debug.apk"
& $adb -s $serial install -r $apk
```

Expected:
- `Success`.

- [ ] **Step 3: Cold-start capture**

Run:
```powershell
$adb="$env:LOCALAPPDATA\Android\Sdk\platform-tools\adb.exe"
$serial="R5CWA0FEPZW"
New-Item -ItemType Directory -Force screenshots\startup-seq-final | Out-Null
& $adb -s $serial shell am force-stop com.swimvpn.app
Start-Sleep -Milliseconds 500
& $adb -s $serial shell am start -n com.swimvpn.app/.MainActivity | Out-Null
for ($i=0; $i -lt 12; $i++) {
  $remote="/sdcard/swim_start_final_$i.png"
  $out="screenshots\startup-seq-final\swim_start_final_$('{0:D2}' -f $i).png"
  & $adb -s $serial shell screencap -p $remote
  & $adb -s $serial pull $remote $out | Out-Null
  & $adb -s $serial shell rm -f $remote
  Start-Sleep -Milliseconds 130
}
```

Expected:
- First frame may be native boot color.
- No launcher icon splash.
- No white flash in dark mode.
- No black blank route after Compose starts.
- First useful app shell appears quickly.

- [ ] **Step 4: Resume capture**

Run:
```powershell
$adb="$env:LOCALAPPDATA\Android\Sdk\platform-tools\adb.exe"
$serial="R5CWA0FEPZW"
& $adb -s $serial shell input keyevent KEYCODE_HOME
Start-Sleep -Seconds 1
& $adb -s $serial shell am start -n com.swimvpn.app/.MainActivity | Out-Null
```

Expected:
- App resumes to previous screen.
- No full startup shell unless Android killed the process.
- No black frame caused by orb recreation.

---

### Task 7: Document The Startup Contract

**Files:**
- Modify: `WORKLOG.md`
- Modify: `DECISIONS.md`
- Modify: `TODO.md`

- [ ] **Step 1: Add WORKLOG entry**

Add:
```markdown
## 2026-05-21 - Android startup reorganization
- Audited cold start and resume behavior.
- Separated Android native boot, first Compose shell, bootstrap refresh, and heavy Home orb rendering.
- Removed blocking startup work from the first visual path where safe.
- Verified with Gradle and ADB startup captures.
```

- [ ] **Step 2: Add DECISIONS entry**

Add:
```markdown
## 2026-05-21 - Startup shell before heavy hydration
- Decision: SwimVPN should render a lightweight branded app shell before noncritical network hydration and heavy GL visuals.
- Reason: Android must show a native starting window, but the app should not add a second blank route or block first draw on avoidable work.
- Consequence: backend bootstrap remains authoritative, but plans, latency, and heavy orb rendering can hydrate after the first useful frame.
```

- [ ] **Step 3: Add TODO entry if any delay remains**

If ADB still shows a long blank:
```markdown
## Startup polish follow-up
- Measure bootstrap endpoint latency and consider cached profile shell if cold-start blank persists above 300ms.
```

---

## Acceptance Criteria

- Android 12+ splash no longer shows launcher icon before the app.
- Dark mode no longer shows white splash/native frame.
- Light mode no longer shows black splash/native frame except if system is still in dark before app theme is known.
- Compose no longer uses a navigable `loading` route.
- App does not block first Compose draw on avoidable preference/theme work.
- Home does not wait on OpenGL orb before showing useful UI.
- Returning from recents does not show a full black blank unless Android killed the process.
- VPN logic is untouched.
- Backend/parser/payment logic is untouched.
- `:app:compileDebugKotlin` passes.
- `:app:assembleDebug` passes.
- APK is installed on the ADB device for visual QA.

---

## Implementation Notes

The native first frame cannot be removed completely on Android. The correct objective is:
- make the native frame theme-correct,
- keep it extremely short,
- avoid launcher icon splash,
- avoid a second app-level blank route,
- show a real SwimVPN surface before expensive rendering.

Do not use `android:windowDisablePreview=true` unless a device-specific QA proves it does not expose the launcher behind the app. Previous Samsung testing showed it can reveal the launcher/search surface, so it is not the default strategy.
