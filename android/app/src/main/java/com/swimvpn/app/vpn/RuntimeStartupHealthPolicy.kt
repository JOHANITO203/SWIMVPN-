package com.swimvpn.app.vpn

object RuntimeStartupHealthPolicy {
    fun canMarkRunning(
        xrayAlive: Boolean,
        requireTun2Socks: Boolean,
        tun2SocksAlive: Boolean,
    ): Boolean {
        if (!xrayAlive) {
            return false
        }

        return !requireTun2Socks || tun2SocksAlive
    }
}
