package com.swimvpn.app.vpn

object StickyReconnectPolicy {
    private const val STICKY_RESTORE_MAX_AGE_MS = 120_000L

    fun shouldRestoreStickySession(
        snapshot: RuntimeStateSnapshot,
        nowMs: Long = System.currentTimeMillis(),
    ): Boolean {
        // Kill-recovery uses its own time window (decoupled from RuntimeStateSnapshot.isFresh(),
        // which is now status-based and intentionally treats active statuses as not-fresh for the UI).
        if (nowMs - snapshot.updatedAt > STICKY_RESTORE_MAX_AGE_MS) {
            return false
        }

        return when (snapshot.status) {
            RuntimeStatus.STARTING,
            RuntimeStatus.RUNNING,
            RuntimeStatus.RECONNECTING,
            RuntimeStatus.DEGRADED,
            RuntimeStatus.NO_NETWORK -> true
            RuntimeStatus.IDLE,
            RuntimeStatus.STOPPING,
            RuntimeStatus.FAILED,
            RuntimeStatus.STOPPED_BY_USER -> false
        }
    }
}
