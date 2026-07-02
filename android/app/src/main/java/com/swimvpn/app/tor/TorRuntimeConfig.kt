package com.swimvpn.app.tor

/**
 * Onion Stealth — configuration source of truth for the embedded Tor client.
 *
 * Chain (Tor-over-REALITY):
 *   app → tun → tun2socks → xray "socks-in" (10808) → xray "tor" outbound → Tor SOCKS (9050)
 *   Tor → Socks5Proxy 127.0.0.1:[EGRESS_SOCKS_PORT] → xray "tor-egress" inbound → xray "proxy" (REALITY)
 *       → your server → guard → … → exit → destination
 *
 * Consequences that make this the differentiating feature (not theatre):
 *  - The ISP only ever sees REALITY to your server — never "this user is on Tor". So Tor stays usable
 *    on networks where guards/bridges are DPI-blocked (e.g. RU): REALITY carries Tor's transport.
 *  - Your server only ever sees encrypted Tor traffic to guards — never the destinations. No-log by
 *    construction.
 *  - Because REALITY IS the pluggable transport, Tor needs NO bridges/obfs4/snowflake here.
 *
 * The ports are the single source of truth shared by [torrc] and the xray document transform
 * (TunnelRuntimeAdapter.applyTorChaining). Changing one without the other breaks the chain.
 *
 * NOTE: control socket, DataDirectory and CacheDirectory are owned by guardianproject's TorService
 * (it passes --ControlSocket / --DataDirectory itself), so this torrc deliberately does NOT set them.
 * These lines are appended to the file returned by `TorService.getTorrc(context)`.
 */
object TorRuntimeConfig {

    /** Tor's SOCKS listener. The xray "tor" outbound dials this so app traffic enters the Tor network. */
    const val SOCKS_PORT: Int = 9050

    /**
     * The xray SOCKS inbound that Tor exits through (torrc `Socks5Proxy`). Kept distinct from the
     * app-facing "socks-in" (10808) and the "http-in" (10809) so the two legs never collide.
     */
    const val EGRESS_SOCKS_PORT: Int = 10810

    /**
     * Render the app-owned torrc lines. Pure/deterministic (no clock, no randomness) so it is
     * unit-testable and stable. Written into `TorService.getTorrc(context)` before the service starts.
     *
     * @param socksPort pinned SOCKS port the xray "tor" outbound dials (overrides TorService's auto one).
     * @param egressSocksPort the xray inbound Tor routes its guard connections through (→ REALITY).
     */
    fun torrc(
        socksPort: Int = SOCKS_PORT,
        egressSocksPort: Int = EGRESS_SOCKS_PORT,
    ): String = buildString {
        appendLine("# Onion Stealth — generated torrc (appended to TorService's torrc). Do not edit by hand.")
        // Pin the SOCKS port so xray's "tor" outbound has a stable target.
        appendLine("SOCKSPort 127.0.0.1:$socksPort")
        // Tor makes ALL of its outbound (guard) connections through this SOCKS5 proxy, which is the
        // xray REALITY tunnel. This is what hides "using Tor" from the ISP and lets Tor work where
        // guards are blocked — so we deliberately do NOT configure any Bridge/obfs4/snowflake.
        appendLine("Socks5Proxy 127.0.0.1:$egressSocksPort")
        // Mobile hygiene: pure client, minimise flash writes.
        appendLine("ClientOnly 1")
        appendLine("AvoidDiskWrites 1")
        // Surface Tor's bootstrap/warning lines in logcat (tag "Tor"). notice = concise (bootstrap %
        // + warnings). Bump to `info` temporarily when a device spike needs per-relay connection detail.
        appendLine("Log notice syslog")
    }
}
