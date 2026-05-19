package com.swimvpn.app.vpn

data class RuntimeServiceDestroyDecision(
    val status: RuntimeStatus,
    val cause: DisconnectCause,
)

object RuntimeServiceDestroyPolicy {
    fun recoveryDecision(
        currentStatus: RuntimeStatus,
        stoppedByUser: Boolean,
    ): RuntimeServiceDestroyDecision? {
        if (stoppedByUser) {
            return null
        }

        return when (currentStatus) {
            RuntimeStatus.STARTING,
            RuntimeStatus.RUNNING,
            RuntimeStatus.RECONNECTING,
            RuntimeStatus.DEGRADED,
            -> RuntimeServiceDestroyDecision(
                status = RuntimeStatus.RECONNECTING,
                cause = DisconnectCause.SERVICE_KILLED,
            )
            RuntimeStatus.IDLE,
            RuntimeStatus.STOPPING,
            RuntimeStatus.FAILED,
            RuntimeStatus.STOPPED_BY_USER,
            -> null
        }
    }
}
