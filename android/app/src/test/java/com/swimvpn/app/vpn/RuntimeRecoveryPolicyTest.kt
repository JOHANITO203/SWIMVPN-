package com.swimvpn.app.vpn

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class RuntimeRecoveryPolicyTest {

    @Test
    fun `active running snapshot with payload restores even when auto connect is disabled`() {
        val snapshot = snapshot(
            status = RuntimeStatus.RUNNING,
            mode = RuntimeMode.FULL_TUNNEL,
            updatedAt = 10_000L,
        )

        assertTrue(
            RuntimeRecoveryPolicy.shouldRecoverKilledSession(
                snapshot = snapshot,
                payloadAvailable = true,
                vpnPermissionAvailable = true,
                nowMs = 20_000L,
            ),
        )
    }

    @Test
    fun `all fresh active runtime statuses can restore`() {
        listOf(
            RuntimeStatus.STARTING,
            RuntimeStatus.RUNNING,
            RuntimeStatus.RECONNECTING,
            RuntimeStatus.DEGRADED,
        ).forEach { status ->
            assertTrue(
                "status=$status should be recoverable",
                RuntimeRecoveryPolicy.shouldRecoverKilledSession(
                    snapshot = snapshot(
                        status = status,
                        mode = RuntimeMode.FULL_TUNNEL,
                        updatedAt = 10_000L,
                    ),
                    payloadAvailable = true,
                    vpnPermissionAvailable = true,
                    nowMs = 20_000L,
                ),
            )
        }
    }

    @Test
    fun `idle snapshot never restores`() {
        val snapshot = snapshot(
            status = RuntimeStatus.IDLE,
            mode = RuntimeMode.FULL_TUNNEL,
            updatedAt = 20_000L,
        )

        assertFalse(
            RuntimeRecoveryPolicy.shouldRecoverKilledSession(
                snapshot = snapshot,
                payloadAvailable = true,
                vpnPermissionAvailable = true,
                nowMs = 20_000L,
            ),
        )
    }

    @Test
    fun `stale running snapshot does not restore`() {
        val snapshot = snapshot(
            status = RuntimeStatus.RUNNING,
            mode = RuntimeMode.FULL_TUNNEL,
            updatedAt = 1_000L,
        )

        assertFalse(
            RuntimeRecoveryPolicy.shouldRecoverKilledSession(
                snapshot = snapshot,
                payloadAvailable = true,
                vpnPermissionAvailable = true,
                nowMs = 130_000L,
            ),
        )
    }

    @Test
    fun `full tunnel without vpn permission does not restore`() {
        val snapshot = snapshot(
            status = RuntimeStatus.RUNNING,
            mode = RuntimeMode.FULL_TUNNEL,
            updatedAt = 10_000L,
        )

        assertFalse(
            RuntimeRecoveryPolicy.shouldRecoverKilledSession(
                snapshot = snapshot,
                payloadAvailable = true,
                vpnPermissionAvailable = false,
                nowMs = 20_000L,
            ),
        )
    }

    @Test
    fun `missing payload does not restore`() {
        val snapshot = snapshot(
            status = RuntimeStatus.RUNNING,
            mode = RuntimeMode.FULL_TUNNEL,
            updatedAt = 10_000L,
        )

        assertFalse(
            RuntimeRecoveryPolicy.shouldRecoverKilledSession(
                snapshot = snapshot,
                payloadAvailable = false,
                vpnPermissionAvailable = true,
                nowMs = 20_000L,
            ),
        )
    }

    @Test
    fun `active local proxy restores without vpn permission`() {
        val snapshot = snapshot(
            status = RuntimeStatus.DEGRADED,
            mode = RuntimeMode.LOCAL_PROXY,
            updatedAt = 10_000L,
        )

        assertTrue(
            RuntimeRecoveryPolicy.shouldRecoverKilledSession(
                snapshot = snapshot,
                payloadAvailable = true,
                vpnPermissionAvailable = false,
                nowMs = 20_000L,
            ),
        )
    }

    private fun snapshot(
        status: RuntimeStatus,
        mode: RuntimeMode,
        updatedAt: Long,
    ) = RuntimeStateSnapshot(
        status = status,
        mode = mode,
        updatedAt = updatedAt,
        error = null,
    )
}
