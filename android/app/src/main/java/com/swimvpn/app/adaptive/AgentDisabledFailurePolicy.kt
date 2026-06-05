package com.swimvpn.app.adaptive

/**
 * Pure policy applied when the AI agent toggle is OFF. The user's manual server selection must be
 * respected, so the decision agent is never allowed to switch servers automatically: any
 * SWITCH_SERVER or MORPH_PROFILE plan is downgraded to a same-server reconnect (keeping the agent's backoff and
 * give-up bounds intact). When the agent is ON the planned action is returned unchanged, so default
 * runtime behavior is preserved.
 */
object AgentDisabledFailurePolicy {

    fun resolve(
        agentEnabled: Boolean,
        currentServerId: String,
        plannedAction: DecisionAction,
    ): DecisionAction {
        val isAutoAction = plannedAction.type == DecisionActionType.SWITCH_SERVER ||
            plannedAction.type == DecisionActionType.MORPH_PROFILE
        if (agentEnabled || !isAutoAction) {
            return plannedAction
        }
        return plannedAction.copy(
            type = DecisionActionType.RECONNECT_SAME,
            targetServerId = currentServerId,
            targetProfileId = null,
            reason = "agent_disabled_reconnect_same_server",
        )
    }
}
