package com.swimvpn.app.adaptive

import kotlin.math.max

data class ServerDecisionCandidate(
    val serverId: String,
    val pingMs: Int,
    val isPinned: Boolean,
    val hasRuntimeConfig: Boolean,
    val premiumBlocked: Boolean,
    val latencyMeasuredAtMs: Long = 0L,
    val latencyProbeFailed: Boolean = false,
    val load: Int? = null,
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

enum class ServerRuntimeQualityState {
    FRESH,
    STALE,
    MISSING_PING,
    FRESH_PROBE_FAILED,
}

data class ServerRecommendationResult(
    val candidate: ServerDecisionCandidate,
    val qualityState: ServerRuntimeQualityState,
    val score: Int,
)

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
    private const val FAILURE_RECOVERY_DECAY_MS = 30 * 60 * 1000L
    private const val FRESH_LATENCY_WINDOW_MS = 2 * 60 * 1000L
    private const val STALE_LATENCY_PENALTY = 120
    private const val MISSING_PING_PENALTY = 300
    private const val FRESH_PROBE_FAILED_PENALTY = 1_000
    private const val PINNED_REWARD = 5
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
    ): ServerDecisionCandidate? = recommendServer(
        candidates = candidates,
        scores = scores,
        currentServerId = currentServerId,
        nowMs = nowMs,
    )?.candidate

    fun recommendServer(
        candidates: List<ServerDecisionCandidate>,
        scores: Map<String, ServerQualityScore>,
        currentServerId: String?,
        nowMs: Long,
    ): ServerRecommendationResult? {
        return candidates
            .asSequence()
            .filter { it.serverId != currentServerId }
            .filter { it.hasRuntimeConfig && !it.premiumBlocked }
            .filter { candidate -> !scores[candidate.serverId].isAvoided(nowMs) }
            .map { candidate -> recommendationFor(candidate, scores[candidate.serverId], nowMs) }
            .filter { it.qualityState != ServerRuntimeQualityState.FRESH_PROBE_FAILED }
            .minWithOrNull(
                compareBy<ServerRecommendationResult> { it.score }
                    .thenBy { normalizedPing(it.candidate.pingMs) }
                    .thenByDescending { it.candidate.isPinned }
                    .thenBy { it.candidate.serverId },
            )
    }

    private fun backoffFor(attempt: Int): Long = BACKOFF_MS[attempt.coerceIn(0, BACKOFF_MS.lastIndex)]

    private fun recommendationFor(
        candidate: ServerDecisionCandidate,
        score: ServerQualityScore?,
        nowMs: Long,
    ): ServerRecommendationResult {
        val qualityState = qualityState(candidate, nowMs)
        val totalScore = normalizedPing(candidate.pingMs) +
            qualityPenalty(qualityState) +
            historyPenalty(score, nowMs) -
            if (candidate.isPinned) PINNED_REWARD else 0

        return ServerRecommendationResult(
            candidate = candidate,
            qualityState = qualityState,
            score = totalScore,
        )
    }

    private fun historyPenalty(score: ServerQualityScore?, nowMs: Long): Int {
        if (score == null) return 0
        val recovered = score.lastFailureAtMs > 0L &&
            nowMs - score.lastFailureAtMs >= FAILURE_RECOVERY_DECAY_MS &&
            !score.isAvoided(nowMs)
        val consecutiveFailures = if (recovered) 0 else score.consecutiveFailures
        val failureCount = if (recovered) 0 else score.failureCount
        val failurePenalty = consecutiveFailures * 250 + failureCount * 25
        val successReward = score.successCount * 10
        return failurePenalty - successReward
    }

    private fun qualityState(candidate: ServerDecisionCandidate, nowMs: Long): ServerRuntimeQualityState {
        if (candidate.pingMs <= 0 || candidate.latencyMeasuredAtMs <= 0L) {
            return ServerRuntimeQualityState.MISSING_PING
        }

        val ageMs = max(0L, nowMs - candidate.latencyMeasuredAtMs)
        val isFresh = ageMs <= FRESH_LATENCY_WINDOW_MS
        return when {
            candidate.latencyProbeFailed && isFresh -> ServerRuntimeQualityState.FRESH_PROBE_FAILED
            isFresh -> ServerRuntimeQualityState.FRESH
            else -> ServerRuntimeQualityState.STALE
        }
    }

    private fun qualityPenalty(state: ServerRuntimeQualityState): Int {
        return when (state) {
            ServerRuntimeQualityState.FRESH -> 0
            ServerRuntimeQualityState.STALE -> STALE_LATENCY_PENALTY
            ServerRuntimeQualityState.MISSING_PING -> MISSING_PING_PENALTY
            ServerRuntimeQualityState.FRESH_PROBE_FAILED -> FRESH_PROBE_FAILED_PENALTY
        }
    }

    private fun normalizedPing(pingMs: Int): Int = if (pingMs <= 0) 999 else max(1, pingMs)
}

private fun ServerQualityScore?.isAvoided(nowMs: Long): Boolean = this?.isAvoided(nowMs) == true
