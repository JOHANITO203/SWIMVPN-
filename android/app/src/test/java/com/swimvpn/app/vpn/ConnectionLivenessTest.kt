package com.swimvpn.app.vpn

import org.junit.Assert.assertEquals
import org.junit.Test

class ConnectionLivenessTest {
    @Test fun `not connected is inactive regardless of bytes`() {
        assertEquals(ConnectionActivity.INACTIVE, ConnectionLiveness.derive(connected = false, bytesIn = 0L))
        assertEquals(ConnectionActivity.INACTIVE, ConnectionLiveness.derive(connected = false, bytesIn = 5_000L))
    }

    @Test fun `connected with no inbound is awaiting, never active`() {
        assertEquals(ConnectionActivity.AWAITING_TRAFFIC, ConnectionLiveness.derive(connected = true, bytesIn = 0L))
    }

    @Test fun `connected with inbound bytes is active`() {
        assertEquals(ConnectionActivity.ACTIVE, ConnectionLiveness.derive(connected = true, bytesIn = 1L))
        assertEquals(ConnectionActivity.ACTIVE, ConnectionLiveness.derive(connected = true, bytesIn = 9_999_999L))
    }

    @Test fun `active is claimed only when inbound is strictly positive`() {
        // The honesty lock: zero inbound must never read as ACTIVE, only AWAITING_TRAFFIC.
        assertEquals(ConnectionActivity.AWAITING_TRAFFIC, ConnectionLiveness.derive(connected = true, bytesIn = 0L))
    }
}
