package com.swimvpn.app.vpn

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class RuntimeReconnectPolicyTest {

    @Test
    fun `network lost reconnect can be cancelled before it starts`() {
        assertTrue(
            RuntimeReconnectPolicy.shouldCancelPendingReconnectForRecoveredNetwork(
                cause = DisconnectCause.NETWORK_LOST,
                started = false,
            ),
        )
    }

    @Test
    fun `started reconnect is not cancelled by late network recovery`() {
        assertFalse(
            RuntimeReconnectPolicy.shouldCancelPendingReconnectForRecoveredNetwork(
                cause = DisconnectCause.NETWORK_LOST,
                started = true,
            ),
        )
    }

    @Test
    fun `engine reconnect is not cancelled by network recovery`() {
        assertFalse(
            RuntimeReconnectPolicy.shouldCancelPendingReconnectForRecoveredNetwork(
                cause = DisconnectCause.ENGINE_CRASH,
                started = false,
            ),
        )
    }
}
