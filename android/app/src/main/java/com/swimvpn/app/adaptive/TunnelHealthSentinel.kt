package com.swimvpn.app.adaptive

/** Verdict returned per probe. DEGRADED fires exactly once per streak (then ALREADY_DEGRADED) so the
 *  caller triggers one recovery action, not one per probe. */
enum class HealthVerdict { HEALTHY, DEGRADED, ALREADY_DEGRADED }

/**
 * Pure debounce for "connected but unhealthy": feed it the outcome of each through-tunnel probe; it
 * declares DEGRADED after [degradedThreshold] consecutive failures and latches until a success or
 * [reset]. No Android deps — the probe I/O and scheduling live in the caller.
 */
class TunnelHealthSentinel(private val degradedThreshold: Int = 3) {
    private var consecutiveFailures = 0
    private var latched = false

    fun onProbe(success: Boolean): HealthVerdict {
        if (success) {
            consecutiveFailures = 0
            latched = false
            return HealthVerdict.HEALTHY
        }
        consecutiveFailures += 1
        if (consecutiveFailures < degradedThreshold) return HealthVerdict.HEALTHY
        if (latched) return HealthVerdict.ALREADY_DEGRADED
        latched = true
        return HealthVerdict.DEGRADED
    }

    fun reset() {
        consecutiveFailures = 0
        latched = false
    }
}
