package com.swimvpn.app.adaptive

import android.content.Context
import com.swimvpn.app.vpn.NetworkType

class ServerScoreStore(context: Context) {
    private val prefs = context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    // Cache the decoded scores so repeated reads (every recommendation refresh / AI-toggle flip)
    // don't re-parse the whole persisted set on the calling thread. Kept fresh on every save.
    @Volatile
    private var cached: Map<String, ServerQualityScore>? = null

    fun loadScores(): Map<String, ServerQualityScore> {
        cached?.let { return it }
        val decoded = prefs.getStringSet(KEY_SCORES, emptySet()).orEmpty()
            .mapNotNull(ServerScoreCodec::decode)
            .associateBy { it.serverId }
        cached = decoded
        return decoded
    }

    fun recordFailure(
        serverId: String,
        nowMs: Long = System.currentTimeMillis(),
        networkType: NetworkType = NetworkType.UNKNOWN,
        hourOfDay: Int = -1,
        profileId: String? = null,
    ): ServerQualityScore {
        val scores = loadScores().toMutableMap()
        val current = scores[serverId] ?: ServerQualityScore(serverId = serverId)
        val updated = AdaptiveDecisionAgent.recordFailure(current, nowMs, networkType, hourOfDay, profileId)
        scores[serverId] = updated
        saveScores(scores)
        return updated
    }

    fun recordManualSelection(serverId: String, nowMs: Long = System.currentTimeMillis()): ServerQualityScore {
        val scores = loadScores().toMutableMap()
        val current = scores[serverId] ?: ServerQualityScore(serverId = serverId)
        val updated = AdaptiveDecisionAgent.recordManualSelection(current, nowMs)
        scores[serverId] = updated
        saveScores(scores)
        return updated
    }

    fun recordSuccess(
        serverId: String,
        nowMs: Long = System.currentTimeMillis(),
        networkType: NetworkType = NetworkType.UNKNOWN,
        hourOfDay: Int = -1,
        profileId: String? = null,
    ): ServerQualityScore {
        val scores = loadScores().toMutableMap()
        val current = scores[serverId] ?: ServerQualityScore(serverId = serverId)
        val updated = AdaptiveDecisionAgent.recordSuccess(current, nowMs, networkType, hourOfDay, profileId)
        scores[serverId] = updated
        saveScores(scores)
        return updated
    }

    private fun saveScores(scores: Map<String, ServerQualityScore>) {
        cached = scores
        prefs.edit()
            .putStringSet(KEY_SCORES, scores.values.map(ServerScoreCodec::encode).toSet())
            .apply()
    }

    private companion object {
        const val PREFS_NAME = "swimvpn_adaptive_scores"
        const val KEY_SCORES = "server_scores"
    }
}

/**
 * Pure, Android-free serialization for [ServerQualityScore].
 *
 * Encoding is versioned and forward compatible:
 *  - [encode] writes a leading [VERSION] token followed by the score fields.
 *  - [decode] accepts BOTH the legacy format (no version token, exactly [LEGACY_FIELD_COUNT]
 *    fields) and the versioned format (leading "v2"+ token). For the versioned format, fields are
 *    parsed defensively by index with safe defaults, so ADDING new trailing fields in a future
 *    version will NOT break decode of older rows.
 */
object ServerScoreCodec {
    const val SEPARATOR = ""
    const val VERSION = "v6"
    private const val LEGACY_FIELD_COUNT = 7
    // Sub-separators for the networkFailures field. Distinct from SEPARATOR () so they cannot
    // collide with the field framing: "WIFI:2;CELLULAR:1".
    private const val NETWORK_ENTRY_SEPARATOR = ";"
    private const val NETWORK_KV_SEPARATOR = ":"

    fun encode(score: ServerQualityScore): String = listOf(
        VERSION,
        score.serverId,
        score.successCount,
        score.failureCount,
        score.consecutiveFailures,
        score.lastSuccessAtMs,
        score.lastFailureAtMs,
        score.avoidUntilMs,
        score.manualSelectionCount,
        score.lastManualSelectionAtMs,
        encodeNetworkMap(score.networkFailures),
        // v4: networkSuccesses appended after networkFailures, same compact format.
        encodeNetworkMap(score.networkSuccesses),
        // v5: hour-of-day maps appended after networkSuccesses, compact int-keyed format "9:2;18:5".
        encodeHourMap(score.successByHour),
        encodeHourMap(score.failureByHour),
        // v6: per-(network×camouflage-profile) outcome maps, composite string keys "WIFI|firefox:3".
        encodeProfileMap(score.profileSuccesses),
        encodeProfileMap(score.profileFailures),
    ).joinToString(SEPARATOR)

    // Omit zero/negative entries; empty map -> "". Stable order for deterministic encoding/tests.
    private fun encodeNetworkMap(networkMap: Map<NetworkType, Int>): String =
        networkMap.entries
            .filter { it.value > 0 }
            .sortedBy { it.key.name }
            .joinToString(NETWORK_ENTRY_SEPARATOR) { "${it.key.name}$NETWORK_KV_SEPARATOR${it.value}" }

    // v5: compact int-keyed hour map. Omit zero/negative entries; empty map -> "". Sorted by hour
    // for deterministic encoding/tests.
    private fun encodeHourMap(hourMap: Map<Int, Int>): String =
        hourMap.entries
            .filter { it.value > 0 }
            .sortedBy { it.key }
            .joinToString(NETWORK_ENTRY_SEPARATOR) { "${it.key}$NETWORK_KV_SEPARATOR${it.value}" }

