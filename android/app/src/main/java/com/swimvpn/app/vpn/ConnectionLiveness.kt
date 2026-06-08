package com.swimvpn.app.vpn

/**
 * Honest derivation of what the UI may CLAIM about a VPN session, from signals already available
 * locally — NO network probe (an in-session probe was found to destabilize the live tunnel).
 *
 *  - INACTIVE: not connected.
 *  - AWAITING_TRAFFIC: connected (tunnel up + kill-switch armed = protected) but no inbound byte has
 *    been observed yet. Honest for BOTH an idle user and a silently-dead tunnel — in neither case is
 *    data actually flowing, so the UI must not claim "active traffic".
 *  - ACTIVE: inbound bytes observed → data really flowed IN through the tunnel.
 *
 * "Protected" (no-leak) stays true for the whole connected session and is surfaced separately; this
 * type governs only the "is traffic actually flowing" claim. Inbound (not outbound) is the proof: a
 * dead tunnel can still send outbound bytes that never get a reply.
 */
enum class ConnectionActivity { INACTIVE, AWAITING_TRAFFIC, ACTIVE }

object ConnectionLiveness {
    fun derive(connected: Boolean, bytesIn: Long): ConnectionActivity = when {
        !connected -> ConnectionActivity.INACTIVE
        bytesIn > 0L -> ConnectionActivity.ACTIVE
        else -> ConnectionActivity.AWAITING_TRAFFIC
    }
}
