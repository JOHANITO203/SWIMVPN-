package com.swimvpn.app.vpn

import kotlinx.coroutines.CancellationException

data class RuntimeStartupFailureDecision(
    val shouldReportFailure: Boolean,
    val cause: DisconnectCause,
)

object RuntimeStartupFailurePolicy {
    fun classify(error: Throwable): RuntimeStartupFailureDecision {
        if (error is CancellationException) {
            return RuntimeStartupFailureDecision(
                shouldReportFailure = false,
                cause = DisconnectCause.UNKNOWN,
            )
        }

        val message = error.message.orEmpty()
        val cause = when {
            message.contains("config", ignoreCase = true) -> DisconnectCause.CONFIG_INVALID
            message.contains("xray", ignoreCase = true) -> DisconnectCause.ENGINE_CRASH
            message.contains("tun2socks", ignoreCase = true) -> DisconnectCause.ENGINE_CRASH
            message.contains("runtime", ignoreCase = true) -> DisconnectCause.ENGINE_CRASH
            else -> DisconnectCause.UNKNOWN
        }

        return RuntimeStartupFailureDecision(
            shouldReportFailure = true,
            cause = cause,
        )
    }
}
