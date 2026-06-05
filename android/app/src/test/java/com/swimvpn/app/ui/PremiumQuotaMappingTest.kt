package com.swimvpn.app.ui

import com.swimvpn.app.data.network.AccessProfileResponse
import com.swimvpn.app.ui.screens.premiumQuotaNumbers
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class PremiumQuotaMappingTest {

    /**
     * Builds a minimal valid [AccessProfileResponse] with the fields under test overridden.
     *
     * [limitGb]   → dataLimitGB   (> 0 = metered plan; 0 = unlimited)
     * [usedBytes] → dataUsedBytes (raw string that parsedDataUsedBytes will parse)
     * [state]     → entitlementState (drives isPremiumAllowed)
     *
     * isPremiumAllowed = isActiveTrial || isActiveSubscription
     *   isActiveSubscription = (normalizedEntitlementState == "ACTIVE_SUBSCRIPTION")
     *   normalizedEntitlementState prefers entitlementState when non-blank,
     *   so setting entitlementState = "ACTIVE_SUBSCRIPTION" guarantees isPremiumAllowed = true.
     */
    private fun profile(
        limitGb: Double,
        usedBytes: String,
        state: String = "ACTIVE_SUBSCRIPTION",
    ) = AccessProfileResponse(
        userNumber = "U-TEST",
        email = null,
        phone = null,
        accessType = "PAID",
        offerCode = "MONTH",
        planType = "STANDARD",
        status = "ACTIVE",
        trialStartedAt = null,
        trialExpiresAt = null,
        subscriptionExpiresAt = null,
        subscriptionUrl = null,
        devicesAllowed = 1,
        dataLimitGB = limitGb,
        dataUsedBytes = usedBytes,
        profileCompletionRequired = false,
        trialEligible = false,
    ).copy(entitlementState = state)

    @Test
    fun `metered plan exposes sold limit and baseline`() {
        val n = premiumQuotaNumbers(profile(50.0, "1073741824"))
        assertEquals(50L * 1024 * 1024 * 1024, n.limitBytes)
        assertEquals(1073741824L, n.usedBaselineBytes)
        assertTrue(!n.isUnlimited)
    }

    @Test
    fun `unlimited plan flagged when no measured limit`() {
        val n = premiumQuotaNumbers(profile(0.0, "0"))
        assertTrue(n.isUnlimited)
    }

    @Test
    fun `active trial is flagged trial and never unlimited`() {
        val n = premiumQuotaNumbers(profile(0.0, "0", state = "ACTIVE_TRIAL"))
        assertTrue(n.isTrial)
        assertTrue(!n.isUnlimited)
    }
}
