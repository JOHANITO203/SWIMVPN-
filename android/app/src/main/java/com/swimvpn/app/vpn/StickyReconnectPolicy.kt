package com.swimvpn.app.vpn

object StickyReconnectPolicy {
    fun shouldRestoreStickySession(
        snapshot: RuntimeStateSnapshot,
        nowMs: Long = System.currentTimeMillis(),
    ): Boolean {
        if (!snapshot.isFresh(now = nowMs)) {
            return false
        }

        return when (snapshot.status) {
            RuntimeStatus.STARTING,
            RuntimeStatus.RUNNING,
            RuntimeStatus.RECONNECTING,
            RuntimeStatus.DEGRADED -> true
            RuntimeStatus.IDLE,
            RuntimeStatus.STOPPING,
            RuntimeStatus.FAILED,
            RuntimeStatus.STOPPED_BY_USER -> false
        }
    }
}
