package com.swimvpn.app.config

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SubscriptionRefreshPolicyTest {

    private val hourMs = 60L * 60L * 1000L

    @Test
    fun `disabled when interval is zero or negative`() {
        assertFalse(SubscriptionRefreshPolicy.shouldRefresh(0, 0L, 1_000L))
        assertFalse(SubscriptionRefreshPolicy.shouldRefresh(-5, 0L, Long.MAX_VALUE))
    }

    @Test
    fun `never refreshed is due immediately when enabled`() {
        assertTrue(SubscriptionRefreshPolicy.shouldRefresh(24, 0L, 5_000L))
    }

    @Test
    fun `not due before interval elapses`() {
        val last = 1_000_000L
        val now = last + 23 * hourMs
        assertFalse(SubscriptionRefreshPolicy.shouldRefresh(24, last, now))
    }

    @Test
    fun `due exactly at the interval boundary`() {
        val last = 1_000_000L
        val now = last + 24 * hourMs
        assertTrue(SubscriptionRefreshPolicy.shouldRefresh(24, last, now))
    }

    @Test
    fun `due after the interval elapses`() {
        val last = 1_000_000L
        val now = last + 25 * hourMs
        assertTrue(SubscriptionRefreshPolicy.shouldRefresh(24, last, now))
    }

    @Test
    fun `clock skew (now before last) is not due`() {
        val last = 10_000_000L
        val now = last - 5 * hourMs
        assertFalse(SubscriptionRefreshPolicy.shouldRefresh(1, last, now))
    }
}
