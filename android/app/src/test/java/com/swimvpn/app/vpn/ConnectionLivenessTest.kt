package com.swimvpn.app.vpn

import org.junit.Assert.assertEquals
import org.junit.Test

class ConnectionLivenessTest {
    @Test fun `not connected is inactive whatever the traffic`() {
        assertEquals(ConnectionActivity.INACTIVE, ConnectionLiveness.derive(false, recentInbound = true, recentOutbound = true))
        assertEquals(ConnectionActivity.INACTIVE, ConnectionLiveness.derive(false, recentInbound = false, recentOutbound = false))
    }

    @Test fun `recent inbound is active even alongside outbound`() {
        assertEquals(ConnectionActivity.ACTIVE, ConnectionLiveness.derive(true, recentInbound = true, recentOutbound = true))
        assertEquals(ConnectionActivity.ACTIVE, ConnectionLiveness.derive(true, recentInbound = true, recentOutbound = false))
    }

    @Test fun `sending recently but nothing back is stalled (the dead-link case)`() {
        assertEquals(ConnectionActivity.STALLED, ConnectionLiveness.derive(true, recentInbound = false, recentOutbound = true))
    }

    @Test fun `no recent traffic either way is idle, never falsely active`() {
        assertEquals(ConnectionActivity.IDLE, ConnectionLiveness.derive(true, recentInbound = false, recentOutbound = false))
    }

    @Test fun `a link that worked then died is never ACTIVE - recency only`() {
        // Cumulative bytesIn would still be > 0 here; recency must NOT read that as ACTIVE.
        assertEquals(ConnectionActivity.STALLED, ConnectionLiveness.derive(true, recentInbound = false, recentOutbound = true))
        assertEquals(ConnectionActivity.IDLE, ConnectionLiveness.derive(true, recentInbound = false, recentOutbound = false))
    }
}
