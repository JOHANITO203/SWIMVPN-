package com.swimvpn.app.vpn

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class QuotaCutoffPolicyTest {
    @Test fun `no cutoff when limit unset`() {
        assertFalse(QuotaCutoffPolicy.isExhausted(limitBytes = 0L, baselineBytes = 0L, sessionBytes = 999L))
        assertFalse(QuotaCutoffPolicy.isExhausted(limitBytes = -1L, baselineBytes = 10L, sessionBytes = 10L))
    }
    @Test fun `cutoff when baseline plus session reaches limit`() {
        assertTrue(QuotaCutoffPolicy.isExhausted(limitBytes = 100L, baselineBytes = 60L, sessionBytes = 40L))
        assertTrue(QuotaCutoffPolicy.isExhausted(limitBytes = 100L, baselineBytes = 0L, sessionBytes = 150L))
    }
    @Test fun `no cutoff below limit`() {
        assertFalse(QuotaCutoffPolicy.isExhausted(limitBytes = 100L, baselineBytes = 60L, sessionBytes = 39L))
    }
}
