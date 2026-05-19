package com.swimvpn.app.vpn

object RuntimeRecoveryPolicy {
    fun shouldRecoverKilledSession(
        snapshot: RuntimeStateSnapshot,
        payloadAvailable: Boolean,
        vpnPermissionAvailable: Boolean,
        nowMs: Long = System.currentTimeMillis(),
    ): Boolean {
        if (!StickyReconnectPolicy.shouldRestoreStickySession(snapshot, nowMs)) {
            return false
        }

        if (!payloadAvailable) {
            return false
        }

        if (snapshot.mode == RuntimeMode.FULL_TUNNEL && !vpnPermissionAvailable) {
            return false
        }

        return true
    }
}
