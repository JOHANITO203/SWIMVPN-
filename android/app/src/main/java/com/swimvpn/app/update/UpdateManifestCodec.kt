package com.swimvpn.app.update

import com.google.gson.Gson

/**
 * The remote version manifest served statically by the landing at
 * `https://app.swimvpn.pro/version.json` (generated at release by
 * `scripts/generate-version-manifest.mjs` — source of truth = build.gradle versionCode).
 *
 * [changelog] maps locale ("ru"/"fr"/"en") to a short human summary of the release.
 */
data class UpdateManifest(
    val latestVersionCode: Int,
    val versionName: String,
    val apkUrl: String,
    val sha256: String,
    val minSupportedCode: Int,
    val changelog: Map<String, String>?,
) {
    /** Changelog in [locale], falling back en → fr → ru → any → empty. */
    fun changelogFor(locale: String): String {
        val map = changelog ?: return ""
        return map[locale]
            ?: map["en"]
            ?: map["fr"]
            ?: map["ru"]
            ?: map.values.firstOrNull()
            ?: ""
    }
}

/**
 * Pure, Android-free decoding of `version.json` — unit-testable without a device.
 * Tolerant like [com.swimvpn.app.data.local.AccessCacheCodec]: null on null/blank/garbage or on a
 * manifest that lacks the fields the updater cannot work without. NEVER throws — a malformed
 * manifest must never crash app launch.
 */
object UpdateManifestCodec {
    private val gson = Gson()

    fun decode(json: String?): UpdateManifest? {
        if (json.isNullOrBlank()) return null
        val parsed = runCatching { gson.fromJson(json, UpdateManifest::class.java) }.getOrNull() ?: return null
        // Gson populates by reflection: non-null Kotlin fields CAN be null at runtime.
        @Suppress("SENSELESS_COMPARISON")
        if (parsed.versionName == null || parsed.apkUrl == null || parsed.sha256 == null) return null
        if (parsed.latestVersionCode <= 0) return null
        if (parsed.versionName.isBlank() || parsed.sha256.isBlank()) return null
        // HTTPS-only (security §5 of the design): refuse a downgraded/plain URL outright.
        if (!parsed.apkUrl.startsWith("https://")) return null
        return parsed
    }
}
