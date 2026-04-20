package com.swimvpn.app.data.network

import com.google.gson.annotations.SerializedName

data class StartTrialRequest(
    val deviceId: String,
    val platform: String = "android",
    val locale: String = "ru"
)

data class AccessProfileResponse(
    val userNumber: String,
    val email: String?,
    val planType: String, // "TRIAL", "PREMIUM", "EXPIRED"
    val status: String,   // "ACTIVE", "EXPIRED"
    val trialStartedAt: String,
    val trialExpiresAt: String,
    val subscriptionExpiresAt: String?,
    val subscriptionUrl: String?,
    val devicesAllowed: Int,
    val dataLimitGB: Int,
    val dataUsedBytes: String
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
