# Home Screen Dark Luxury Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Rebuild only the Android Home / Connected screen to match the SwimVPN Dark Luxury mock, including orb, server pill, stats card, iconography, and metaball dock, without changing backend, entitlement, trial/subscription, parsing, or VPN runtime contracts.

**Architecture:** Keep `MainActivity` navigation and business callbacks authoritative, but move Home rendering into focused Compose files. The new Home components are visual/presentational: they receive existing `AppState.Success`, `VpnState`, server, profile, bytes, and callbacks, and never invent access state or bypass premium checks.

**Tech Stack:** Android Kotlin, Jetpack Compose, Material3 primitives where useful, custom `Canvas`/`Path` for orb, bloom, hardware surfaces, and metaball dock.

---

## Locked Scope

This is a design-only pass.

Do not change:

- backend services;
- Prisma schema or migrations;
- entitlement/access/trial/subscription contracts;
- payment/fulfillment flows;
- VPN runtime logic;
- config parsing/normalization;
- API models or Retrofit contracts;
- Docker, Dokploy, Traefik, env, domains.

Allowed changes:

- Android Compose visual structure for Home;
- Android UI-only components;
- design token constants;
- localized UI strings only if a visible Home label requires them;
- documentation/worklog updates.

## Source Inputs

- `docs/SWIMVPN_DESIGN_DNA_2026-05.md`
- `docs/SWIMVPN_HOME_SCREEN_TOKENS_2026-05.md`
- Mock Home connected: `Gemini_Generated_Image_fqx77fqx77fqx77f (1).png`
- Mock dock metaball: `ChatGPT Image 20 mai 2026, 01_52_32.png`

## Agent Inputs Already Collected

- Design mock agent: extracted component hierarchy, proportions, glows, iconography, pixel-perfect risks.
- Android architecture agent: located current Home in `MainActivity.kt`, confirmed no metaball dock exists, recommended extracting Home components to dedicated files.

## Current Repo State

- `HomeScreen`, `ServerSelectionCard`, and `StatItem` currently live in `android/app/src/main/java/com/swimvpn/app/MainActivity.kt`.
- Current Home uses Material blue/navy style, logo top-left, status badge, power button, server card, stats row, and FAB import.
- Current app has Poppins typography and Material Icons Extended available.
- Existing callbacks must remain intact:
  - `onNavigateProfile`
  - `onNavigateServers`
  - `onNavigateImport`
  - `onNavigateSubscription`
  - `viewModel.toggleVpn(context, activeServer, profile)`

## Target File Structure

Create:

- `android/app/src/main/java/com/swimvpn/app/ui/theme/SwimDesignTokens.kt`
  - Dark Luxury color, spacing, radius, glow/elevation constants.

- `android/app/src/main/java/com/swimvpn/app/ui/components/SwimHardwareSurfaces.kt`
  - Reusable monolithic surfaces: background, circular icon badge, pill surface, hardware card.

- `android/app/src/main/java/com/swimvpn/app/ui/components/SwimHomeOrb.kt`
  - Power orb, power button layers, orbital mesh approximation.

- `android/app/src/main/java/com/swimvpn/app/ui/components/SwimMetaballDock.kt`
  - Four-node metaball dock with Home active for this screen.

- `android/app/src/main/java/com/swimvpn/app/ui/screens/HomeScreen.kt`
  - Home screen composition and UI-only state mapping from existing inputs.

Modify:

- `android/app/src/main/java/com/swimvpn/app/MainActivity.kt`
  - Remove/stop using the old inline Home implementation.
  - Delegate Home route to the new `ui.screens.HomeScreen`.
  - Preserve the exact permission launcher and `toggleVpn` flow.

- `android/app/src/main/res/values/strings.xml`
- `android/app/src/main/res/values-fr/strings.xml`
- `android/app/src/main/res/values-ru/strings.xml`
  - Add only missing accessibility labels or visible Home labels needed by the dock/stats.

- `WORKLOG.md`
  - Record implementation and verification.

Do not modify `MainViewModel.kt` unless compile errors prove that a UI-only import boundary needs a minor visibility adjustment.

---

## Task 1: Extract Home Without Visual Redesign

**Files:**

