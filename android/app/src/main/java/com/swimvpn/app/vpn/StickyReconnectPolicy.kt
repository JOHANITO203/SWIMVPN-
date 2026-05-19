package com.swimvpn.app.vpn

object StickyReconnectPolicy {
    private const val STICKY_RESTORE_MAX_AGE_MS = 120_000L

    fun shouldRestoreStickySession(
        snapshot: RuntimeStateSnapshot,
        nowMs: Long = System.currentTimeMillis(),
    ): Boolean {
        if (!snapshot.isFresh(now = nowMs, maxAgeMs = STICKY_RESTORE_MAX_AGE_MS)) {
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
