package com.swimvpn.app.vpn

import org.junit.Assert.assertFalse
import org.junit.Test

class TunnelFallbackPolicyTest {

    @Test
    fun `never falls back to proxy — LOCAL_PROXY does not route (B1B2)`() {
        // Even tunnel-infra failures must NOT degrade to LOCAL_PROXY: it routes nothing, so the
        // old fallback produced a false-connected leak. A data-plane failure now surfaces as FAILED.
        listOf(DisconnectCause.ENGINE_CRASH, DisconnectCause.UNKNOWN).forEach { cause ->
            assertFalse(
                "cause=$cause must NOT fall back (proxy fallback removed)",
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
