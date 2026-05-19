package com.swimvpn.app.vpn

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class StickyReconnectPolicyTest {

    @Test
    fun `allows fresh active runtime snapshot`() {
        val snapshot = snapshot(
            status = RuntimeStatus.RUNNING,
            updatedAt = 10_000L,
        )

        assertTrue(
            StickyReconnectPolicy.shouldRestoreStickySession(
                snapshot = snapshot,
                nowMs = 20_000L,
            ),
        )
    }

    @Test
    fun `rejects stale active runtime snapshot`() {
        val snapshot = snapshot(
            status = RuntimeStatus.RUNNING,
            updatedAt = 1_000L,
        )

        assertFalse(
            StickyReconnectPolicy.shouldRestoreStickySession(
                snapshot = snapshot,
                nowMs = 130_000L,
            ),
        )
    }

    @Test
    fun `allows delayed system restart inside bounded active window`() {
        val snapshot = snapshot(
            status = RuntimeStatus.RUNNING,
            updatedAt = 10_000L,
        )

        assertTrue(
            StickyReconnectPolicy.shouldRestoreStickySession(
                snapshot = snapshot,
                nowMs = 70_000L,
            ),
        )
    }

    @Test
    fun `ui freshness remains short even when sticky restore window is longer`() {
        val snapshot = snapshot(
            status = RuntimeStatus.RUNNING,
            updatedAt = 10_000L,
        )

        assertFalse(snapshot.isFresh(now = 70_000L))
    }

    @Test
    fun `rejects active runtime snapshot beyond bounded restore window`() {
        val snapshot = snapshot(
            status = RuntimeStatus.RUNNING,
            updatedAt = 10_000L,
        )

        assertFalse(
            StickyReconnectPolicy.shouldRestoreStickySession(
                snapshot = snapshot,
                nowMs = 190_000L,
            ),
        )
    }

    @Test
    fun `rejects stopped or failed runtime snapshots`() {
        listOf(
            RuntimeStatus.IDLE,
            RuntimeStatus.STOPPING,
            RuntimeStatus.FAILED,
            RuntimeStatus.STOPPED_BY_USER,
        ).forEach { status ->
            assertFalse(
                "status=$status should not restore",
                StickyReconnectPolicy.shouldRestoreStickySession(
                    snapshot = snapshot(
                        status = status,
                        updatedAt = 20_000L,
                    ),
                    nowMs = 20_000L,
                ),
            )
        }
    }

    private fun snapshot(
        status: RuntimeStatus,
        updatedAt: Long,
    ) = RuntimeStateSnapshot(
        status = status,
        mode = RuntimeMode.FULL_TUNNEL,
        updatedAt = updatedAt,
        error = null,
    )
}
