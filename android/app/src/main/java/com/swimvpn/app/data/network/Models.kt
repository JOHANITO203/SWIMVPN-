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
    val planDisplayName: String? = null,
    val planType: String,
    val status: String,
    val entitlementState: String? = null,
    val trialStartedAt: String?,
    val trialExpiresAt: String?,
    val subscriptionExpiresAt: String?,
    val subscriptionUrl: String?,
    val devicesAllowed: Int,
    val dataLimitGB: Double,
    val dataUsedBytes: String,
    val fulfillmentStatus: String? = null,
    val supplierProviderName: String? = null,
    val supplierExpiresAt: String? = null,
    val profileCompletionRequired: Boolean,
    val trialEligible: Boolean
) {
    val normalizedEntitlementState: String
        get() = when {
            !entitlementState.isNullOrBlank() -> entitlementState
            status == "ACTIVE" && accessType == "TRIAL" -> "ACTIVE_TRIAL"
            status == "ACTIVE" && accessType == "PAID" -> "ACTIVE_SUBSCRIPTION"
            status == "EXPIRED" && accessType == "TRIAL" -> "EXPIRED_TRIAL"
            status == "EXPIRED" && accessType == "PAID" -> "EXPIRED_SUBSCRIPTION"
            else -> status
        }

    val requiresProfileCompletion: Boolean
        get() = normalizedEntitlementState == "PROFILE_INCOMPLETE"

    val isTrialAvailable: Boolean
        get() = normalizedEntitlementState == "TRIAL_AVAILABLE"

    val isPendingFulfillment: Boolean
        get() = normalizedEntitlementState == "PENDING_FULFILLMENT"

    val isActiveTrial: Boolean
        get() = normalizedEntitlementState == "ACTIVE_TRIAL"

    val isActiveSubscription: Boolean
        get() = normalizedEntitlementState == "ACTIVE_SUBSCRIPTION"

    val isFreemium: Boolean
        get() = normalizedEntitlementState == "FREEMIUM"

    val isPremiumAllowed: Boolean
        get() = isActiveTrial || isActiveSubscription

    fun shouldSuperviseBackendPremiumConnection(serverSource: String?): Boolean =
        serverSource == "backend" && isPremiumAllowed

    fun preserveRuntimeAccessFrom(previous: AccessProfileResponse): AccessProfileResponse {
        if (!isPremiumAllowed || !subscriptionUrl.isNullOrBlank()) {
            return this
        }

        return copy(
            subscriptionUrl = previous.subscriptionUrl,
            supplierProviderName = supplierProviderName ?: previous.supplierProviderName,
            supplierExpiresAt = supplierExpiresAt ?: previous.supplierExpiresAt,
        )
    }

    val parsedDataUsedBytes: Long
        get() = dataUsedBytes.filter { it.isDigit() }.toLongOrNull() ?: 0L

    val effectiveExpiryAt: String?
        get() = when {
            isActiveTrial -> trialExpiresAt
            isActiveSubscription || isPendingFulfillment -> subscriptionExpiresAt
            else -> null
        }

    val isExpired: Boolean
        get() = normalizedEntitlementState == "EXPIRED_TRIAL" ||
            normalizedEntitlementState == "EXPIRED_SUBSCRIPTION" ||
            status == "EXPIRED"

    val hasMeasuredLimit: Boolean
        get() = dataLimitGB > 0

    val dataLimitBytes: Long
        get() = if (hasMeasuredLimit) (dataLimitGB * 1024.0 * 1024.0 * 1024.0).toLong() else 0L

    fun totalConsumedBytes(bytesIn: Long = 0L, bytesOut: Long = 0L): Long =
        parsedDataUsedBytes + bytesIn + bytesOut

    fun remainingBytes(bytesIn: Long = 0L, bytesOut: Long = 0L): Long =
        if (!hasMeasuredLimit) 0L else (dataLimitBytes - totalConsumedBytes(bytesIn, bytesOut)).coerceAtLeast(0L)

    fun consumedPercentage(bytesIn: Long = 0L, bytesOut: Long = 0L): Float =
        if (!hasMeasuredLimit) 0f else (totalConsumedBytes(bytesIn, bytesOut).toFloat() / dataLimitBytes.toFloat()).coerceIn(0f, 1f)

    val publicPlanName: String?
        get() = when {
            !isPremiumAllowed && !isPendingFulfillment -> null
            accessType == "TRIAL" -> null
            !planDisplayName.isNullOrBlank() -> planDisplayName
            offerCode == "MONTH" -> "Premium"
            offerCode == "QUARTER" -> "Platinum"
            offerCode == "WEEK" -> "Basic"
            else -> null
        }
}

data class ActivateTrialRequest(
    val userNumber: String,
    val deviceId: String,
    val email: String,
    val phone: String
)

data class CompleteProfileRequest(
    val userNumber: String,
    val deviceId: String,
    val email: String?,
    val phone: String?
)

data class CancelCurrentSubscriptionRequest(
    val userNumber: String,
    val deviceId: String,
    val reason: String = "CUSTOMER_CANCELLED"
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

data class ReportUsageRequest(
    val userNumber: String,
    val measuredUsedBytes: String,
    val deviceId: String? = null
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
    val load: Int? = null,
    val ping: Int = 0,
    val latencyMeasuredAtMs: Long = 0L,
    val latencyProbeFailed: Boolean = false,
    val groupId: String? = null,
    val groupName: String? = null,
    val rawConfig: String? = null,
    val source: String? = null,
    val isPinned: Boolean = false,
    val providerName: String? = null,
    val availabilityStatus: String? = null,
    val trafficUsedBytes: String? = null,
    val trafficTotalBytes: String? = null,
    val expiresAt: String? = null,
)

data class ServerGroup(
    val id: String,
    val title: String,
    val subtitle: String? = null,
    val source: String,
    val servers: List<ServerNode>,
)
