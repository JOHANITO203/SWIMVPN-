package com.swimvpn.app.data.model

import com.google.gson.annotations.SerializedName

data class Plan(
    @SerializedName("id") val id: String,
    @SerializedName("code") val code: String, // WEEK, MONTH, QUARTER
    @SerializedName("name") val name: String,
    @SerializedName("duration_label") val durationLabel: String,
    @SerializedName("quota_label") val quotaLabel: String,
    @SerializedName("price_rub") val priceRub: String, // Decimal usually comes as string in JSON
    @SerializedName("active") val active: Boolean,
    @SerializedName("display_order") val displayOrder: Int,
    @SerializedName("slot_count") val slotCount: Int? = null
)

data class CreateOrderRequest(
    @SerializedName("email") val email: String?,
    @SerializedName("phone") val phone: String?,
    @SerializedName("planId") val planId: String,
    @SerializedName("amountRub") val amountRub: Double
)

data class OrderResponse(
    @SerializedName("id") val id: String,
    @SerializedName("order_ref") val orderRef: String,
    @SerializedName("status") val status: String,
    @SerializedName("amount_rub") val amountRub: String
)

data class CheckoutRequest(
    @SerializedName("userNumber") val userNumber: String? = null,
    @SerializedName("deviceId") val deviceId: String? = null,
    @SerializedName("email") val email: String?,
    @SerializedName("phone") val phone: String?,
    @SerializedName("planId") val planId: String,
    @SerializedName("paymentMethod") val paymentMethod: String,
    @SerializedName("cryptoAsset") val cryptoAsset: String? = null,
)

data class CheckoutResponse(
    @SerializedName("orderRef") val orderRef: String,
    @SerializedName("status") val status: String,
    @SerializedName("amountRub") val amountRub: String,
    @SerializedName("paymentMethod") val paymentMethod: String,
    @SerializedName("redirectUrl") val redirectUrl: String?,
    @SerializedName("message") val message: String?,
)
