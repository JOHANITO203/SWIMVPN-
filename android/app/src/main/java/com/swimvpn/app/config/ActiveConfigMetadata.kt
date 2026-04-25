package com.swimvpn.app.config

import com.swimvpn.app.config.subscriptionparser.ParsedSubscription
import com.swimvpn.app.config.subscriptionparser.ParsedVpnProfile
import com.swimvpn.app.data.network.ServerNode

enum class ActiveConfigSource {
    SWIMVPN_MANAGED,
    IMPORTED_CONFIG,
}

data class ActiveConfigFallbackMetadata(
    val providerName: String? = null,
    val trafficUsedBytes: Long? = null,
    val trafficTotalBytes: Long? = null,
    val expiresAt: String? = null,
    val warnings: List<String> = emptyList(),
)

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
        fun fromParsedProfile(
            profile: ParsedVpnProfile,
            fallback: ActiveConfigFallbackMetadata,
            source: ActiveConfigSource,
            isActive: Boolean,
        ): ActiveConfigMetadata {
            return ActiveConfigMetadata(
                source = source,
                isActive = isActive,
                displayName = profile.displayName,
                providerName = profile.providerName ?: fallback.providerName,
                protocol = profile.protocol,
                serverHost = profile.serverHost,
                trafficUsedBytes = profile.trafficUsedBytes ?: fallback.trafficUsedBytes,
                trafficTotalBytes = profile.trafficTotalBytes ?: fallback.trafficTotalBytes,
                expiresAt = profile.expiresAt ?: fallback.expiresAt,
                warnings = (fallback.warnings + profile.warnings).distinct(),
            )
        }

        fun fromFirstProfileInSubscription(
            parsed: ParsedSubscription,
            source: ActiveConfigSource,
            isActive: Boolean,
        ): ActiveConfigMetadata? {
            val profile = parsed.profiles.firstOrNull() ?: return null

            return fromParsedProfile(
                profile = profile,
                fallback = ActiveConfigFallbackMetadata(
                    providerName = parsed.providerName,
                    trafficUsedBytes = parsed.trafficUsedBytes,
                    trafficTotalBytes = parsed.trafficTotalBytes,
                    expiresAt = parsed.expiresAt,
                    warnings = parsed.warnings,
                ),
                source = source,
                isActive = isActive,
            )
        }

        fun fromRawConfig(
            rawConfig: String,
            source: ActiveConfigSource,
            displayNameFallback: String,
            isActive: Boolean = true,
        ): ActiveConfigMetadata? {
            val parsed = com.swimvpn.app.config.subscriptionparser.SubscriptionParser.parse(rawConfig)
            val firstProfile = parsed.profiles.firstOrNull()
            return if (firstProfile == null) {
                ActiveConfigMetadata(
                    source = source,
                    isActive = isActive,
                    displayName = displayNameFallback,
                    providerName = parsed.providerName,
                    trafficUsedBytes = parsed.trafficUsedBytes,
                    trafficTotalBytes = parsed.trafficTotalBytes,
                    expiresAt = parsed.expiresAt,
                    warnings = parsed.warnings,
                )
            } else {
                fromFirstProfileInSubscription(parsed, source, isActive)
                    ?.copy(displayName = firstProfile.displayName.ifBlank { displayNameFallback })
            }
        }

        fun fromManagedServer(
            server: ServerNode,
            isActive: Boolean = true,
        ): ActiveConfigMetadata {
            val displayName = listOf(server.city, server.country)
                .filter { it.isNotBlank() }
                .distinct()
                .joinToString(", ")
                .ifBlank { server.host }

            return ActiveConfigMetadata(
                source = ActiveConfigSource.SWIMVPN_MANAGED,
                isActive = isActive,
                displayName = displayName,
                protocol = server.protocol.takeIf { it.isNotBlank() },
                serverHost = server.host.takeIf { it.isNotBlank() },
            )
        }
    }
}