- Create: `android/app/src/main/java/com/swimvpn/app/ui/screens/HomeScreen.kt`
- Modify: `android/app/src/main/java/com/swimvpn/app/MainActivity.kt`

- [ ] Move the current `HomeScreen`, `ServerSelectionCard`, and `StatItem` composables out of `MainActivity.kt` into `ui/screens/HomeScreen.kt`.
- [ ] Keep signatures compatible with the current route.
- [ ] Keep all existing callbacks and permission behavior unchanged.
- [ ] Keep visual output intentionally equivalent in this task.
- [ ] Run:

```powershell
cd android
.\gradlew.bat :app:compileDebugKotlin
```

Expected: Kotlin compile passes.

**Why first:** this reduces risk before the design rewrite. If the extraction breaks behavior, the visual pass would hide the cause.

## Task 2: Add Dark Luxury Design Tokens

**Files:**

- Create: `android/app/src/main/java/com/swimvpn/app/ui/theme/SwimDesignTokens.kt`

- [ ] Add color constants from `docs/SWIMVPN_HOME_SCREEN_TOKENS_2026-05.md`.
- [ ] Add spacing/radius constants for Home, pill, card, orb, dock.
- [ ] Add helper values for alpha/glow/stroke usage.
- [ ] Do not replace global `SwimVpnTheme` yet.
- [ ] Run:

```powershell
cd android
.\gradlew.bat :app:compileDebugKotlin
```

Expected: Kotlin compile passes.

## Task 3: Build Hardware Surface Primitives

**Files:**

- Create: `android/app/src/main/java/com/swimvpn/app/ui/components/SwimHardwareSurfaces.kt`

- [ ] Implement `SwimDarkLuxuryBackground`.
- [ ] Implement `SwimCircularIconButton`.
- [ ] Implement `SwimPillSurface`.
- [ ] Implement `SwimHardwareCard`.
- [ ] Use Compose `Brush`, `Canvas`, `drawBehind`, `clip`, `border`, and `shadow` carefully.
- [ ] Ensure touch targets are at least `48.dp`.
- [ ] Run:

```powershell
cd android
.\gradlew.bat :app:compileDebugKotlin
```

Expected: Kotlin compile passes.

## Task 4: Build Power Orb Component

**Files:**

- Create: `android/app/src/main/java/com/swimvpn/app/ui/components/SwimHomeOrb.kt`
- Modify: `android/app/src/main/java/com/swimvpn/app/ui/screens/HomeScreen.kt`

- [ ] Implement `SwimPowerOrb`.
- [ ] Use custom `Canvas` for the purple orbital mesh: rings, dots, wave arcs, localized glow.
- [ ] Keep click behavior outside or pass `onClick` into the component; do not move entitlement or VPN permission logic into the orb.
- [ ] Support connected, connecting, disconnecting, disconnected, and error visual variants.
- [ ] Use `Icons.Rounded.PowerSettingsNew`.
- [ ] Run:

```powershell
cd android
.\gradlew.bat :app:compileDebugKotlin
```

Expected: Kotlin compile passes.

## Task 5: Build Server Pill And Stats Card

**Files:**

- Modify: `android/app/src/main/java/com/swimvpn/app/ui/screens/HomeScreen.kt`
- Modify or create in: `android/app/src/main/java/com/swimvpn/app/ui/components/SwimHardwareSurfaces.kt`

- [ ] Replace `ServerSelectionCard` visual with `SwimServerPill`.
- [ ] Show country and city/auto text only; do not expose quota/expiration on Home.
- [ ] Keep server pill click target as `onNavigateServers`.
- [ ] Replace stats row with `SwimStatsCard`.
- [ ] Match mock columns: Downloaded, Uploaded, Connected.
- [ ] Preserve existing `bytesIn` and `bytesOut` data if values remain visible; if the mock-only label mode is chosen, keep runtime data available but visually secondary.
- [ ] Run:

```powershell
cd android
.\gradlew.bat :app:compileDebugKotlin
```

Expected: Kotlin compile passes.

## Task 6: Build Metaball Dock

**Files:**

- Create: `android/app/src/main/java/com/swimvpn/app/ui/components/SwimMetaballDock.kt`
- Modify: `android/app/src/main/java/com/swimvpn/app/ui/screens/HomeScreen.kt`
- Modify: string resources only if accessibility labels are missing.

