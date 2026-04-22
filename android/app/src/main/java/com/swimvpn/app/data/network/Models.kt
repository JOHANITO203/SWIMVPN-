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
)

data class ActivateTrialRequest(
    val userNumber: String,
    val email: String,
    val phone: String
)

data class ImportSubscriptionRequest(
    val userNumber: String,
    val subscriptionUrl: String
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
    val ping: Int = 0
)
