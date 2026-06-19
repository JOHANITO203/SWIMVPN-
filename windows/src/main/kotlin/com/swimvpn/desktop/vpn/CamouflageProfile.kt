package com.swimvpn.desktop.vpn

/**
 * Client-side camouflage = the uTLS **fingerprint** the TLS/REALITY handshake mimics. This is the
 * ONLY anti-DPI knob that is actually adjustable from the client (transport + cover-domain are
 * server-side). AUTO = no override (use the link's own fingerprint) — the safe, zero-regression
 * default. We learn connection RELIABILITY per server/profile, never an unmeasurable "stealth".
 */
enum class CamouflageProfile(val id: String, val label: String, val fingerprint: String?) {
    AUTO("auto", "Auto", null),
    CHROME("chrome", "Chrome", "chrome"),
    FIREFOX("firefox", "Firefox", "firefox"),
    SAFARI("safari", "Safari", "safari"),
    IOS("ios", "iOS", "ios"),
    RANDOM("random", "Random", "randomized");

    companion object {
        fun byId(id: String?): CamouflageProfile = entries.firstOrNull { it.id == id } ?: AUTO
        /** Auto-heal order: link default first (least intrusive), then common browsers, random last. */
        val cascade: List<CamouflageProfile> = listOf(AUTO, CHROME, FIREFOX, SAFARI, RANDOM)
    }
}
