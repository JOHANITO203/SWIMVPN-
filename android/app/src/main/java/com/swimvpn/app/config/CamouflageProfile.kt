package com.swimvpn.app.config

import com.swimvpn.app.R

/**
 * A client-side camouflage profile. The ONLY camouflage knob that is safely tunable from the client
 * without server cooperation is the **uTLS fingerprint** (the ClientHello is made to look like a real
 * browser). Transport, cover-domain SNI, etc. are server-determined and intentionally NOT modeled here.
 *
 * [fingerprint] is an Xray uTLS value ("chrome"/"firefox"/"safari"/"ios"/"randomized"). It is applied
 * only where the server's transport already uses TLS or Reality (see TunnelRuntimeAdapter); on a
 * plaintext transport it is a no-op.
 *
 * IMPORTANT (honesty): a profile changes how the handshake *looks*, which can help against some DPI —
 * but the client cannot verify stealth. We learn connection RELIABILITY per profile/network, never a
 * "detectability score". UI wording must say compatibility/profile, never "invisible/undetectable".
 */
data class CamouflageProfile(
    val id: String,
    val displayNameRes: Int,
    val fingerprint: String,
)

object CamouflageProfileRepository {
    val CHROME = CamouflageProfile("chrome", R.string.camouflage_chrome, "chrome")
    val FIREFOX = CamouflageProfile("firefox", R.string.camouflage_firefox, "firefox")
    val SAFARI = CamouflageProfile("safari", R.string.camouflage_safari, "safari")
    val IOS = CamouflageProfile("ios", R.string.camouflage_ios, "ios")
    val RANDOMIZED = CamouflageProfile("randomized", R.string.camouflage_randomized, "randomized")

    /** Default profile = chrome, i.e. today's behavior (Reality already defaults to "chrome"). */
    val DEFAULT: CamouflageProfile = CHROME

    private val ALL: List<CamouflageProfile> = listOf(CHROME, FIREFOX, SAFARI, IOS, RANDOMIZED)

    /**
     * Order the adaptive agent prefers on ties / walks as a cascade when a profile keeps failing on a
     * network. chrome first (most common, blends in), then the others, randomized last (most distinct).
     */
    val fallbackOrder: List<CamouflageProfile> = listOf(CHROME, FIREFOX, SAFARI, IOS, RANDOMIZED)

    fun all(): List<CamouflageProfile> = ALL

    /** Resolve by id, falling back to [DEFAULT] for null/unknown ids. */
    fun byId(id: String?): CamouflageProfile = ALL.firstOrNull { it.id == id } ?: DEFAULT
}
