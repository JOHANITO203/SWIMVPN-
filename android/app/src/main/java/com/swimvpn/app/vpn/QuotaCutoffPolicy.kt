package com.swimvpn.app.vpn

/** Pure quota-cutoff rule (sold quota). limitBytes <= 0 = unmetered/unlimited => never cut (fail-open). */
object QuotaCutoffPolicy {
    fun isExhausted(limitBytes: Long, baselineBytes: Long, sessionBytes: Long): Boolean {
        if (limitBytes <= 0L) return false
        return baselineBytes + sessionBytes >= limitBytes
    }
}
