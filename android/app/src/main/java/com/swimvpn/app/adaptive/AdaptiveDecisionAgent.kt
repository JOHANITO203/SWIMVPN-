package com.swimvpn.app.adaptive

import kotlin.math.max

 data class ServerDecisionCandidate(
    val serverId: String,
    val pingMs: Int,
    val isPinned: Boolean,
    val hasRuntimeConfig: Boolean,
    val premiumBlocked: Boolean,
)

data class ServerQualityScore(
    val serverId: String,
    val successCount: Int = 0,
    val failureCount: Int = 0,
    val consecutiveFailures: Int = 0,
    val lastSuccessAtMs: Long = 0L,
    val lastFailureAtMs: Long = 0L,
    val avoidUntilMs: Long = 0L,
) {
    fun isAvoided(nowMs: Long): Boolean = avoidUntilMs > nowMs
}

enum class DecisionActionType {
    RECONNECT_SAME,
    SWITCH_SERVER,
    GIVE_UP,
}

data class DecisionAction(
    val type: DecisionActionType,
    val targetServerId: String?,
    val delayMs: Long,
    val reason: String,
)

object AdaptiveDecisionAgent {
    const val MAX_RECONNECT_ATTEMPTS = 5
    private const val SAME_SERVER_RETRY_LIMIT = 2
    private const val AVOID_AFTER_CONSECUTIVE_FAILURES = 2
    private const val AVOID_DURATION_MS = 10 * 60 * 1000L
    private val BACKOFF_MS = longArrayOf(1_000L, 3_000L, 5_000L, 10_000L, 30_000L)

    fun recordFailure(
        score: ServerQualityScore,
        nowMs: Long,
    ): ServerQualityScore {
        val nextConsecutiveFailures = score.consecutiveFailures + 1
        val avoidUntil = if (nextConsecutiveFailures >= AVOID_AFTER_CONSECUTIVE_FAILURES) {
            nowMs + AVOID_DURATION_MS
        } else {
            score.avoidUntilMs
        }

        return score.copy(
            failureCount = score.failureCount + 1,
            consecutiveFailures = nextConsecutiveFailures,
            lastFailureAtMs = nowMs,
            avoidUntilMs = avoidUntil,
        )
    }

    fun recordSuccess(
        score: ServerQualityScore,
        nowMs: Long,
    ): ServerQualityScore = score.copy(
        successCount = score.successCount + 1,
        consecutiveFailures = 0,
        lastSuccessAtMs = nowMs,
        avoidUntilMs = 0L,
    )

    fun planAfterFailure(
        currentServerId: String?,
        candidates: List<ServerDecisionCandidate>,
        scores: Map<String, ServerQualityScore>,
        reconnectAttempt: Int,
        nowMs: Long,
    ): DecisionAction {
        if (currentServerId == null || reconnectAttempt >= MAX_RECONNECT_ATTEMPTS) {
            return DecisionAction(
                type = DecisionActionType.GIVE_UP,
                targetServerId = null,
                delayMs = 0L,
                reason = "max_reconnect_attempts_reached_or_no_active_server",
            )
        }

        if (reconnectAttempt < SAME_SERVER_RETRY_LIMIT && !scores[currentServerId].isAvoided(nowMs)) {
            return DecisionAction(
                type = DecisionActionType.RECONNECT_SAME,
                targetServerId = currentServerId,
                delayMs = backoffFor(reconnectAttempt),
                reason = "retry_same_server_before_fallback",
            )
        }

        val fallback = selectBestServer(
            candidates = candidates,
            scores = scores,
            currentServerId = currentServerId,
            nowMs = nowMs,
        )

        return if (fallback != null) {
            DecisionAction(
                type = DecisionActionType.SWITCH_SERVER,
                targetServerId = fallback.serverId,
                delayMs = backoffFor(reconnectAttempt),
                reason = "current_server_unstable_switching_to_best_available",
            )
        } else {
            DecisionAction(
                type = DecisionActionType.GIVE_UP,
                targetServerId = null,
                delayMs = 0L,
                reason = "no_safe_fallback_server_available",
            )
        }
    }

    fun selectBestServer(
        candidates: List<ServerDecisionCandidate>,
        scores: Map<String, ServerQualityScore>,
        currentServerId: String?,
        nowMs: Long,
    ): ServerDecisionCandidate? {
        return candidates
            .asSequence()
            .filter { it.serverId != currentServerId }
            .filter { it.hasRuntimeConfig && !it.premiumBlocked }
            .filter { candidate -> !scores[candidate.serverId].isAvoided(nowMs) }
            .minWithOrNull(compareBy<ServerDecisionCandidate> { scorePenalty(it, scores[it.serverId]) }
                .thenBy { normalizedPing(it.pingMs) }
                .thenByDescending { it.isPinned })
    }

    private fun backoffFor(attempt: Int): Long = BACKOFF_MS[attempt.coerceIn(0, BACKOFF_MS.lastIndex)]

    private fun scorePenalty(candidate: ServerDecisionCandidate, score: ServerQualityScore?): Int {
        if (score == null) return if (candidate.isPinned) -10 else 0
        val failurePenalty = score.consecutiveFailures * 250 + score.failureCount * 25
        val successReward = score.successCount * 10
        val pinnedReward = if (candidate.isPinned) 20 else 0
        return failurePenalty - successReward - pinnedReward
    }

    private fun normalizedPing(pingMs: Int): Int = if (pingMs <= 0) 999 else max(1, pingMs)
}

private fun ServerQualityScore?.isAvoided(nowMs: Long): Boolean = this?.isAvoided(nowMs) == true
