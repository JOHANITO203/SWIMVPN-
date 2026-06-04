package com.swimvpn.app.adaptive

import com.swimvpn.app.vpn.NetworkType

/**
 * Honest, privacy-first session telemetry ("what works here").
 *
 * Captures ONLY technical session facts — transport/protocol, camouflage profile, network class,
 * connection outcome — and NEVER any user data, destination, content, or identifier. It is the
 * structured companion to the per-profile learning persisted in [ServerScoreStore]: it makes the
 * "which profile connected on which network" signal inspectable, and is the seam for an OPTIONAL
 * future backend aggregation of per-network RELIABILITY.
 *
 * It deliberately does NOT compute a "detectability"/"stealth" score: the client cannot measure how a
 * censor classifies traffic, so any such number would be false confidence. We only report reliability.
 */
data class SessionBenchmarkRecord(
    val transport: String,
    val profileId: String,
    val networkType: NetworkType,
    val success: Boolean,
    val failureCause: String? = null,
) {
    /** Flat, PII-free fields for logging / future serialization. */
    fun toLogFields(): Map<String, Any?> = buildMap {
        put("transport", transport)
        put("profile", profileId)
        put("network", networkType.name)
        put("success", success)
        failureCause?.let { put("cause", it) }
    }
}

object BenchmarkCollector {
    /** Emit a session record locally (logcat only — no network, no PII). */
    fun record(record: SessionBenchmarkRecord) {
        AdaptiveEventLogger.log(event = "session_benchmark", details = record.toLogFields())
    }
}
