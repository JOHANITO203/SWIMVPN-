package com.swimvpn.app.adaptive

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class BanditPolicyTest {
    private val H = BanditPolicy.DEFAULT_HALF_LIFE_MS

    // --- decay ---
    @Test fun `decay halves the mass at exactly one half-life`() {
        assertEquals(5.0, BanditPolicy.decay(10.0, lastUpdateMs = 0L, nowMs = H, halfLifeMs = H), 1e-9)
    }

    @Test fun `decay quarters the mass at two half-lives`() {
        assertEquals(2.5, BanditPolicy.decay(10.0, lastUpdateMs = 0L, nowMs = 2 * H, halfLifeMs = H), 1e-9)
    }

    @Test fun `decay is a no-op when now is not after last update`() {
        assertEquals(10.0, BanditPolicy.decay(10.0, lastUpdateMs = 100L, nowMs = 100L, halfLifeMs = H), 1e-9)
        assertEquals(10.0, BanditPolicy.decay(10.0, lastUpdateMs = 200L, nowMs = 100L, halfLifeMs = H), 1e-9)
    }

    @Test fun `decay floors at zero for non-positive mass`() {
        assertEquals(0.0, BanditPolicy.decay(0.0, 0L, H, H), 1e-9)
    }

    // --- ucbScore ---
    @Test fun `cold-start arm scores its prior plus the full exploration bonus`() {
        val c = 0.7
        val total = 9.0
        val expectedBonus = c * Math.sqrt(Math.log(total + 1.0) / 1.0)
        val score = BanditPolicy.ucbScore(successMass = 0.0, failureMass = 0.0, totalMass = total, priorMean = 0.4, explorationC = c)
        assertEquals(0.4 + expectedBonus, score, 1e-9)
    }

    @Test fun `the uncertainty bonus shrinks as an arm accrues mass`() {
        val thin = BanditPolicy.ucbScore(1.0, 1.0, totalMass = 100.0, priorMean = 0.5)
        val thick = BanditPolicy.ucbScore(50.0, 50.0, totalMass = 100.0, priorMean = 0.5)
        // Same 50% mean, but the thin arm carries a larger exploration bonus.
        assertTrue("under-sampled arm must keep more exploration bonus", thin > thick)
    }

    @Test fun `a never-tried arm outranks a heavily-failed arm (exploration + low mean)`() {
        val fresh = BanditPolicy.ucbScore(0.0, 0.0, totalMass = 40.0, priorMean = 0.5)
        val failer = BanditPolicy.ucbScore(successMass = 1.0, failureMass = 30.0, totalMass = 40.0, priorMean = 0.5)
        assertTrue(fresh > failer)
    }

    @Test fun `with the gentle default c, a strongly-proven arm exploits over a thin one (no dethroning)`() {
        // The invariant choice: a thin arm must NOT dethrone a strongly-proven one at the live pick.
        val proven = BanditPolicy.ucbScore(successMass = 30.0, failureMass = 1.0, totalMass = 60.0, priorMean = 0.5)
        val thin = BanditPolicy.ucbScore(successMass = 1.0, failureMass = 1.0, totalMass = 60.0, priorMean = 0.5)
        assertTrue("proven arm should win under gentle exploration", proven > thin)
    }

    // --- select ---
    @Test fun `select returns null on empty candidates`() {
        assertNull(BanditPolicy.select(emptyList<String>()) { 1.0 })
    }

    @Test fun `select picks the highest score and breaks ties on input order`() {
        val arms = listOf("a", "b", "c")
        val scores = mapOf("a" to 0.5, "b" to 0.9, "c" to 0.9)
        assertEquals("b", BanditPolicy.select(arms) { scores.getValue(it) }) // first of the tied maxima
    }
}
