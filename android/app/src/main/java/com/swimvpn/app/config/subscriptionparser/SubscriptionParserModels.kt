package com.swimvpn.app.config.subscriptionparser

import java.util.UUID

data class ParsedVpnProfile(
    val id: String = UUID.randomUUID().toString(),
    val providerName: String? = null,
    val displayName: String,
    val protocol: String,
    val transport: String? = null,
    val security: String? = null,
    val serverHost: String? = null,
    val serverPort: Int? = null,
    val userId: String? = null,
    val password: String? = null,
    val method: String? = null,
    val sni: String? = null,
    val fingerprint: String? = null,
    val path: String? = null,
    val flow: String? = null,
    val publicKey: String? = null,
    val shortId: String? = null,
    val remark: String? = null,
    val countryEmoji: String? = null,
    val trafficUsedBytes: Long? = null,
    val trafficTotalBytes: Long? = null,
    val expiresAt: String? = null,
    val autoUpdateIntervalHours: Int? = null,
    val raw: String,
    val warnings: List<String> = emptyList(),
    val isImportable: Boolean = true,
)

data class ParsedSubscription(
    val id: String = UUID.randomUUID().toString(),
    val sourceUrl: String? = null,
    val providerName: String? = null,
    val profiles: List<ParsedVpnProfile>,
    val trafficUsedBytes: Long? = null,
    val trafficTotalBytes: Long? = null,
    val expiresAt: String? = null,
    val autoUpdateIntervalHours: Int? = null,
    val warnings: List<String> = emptyList(),
    val raw: String,
)

internal data class ParsedTrafficMetadata(
    val usedBytes: Long? = null,
    val totalBytes: Long? = null,
    val warnings: List<String> = emptyList(),
)

internal data class ParsedSubscriptionMetadata(
    val providerName: String? = null,
    val trafficUsedBytes: Long? = null,
    val trafficTotalBytes: Long? = null,
    val expiresAt: String? = null,
    val autoUpdateIntervalHours: Int? = null,
    val warnings: List<String> = emptyList(),
)

data class SubscriptionHeaderMetadata(
    val providerName: String? = null,
    val trafficUsedBytes: Long? = null,
    val trafficTotalBytes: Long? = null,
    val expiresAt: String? = null,
    val autoUpdateIntervalHours: Int? = null,
    val warnings: List<String> = emptyList(),
) {
    val hasValues: Boolean
        get() = providerName != null ||
            trafficUsedBytes != null ||
            trafficTotalBytes != null ||
            expiresAt != null ||
            autoUpdateIntervalHours != null ||
            warnings.isNotEmpty()
}

internal data class DecodedPayload(
    val payload: String,
    val warnings: List<String> = emptyList(),
)
