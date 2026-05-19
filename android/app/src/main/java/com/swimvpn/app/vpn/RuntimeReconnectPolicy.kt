package com.swimvpn.app.vpn

object RuntimeReconnectPolicy {
    fun shouldCancelPendingReconnectForRecoveredNetwork(
        cause: DisconnectCause?,
        started: Boolean,
    ): Boolean {
        return cause == DisconnectCause.NETWORK_LOST && !started
    }
}
