package com.swimvpn.app.config

import com.swimvpn.app.config.subscriptionparser.ParsedSubscription

enum class ActiveConfigSource {
    SWIMVPN_MANAGED,
    IMPORTED_CONFIG,
}

data class ActiveConfigMetadata(
    val source: ActiveConfigSource,
    val isActive: Boolean,
    val displayName: String,
    val providerName: String? = null,
    val protocol: String? = null,
    val serverHost: String? = null,
    val trafficUsedBytes: Long? = null,
    val trafficTotalBytes: Long? = null,
    val expiresAt: String? = null,
    val warnings: List<String> = emptyList(),
) {
    companion object {
        fun fromParsedSubscription(
            parsed: ParsedSubscription,
            source: ActiveConfigSource,
            isActive: Boolean,
        ): ActiveConfigMetadata? {
            val profile = parsed.profiles.firstOrNull() ?: return null

            return ActiveConfigMetadata(
                source = source,
                isActive = isActive,
                displayName = profile.displayName,
                providerName = profile.providerName ?: parsed.providerName,
                protocol = profile.protocol,
                serverHost = profile.serverHost,
                trafficUsedBytes = profile.trafficUsedBytes ?: parsed.trafficUsedBytes,
                trafficTotalBytes = profile.trafficTotalBytes ?: parsed.trafficTotalBytes,
                expiresAt = profile.expiresAt ?: parsed.expiresAt,
                warnings = (parsed.warnings + profile.warnings).distinct(),
            )
        }
    }
}
