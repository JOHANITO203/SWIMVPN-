package com.swimvpn.app.config

import com.swimvpn.app.R

/** xray sockopt.fragment: split outgoing TLS records across TCP segments to blur SNI/size DPI. */
data class FragmentSpec(val packets: String, val length: String, val interval: String)

/** xray sockopt.noises: inject junk packets before the handshake. */
data class NoiseSpec(val type: String, val packet: String, val delay: String)

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
    val fragment: FragmentSpec? = null,
    val noises: List<NoiseSpec>? = null,
)

object CamouflageProfileRepository {
    /**
     * AUTO = keep the server/link's own uTLS fingerprint (empty [fingerprint] ⇒ no override). This is
     * the default and the safe baseline: suppliers ship links with a fingerprint they have validated
     * (e.g. Reality `fp=random`), and forcing a different one (the old chrome default) broke premium
     * Reality nodes on DPI networks — the ClientHello no longer matched what the server/path expected.
     * Camouflage only *overrides* the fingerprint when the user or the agent explicitly picks a browser.
     */
    val AUTO = CamouflageProfile("auto", R.string.camouflage_auto, "")
    val CHROME = CamouflageProfile("chrome", R.string.camouflage_chrome, "chrome")
    val FIREFOX = CamouflageProfile("firefox", R.string.camouflage_firefox, "firefox")
    val SAFARI = CamouflageProfile("safari", R.string.camouflage_safari, "safari")
    val IOS = CamouflageProfile("ios", R.string.camouflage_ios, "ios")
    val RANDOMIZED = CamouflageProfile("randomized", R.string.camouflage_randomized, "randomized")

    // Fragmentation presets keep the link's own fp ("") and only ADD TLS fragmentation — completing
    // the supplier fingerprint, never overriding it. Picker-only until device-validated (a later task).
    val FRAG_LIGHT = CamouflageProfile(
        "frag_light", R.string.camouflage_frag_light, "",
        fragment = FragmentSpec("tlshello", "100-200", "10-20"),
    )
    val FRAG_AGGRESSIVE = CamouflageProfile(
        "frag_aggressive", R.string.camouflage_frag_aggressive, "",
        fragment = FragmentSpec("1-3", "40-80", "5-15"),
    )

    /** Default = AUTO: respect the link's fingerprint unless a browser profile is explicitly chosen. */
    val DEFAULT: CamouflageProfile = AUTO

    private val ALL: List<CamouflageProfile> = listOf(AUTO, CHROME, FIREFOX, SAFARI, IOS, RANDOMIZED, FRAG_LIGHT, FRAG_AGGRESSIVE)

    /**
     * Order the adaptive agent prefers on ties / walks as a cascade when a profile keeps failing on a
     * network. AUTO first (respect the supplier's validated fingerprint), then the browser profiles, then
     * the device-validated TLS-fragmentation presets as a last-resort escalation when nothing else holds.
     */
    val fallbackOrder: List<CamouflageProfile> =
        listOf(AUTO, CHROME, FIREFOX, SAFARI, IOS, RANDOMIZED, FRAG_LIGHT, FRAG_AGGRESSIVE)

    fun all(): List<CamouflageProfile> = ALL

    /** Resolve by id, falling back to [DEFAULT] for null/unknown ids. */
    fun byId(id: String?): CamouflageProfile = ALL.firstOrNull { it.id == id } ?: DEFAULT
}
