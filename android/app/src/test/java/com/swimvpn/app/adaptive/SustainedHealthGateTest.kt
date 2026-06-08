package com.swimvpn.app.adaptive

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SustainedHealthGateTest {
    @Test fun `one healthy probe under threshold does not credit`() {
        val g = SustainedHealthGate(threshold = 2)
        g.onHealthy()
        assertFalse(g.shouldCredit())
    }

    @Test fun `two consecutive healthy probes credit exactly once`() {
        val g = SustainedHealthGate(threshold = 2)
        g.onHealthy(); g.onHealthy()
        assertTrue(g.shouldCredit())
        g.onHealthy()
        assertFalse(g.shouldCredit())
    }

    @Test fun `sustained health after the credit never re-credits`() {
        val g = SustainedHealthGate(threshold = 2)
        g.onHealthy(); g.onHealthy()
        assertTrue(g.shouldCredit())
        repeat(5) { g.onHealthy(); assertFalse(g.shouldCredit()) }
    }

    @Test fun `an unhealthy probe breaks the streak`() {
        val g = SustainedHealthGate(threshold = 2)
        g.onHealthy(); g.onUnhealthy(); g.onHealthy()
        assertFalse(g.shouldCredit())
    }

    @Test fun `unhealthy re-arms so a later streak can credit again`() {
        val g = SustainedHealthGate(threshold = 2)
        g.onHealthy(); g.onHealthy(); assertTrue(g.shouldCredit())
        g.onUnhealthy()
        g.onHealthy(); g.onHealthy(); assertTrue(g.shouldCredit())
    }

    @Test fun `reset clears streak and latch for a fresh session`() {
        val g = SustainedHealthGate(threshold = 2)
        g.onHealthy(); g.onHealthy(); assertTrue(g.shouldCredit())
        g.reset()
        g.onHealthy(); g.onHealthy(); assertTrue(g.shouldCredit())
    }
}
