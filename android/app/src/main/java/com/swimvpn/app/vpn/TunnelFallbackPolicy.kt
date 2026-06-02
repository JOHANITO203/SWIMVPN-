package com.swimvpn.app.vpn

/**
 * Pure decision policy for degrading a failed FULL_TUNNEL session to LOCAL_PROXY.
 *
 * Motivation (OEM hardening): on some devices (notably aggressive MIUI / mid-range
 * Samsung builds) the full-tunnel data plane — `VpnService.Builder.establish()` and/or
 * the in-process tun2socks JNI — can fail or crash while a plain SOCKS proxy still works.
 * Rather than leaving the user with nothing, we retry the session once in LOCAL_PROXY mode
 * (no VpnService tun, no tun2socks) so they keep working connectivity.
 *
 * We only fall back for causes that point at the tunnel INFRASTRUCTURE, never for problems
 * a proxy session would hit identically (no network, server unreachable, invalid config) or
 * for a deliberate user stop.
 */
object TunnelFallbackPolicy {

    /**
     * Causes that represent a FULL_TUNNEL data-plane failure a LOCAL_PROXY session does not
     * depend on (establish() failure, tun2socks unavailable/crash). Network/server/config/user
     * causes are intentionally excluded — proxy mode cannot rescue those.
     */
    private val PROXY_RESCUABLE_CAUSES = setOf(
        DisconnectCause.ENGINE_CRASH,
        DisconnectCause.UNKNOWN,
    )

    fun shouldFallbackToProxy(
        requestedMode: RuntimeMode,
        cause: DisconnectCause,
        stoppedByUser: Boolean,
        alreadyFellBack: Boolean,
    ): Boolean =
        requestedMode == RuntimeMode.FULL_TUNNEL &&
            !stoppedByUser &&
            !alreadyFellBack &&
            cause in PROXY_RESCUABLE_CAUSES
}
