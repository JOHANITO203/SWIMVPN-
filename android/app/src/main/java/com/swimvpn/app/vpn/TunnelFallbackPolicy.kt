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
     * NEUTRALIZED (B1/B2): LOCAL_PROXY does NOT route device traffic (no VpnService tun, no
     * setHttpProxy), so degrading a failed FULL_TUNNEL to it produced a "connected" UI with no
     * actual protection — a leak. We therefore NEVER fall back to proxy; a data-plane failure now
     * surfaces honestly as FAILED. This is kept as the single decision point in case a *real*
     * (routing) fallback is introduced later. See docs/LOCAL_PROXY_ANALYSIS.md.
     */
    private val PROXY_RESCUABLE_CAUSES = emptySet<DisconnectCause>()

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
