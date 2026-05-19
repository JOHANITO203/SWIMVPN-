package com.swimvpn.app.vpn

import kotlinx.coroutines.CancellationException
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class RuntimeStartupFailurePolicyTest {

    @Test
    fun `cancellation is not reported as startup failure`() {
        val decision = RuntimeStartupFailurePolicy.classify(CancellationException("service destroyed"))

        assertFalse(decision.shouldReportFailure)
        assertEquals(DisconnectCause.UNKNOWN, decision.cause)
    }

    @Test
    fun `invalid config maps to config invalid`() {
        val decision = RuntimeStartupFailurePolicy.classify(
            IllegalStateException("Invalid runtime config: bad uri"),
        )

        assertTrue(decision.shouldReportFailure)
        assertEquals(DisconnectCause.CONFIG_INVALID, decision.cause)
    }

    @Test
    fun `early engine exits map to engine crash`() {
        listOf(
            "Xray runtime stopped before startup proof completed",
            "tun2socks data plane stopped before startup proof completed",
            "runtime failed before verification",
        ).forEach { message ->
            val decision = RuntimeStartupFailurePolicy.classify(IllegalStateException(message))

            assertTrue("message=$message", decision.shouldReportFailure)
            assertEquals("message=$message", DisconnectCause.ENGINE_CRASH, decision.cause)
        }
    }
}
