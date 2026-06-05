package com.swimvpn.app.adaptive

import org.junit.Assert.assertEquals
import org.junit.Test

class TunnelHealthSentinelTest {

    @Test
    fun `stays healthy until the threshold of consecutive failures`() {
        val s = TunnelHealthSentinel(degradedThreshold = 3)
        assertEquals(HealthVerdict.HEALTHY, s.onProbe(success = false)) // 1
        assertEquals(HealthVerdict.HEALTHY, s.onProbe(success = false)) // 2
        assertEquals(HealthVerdict.DEGRADED, s.onProbe(success = false)) // 3 -> degraded
    }

    @Test
    fun `a single success resets the failure streak`() {
        val s = TunnelHealthSentinel(degradedThreshold = 3)
        s.onProbe(false); s.onProbe(false)
        assertEquals(HealthVerdict.HEALTHY, s.onProbe(success = true)) // reset
        assertEquals(HealthVerdict.HEALTHY, s.onProbe(success = false)) // 1 again
        assertEquals(HealthVerdict.HEALTHY, s.onProbe(success = false)) // 2
        assertEquals(HealthVerdict.DEGRADED, s.onProbe(success = false)) // 3
    }

    @Test
    fun `reset clears the streak`() {
        val s = TunnelHealthSentinel(degradedThreshold = 3)
        s.onProbe(false); s.onProbe(false)
        s.reset()
        assertEquals(HealthVerdict.HEALTHY, s.onProbe(success = false)) // back to 1
    }

    @Test
    fun `degraded latches only once until reset (no repeated firing)`() {
        val s = TunnelHealthSentinel(degradedThreshold = 2)
        assertEquals(HealthVerdict.HEALTHY, s.onProbe(false))
        assertEquals(HealthVerdict.DEGRADED, s.onProbe(false)) // fires
        assertEquals(HealthVerdict.ALREADY_DEGRADED, s.onProbe(false)) // does not re-fire
    }
}
