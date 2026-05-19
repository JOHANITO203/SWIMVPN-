package com.swimvpn.app.adaptive

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class AdaptiveDecisionAgentTest {

    @Test
    fun `first failure retries same server with short backoff`() {
        val action = AdaptiveDecisionAgent.planAfterFailure(
            currentServerId = "server-a",
            candidates = listOf(candidate("server-a"), candidate("server-b", ping = 90)),
            scores = emptyMap(),
            reconnectAttempt = 0,
            nowMs = 1_000L,
        )

        assertEquals(DecisionActionType.RECONNECT_SAME, action.type)
        assertEquals("server-a", action.targetServerId)
        assertEquals(1_000L, action.delayMs)
    }

    @Test
    fun `repeated failure switches away from avoided server`() {
        val failedScore = AdaptiveDecisionAgent.recordFailure(
            score = ServerQualityScore(serverId = "server-a", consecutiveFailures = 1),
            nowMs = 10_000L,
        )

        assertTrue(failedScore.avoidUntilMs > 10_000L)

        val action = AdaptiveDecisionAgent.planAfterFailure(
            currentServerId = "server-a",
            candidates = listOf(
                candidate("server-a", ping = 30),
                candidate("server-b", ping = 80),
            ),
            scores = mapOf("server-a" to failedScore),
            reconnectAttempt = 2,
            nowMs = 10_000L,
        )

        assertEquals(DecisionActionType.SWITCH_SERVER, action.type)
        assertEquals("server-b", action.targetServerId)
    }

    @Test
    fun `premium blocked and configless candidates are ignored`() {
        val selected = AdaptiveDecisionAgent.selectBestServer(
            candidates = listOf(
                candidate("premium", ping = 10, premiumBlocked = true),
                candidate("empty", ping = 20, hasRuntimeConfig = false),
                candidate("imported", ping = 120),
            ),
            scores = emptyMap(),
            currentServerId = null,
            nowMs = 1_000L,
        )

        assertEquals("imported", selected?.serverId)
    }

    @Test
    fun `fresh probe failure loses to healthy candidate`() {
        val now = 60_000L

        val selected = AdaptiveDecisionAgent.selectBestServer(
            candidates = listOf(
                candidate(
                    id = "failed-pinned-fast",
                    ping = 15,
                    isPinned = true,
                    latencyMeasuredAtMs = now - 1_000L,
                    latencyProbeFailed = true,
                ),
                candidate(
                    id = "healthy",
                    ping = 85,
                    latencyMeasuredAtMs = now - 1_000L,
                ),
            ),
            scores = emptyMap(),
            currentServerId = null,
            nowMs = now,
        )

        assertEquals("healthy", selected?.serverId)
    }

    @Test
    fun `fresh probe failure is not selected when it is the only candidate`() {
        val now = 90_000L

        val selected = AdaptiveDecisionAgent.selectBestServer(
            candidates = listOf(
                candidate(
                    id = "failed-only",
                    ping = 15,
                    latencyMeasuredAtMs = now - 1_000L,
                    latencyProbeFailed = true,
                ),
            ),
            scores = emptyMap(),
            currentServerId = null,
            nowMs = now,
        )

        assertNull(selected)
    }

    @Test
    fun `recommend server exposes fresh quality state for validated candidate`() {
        val now = 100_000L

        val result = AdaptiveDecisionAgent.recommendServer(
            candidates = listOf(
                candidate(
                    id = "fresh",
                    ping = 45,
                    latencyMeasuredAtMs = now - 1_000L,
                ),
            ),
            scores = emptyMap(),
            currentServerId = null,
            nowMs = now,
        )

        assertEquals("fresh", result?.candidate?.serverId)
        assertEquals(ServerRuntimeQualityState.FRESH, result?.qualityState)
    }

    @Test
    fun `fresh low ping is preferred over stale faster ping`() {
        val now = 120_000L

        val selected = AdaptiveDecisionAgent.selectBestServer(
            candidates = listOf(
                candidate(
                    id = "stale-fast",
                    ping = 10,
                    latencyMeasuredAtMs = now - 20 * 60 * 1_000L,
                ),
                candidate(
                    id = "fresh-low",
                    ping = 45,
                    latencyMeasuredAtMs = now - 5_000L,
                ),
                candidate(
                    id = "missing",
                    ping = 0,
                    latencyMeasuredAtMs = 0L,
                ),
            ),
            scores = emptyMap(),
            currentServerId = null,
            nowMs = now,
        )

        assertEquals("fresh-low", selected?.serverId)
    }

    @Test
    fun `pin breaks healthy tie`() {
        val now = 180_000L

        val selected = AdaptiveDecisionAgent.selectBestServer(
            candidates = listOf(
                candidate(
                    id = "unpinned",
                    ping = 60,
                    latencyMeasuredAtMs = now - 2_000L,
                ),
                candidate(
                    id = "pinned",
                    ping = 60,
                    isPinned = true,
                    latencyMeasuredAtMs = now - 2_000L,
                ),
            ),
            scores = emptyMap(),
            currentServerId = null,
            nowMs = now,
        )

        assertEquals("pinned", selected?.serverId)
    }

    @Test
    fun `success history beats recent failure history`() {
        val now = 240_000L

        val selected = AdaptiveDecisionAgent.selectBestServer(
            candidates = listOf(
                candidate(
                    id = "recent-failures",
                    ping = 40,
                    latencyMeasuredAtMs = now - 2_000L,
                ),
                candidate(
                    id = "proven",
                    ping = 55,
                    latencyMeasuredAtMs = now - 2_000L,
                ),
            ),
            scores = mapOf(
                "recent-failures" to ServerQualityScore(
                    serverId = "recent-failures",
                    failureCount = 3,
                    consecutiveFailures = 1,
                    lastFailureAtMs = now - 5_000L,
                ),
                "proven" to ServerQualityScore(
                    serverId = "proven",
                    successCount = 4,
                    lastSuccessAtMs = now - 4_000L,
                ),
            ),
            currentServerId = null,
            nowMs = now,
        )

        assertEquals("proven", selected?.serverId)
    }

    @Test
    fun `old failure history decays so recovered low latency server can be selected`() {
        val selected = AdaptiveDecisionAgent.selectBestServer(
            candidates = listOf(
                candidate("recovered", ping = 30),
                candidate("slow", ping = 220),
            ),
            scores = mapOf(
                "recovered" to ServerQualityScore(
                    serverId = "recovered",
                    failureCount = 6,
                    consecutiveFailures = 3,
                    lastFailureAtMs = 10_000L,
                    avoidUntilMs = 610_000L,
                ),
            ),
            currentServerId = null,
            nowMs = 3_700_000L,
        )

        assertEquals("recovered", selected?.serverId)
    }

    @Test
    fun `max reconnect attempts stops adaptive loop`() {
        val action = AdaptiveDecisionAgent.planAfterFailure(
            currentServerId = "server-a",
            candidates = listOf(candidate("server-a"), candidate("server-b")),
            scores = emptyMap(),
            reconnectAttempt = AdaptiveDecisionAgent.MAX_RECONNECT_ATTEMPTS,
            nowMs = 1_000L,
        )

        assertEquals(DecisionActionType.GIVE_UP, action.type)
        assertNull(action.targetServerId)
    }

    @Test
    fun `backoff grows through production retry sequence`() {
        val delays = (0 until AdaptiveDecisionAgent.MAX_RECONNECT_ATTEMPTS).map { attempt ->
            AdaptiveDecisionAgent.planAfterFailure(
                currentServerId = "server-a",
                candidates = listOf(candidate("server-a"), candidate("server-b")),
                scores = emptyMap(),
                reconnectAttempt = attempt,
                nowMs = 1_000L,
            ).delayMs
        }

        assertEquals(listOf(1_000L, 3_000L, 5_000L, 10_000L, 30_000L), delays)
    }

    private fun candidate(
        id: String,
        ping: Int = 50,
        isPinned: Boolean = false,
        hasRuntimeConfig: Boolean = true,
        premiumBlocked: Boolean = false,
        latencyMeasuredAtMs: Long = 0L,
        latencyProbeFailed: Boolean = false,
        load: Int? = null,
    ) = ServerDecisionCandidate(
        serverId = id,
        pingMs = ping,
        isPinned = isPinned,
        hasRuntimeConfig = hasRuntimeConfig,
        premiumBlocked = premiumBlocked,
        latencyMeasuredAtMs = latencyMeasuredAtMs,
        latencyProbeFailed = latencyProbeFailed,
        load = load,
    )
}
