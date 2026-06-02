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

    @Test
    fun `runtime cannot run when traffic is not confirmed`() {
        assertFalse(
            RuntimeStartupHealthPolicy.canMarkRunning(
                xrayAlive = true,
                requireTun2Socks = false,
                tun2SocksAlive = false,
                trafficConfirmed = false,
            ),
        )
    }

    @Test
    fun `runtime can run when alive and traffic confirmed`() {
        assertTrue(
            RuntimeStartupHealthPolicy.canMarkRunning(
                xrayAlive = true,
                requireTun2Socks = true,
                tun2SocksAlive = true,
                trafficConfirmed = true,
            ),
        )
    }

    @Test
    fun `traffic is stalled when outbound flows but inbound is zero after threshold`() {
        assertTrue(
            RuntimeStartupHealthPolicy.isTrafficStalled(
                bytesIn = 0L,
                bytesOut = 1_024L,
                elapsedMs = 5_000L,
                thresholdMs = 5_000L,
            ),
        )
    }

    @Test
    fun `idle traffic is not stalled`() {
        assertFalse(
            RuntimeStartupHealthPolicy.isTrafficStalled(
                bytesIn = 0L,
                bytesOut = 0L,
                elapsedMs = 10_000L,
                thresholdMs = 5_000L,
            ),
        )
    }

    @Test
    fun `healthy inbound traffic is not stalled`() {
        assertFalse(
            RuntimeStartupHealthPolicy.isTrafficStalled(
                bytesIn = 2_048L,
                bytesOut = 1_024L,
                elapsedMs = 10_000L,
                thresholdMs = 5_000L,
            ),
        )
    }

    @Test
    fun `traffic is not stalled before threshold elapses`() {
        assertFalse(
            RuntimeStartupHealthPolicy.isTrafficStalled(
                bytesIn = 0L,
                bytesOut = 1_024L,
                elapsedMs = 4_999L,
                thresholdMs = 5_000L,
            ),
        )
    }
}
