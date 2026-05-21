package com.swimvpn.app.data.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PaymentMethodPolicyTest {
    @Test
    fun `defaults checkout to SwimPay while keeping supported methods available`() {
        assertEquals(PaymentMethodPolicy.SWIMPAY, PaymentMethodPolicy.DEFAULT_METHOD)
        assertEquals(
            listOf(
                PaymentMethodPolicy.SWIMPAY,
                PaymentMethodPolicy.CRYPTO,
            ),
            PaymentMethodPolicy.VISIBLE_METHODS,
        )
    }

    @Test
    fun `external checkout refresh window is bounded`() {
        val openedAt = 1_000L
        val refreshUntil = CheckoutRefreshPolicy.refreshUntil(openedAt)

        assertTrue(CheckoutRefreshPolicy.shouldRefreshAfterReturn(openedAt, refreshUntil))
        assertTrue(CheckoutRefreshPolicy.shouldRefreshAfterReturn(refreshUntil, refreshUntil))
        assertFalse(CheckoutRefreshPolicy.shouldRefreshAfterReturn(refreshUntil + 1L, refreshUntil))
        assertFalse(CheckoutRefreshPolicy.shouldRefreshAfterReturn(openedAt, 0L))
    }

    @Test
    fun `pending fulfillment refresh window is bounded`() {
        val pendingAt = 2_000L
        val refreshUntil = PendingFulfillmentRefreshPolicy.refreshUntil(pendingAt)

        assertTrue(PendingFulfillmentRefreshPolicy.shouldRefresh(nowMs = pendingAt, refreshUntilMs = refreshUntil))
        assertTrue(PendingFulfillmentRefreshPolicy.shouldRefresh(nowMs = refreshUntil, refreshUntilMs = refreshUntil))
        assertFalse(PendingFulfillmentRefreshPolicy.shouldRefresh(nowMs = refreshUntil + 1L, refreshUntilMs = refreshUntil))
        assertFalse(PendingFulfillmentRefreshPolicy.shouldRefresh(nowMs = pendingAt, refreshUntilMs = 0L))
    }

    @Test
    fun `pending fulfillment refresh is not tied to checkout policy`() {
        val pendingAt = 3_000L
        val refreshUntil = PendingFulfillmentRefreshPolicy.refreshUntil(pendingAt)

        assertTrue(
            PendingFulfillmentRefreshPolicy.shouldRefreshAfterReturn(
                nowMs = pendingAt,
                refreshUntilMs = refreshUntil,
            )
        )
    }
}
