package com.swimvpn.app.vpn

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class RuntimeServiceDestroyPolicyTest {

    @Test
    fun `system destroy during active states persists recoverable reconnecting state`() {
        listOf(
            RuntimeStatus.STARTING,
            RuntimeStatus.RUNNING,
            RuntimeStatus.RECONNECTING,
            RuntimeStatus.DEGRADED,
        ).forEach { status ->
            val decision = RuntimeServiceDestroyPolicy.recoveryDecision(
                currentStatus = status,
                stoppedByUser = false,
            )

            assertEquals("status=$status", RuntimeStatus.RECONNECTING, decision?.status)
            assertEquals("status=$status", DisconnectCause.SERVICE_KILLED, decision?.cause)
        }
    }

    @Test
    fun `user destroy does not persist recoverable state`() {
        val decision = RuntimeServiceDestroyPolicy.recoveryDecision(
            currentStatus = RuntimeStatus.RUNNING,
            stoppedByUser = true,
        )

        assertNull(decision)
    }

    @Test
    fun `terminal states do not persist recoverable state`() {
        listOf(
            RuntimeStatus.IDLE,
            RuntimeStatus.STOPPING,
            RuntimeStatus.FAILED,
            RuntimeStatus.STOPPED_BY_USER,
        ).forEach { status ->
            val decision = RuntimeServiceDestroyPolicy.recoveryDecision(
                currentStatus = status,
                stoppedByUser = false,
            )

            assertNull("status=$status should not recover", decision)
        }
    }
}
