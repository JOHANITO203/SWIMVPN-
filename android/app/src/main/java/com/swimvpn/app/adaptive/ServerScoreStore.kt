package com.swimvpn.app.adaptive

import android.content.Context

class ServerScoreStore(context: Context) {
    private val prefs = context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    fun loadScores(): Map<String, ServerQualityScore> {
        return prefs.getStringSet(KEY_SCORES, emptySet()).orEmpty()
            .mapNotNull(::decode)
            .associateBy { it.serverId }
    }

    fun recordFailure(serverId: String, nowMs: Long = System.currentTimeMillis()): ServerQualityScore {
        val scores = loadScores().toMutableMap()
        val current = scores[serverId] ?: ServerQualityScore(serverId = serverId)
        val updated = AdaptiveDecisionAgent.recordFailure(current, nowMs)
        scores[serverId] = updated
        saveScores(scores)
        return updated
    }

    fun recordSuccess(serverId: String, nowMs: Long = System.currentTimeMillis()): ServerQualityScore {
        val scores = loadScores().toMutableMap()
        val current = scores[serverId] ?: ServerQualityScore(serverId = serverId)
        val updated = AdaptiveDecisionAgent.recordSuccess(current, nowMs)
        scores[serverId] = updated
        saveScores(scores)
        return updated
    }

    private fun saveScores(scores: Map<String, ServerQualityScore>) {
        prefs.edit()
            .putStringSet(KEY_SCORES, scores.values.map(::encode).toSet())
            .apply()
    }

    private fun encode(score: ServerQualityScore): String = listOf(
        score.serverId,
        score.successCount,
        score.failureCount,
        score.consecutiveFailures,
        score.lastSuccessAtMs,
        score.lastFailureAtMs,
        score.avoidUntilMs,
    ).joinToString(SEPARATOR)

    private fun decode(raw: String): ServerQualityScore? {
        val parts = raw.split(SEPARATOR)
        if (parts.size != 7) return null
        return runCatching {
            ServerQualityScore(
                serverId = parts[0],
                successCount = parts[1].toInt(),
                failureCount = parts[2].toInt(),
                consecutiveFailures = parts[3].toInt(),
                lastSuccessAtMs = parts[4].toLong(),
                lastFailureAtMs = parts[5].toLong(),
                avoidUntilMs = parts[6].toLong(),
            )
        }.getOrNull()
    }

    private companion object {
        const val PREFS_NAME = "swimvpn_adaptive_scores"
        const val KEY_SCORES = "server_scores"
        const val SEPARATOR = "\u001F"
    }
}
