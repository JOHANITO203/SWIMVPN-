package com.swimvpn.app.vpn

/**
 * Honest derivation of what the UI may CLAIM about a VPN session, from RECENT traffic deltas the app
 * already has locally — NO network probe (an in-session probe destabilizes the live tunnel).
 *
 * RECENCY, not cumulative totals: bytesIn/bytesOut are running totals that never decrease, so
 * "ever received" (bytesIn > 0) wrongly stays true after a working link dies (e.g. you leave coverage).
 * The honest signal is whether inbound/outbound INCREASED within a recent window — computed by the caller.
 *
 *  - INACTIVE: not connected.
 *  - ACTIVE: inbound increased recently → data is really flowing IN.
 *  - STALLED: connected, we sent recently but got NOTHING back → "no response" (left coverage, captive,
 *    dead server while apps keep trying). This is the state that must UPDATE, not stay "Connected".
 *  - IDLE: connected, no recent traffic either way → protected but idle. A live-idle link and a silently
 *    dead one are indistinguishable without a probe, so we claim nothing more than "protected / awaiting".
 */
enum class ConnectionActivity { INACTIVE, IDLE, STALLED, ACTIVE }

object ConnectionLiveness {
    fun derive(connected: Boolean, recentInbound: Boolean, recentOutbound: Boolean): ConnectionActivity = when {
        !connected -> ConnectionActivity.INACTIVE
        recentInbound -> ConnectionActivity.ACTIVE
        recentOutbound -> ConnectionActivity.STALLED
        else -> ConnectionActivity.IDLE
    }
}
