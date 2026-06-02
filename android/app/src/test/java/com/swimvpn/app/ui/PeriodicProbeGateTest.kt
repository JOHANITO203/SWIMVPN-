package com.swimvpn.app.ui

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PeriodicProbeGateTest {

    @Test
    fun probesWhenInteractiveAndNotPowerSaving() {
        assertTrue(PeriodicProbeGate.shouldProbe(isInteractive = true, powerSaveMode = false))
    }

    @Test
    fun skipsWhenScreenNotInteractive() {
        assertFalse(PeriodicProbeGate.shouldProbe(isInteractive = false, powerSaveMode = false))
    }

    @Test
    fun skipsWhenPowerSaveModeActive() {
        assertFalse(PeriodicProbeGate.shouldProbe(isInteractive = true, powerSaveMode = true))
    }

    @Test
    fun skipsWhenNotInteractiveAndPowerSaving() {
        assertFalse(PeriodicProbeGate.shouldProbe(isInteractive = false, powerSaveMode = true))
    }
}
