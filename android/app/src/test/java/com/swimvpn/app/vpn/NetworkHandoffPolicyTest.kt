package com.swimvpn.app.vpn

import org.junit.Assert.assertEquals
import org.junit.Test

class NetworkHandoffPolicyTest {

    @Test
    fun `on lost schedules debounced reconnect`() {
        val decision = NetworkHandoffPolicy.onLost(
            isActiveUnderlyingNetwork = true,
            stoppedByUser = false,
            currentStatus = RuntimeStatus.RUNNING,
        )

        assertEquals(NetworkHandoffAction.DEBOUNCE_RECONNECT, decision.action)
        assertEquals(NetworkHandoffPolicy.NETWORK_HANDOFF_GRACE_MS, decision.delayMs)
    }

    @Test
    fun `on available inside grace window cancels reconnect`() {
        val decision = NetworkHandoffPolicy.onAvailable(
            hasPendingHandoffReconnect = true,
        )

        assertEquals(NetworkHandoffAction.CANCEL_DEBOUNCE, decision.action)
        assertEquals(0L, decision.delayMs)
    }

    @Test
    fun `no available after grace window allows reconnect`() {
        val decision = NetworkHandoffPolicy.onGraceExpired(
            hasUsableUnderlyingNetwork = false,
            stoppedByUser = false,
        )

        assertEquals(NetworkHandoffAction.RECONNECT_NOW, decision.action)
        assertEquals(0L, decision.delayMs)
    }

    @Test
    fun `user stopped handoff is ignored`() {
        val decision = NetworkHandoffPolicy.onLost(
            isActiveUnderlyingNetwork = true,
            stoppedByUser = true,
            currentStatus = RuntimeStatus.RUNNING,
        )

        assertEquals(NetworkHandoffAction.IGNORE, decision.action)
    }
}
