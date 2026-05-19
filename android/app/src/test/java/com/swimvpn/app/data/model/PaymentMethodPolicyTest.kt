package com.swimvpn.app.data.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PaymentMethodPolicyTest {
    @Test
    fun `defaults checkout to SwimPay while keeping fallback methods available`() {
        assertEquals(PaymentMethodPolicy.SWIMPAY, PaymentMethodPolicy.DEFAULT_METHOD)
        assertEquals(
            listOf(
                PaymentMethodPolicy.CARD_MANUAL,
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
}
