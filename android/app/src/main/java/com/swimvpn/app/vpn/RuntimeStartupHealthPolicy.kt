package com.swimvpn.app.vpn

object RuntimeStartupHealthPolicy {
    fun canMarkRunning(
        xrayAlive: Boolean,
        requireTun2Socks: Boolean,
        tun2SocksAlive: Boolean,
        trafficConfirmed: Boolean = true,
    ): Boolean {
        if (!xrayAlive) {
            return false
        }

        if (!trafficConfirmed) {
            return false
        }

        return !requireTun2Socks || tun2SocksAlive
    }

    fun isTrafficStalled(
        bytesIn: Long,
        bytesOut: Long,
        elapsedMs: Long,
        thresholdMs: Long,
    ): Boolean = elapsedMs >= thresholdMs && bytesOut > 0 && bytesIn == 0L
}
