package com.swimvpn.app.vpn

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class VpnManagerDiagnosticsTest {

    @Test
    fun `set diagnostics with only cause does not erase log paths`() {
        VpnManager.setRuntimeDiagnostics(
            activeMode = RuntimeMode.FULL_TUNNEL.name,
            xraySessionId = "xray-session",
            xrayLogPath = "/runtime/xray.log",
            tun2SocksSessionId = "tun-session",
            tun2SocksLogPath = "/runtime/tun2socks.log",
            reconnectCount = 2,
            sessionStartedAt = 1234L,
        )

        VpnManager.setRuntimeDiagnostics(lastDisconnectCause = DisconnectCause.ENGINE_CRASH)

        val metrics = VpnManager.metrics.value
        assertEquals("/runtime/xray.log", metrics.xrayLogPath)
        assertEquals("/runtime/tun2socks.log", metrics.tun2SocksLogPath)
        assertEquals(DisconnectCause.ENGINE_CRASH, metrics.lastDisconnectCause)
        assertEquals(2, metrics.reconnectCount)
        assertEquals(1234L, metrics.sessionStartedAt)
    }

    @Test
    fun `clear runtime diagnostics keeps last failure evidence`() {
        VpnManager.setRuntimeDiagnostics(
            activeMode = RuntimeMode.FULL_TUNNEL.name,
            xraySessionId = "xray-session",
            xrayLogPath = "/runtime/xray.log",
            tun2SocksSessionId = "tun-session",
            tun2SocksLogPath = "/runtime/tun2socks.log",
            lastDisconnectCause = DisconnectCause.SERVICE_KILLED,
            reconnectCount = 4,
            sessionStartedAt = 5678L,
        )

        VpnManager.clearRuntimeDiagnostics()

        val metrics = VpnManager.metrics.value
        assertNull(metrics.activeMode)
        assertNull(metrics.xraySessionId)
        assertNull(metrics.tun2SocksSessionId)
        assertEquals("/runtime/xray.log", metrics.xrayLogPath)
        assertEquals("/runtime/tun2socks.log", metrics.tun2SocksLogPath)
        assertEquals(DisconnectCause.SERVICE_KILLED, metrics.lastDisconnectCause)
        assertEquals(4, metrics.reconnectCount)
        assertEquals(5678L, metrics.sessionStartedAt)
    }

    @Test
    fun `reconcile runtime snapshot restores persisted diagnostics`() {
        VpnManager.reconcileRuntimeSnapshot(
            RuntimeStateSnapshot(
                status = RuntimeStatus.FAILED,
                mode = RuntimeMode.FULL_TUNNEL,
                updatedAt = System.currentTimeMillis(),
                error = "xray crashed",
                lastDisconnectCause = DisconnectCause.ENGINE_CRASH,
                reconnectCount = 3,
                sessionStartedAt = 2468L,
                xrayLogPath = "/runtime/xray-after-restart.log",
                tun2SocksLogPath = "/runtime/tun-after-restart.log",
            ),
        )

        val metrics = VpnManager.metrics.value
        assertEquals("xray crashed", metrics.lastError)
        assertEquals(DisconnectCause.ENGINE_CRASH, metrics.lastDisconnectCause)
        assertEquals(3, metrics.reconnectCount)
        assertEquals(2468L, metrics.sessionStartedAt)
        assertEquals("/runtime/xray-after-restart.log", metrics.xrayLogPath)
        assertEquals("/runtime/tun-after-restart.log", metrics.tun2SocksLogPath)
    }

    @Test
    fun `healthy runtime session clears previous failure cause and reconnect count`() {
        VpnManager.setRuntimeDiagnostics(
            xrayLogPath = "/runtime/current-xray.log",
            tun2SocksLogPath = "/runtime/current-tun.log",
            lastDisconnectCause = DisconnectCause.ENGINE_CRASH,
            reconnectCount = 3,
            sessionStartedAt = 1111L,
        )

        VpnManager.markHealthyRuntimeSession(
            reconnectCount = 0,
            sessionStartedAt = 2222L,
        )

        val metrics = VpnManager.metrics.value
        assertNull(metrics.lastDisconnectCause)
        assertEquals(0, metrics.reconnectCount)
        assertEquals(2222L, metrics.sessionStartedAt)
        assertEquals("/runtime/current-xray.log", metrics.xrayLogPath)
        assertEquals("/runtime/current-tun.log", metrics.tun2SocksLogPath)
    }
}
