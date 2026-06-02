package com.swimvpn.app.config

/**
 * Pure decision policy for the conservative subscription auto-refresh feature.
 *
 * A subscription is only eligible for an automatic refresh when:
 *  - its provider declared an auto-update interval ([intervalHours] > 0), AND
 *  - the time elapsed since the last successful refresh is at least the interval.
 *
 * This object holds NO state and performs NO I/O so it can be unit-tested in isolation.
 * The triggering (when/where to call) and the actual fetch/import live elsewhere.
 */
object SubscriptionRefreshPolicy {

    /**
     * @param intervalHours provider-declared auto-update interval in hours (<= 0 disables auto-refresh).
     * @param lastRefreshedAtMs epoch millis of the last successful refresh (0 when never refreshed).
     * @param nowMs current epoch millis.
     * @return true only when auto-refresh is enabled and the interval has elapsed.
     */
    fun shouldRefresh(intervalHours: Int, lastRefreshedAtMs: Long, nowMs: Long): Boolean {
        if (intervalHours <= 0) return false
        // Treat a missing/zero timestamp as "due" so a freshly tracked subscription
        // refreshes on the next eligible trigger rather than waiting a full interval.
        if (lastRefreshedAtMs <= 0L) return true
        val intervalMs = intervalHours.toLong() * 60L * 60L * 1000L
        val elapsed = nowMs - lastRefreshedAtMs
        // Guard against clock skew / time travel: a negative elapsed is not "due".
        if (elapsed < 0L) return false
        return elapsed >= intervalMs
    }
}
