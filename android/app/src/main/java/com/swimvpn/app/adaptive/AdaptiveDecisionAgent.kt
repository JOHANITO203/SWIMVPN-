package com.swimvpn.app.adaptive

import com.swimvpn.app.data.network.ProbeFailureReason
import com.swimvpn.app.vpn.NetworkType
import kotlin.math.max

data class ServerDecisionCandidate(
    val serverId: String,
    val pingMs: Int,
    val isPinned: Boolean,
    val hasRuntimeConfig: Boolean,
    val premiumBlocked: Boolean,
    val latencyMeasuredAtMs: Long = 0L,
    val latencyProbeFailed: Boolean = false,
    val latencyProbeFailureReason: ProbeFailureReason? = null,
    val load: Int? = null,
    val availabilityStatus: String? = null,
    // Stage 2: score on networkType/source. Carried only for now; not used in scoring.
    val source: String? = null,
)

data class ServerQualityScore(
    val serverId: String,
    val successCount: Int = 0,
    val failureCount: Int = 0,
    val consecutiveFailures: Int = 0,
    val lastSuccessAtMs: Long = 0L,
    val lastFailureAtMs: Long = 0L,
    val avoidUntilMs: Long = 0L,
    // Stage 2: bounded manual-override learning. Count of times the user manually picked this server.
    val manualSelectionCount: Int = 0,
    val lastManualSelectionAtMs: Long = 0L,
    // Stage 2: per-network failure history. Count of failures observed per concrete transport.
    // Only concrete transports (WIFI/CELLULAR/ETHERNET) are bucketed; UNKNOWN/NONE/OTHER are not.
    val networkFailures: Map<NetworkType, Int> = emptyMap(),
    // Stage 3: per-network success history ("works here"). Count of successes observed per concrete
    // transport. Only WIFI/CELLULAR/ETHERNET are bucketed; UNKNOWN/NONE/OTHER are not.
    val networkSuccesses: Map<NetworkType, Int> = emptyMap(),
    // Stage 4: hour-of-day outcome history. Counts of successes/failures bucketed by local hour
    // (keys 0..23). This is the WEAKEST signal and only nudges ranking very lightly; out-of-range
    // hours (e.g. -1) are never bucketed.
    val successByHour: Map<Int, Int> = emptyMap(),
    val failureByHour: Map<Int, Int> = emptyMap(),
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
    FRESH_PROBE_FAILED_TRANSIENT,
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
    // Discrete freshness window that gates the UI "validated recommendation" badge.
    // A recommendation within this window reports qualityState == FRESH.
    private const val FRESH_LATENCY_WINDOW_MS = 2 * 60 * 1000L
    // Graduated latency-age penalty calibration (scoring only, separate from the badge state).
    // No penalty at/below the fresh floor; ramps linearly to the legacy STALE penalty at the
    // legacy fresh/stale boundary; then grows modestly toward a cap kept below MISSING_PING.
    private const val LATENCY_FRESH_FLOOR_MS = 30 * 1000L
    private const val LATENCY_PENALTY_AT_BOUNDARY = 120
    private const val LATENCY_PENALTY_CAP = 200
    private const val LATENCY_AGE_PENALTY_BOUNDARY_MS = 2 * 60 * 1000L
    // Beyond the boundary the cap (200) is reached at this age, then held flat.
    private const val LATENCY_AGE_PENALTY_CAP_AT_MS = 10 * 60 * 1000L
    private const val MISSING_PING_PENALTY = 300
    private const val FRESH_PROBE_FAILED_PENALTY = 1_000
    private const val FRESH_PROBE_FAILED_TRANSIENT_PENALTY = 150
    private const val GLOBAL_OUTAGE_MIN_CANDIDATES = 2
    private const val GLOBAL_OUTAGE_FAILURE_RATIO = 0.6
    private const val PINNED_REWARD = 5
    // Bounded manual-override reward: small nudge comparable to the pinned reward, never large enough
    // to resurrect a failing/avoided server (those are filtered before scoring anyway).
    private const val MANUAL_OVERRIDE_REWARD_STEP = 8
    private const val MANUAL_OVERRIDE_REWARD_CAP = 40
    // Manual reward fully decays once the last manual selection is older than this window, so a
    // long-stale preference stops nudging ranking.
    private const val MANUAL_OVERRIDE_REWARD_DECAY_MS = 30L * 24 * 60 * 60 * 1000L
    // Bounded per-network failure penalty: penalizes a server that keeps failing ON THE CURRENT
    // network without affecting it on other networks. Capped below MISSING_PING (300).
    private const val NETWORK_FAILURE_PENALTY_STEP = 50
    private const val NETWORK_FAILURE_PENALTY_CAP = 250
    // Stage 3: bounded "works-here" reward for a server PROVEN to succeed on the current concrete
    // network. Applied as a negative penalty, comparable to latency/penalty magnitudes but never
    // large enough on its own to resurrect a filtered (avoided / probe-failed / blocked) server.
    private const val NETWORK_SUCCESS_REWARD_STEP = 30
    private const val NETWORK_SUCCESS_REWARD_CAP = 120
    // Stage 3: preferredNetwork helper thresholds. A transport is "clearly better" only with a
    // minimum proven sample AND a margin that beats the runner-up by at least the gap threshold.
    private const val PREFERRED_NETWORK_MIN_SAMPLES = 3
    private const val PREFERRED_NETWORK_MARGIN_GAP = 2
    // Stage 4: hour-of-day nudge. The WEAKEST signal: a small bounded adjustment applied only when a
    // server has enough samples at the current hour. Magnitudes are comparable to the pinned/manual
    // reward (cap 30) and strictly smaller than the per-network penalty/reward, so latency and
    // per-network signals always dominate. Suppressed under a global outage; applied after filters.
    private const val HOURLY_NUDGE_MIN_SAMPLES = 3
    private const val HOURLY_NUDGE_CAP = 30
    private const val LOAD_PENALTY_DIVISOR = 2
    private const val UNKNOWN_LOAD_PENALTY = 20
    private const val CONGESTED_AVAILABILITY_PENALTY = 50
    private val BACKOFF_MS = longArrayOf(1_000L, 3_000L, 5_000L, 10_000L, 30_000L)

    fun recordFailure(
        score: ServerQualityScore,
        nowMs: Long,
        networkType: NetworkType = NetworkType.UNKNOWN,
        hourOfDay: Int = -1,
    ): ServerQualityScore {
        val nextConsecutiveFailures = score.consecutiveFailures + 1
        val avoidUntil = if (nextConsecutiveFailures >= AVOID_AFTER_CONSECUTIVE_FAILURES) {
            nowMs + AVOID_DURATION_MS
        } else {
            score.avoidUntilMs
        }

        // Only bucket failures on concrete transports; UNKNOWN/NONE/OTHER leave the map unchanged.
        val nextNetworkFailures = if (isBucketedNetwork(networkType)) {
            score.networkFailures + (networkType to ((score.networkFailures[networkType] ?: 0) + 1))
        } else {
            score.networkFailures
        }

        return score.copy(
            failureCount = score.failureCount + 1,
            consecutiveFailures = nextConsecutiveFailures,
            lastFailureAtMs = nowMs,
            avoidUntilMs = avoidUntil,
            networkFailures = nextNetworkFailures,
            failureByHour = incrementHour(score.failureByHour, hourOfDay),
        )
    }

    /**
     * Records a manual server selection by the user. Increments [ServerQualityScore.manualSelectionCount]
     * and stamps [ServerQualityScore.lastManualSelectionAtMs]. Does NOT touch failure/success counters.
     */
    fun recordManualSelection(
        score: ServerQualityScore,
        nowMs: Long,
    ): ServerQualityScore = score.copy(
        manualSelectionCount = score.manualSelectionCount + 1,
        lastManualSelectionAtMs = nowMs,
    )

    private fun isBucketedNetwork(networkType: NetworkType): Boolean = when (networkType) {
        NetworkType.WIFI, NetworkType.CELLULAR, NetworkType.ETHERNET -> true
        else -> false
    }

    // Increment the hour bucket only for a valid local hour (0..23). Any other value (e.g. -1)
    // leaves the map untouched.
    private fun incrementHour(byHour: Map<Int, Int>, hourOfDay: Int): Map<Int, Int> =
        if (hourOfDay in 0..23) {
            byHour + (hourOfDay to ((byHour[hourOfDay] ?: 0) + 1))
        } else {
            byHour
        }

    fun recordSuccess(
        score: ServerQualityScore,
        nowMs: Long,
        networkType: NetworkType = NetworkType.UNKNOWN,
        hourOfDay: Int = -1,
    ): ServerQualityScore {
        // On concrete transports: credit a per-network success AND shed one accumulated failure on
        // that same network (recovery — a server that starts working again here sheds a failure,
        // floored at 0). UNKNOWN/NONE/OTHER leave both maps untouched.
        val (nextNetworkSuccesses, nextNetworkFailures) = if (isBucketedNetwork(networkType)) {
            val successes = score.networkSuccesses +
                (networkType to ((score.networkSuccesses[networkType] ?: 0) + 1))
            val priorFailures = score.networkFailures[networkType] ?: 0
            val failures = if (priorFailures > 0) {
                val decremented = priorFailures - 1
                if (decremented > 0) {
                    score.networkFailures + (networkType to decremented)
                } else {
                    score.networkFailures - networkType
                }
            } else {
                score.networkFailures
            }
            successes to failures
        } else {
            score.networkSuccesses to score.networkFailures
        }

        return score.copy(
            successCount = score.successCount + 1,
            consecutiveFailures = 0,
            lastSuccessAtMs = nowMs,
            avoidUntilMs = 0L,
            networkSuccesses = nextNetworkSuccesses,
            networkFailures = nextNetworkFailures,
            successByHour = incrementHour(score.successByHour, hourOfDay),
        )
    }

    fun planAfterFailure(
        currentServerId: String?,
        candidates: List<ServerDecisionCandidate>,
        scores: Map<String, ServerQualityScore>,
        reconnectAttempt: Int,
        nowMs: Long,
        networkType: NetworkType = NetworkType.UNKNOWN,
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
            networkType = networkType,
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
        networkType: NetworkType = NetworkType.UNKNOWN,
    ): ServerDecisionCandidate? = recommendServer(
        candidates = candidates,
        scores = scores,
        currentServerId = currentServerId,
        nowMs = nowMs,
        networkType = networkType,
    )?.candidate

    /**
     * Pure heuristic: returns true when a strong majority of candidates report a probe failure
     * whose reason points at the local/transport network being down (TIMEOUT / NETWORK_UNREACHABLE)
     * rather than at the individual servers. In that case per-server avoidance/penalty should be
     * suppressed so the agent does not blacklist healthy servers during a global outage.
     */
    fun isLikelyGlobalOutage(candidates: List<ServerDecisionCandidate>): Boolean {
        if (candidates.size < GLOBAL_OUTAGE_MIN_CANDIDATES) return false
        val networkFailures = candidates.count { candidate ->
            candidate.latencyProbeFailed && isOutageReason(candidate.latencyProbeFailureReason)
        }
        if (networkFailures < GLOBAL_OUTAGE_MIN_CANDIDATES) return false
        return networkFailures.toDouble() / candidates.size >= GLOBAL_OUTAGE_FAILURE_RATIO
    }

    fun recommendServer(
        candidates: List<ServerDecisionCandidate>,
        scores: Map<String, ServerQualityScore>,
        currentServerId: String?,
        nowMs: Long,
        // Stage 2: bounded contextual scoring. networkType nudges ranking via the manual-override
        // reward and a per-network failure penalty; UNKNOWN/NONE leave outcomes unchanged.
        networkType: NetworkType = NetworkType.UNKNOWN,
        // Stage 4: weakest signal. A small bounded hour-of-day nudge; -1 (default) leaves ranking
        // byte-for-byte unchanged.
        hourOfDay: Int = -1,
    ): ServerRecommendationResult? {
        val globalOutage = isLikelyGlobalOutage(candidates)
        return candidates
            .asSequence()
            .filter { it.serverId != currentServerId }
            .filter { it.hasRuntimeConfig && !it.premiumBlocked }
            .filter { candidate -> globalOutage || !scores[candidate.serverId].isAvoided(nowMs) }
            .map { candidate -> recommendationFor(candidate, scores[candidate.serverId], nowMs, globalOutage, networkType, hourOfDay) }
            .filter { globalOutage || it.qualityState != ServerRuntimeQualityState.FRESH_PROBE_FAILED }
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
        globalOutage: Boolean = false,
        networkType: NetworkType = NetworkType.UNKNOWN,
        hourOfDay: Int = -1,
    ): ServerRecommendationResult {
        val qualityState = qualityState(candidate, nowMs)
        // During a likely global outage the failures are not the servers' fault, so neither the
        // heavy probe penalty nor the accumulated failure history is held against them.
        val qualityPenalty = if (globalOutage) 0 else qualityPenalty(qualityState, candidate, nowMs)
        val historyPenalty = if (globalOutage) 0 else historyPenalty(score, nowMs)
        // Per-network penalty is suppressed under a global outage like the other failure penalties.
        val networkPenalty = if (globalOutage) 0 else networkFailurePenalty(score, networkType)
        // "Works-here" reward: prefer a server proven to succeed on the current network. Suppressed
        // under a global outage like the per-network penalty (the successes are not informative when
        // the whole transport is down). Applied after avoidance/probe filtering, so it can never
        // resurrect a filtered server on its own.
        val networkSuccessReward = if (globalOutage) 0 else networkSuccessReward(score, networkType)
        // Bounded manual-override reward only nudges ranking among otherwise-eligible servers; it is
        // applied after avoidance/probe filtering above, so it can never resurrect a filtered server.
        val manualReward = manualOverrideReward(score, nowMs)
        // Stage 4 (weakest signal): hour-of-day nudge. A small bounded +penalty / -reward applied
        // only with sufficient samples at this hour. Suppressed under a global outage like the other
        // history-derived signals, and applied after filtering so it can never resurrect a filtered
        // server. hourOfDay = -1 yields 0 (ranking unchanged).
        val hourlyNudge = if (globalOutage) 0 else hourlyNudge(score, hourOfDay)
        val totalScore = normalizedPing(candidate.pingMs) +
            qualityPenalty +
            loadPenalty(candidate.load) +
            availabilityPenalty(candidate.availabilityStatus) +
            historyPenalty +
            networkPenalty +
            hourlyNudge -
            (if (candidate.isPinned) PINNED_REWARD else 0) -
            manualReward -
            networkSuccessReward

        return ServerRecommendationResult(
            candidate = candidate,
            qualityState = qualityState,
            score = totalScore,
        )
    }

    /**
     * Bounded reward (subtracted from the score) for a manually-preferred server. Grows with
     * [ServerQualityScore.manualSelectionCount] up to [MANUAL_OVERRIDE_REWARD_CAP], then fully decays
     * once the last manual selection is older than [MANUAL_OVERRIDE_REWARD_DECAY_MS].
     */
    private fun manualOverrideReward(score: ServerQualityScore?, nowMs: Long): Int {
        if (score == null || score.manualSelectionCount <= 0) return 0
        val stamped = score.lastManualSelectionAtMs
        val decayed = stamped > 0L && nowMs - stamped >= MANUAL_OVERRIDE_REWARD_DECAY_MS
        if (decayed) return 0
        return minOf(MANUAL_OVERRIDE_REWARD_CAP, score.manualSelectionCount * MANUAL_OVERRIDE_REWARD_STEP)
    }

    /**
     * Bounded penalty for a server that keeps failing on the CURRENT concrete network, leaving its
     * standing on other networks untouched. UNKNOWN/NONE/OTHER carry no per-network penalty. Capped
     * at [NETWORK_FAILURE_PENALTY_CAP], below MISSING_PING.
     */
    private fun networkFailurePenalty(score: ServerQualityScore?, networkType: NetworkType): Int {
        if (score == null || !isBucketedNetwork(networkType)) return 0
        val failures = score.networkFailures[networkType] ?: return 0
        if (failures <= 0) return 0
        return minOf(NETWORK_FAILURE_PENALTY_CAP, failures * NETWORK_FAILURE_PENALTY_STEP)
    }

    /**
     * Bounded "works-here" reward for a server with PROVEN successes on the CURRENT concrete network.
     * Grows with [ServerQualityScore.networkSuccesses] up to [NETWORK_SUCCESS_REWARD_CAP].
     * UNKNOWN/NONE/OTHER carry no reward.
     */
    private fun networkSuccessReward(score: ServerQualityScore?, networkType: NetworkType): Int {
        if (score == null || !isBucketedNetwork(networkType)) return 0
        val successes = score.networkSuccesses[networkType] ?: return 0
        if (successes <= 0) return 0
        return minOf(NETWORK_SUCCESS_REWARD_CAP, successes * NETWORK_SUCCESS_REWARD_STEP)
    }

    /**
     * Stage 4 (WEAKEST signal): bounded hour-of-day nudge returned as a signed score delta
     * (positive = light penalty, negative = light reward). Active only when [hourOfDay] is a valid
     * local hour (0..23) AND the server has at least [HOURLY_NUDGE_MIN_SAMPLES] total events at that
     * hour. The magnitude is the (failure - success) margin at that hour, capped at
     * [HOURLY_NUDGE_CAP] in each direction — comparable to the pinned/manual reward and strictly
     * smaller than the per-network penalty/reward, so latency and per-network signals dominate.
     * Returns 0 for a null score, an out-of-range hour, or an insufficient sample.
     */
    private fun hourlyNudge(score: ServerQualityScore?, hourOfDay: Int): Int {
        if (score == null || hourOfDay !in 0..23) return 0
        val successes = score.successByHour[hourOfDay] ?: 0
        val failures = score.failureByHour[hourOfDay] ?: 0
        if (successes + failures < HOURLY_NUDGE_MIN_SAMPLES) return 0
        // Positive when failures dominate (light penalty); negative when successes dominate (light
        // reward). Bounded symmetrically by the cap.
        return (failures - successes).coerceIn(-HOURLY_NUDGE_CAP, HOURLY_NUDGE_CAP)
    }

    /**
     * Pure, side-effect-free helper for the UI "works better on Wi-Fi / mobile" hint.
     *
     * Returns the concrete transport (WIFI/CELLULAR/ETHERNET) on which this server has CLEARLY better
     * outcomes than every other concrete transport, or null when the data is insufficient or
     * ambiguous. "Clearly better" is defined conservatively and deterministically:
     *  - the winner has at least [PREFERRED_NETWORK_MIN_SAMPLES] successes on that transport, AND
     *  - the winner's (successes - failures) margin exceeds the runner-up transport's margin by at
     *    least [PREFERRED_NETWORK_MARGIN_GAP].
     * Ties, missing data, or a too-small sample all return null.
     */
    fun preferredNetwork(score: ServerQualityScore?): NetworkType? {
        if (score == null) return null
        val transports = listOf(NetworkType.WIFI, NetworkType.CELLULAR, NetworkType.ETHERNET)
        val margins = transports.map { type ->
            val successes = score.networkSuccesses[type] ?: 0
            val failures = score.networkFailures[type] ?: 0
            Triple(type, successes, successes - failures)
        }
        val ranked = margins.sortedByDescending { it.third }
        val winner = ranked[0]
        val runnerUp = ranked[1]
        if (winner.second < PREFERRED_NETWORK_MIN_SAMPLES) return null
        if (winner.third - runnerUp.third < PREFERRED_NETWORK_MARGIN_GAP) return null
        return winner.first
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
            candidate.latencyProbeFailed && isFresh ->
                if (isNetworkSideReason(candidate.latencyProbeFailureReason)) {
                    // TIMEOUT / NETWORK_UNREACHABLE / UNKNOWN usually mean the local network is down,
                    // not that this specific server is bad: penalize lightly and stay selectable.
                    ServerRuntimeQualityState.FRESH_PROBE_FAILED_TRANSIENT
                } else {
                    // CONNECTION_REFUSED / DNS_FAILURE are server-side: heavy avoid/penalty.
                    ServerRuntimeQualityState.FRESH_PROBE_FAILED
                }
            isFresh -> ServerRuntimeQualityState.FRESH
            else -> ServerRuntimeQualityState.STALE
        }
    }

    private fun isOutageReason(reason: ProbeFailureReason?): Boolean =
        reason == ProbeFailureReason.TIMEOUT || reason == ProbeFailureReason.NETWORK_UNREACHABLE

    private fun isNetworkSideReason(reason: ProbeFailureReason?): Boolean = when (reason) {
        ProbeFailureReason.TIMEOUT,
        ProbeFailureReason.NETWORK_UNREACHABLE,
        ProbeFailureReason.UNKNOWN,
        -> true
        // CONNECTION_REFUSED / DNS_FAILURE are server-side. null = legacy probe failure with no
        // recorded reason: keep the historical heavy avoid/penalty for backward compatibility.
        else -> false
    }

    private fun qualityPenalty(
        state: ServerRuntimeQualityState,
        candidate: ServerDecisionCandidate,
        nowMs: Long,
    ): Int {
        return when (state) {
            // FRESH (badge state) and STALE both score on the graduated latency-age penalty so the
            // scoring cost of a stale ping grows smoothly with age instead of snapping to a flat
            // STALE penalty at the 120s boundary. The discrete FRESH state is unchanged and still
            // gates the UI validated-recommendation badge.
            ServerRuntimeQualityState.FRESH,
            ServerRuntimeQualityState.STALE,
            -> latencyAgePenalty(max(0L, nowMs - candidate.latencyMeasuredAtMs))
            ServerRuntimeQualityState.MISSING_PING -> MISSING_PING_PENALTY
            ServerRuntimeQualityState.FRESH_PROBE_FAILED -> FRESH_PROBE_FAILED_PENALTY
            ServerRuntimeQualityState.FRESH_PROBE_FAILED_TRANSIENT -> FRESH_PROBE_FAILED_TRANSIENT_PENALTY
        }
    }

    /**
     * Pure graduated penalty for the age of the last latency measurement (scoring only).
     *
     * Calibrated to match the legacy binary fresh/stale endpoints so the behavior change is minimal:
     *  - age <= [LATENCY_FRESH_FLOOR_MS] (30s): penalty 0 (no cost for a recent ping).
     *  - linear ramp from 0 at the floor to [LATENCY_PENALTY_AT_BOUNDARY] (120) at
     *    [LATENCY_AGE_PENALTY_BOUNDARY_MS] (120s, the legacy fresh/stale boundary).
     *  - beyond the boundary it keeps growing linearly to [LATENCY_PENALTY_CAP] (200) at
     *    [LATENCY_AGE_PENALTY_CAP_AT_MS] and is then held flat — always below MISSING_PING (300),
     *    so a very stale ping is worse than a moderately stale one but still better than no ping.
     *
     * Monotonic non-decreasing in [ageMs].
     */
    fun latencyAgePenalty(ageMs: Long): Int {
        val age = max(0L, ageMs)
        return when {
            age <= LATENCY_FRESH_FLOOR_MS -> 0
            age <= LATENCY_AGE_PENALTY_BOUNDARY_MS -> {
                val span = LATENCY_AGE_PENALTY_BOUNDARY_MS - LATENCY_FRESH_FLOOR_MS
                (LATENCY_PENALTY_AT_BOUNDARY * (age - LATENCY_FRESH_FLOOR_MS) / span).toInt()
            }
            age >= LATENCY_AGE_PENALTY_CAP_AT_MS -> LATENCY_PENALTY_CAP
            else -> {
                val span = LATENCY_AGE_PENALTY_CAP_AT_MS - LATENCY_AGE_PENALTY_BOUNDARY_MS
                val extra = (LATENCY_PENALTY_CAP - LATENCY_PENALTY_AT_BOUNDARY).toLong()
                LATENCY_PENALTY_AT_BOUNDARY + (extra * (age - LATENCY_AGE_PENALTY_BOUNDARY_MS) / span).toInt()
            }
        }
    }

    private fun loadPenalty(load: Int?): Int {
        val normalizedLoad = load?.coerceIn(0, 100) ?: return UNKNOWN_LOAD_PENALTY
        return normalizedLoad / LOAD_PENALTY_DIVISOR
    }

    private fun availabilityPenalty(availabilityStatus: String?): Int {
        return if (availabilityStatus.equals("CONGESTED", ignoreCase = true)) {
            CONGESTED_AVAILABILITY_PENALTY
        } else {
            0
        }
    }

    private fun normalizedPing(pingMs: Int): Int = if (pingMs <= 0) 999 else max(1, pingMs)
}

private fun ServerQualityScore?.isAvoided(nowMs: Long): Boolean = this?.isAvoided(nowMs) == true
