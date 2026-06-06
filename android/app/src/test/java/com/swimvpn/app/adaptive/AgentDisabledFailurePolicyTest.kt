package com.swimvpn.app.adaptive

import org.junit.Assert.assertEquals
import org.junit.Test

class AgentDisabledFailurePolicyTest {

    private fun switchAction(target: String) = DecisionAction(
        type = DecisionActionType.SWITCH_SERVER,
        targetServerId = target,
        delayMs = 3_000L,
        reason = "current_server_unstable_switching_to_best_available",
    )

    @Test
    fun agentEnabledLeavesSwitchUnchanged() {
        val planned = switchAction(target = "server-B")
        val resolved = AgentDisabledFailurePolicy.resolve(
            agentEnabled = true,
            currentServerId = "server-A",
            plannedAction = planned,
        )
        assertEquals(planned, resolved)
    }

    @Test
    fun agentDisabledDowngradesSwitchToReconnectSame() {
        val planned = switchAction(target = "server-B")
        val resolved = AgentDisabledFailurePolicy.resolve(
            agentEnabled = false,
            currentServerId = "server-A",
            plannedAction = planned,
        )
        assertEquals(DecisionActionType.RECONNECT_SAME, resolved.type)
        assertEquals("server-A", resolved.targetServerId)
        // Backoff is preserved so the disabled path keeps the same retry pacing.
        assertEquals(planned.delayMs, resolved.delayMs)
        assertEquals("agent_disabled_reconnect_same_server", resolved.reason)
    }

    @Test
    fun agentDisabledLeavesReconnectSameUnchanged() {
        val planned = DecisionAction(
            type = DecisionActionType.RECONNECT_SAME,
            targetServerId = "server-A",
            delayMs = 1_000L,
            reason = "retry_same_server_before_fallback",
        )
        val resolved = AgentDisabledFailurePolicy.resolve(
            agentEnabled = false,
            currentServerId = "server-A",
            plannedAction = planned,
        )
        assertEquals(planned, resolved)
    }

    @Test
    fun agentDisabledLeavesGiveUpUnchanged() {
        val planned = DecisionAction(
            type = DecisionActionType.GIVE_UP,
            targetServerId = null,
            delayMs = 0L,
            reason = "max_reconnect_attempts_reached_or_no_active_server",
        )
        val resolved = AgentDisabledFailurePolicy.resolve(
            agentEnabled = false,
            currentServerId = "server-A",
            plannedAction = planned,
        )
        assertEquals(planned, resolved)
    }

    @Test
    fun `agent off downgrades MORPH_PROFILE to same-server reconnect`() {
        val planned = DecisionAction(
            type = DecisionActionType.MORPH_PROFILE,
            targetServerId = "s",
            delayMs = 0L,
            reason = "x",
            targetProfileId = "chrome",
        )
        val resolved = AgentDisabledFailurePolicy.resolve(agentEnabled = false, currentServerId = "s", plannedAction = planned)
        assertEquals(DecisionActionType.RECONNECT_SAME, resolved.type)
        assertEquals(null, resolved.targetProfileId)
    }

    @Test
    fun `agent on leaves MORPH_PROFILE unchanged`() {
        val planned = DecisionAction(DecisionActionType.MORPH_PROFILE, "s", 0L, "x", "chrome")
        assertEquals(planned, AgentDisabledFailurePolicy.resolve(true, "s", planned))
    }
}
