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
        hasRuntimeConfig: Boolean = true,
        premiumBlocked: Boolean = false,
    ) = ServerDecisionCandidate(
        serverId = id,
        pingMs = ping,
        isPinned = false,
        hasRuntimeConfig = hasRuntimeConfig,
        premiumBlocked = premiumBlocked,
    )
}
