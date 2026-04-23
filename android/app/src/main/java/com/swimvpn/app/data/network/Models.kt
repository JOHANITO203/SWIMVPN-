package com.swimvpn.app.data.network

import com.google.gson.annotations.SerializedName

data class StartTrialRequest(
    val deviceId: String,
    val platform: String = "android",
    val locale: String = "ru"
)

data class BootstrapAccessRequest(
    val deviceId: String,
    val platform: String = "android",
    val locale: String = "ru"
)

data class BootstrapAccessResponse(
    val userNumber: String,
    val email: String?,
    val phone: String?,
    val trialEligible: Boolean,
    val profileCompletionRequired: Boolean,
    val hasActiveAccess: Boolean,
    val profile: AccessProfileResponse?
)

data class AccessProfileResponse(
    val userNumber: String,
    val email: String?,
    val phone: String?,
    val accessType: String,
    val offerCode: String?,
    val planType: String,
    val status: String,
    val trialStartedAt: String?,
    val trialExpiresAt: String?,
    val subscriptionExpiresAt: String?,
    val subscriptionUrl: String?,
    val devicesAllowed: Int,
    val dataLimitGB: Int,
    val dataUsedBytes: String,
    val profileCompletionRequired: Boolean,
    val trialEligible: Boolean
) {
    val parsedDataUsedBytes: Long
        get() = dataUsedBytes.filter { it.isDigit() }.toLongOrNull() ?: 0L

    val effectiveExpiryAt: String?
        get() = if (accessType == "TRIAL") trialExpiresAt else subscriptionExpiresAt

    val isExpired: Boolean
        get() = status == "EXPIRED"

    val hasMeasuredLimit: Boolean
        get() = dataLimitGB > 0

    val dataLimitBytes: Long
        get() = if (hasMeasuredLimit) dataLimitGB.toLong() * 1024L * 1024L * 1024L else 0L

    fun totalConsumedBytes(bytesIn: Long = 0L, bytesOut: Long = 0L): Long =
        parsedDataUsedBytes + bytesIn + bytesOut

    fun remainingBytes(bytesIn: Long = 0L, bytesOut: Long = 0L): Long =
        if (!hasMeasuredLimit) 0L else (dataLimitBytes - totalConsumedBytes(bytesIn, bytesOut)).coerceAtLeast(0L)

    fun consumedPercentage(bytesIn: Long = 0L, bytesOut: Long = 0L): Float =
        if (!hasMeasuredLimit) 0f else (totalConsumedBytes(bytesIn, bytesOut).toFloat() / dataLimitBytes.toFloat()).coerceIn(0f, 1f)
}

data class ActivateTrialRequest(
    val userNumber: String,
    val email: String,
    val phone: String
)

data class ImportSubscriptionRequest(
    val userNumber: String,
    val subscriptionUrl: String
)

data class ResolveCryptSubscriptionRequest(
    val userNumber: String,
    val deviceId: String,
    val encryptedLink: String
)

data class ResolveCryptSubscriptionResponse(
    val version: String,
    val rawConfig: String,
    val compressed: Boolean
)

data class ActivateCodeRequest(
    val userNumber: String,
    val code: String
)

data class ServerNode(
    val id: String,
    val country: String,
    val city: String,
    val host: String,
    val port: Int,
    val protocol: String,
    val tags: List<String>,
    val planScope: String,
    val countryCode: String? = null,
    val load: Int = 0,
    val ping: Int = 0,
    val groupId: String? = null,
    val groupName: String? = null,
    val rawConfig: String? = null,
    val source: String? = null,
    val isPinned: Boolean = false,
)

data class ServerGroup(
    val id: String,
    val title: String,
    val subtitle: String? = null,
    val source: String,
    val servers: List<ServerNode>,
)
