package com.swimvpn.app.vpn

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class RuntimeStartupHealthPolicyTest {

    @Test
    fun `local proxy can run when xray is alive`() {
        assertTrue(
            RuntimeStartupHealthPolicy.canMarkRunning(
                xrayAlive = true,
                requireTun2Socks = false,
                tun2SocksAlive = false,
            ),
        )
    }

    @Test
    fun `runtime cannot run when xray is dead`() {
        assertFalse(
            RuntimeStartupHealthPolicy.canMarkRunning(
                xrayAlive = false,
                requireTun2Socks = false,
                tun2SocksAlive = true,
            ),
        )
    }

    @Test
    fun `full tunnel requires tun2socks alive`() {
        assertFalse(
            RuntimeStartupHealthPolicy.canMarkRunning(
                xrayAlive = true,
                requireTun2Socks = true,
                tun2SocksAlive = false,
            ),
        )

        assertTrue(
            RuntimeStartupHealthPolicy.canMarkRunning(
                xrayAlive = true,
                requireTun2Socks = true,
                tun2SocksAlive = true,
            ),
        )
    }
}
