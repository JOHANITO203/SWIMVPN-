package com.swimvpn.app.config

import com.swimvpn.app.config.subscriptionparser.ParsedSubscription
import com.swimvpn.app.config.subscriptionparser.ParsedVpnProfile
import com.swimvpn.app.data.network.AccessProfileResponse
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
    val availabilityStatus: String? = null,
    val loadPercent: Int? = null,
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

        fun fromImportedProfile(
            profile: SwimVpnProfile,
            isActive: Boolean = true,
        ): ActiveConfigMetadata {
            val parsed = fromRawConfig(
                rawConfig = profile.rawConfig,
                source = ActiveConfigSource.IMPORTED_CONFIG,
                displayNameFallback = profile.displayName,
                isActive = isActive,
            )

            return (parsed ?: ActiveConfigMetadata(
                source = ActiveConfigSource.IMPORTED_CONFIG,
                isActive = isActive,
                displayName = profile.displayName,
                protocol = profile.protocol.name.lowercase(),
                serverHost = profile.address.takeIf { it.isNotBlank() },
                warnings = profile.parseWarnings,
            )).copy(
                providerName = profile.subscriptionProviderName ?: parsed?.providerName,
                trafficUsedBytes = profile.subscriptionTrafficUsedBytes ?: parsed?.trafficUsedBytes,
                trafficTotalBytes = profile.subscriptionTrafficTotalBytes ?: parsed?.trafficTotalBytes,
                expiresAt = profile.subscriptionExpiresAt ?: parsed?.expiresAt,
                warnings = (parsed?.warnings.orEmpty() + profile.parseWarnings).distinct(),
            )
        }

        fun fromManagedServer(
            server: ServerNode,
            isActive: Boolean = true,
        ): ActiveConfigMetadata {
            val displayName = server.city.ifBlank { server.country.ifBlank { server.host } }

            return ActiveConfigMetadata(
                source = ActiveConfigSource.SWIMVPN_MANAGED,
                isActive = isActive,
                displayName = displayName,
                protocol = server.protocol.takeIf { it.isNotBlank() },
                serverHost = server.host.takeIf { it.isNotBlank() },
                providerName = server.providerName,
                trafficUsedBytes = parseTrafficByteString(server.trafficUsedBytes),
                trafficTotalBytes = parseTrafficByteString(server.trafficTotalBytes),
                availabilityStatus = server.availabilityStatus,
                loadPercent = server.load?.coerceIn(0, 100),
                expiresAt = server.expiresAt,
            )
        }

        fun fromManagedProfile(
            profile: AccessProfileResponse,
            server: ServerNode?,
        ): ActiveConfigMetadata {
            val base = server?.let { fromManagedServer(it, profile.isPremiumAllowed) }
            val displayName = base?.displayName
                ?: profile.planDisplayName
                ?: profile.offerCode
                ?: "SWIMVPN Premium"
            val totalBytes = profile.dataLimitBytes.takeIf { profile.hasMeasuredLimit }

            return (base ?: ActiveConfigMetadata(
                source = ActiveConfigSource.SWIMVPN_MANAGED,
                isActive = profile.isPremiumAllowed,
                displayName = displayName,
            )).copy(
                isActive = profile.isPremiumAllowed,
                displayName = displayName,
                providerName = profile.supplierProviderName ?: base?.providerName,
                trafficUsedBytes = profile.parsedDataUsedBytes.takeIf { totalBytes != null || it > 0L },
                trafficTotalBytes = totalBytes,
                expiresAt = profile.effectiveExpiryAt,
            )
        }

        private fun parseTrafficByteString(value: String?): Long? {
            return value?.trim()?.takeIf { it.isNotEmpty() }?.toLongOrNull()?.takeIf { it >= 0L }
        }
    }
}
