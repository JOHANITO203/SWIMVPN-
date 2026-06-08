package com.swimvpn.app.adaptive

/**
 * Pure fire-once gate for HONEST reliability credit. A profile is credited a success only after the
 * health sentinel has seen [threshold] CONSECUTIVE healthy probes this session — i.e. the tunnel really
 * carried traffic over time, not just completed a handshake (the old "RUNNING = success" false oracle).
 *
 * Fed by the EXISTING gstatic liveness probe (no new network I/O). [shouldCredit] fires exactly once per
 * healthy streak then latches, so the 8s sentinel cadence cannot inflate the score every cycle. An
 * unhealthy probe breaks the streak and re-arms. Counts PROBES, never wall-clock. No Android deps.
 */
class SustainedHealthGate(private val threshold: Int = 2) {
    private var consecutiveHealthy = 0
    private var credited = false

    fun onHealthy() { consecutiveHealthy += 1 }
    fun onUnhealthy() { consecutiveHealthy = 0; credited = false }
    fun reset() { consecutiveHealthy = 0; credited = false }

    fun shouldCredit(): Boolean {
        if (consecutiveHealthy < threshold || credited) return false
        credited = true
        return true
    }
}
