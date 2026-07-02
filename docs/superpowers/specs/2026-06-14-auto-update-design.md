# Auto-update (sideload) — design

**Goal:** let the **sideload** SWIMVPN build detect a newer release, download it, and install it in **one
tap** (the OS shows its install confirmation). NOT a silent background self-update — that is impossible
for a non-privileged sideloaded Android app, and we don't pretend otherwise.

**Status:** design. To be implemented on the reserved branch (`feat/auto-update` / "MAJ"), flavor-gated,
device-gated before merge. No code until this spec + its plan are approved.

---

## 1. Hard constraint (the honesty line)
A sideloaded app (not Play) **cannot install an APK without user approval** — only device-owner/system
apps install silently. So "se met à jour toute seule" with zero interaction is **out of reach**. What we
build: **auto-CHECK + auto-DOWNLOAD + one-tap install** (Android shows the system "Update" dialog; the user
taps once). The UI must never imply a fully silent update.

## 2. Scope & gating
- **Sideload build ONLY.** The whole feature (incl. the `REQUEST_INSTALL_PACKAGES` permission and the
  installer code) is **gated by build flavor** and absent from the **Play build** — Google Play forbids
  self-updating / `REQUEST_INSTALL_PACKAGES`; the Play build updates via Play (Play Core In-App Updates,
  separate, out of scope here). This dovetails with the "two builds" plan ([[launch-and-store-plan]]).
- **No backend change.** The version manifest is a static file served by the landing (same model as the
  APK in `public/downloads/`).

## 3. Architecture
### Version manifest — `public/version.json` (served at `https://app.swimvpn.pro/version.json`)
```json
{
  "latestVersionCode": 11,
  "versionName": "1.0.10",
  "apkUrl": "https://app.swimvpn.pro/downloads/swimvpn.apk",
  "sha256": "<hex digest of the APK>",
  "minSupportedCode": 1,
  "changelog": { "ru": "…", "fr": "…", "en": "…" }
}
```
- **Source of truth = `build.gradle` `versionCode`.** The manifest MUST be generated/synced at release
  (recommended: a small release script emits `version.json` + the APK's SHA-256 — avoids drift, the
  prerender lesson [[verify-after-backend-merge]]). Manual edits are drift-prone.

### Components (pure logic split from Android I/O, like ServerScoreCodec/AccessCacheCodec)
- **`UpdateManifestCodec`** (pure): parse `version.json` → `UpdateManifest?`, tolerant (null on garbage,
  never throws). Unit-tested.
- **`UpdatePolicy`** (pure): `decide(currentCode: Int, manifest: UpdateManifest): UpdateDecision` →
  - `currentCode >= latestVersionCode` → `UpToDate`
  - `currentCode < minSupportedCode` → `Mandatory(versionName, changelog)`
  - else → `Optional(versionName, changelog)`
  This is the core; TDD it (boundary cases: equal, newer, below-min, malformed).
- **`UpdateChecker`** (Android): fetch the manifest (existing OkHttp/Retrofit), **throttle** (≤1×/day via
  a `last_update_check_at` pref), feed `BuildConfig.VERSION_CODE` into `UpdatePolicy`. Network failure =
  **silent no-op** (no nag, consistent with offline-access).
- **`ApkInstaller`** (Android): `DownloadManager` → on completion **verify SHA-256 vs manifest** → launch
  `Intent(ACTION_INSTALL_PACKAGE)` / `PackageInstaller` via a FileProvider URI → system confirm.

### Permissions (sideload flavor only)
- `REQUEST_INSTALL_PACKAGES` (Android 8+, to launch the installer). A FileProvider for the downloaded APK.

## 4. UX
- **Optional** update → dismissable banner/dialog with versionName + changelog (locale) + "Update" / "Later".
  Remember the dismissed versionCode so we don't nag for the same one.
- **Mandatory** (`< minSupportedCode`, e.g. a security/critical fix) → **blocking** screen "Update required
  to continue" with only the Update action.
- Check timing: on app launch + at most once/day. Honest copy; one tap leads to the OS install dialog.

## 5. Security
- Android **natively rejects** an update signed by a different keystore → only our genuinely-signed APK can
  update the install (cert pinning by the OS). HTTPS-only manifest + APK. **SHA-256 verification** of the
  downloaded APK against the manifest **before** launching the installer (defense-in-depth vs a tampered
  CDN/file). Abort + warn on mismatch.

## 6. Error handling
- Manifest fetch fails / offline → silent no-op (never block, never error-toast on launch).
- Download fails → toast + keep the banner (retry on next tap).
- SHA-256 mismatch → abort, surface a clear "update verification failed" message, do NOT install.
- User declines the OS install → banner stays for next time.

## 7. Out of scope (no theater)
- **Silent / background install** (impossible for sideload). **Play In-App Updates** implementation
  (separate, Play flavor). Delta/differential updates. Auto-rollback. Forced install without user tap.

## 8. Verification plan
- **Pure unit tests (TDD):** `UpdatePolicy` (all decision branches + boundaries) + `UpdateManifestCodec`
  (round-trip, tolerant decode, null/garbage). RAM-constrained gradle flags as usual.
- **Device gate (sideload build):** real cycle on the Samsung — bump a test manifest → app shows the
  banner → tap → download → SHA-256 ok → system install dialog → updated build runs. Mandatory path
  (`minSupportedCode` > current) shows the blocking screen. Network-off = no nag. 0 crash.
- Branch `feat/auto-update`, device-gated before any merge; Play flavor must NOT contain the feature.

## 9. Open questions (confirm before/at plan)
- `version.json` generation: a **release script** (auto from `versionCode` + APK SHA-256) vs manual. (Reco: script.)
- Banner placement: Home-level vs app-level (above the nav). 
- Daily-throttle + "dismissed version" persistence keys (PreferencesManager).
- Flavor mechanism: a Gradle `productFlavor` (sideload/play) vs a `BuildConfig` boolean + manifest-merger
  exclusion of the permission. (Reco: real productFlavor — also serves the Play split cleanly.)