    // v6: composite string-keyed profile map ("WIFI|firefox:3"). Keys contain "|" (never ":" or ";"),
    // so they don't collide with the sub-separators. Omit zero/negative entries; sorted for determinism.
    private fun encodeProfileMap(profileMap: Map<String, Int>): String =
        profileMap.entries
            .filter { it.value > 0 && it.key.isNotEmpty() }
            .sortedBy { it.key }
            .joinToString(NETWORK_ENTRY_SEPARATOR) { "${it.key}$NETWORK_KV_SEPARATOR${it.value}" }

    fun decode(raw: String): ServerQualityScore? {
        val parts = raw.split(SEPARATOR)
        return when {
            isVersioned(parts) -> decodeVersioned(parts)
            parts.size == LEGACY_FIELD_COUNT -> decodeLegacy(parts)
            else -> null
        }
    }

    private fun isVersioned(parts: List<String>): Boolean =
        parts.isNotEmpty() && parts[0].startsWith("v")

    // Versioned rows offset every field by 1 (token at index 0). Parse by index with defaults so
    // unknown trailing fields from a newer version are simply ignored, never rejected.
    private fun decodeVersioned(parts: List<String>): ServerQualityScore? {
        val serverId = parts.getOrNull(1)?.takeIf { it.isNotEmpty() } ?: return null
        return ServerQualityScore(
            serverId = serverId,
            successCount = parts.intAt(2),
            failureCount = parts.intAt(3),
            consecutiveFailures = parts.intAt(4),
            lastSuccessAtMs = parts.longAt(5),
            lastFailureAtMs = parts.longAt(6),
            avoidUntilMs = parts.longAt(7),
            // v3 fields; absent in v2 rows -> defaults so older rows upgrade losslessly.
            manualSelectionCount = parts.intAt(8),
            lastManualSelectionAtMs = parts.longAt(9),
            networkFailures = decodeNetworkMap(parts.getOrNull(10)),
            // v4 field; absent in v2/v3 rows -> emptyMap so older rows upgrade losslessly.
            networkSuccesses = decodeNetworkMap(parts.getOrNull(11)),
            // v5 fields; absent in v2/v3/v4 rows -> emptyMap so older rows upgrade losslessly.
            successByHour = decodeHourMap(parts.getOrNull(12)),
            failureByHour = decodeHourMap(parts.getOrNull(13)),
            // v6 fields; absent in v2..v5 rows -> emptyMap so older rows upgrade losslessly.
            profileSuccesses = decodeProfileMap(parts.getOrNull(14)),
            profileFailures = decodeProfileMap(parts.getOrNull(15)),
        )
    }

    // Defensive: unknown NetworkType names and malformed entries are skipped without throwing.
    private fun decodeNetworkMap(raw: String?): Map<NetworkType, Int> {
        if (raw.isNullOrEmpty()) return emptyMap()
        val result = mutableMapOf<NetworkType, Int>()
        raw.split(NETWORK_ENTRY_SEPARATOR).forEach { entry ->
            val kv = entry.split(NETWORK_KV_SEPARATOR)
            if (kv.size != 2) return@forEach
            val type = runCatching { NetworkType.valueOf(kv[0]) }.getOrNull() ?: return@forEach
            val count = kv[1].toIntOrNull()?.takeIf { it > 0 } ?: return@forEach
            result[type] = count
        }
        return result
    }

    // Defensive: non-numeric keys/values and out-of-range hours (outside 0..23) are skipped without
    // throwing. Mirrors decodeNetworkMap's tolerance for malformed rows.
    private fun decodeHourMap(raw: String?): Map<Int, Int> {
        if (raw.isNullOrEmpty()) return emptyMap()
        val result = mutableMapOf<Int, Int>()
        raw.split(NETWORK_ENTRY_SEPARATOR).forEach { entry ->
            val kv = entry.split(NETWORK_KV_SEPARATOR)
            if (kv.size != 2) return@forEach
            val hour = kv[0].toIntOrNull()?.takeIf { it in 0..23 } ?: return@forEach
            val count = kv[1].toIntOrNull()?.takeIf { it > 0 } ?: return@forEach
            result[hour] = count
        }
        return result
    }

    // v6: composite string-keyed profile map. Split on ":" with limit 2 so the value is the trailing
    // token; the key (e.g. "WIFI|firefox") never contains ":". Malformed entries are skipped.
    private fun decodeProfileMap(raw: String?): Map<String, Int> {
        if (raw.isNullOrEmpty()) return emptyMap()
        val result = mutableMapOf<String, Int>()
        raw.split(NETWORK_ENTRY_SEPARATOR).forEach { entry ->
            val kv = entry.split(NETWORK_KV_SEPARATOR, limit = 2)
            if (kv.size != 2) return@forEach
            val key = kv[0].takeIf { it.isNotEmpty() } ?: return@forEach
            val count = kv[1].toIntOrNull()?.takeIf { it > 0 } ?: return@forEach
            result[key] = count
        }
        return result
    }

    private fun decodeLegacy(parts: List<String>): ServerQualityScore? = runCatching {
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

    private fun List<String>.intAt(index: Int): Int = getOrNull(index)?.toIntOrNull() ?: 0

    private fun List<String>.longAt(index: Int): Long = getOrNull(index)?.toLongOrNull() ?: 0L
}
