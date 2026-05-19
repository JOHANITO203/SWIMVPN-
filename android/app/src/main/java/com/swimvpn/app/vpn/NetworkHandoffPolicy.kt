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
}
