package com.swimvpn.app.vpn

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class TunnelFallbackPolicyTest {

    @Test
    fun `falls back to proxy on tunnel infra failure`() {
        listOf(DisconnectCause.ENGINE_CRASH, DisconnectCause.UNKNOWN).forEach { cause ->
            assertTrue(
                "cause=$cause should fall back",
                TunnelFallbackPolicy.shouldFallbackToProxy(
                    requestedMode = RuntimeMode.FULL_TUNNEL,
                    cause = cause,
                    stoppedByUser = false,
                    alreadyFellBack = false,
                ),
            )
        }
    }

    @Test
    fun `does not fall back for network server config or user causes`() {
        listOf(
            DisconnectCause.NETWORK_LOST,
            DisconnectCause.NETWORK_NOT_VALIDATED,
            DisconnectCause.SERVER_UNREACHABLE,
            DisconnectCause.NO_TRAFFIC,
            DisconnectCause.CONFIG_INVALID,
            DisconnectCause.PERMISSION_REVOKED,
            DisconnectCause.USER_STOPPED,
        ).forEach { cause ->
            assertFalse(
                "cause=$cause must not fall back",
                TunnelFallbackPolicy.shouldFallbackToProxy(
                    requestedMode = RuntimeMode.FULL_TUNNEL,
                    cause = cause,
                    stoppedByUser = false,
                    alreadyFellBack = false,
                ),
            )
        }
    }

    @Test
    fun `does not fall back when already in proxy mode`() {
        assertFalse(
            TunnelFallbackPolicy.shouldFallbackToProxy(
                requestedMode = RuntimeMode.LOCAL_PROXY,
                cause = DisconnectCause.ENGINE_CRASH,
                stoppedByUser = false,
                alreadyFellBack = false,
            ),
        )
    }

    @Test
    fun `does not fall back when user stopped or already fell back`() {
        assertFalse(
            TunnelFallbackPolicy.shouldFallbackToProxy(
                requestedMode = RuntimeMode.FULL_TUNNEL,
                cause = DisconnectCause.ENGINE_CRASH,
                stoppedByUser = true,
                alreadyFellBack = false,
            ),
        )
        assertFalse(
            TunnelFallbackPolicy.shouldFallbackToProxy(
                requestedMode = RuntimeMode.FULL_TUNNEL,
                cause = DisconnectCause.ENGINE_CRASH,
                stoppedByUser = false,
                alreadyFellBack = true,
            ),
        )
    }
}
