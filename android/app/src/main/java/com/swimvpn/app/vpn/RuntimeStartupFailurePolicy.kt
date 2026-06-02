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
            message.contains("network", ignoreCase = true) -> DisconnectCause.NETWORK_LOST
            message.contains("config", ignoreCase = true) -> DisconnectCause.CONFIG_INVALID
            message.contains("permission", ignoreCase = true) -> DisconnectCause.SERVICE_KILLED
            message.contains("xray", ignoreCase = true) -> DisconnectCause.ENGINE_CRASH
            message.contains("tun2socks", ignoreCase = true) -> DisconnectCause.ENGINE_CRASH
            message.contains("runtime", ignoreCase = true) -> DisconnectCause.ENGINE_CRASH
            // Any other failure must still surface as a visible, reportable state
            // (a safe default cause) so nothing escapes as an unhandled re-throw.
            else -> DisconnectCause.UNKNOWN
        }

        // Every non-cancellation failure is reportable by contract.
        return RuntimeStartupFailureDecision(
            shouldReportFailure = true,
            cause = cause,
        )
    }
}
