package com.swimvpn.app.data.network

import org.junit.Assert.assertFalse
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class AccessProfileResponseTest {

    @Test
    fun `backend premium supervision does not require measured data limit`() {
        val unmeteredPremium = profile(
            entitlementState = "ACTIVE_SUBSCRIPTION",
            accessType = "PAID",
            dataLimitGB = 0.0,
        )

        assertTrue(unmeteredPremium.shouldSuperviseBackendPremiumConnection("backend"))
    }

    @Test
    fun `backend premium supervision allows active trial`() {
        val activeTrial = profile(
            entitlementState = "ACTIVE_TRIAL",
            accessType = "TRIAL",
            dataLimitGB = 0.0,
        )

        assertTrue(activeTrial.shouldSuperviseBackendPremiumConnection("backend"))
    }

    @Test
    fun `backend premium supervision rejects imported and expired access`() {
        val expired = profile(
            entitlementState = "EXPIRED_SUBSCRIPTION",
            status = "EXPIRED",
            accessType = "PAID",
        )

        assertFalse(expired.shouldSuperviseBackendPremiumConnection("backend"))
        assertFalse(profile().shouldSuperviseBackendPremiumConnection("imported"))
    }

    @Test
    fun `explicit profile completion requirement is authoritative`() {
        val activeButIncomplete = profile(
            entitlementState = "ACTIVE_SUBSCRIPTION",
            accessType = "PAID",
            profileCompletionRequired = true,
        )

        assertTrue(activeButIncomplete.requiresProfileCompletion)
    }

    @Test
    fun `paid pending fulfillment can be visible while trial stays active`() {
        val activeTrialWithPaidPending = profile(
            entitlementState = "ACTIVE_TRIAL",
            accessType = "TRIAL",
            fulfillmentStatus = "PENDING_FULFILLMENT",
        )

        assertTrue(activeTrialWithPaidPending.isActiveTrial)
        assertTrue(activeTrialWithPaidPending.isPremiumAllowed)
        assertTrue(activeTrialWithPaidPending.isPendingFulfillment)
    }

    @Test
    fun `active refresh without runtime url preserves previous runtime access`() {
        val previous = profile(subscriptionUrl = "https://provider.example/sub")
        val refreshedWithoutRuntime = profile(subscriptionUrl = null)

        val merged = refreshedWithoutRuntime.preserveRuntimeAccessFrom(previous)

        assertEquals("https://provider.example/sub", merged.subscriptionUrl)
    }

    private fun profile(
        entitlementState: String = "ACTIVE_SUBSCRIPTION",
        status: String = "ACTIVE",
        accessType: String = "PAID",
        dataLimitGB: Double = 10.0,
        subscriptionUrl: String? = "https://example.com/sub",
        fulfillmentStatus: String? = null,
        profileCompletionRequired: Boolean = false,
    ) = AccessProfileResponse(
        userNumber = "SW-TEST",
        email = "user@example.com",
        phone = "+1000",
        accessType = accessType,
        offerCode = "WEEK",
        planDisplayName = "Basic",
        planType = "paid",
        status = status,
        entitlementState = entitlementState,
        trialStartedAt = null,
        trialExpiresAt = null,
        subscriptionExpiresAt = "2026-05-09T00:00:00Z",
        subscriptionUrl = subscriptionUrl,
        devicesAllowed = 2,
        fulfillmentStatus = fulfillmentStatus,
        dataLimitGB = dataLimitGB,
        dataUsedBytes = "0",
        profileCompletionRequired = profileCompletionRequired,
        trialEligible = false,
    )
}
