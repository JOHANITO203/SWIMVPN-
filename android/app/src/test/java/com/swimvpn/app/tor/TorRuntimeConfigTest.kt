package com.swimvpn.app.tor

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class TorRuntimeConfigTest {

    @Test
    fun `torrc pins the SOCKS port and exits via REALITY`() {
        val torrc = TorRuntimeConfig.torrc()

        assertTrue("app traffic enters Tor on the pinned SOCKS port",
            torrc.contains("SOCKSPort 127.0.0.1:${TorRuntimeConfig.SOCKS_PORT}"))
        // The load-bearing line: Tor's own guard connections must exit through the xray egress inbound
        // (→ REALITY). Without this, Tor would dial guards directly and be blocked/visible.
        assertTrue("Tor must exit through the REALITY egress proxy",
            torrc.contains("Socks5Proxy 127.0.0.1:${TorRuntimeConfig.EGRESS_SOCKS_PORT}"))
        assertTrue(torrc.contains("ClientOnly 1"))
    }

    @Test
    fun `torrc does not set TorService-owned control or data directory`() {
        val torrc = TorRuntimeConfig.torrc()
        // guardianproject TorService owns --ControlSocket / --DataDirectory; setting them here would
        // collide with the service. Guard against reintroducing them.
        assertFalse(torrc.contains("ControlPort"))
        assertFalse(torrc.contains("ControlSocket"))
        assertFalse(torrc.contains("DataDirectory"))
    }

    @Test
    fun `torrc configures no bridges because REALITY is the transport`() {
        val torrc = TorRuntimeConfig.torrc()
        // REALITY carries Tor's transport, so bridges/obfs4/snowflake are intentionally absent —
        // asserting this prevents a regression that would reintroduce a blockable direct guard path.
        assertFalse(torrc.contains("Bridge"))
        assertFalse(torrc.contains("UseBridges"))
        assertFalse(torrc.contains("ClientTransportPlugin"))
    }

    @Test
    fun `torrc is deterministic`() {
        assertEquals(TorRuntimeConfig.torrc(), TorRuntimeConfig.torrc())
    }
}
