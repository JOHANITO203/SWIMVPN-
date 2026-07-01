package com.swimvpn.app.update

/** What the app should do about an available (or not) update. */
sealed class UpdateDecision {
    /** Current build is the latest (or newer, e.g. a dev build) — nothing to show. */
    object UpToDate : UpdateDecision()

    /** A newer build exists; show a dismissable prompt. */
    data class Optional(val manifest: UpdateManifest) : UpdateDecision()

    /**
     * Current build is below [UpdateManifest.minSupportedCode] (critical/security floor) —
     * show the blocking "update required" screen.
     */
    data class Mandatory(val manifest: UpdateManifest) : UpdateDecision()
}

/**
 * Pure decision core (design §3): given the installed versionCode and the remote manifest,
 * decide UpToDate / Optional / Mandatory. No I/O, no Android — TDD'd.
 */
object UpdatePolicy {
    fun decide(currentCode: Int, manifest: UpdateManifest): UpdateDecision = when {
        currentCode >= manifest.latestVersionCode -> UpdateDecision.UpToDate
        currentCode < manifest.minSupportedCode -> UpdateDecision.Mandatory(manifest)
        else -> UpdateDecision.Optional(manifest)
    }
}