- [ ] Implement four dock nodes: Home, Servers, Subscription, Settings/Profile.
- [ ] Use a custom `Path` or layered circles/bridges to create the fused metaball silhouette.
- [ ] Home is active on this screen.
- [ ] Inactive nodes use recessed black circular zones and muted white outline icons.
- [ ] Wire tap actions:
  - Home: no-op or stay on current route.
  - Servers: `onNavigateServers`.
  - Subscription: `onNavigateSubscription`.
  - Settings/Profile: if Settings route is not part of this screen contract yet, use existing `onNavigateProfile` rather than inventing a new route.
- [ ] Do not use Material `NavigationBar`.
- [ ] Run:

```powershell
cd android
.\gradlew.bat :app:compileDebugKotlin
```

Expected: Kotlin compile passes.

## Task 7: Compose Final Home Layout

**Files:**

- Modify: `android/app/src/main/java/com/swimvpn/app/ui/screens/HomeScreen.kt`

- [ ] Remove top-left logo/app name from Home.
- [ ] Keep top-right profile button.
- [ ] Place orb in upper visual band.
- [ ] Place centered status copy below orb.
- [ ] Place server pill below status.
- [ ] Place stats card above dock.
- [ ] Overlay dock at bottom center without hiding stats.
- [ ] Use responsive vertical scaling for short screens.
- [ ] Keep all runtime state mapping from current Home:
  - `VpnManager.state`
  - `RuntimeStateStore.read`
  - `VpnState` transitions
  - notification permission launcher
  - VPN permission launcher
  - premium navigation guard before backend server connect
- [ ] Run:

```powershell
cd android
.\gradlew.bat :app:compileDebugKotlin
```

Expected: Kotlin compile passes.

## Task 8: Visual QA Capture

**Files:**

- No source change unless findings require a targeted UI fix.

- [ ] Run debug build:

```powershell
cd android
.\gradlew.bat :app:assembleDebug
```

Expected: APK builds.

- [ ] If an emulator/device is connected, install/launch and capture:

```powershell
adb devices
adb shell screencap -p /sdcard/swimvpn_home_dark_luxury.png
adb pull /sdcard/swimvpn_home_dark_luxury.png screenshots/
```

- [ ] Compare against the mock:
  - background bloom;
  - orb size and placement;
  - power button depth;
  - status text hierarchy;
  - server pill shape;
  - stats card spacing;
  - dock metaball silhouette;
  - no overlap at bottom;
  - profile button safe-area placement.

## Task 9: Final Verification And Notes

**Files:**

- Modify: `WORKLOG.md`
- Modify: `TODO.md` if visual QA remains pending.
- Modify: `DECISIONS.md` only if the dock/settings route decision changes.

- [ ] Run strongest available Android check:

```powershell
cd android
.\gradlew.bat :app:assembleDebug
```

Expected: build passes.

- [ ] Run `git diff --check`.
- [ ] Run `git status --short --branch`.
- [ ] Update `WORKLOG.md` with files changed and verification.
- [ ] Update `TODO.md` for any live visual QA that could not be completed.

---

## Review Checkpoints

After each implementation task:

1. Spec review: confirm no backend/contract/runtime access behavior changed.
2. Visual review: compare to token doc and mocks.
3. Code quality review: ensure new files stay focused and `MainActivity.kt` gets smaller, not larger.

## Manual QA Checklist

- Home opens for active user without navigation loop.
- Expired/freemium user still enters app shell.
- Premium backend server still redirects to subscription when not allowed.
- Power button still requests VPN permission when needed.
- Connect/disconnect still calls existing `viewModel.toggleVpn`.
- Server pill opens server selection.
- Subscription dock node opens subscription.
- Profile/settings dock node does not invent unavailable settings route.
- Dock does not hide stats on 360dp, 390dp, 412dp, 430dp widths.
- Touch targets are at least `48.dp`.
- TalkBack labels exist for power, profile, server selection, and dock nodes.

## Risk Level

Medium.

The backend/business surface is frozen and untouched, so product-contract risk is low. Visual risk is medium/high because the orb mesh, metaball shape, glows, and inner shadows require custom Compose drawing and may need iterative screenshot tuning.
