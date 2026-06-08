package com.swimvpn.app.vpn

enum class NetworkHandoffAction {
    IGNORE,
    DEBOUNCE_RECONNECT,
    CANCEL_DEBOUNCE,
    RECONNECT_NOW,
}

data class NetworkHandoffDecision(
    val action: NetworkHandoffAction,
    val delayMs: Long = 0L,
)

object NetworkHandoffPolicy {
    const val NETWORK_HANDOFF_GRACE_MS = 4_000L

    fun onLost(
        isActiveUnderlyingNetwork: Boolean,
        stoppedByUser: Boolean,
        currentStatus: RuntimeStatus,
    ): NetworkHandoffDecision {
        if (!isActiveUnderlyingNetwork || stoppedByUser) {
            return NetworkHandoffDecision(NetworkHandoffAction.IGNORE)
        }

        return when (currentStatus) {
            RuntimeStatus.STARTING,
            RuntimeStatus.RUNNING,
            RuntimeStatus.RECONNECTING,
            RuntimeStatus.DEGRADED,
            RuntimeStatus.NO_NETWORK,
            -> NetworkHandoffDecision(
                action = NetworkHandoffAction.DEBOUNCE_RECONNECT,
                delayMs = NETWORK_HANDOFF_GRACE_MS,
            )
            RuntimeStatus.IDLE,
            RuntimeStatus.STOPPING,
            RuntimeStatus.FAILED,
            RuntimeStatus.STOPPED_BY_USER,
            -> NetworkHandoffDecision(NetworkHandoffAction.IGNORE)
        }
    }

    fun onAvailable(hasPendingHandoffReconnect: Boolean): NetworkHandoffDecision {
        if (!hasPendingHandoffReconnect) {
            return NetworkHandoffDecision(NetworkHandoffAction.IGNORE)
        }

        return NetworkHandoffDecision(NetworkHandoffAction.CANCEL_DEBOUNCE)
    }

    fun onGraceExpired(
        hasUsableUnderlyingNetwork: Boolean,
        stoppedByUser: Boolean,
    ): NetworkHandoffDecision {
        if (hasUsableUnderlyingNetwork || stoppedByUser) {
            return NetworkHandoffDecision(NetworkHandoffAction.IGNORE)
        }

        return NetworkHandoffDecision(NetworkHandoffAction.RECONNECT_NOW)
    }

    /**
     * Detection-only signal from a NOT_VPN network monitor. registerDefaultNetworkCallback does NOT
     * report the underlying network's onLost while the VPN tun is up (the tun masks the default), so
     * losing wifi goes unseen and the UI stays "Connected". A dedicated monitor counts usable
     * UNDERLYING (non-VPN) internet networks; when that count hits ZERO on an active session the tunnel
     * has lost its carrier → debounced reconnect (→ DEGRADED). Any underlying network present cancels a
     * pending handoff. This never drives setUnderlyingNetworks, so the dual-SIM flapping fix stands.
     */
    fun onUnderlyingNetworksChanged(
        underlyingCount: Int,
        stoppedByUser: Boolean,
        currentStatus: RuntimeStatus,
    ): NetworkHandoffDecision {
        if (stoppedByUser) return NetworkHandoffDecision(NetworkHandoffAction.IGNORE)
        if (underlyingCount > 0) return NetworkHandoffDecision(NetworkHandoffAction.CANCEL_DEBOUNCE)
        return when (currentStatus) {
            RuntimeStatus.STARTING,
            RuntimeStatus.RUNNING,
            RuntimeStatus.RECONNECTING,
            RuntimeStatus.DEGRADED,
            RuntimeStatus.NO_NETWORK,
            -> NetworkHandoffDecision(NetworkHandoffAction.DEBOUNCE_RECONNECT, NETWORK_HANDOFF_GRACE_MS)
            RuntimeStatus.IDLE,
            RuntimeStatus.STOPPING,
            RuntimeStatus.FAILED,
            RuntimeStatus.STOPPED_BY_USER,
            -> NetworkHandoffDecision(NetworkHandoffAction.IGNORE)
        }
    }
}
