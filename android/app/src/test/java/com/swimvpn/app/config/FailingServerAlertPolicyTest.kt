package com.swimvpn.app.config

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class FailingServerAlertPolicyTest {

    @Test
    fun `alerts for imported server at threshold when not yet alerted`() {
        assertTrue(
            FailingServerAlertPolicy.shouldAlert(
                source = "imported",
                consecutiveFailures = 3,
                alreadyAlertedForServer = false,
            ),
        )
    }

    @Test
    fun `alerts above threshold`() {
        assertTrue(
            FailingServerAlertPolicy.shouldAlert("imported", 7, false),
        )
    }

    @Test
    fun `does not alert below threshold`() {
        assertFalse(FailingServerAlertPolicy.shouldAlert("imported", 2, false))
    }

    @Test
    fun `does not alert for non-imported sources`() {
        assertFalse(FailingServerAlertPolicy.shouldAlert("backend", 9, false))
        assertFalse(FailingServerAlertPolicy.shouldAlert(null, 9, false))
    }

    @Test
    fun `does not alert twice for the same server`() {
        assertFalse(
            FailingServerAlertPolicy.shouldAlert("imported", 5, alreadyAlertedForServer = true),
        )
    }
}
