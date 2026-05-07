package com.swimvpn.app.vpn

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

enum class VpnState {
    DISCONNECTED,
    CONNECTING,
    CONNECTED,
    DISCONNECTING,
    ERROR
}

/**
 * Singleton servant de pont entre l'interface utilisateur (Compose/ViewModel)
 * et le service VPN Android qui tourne en arriere-plan.
 */
object VpnManager {
    private val _state = MutableStateFlow(VpnState.DISCONNECTED)
    val state: StateFlow<VpnState> = _state.asStateFlow()

    private val _runtimeMode = MutableStateFlow(RuntimeMode.FULL_TUNNEL)
    val runtimeMode: StateFlow<RuntimeMode> = _runtimeMode.asStateFlow()

    private val _runtimeStatus = MutableStateFlow(RuntimeStatus.IDLE)
    val runtimeStatus: StateFlow<RuntimeStatus> = _runtimeStatus.asStateFlow()

    private val _errorMessage = MutableStateFlow<String?>(null)
    val errorMessage: StateFlow<String?> = _errorMessage.asStateFlow()

    private val _metrics = MutableStateFlow(RuntimeMetrics())
    val metrics: StateFlow<RuntimeMetrics> = _metrics.asStateFlow()

    private val _bytesIn = MutableStateFlow(0L)
    val bytesIn: StateFlow<Long> = _bytesIn.asStateFlow()

    private val _bytesOut = MutableStateFlow(0L)
    val bytesOut: StateFlow<Long> = _bytesOut.asStateFlow()

    fun setRuntimeMode(mode: RuntimeMode) {
        _runtimeMode.value = mode
    }

    fun updateRuntimeStatus(newStatus: RuntimeStatus) {
        _runtimeStatus.value = newStatus
        _state.value = when (newStatus) {
            RuntimeStatus.IDLE -> VpnState.DISCONNECTED
            RuntimeStatus.STARTING -> VpnState.CONNECTING
            RuntimeStatus.RUNNING -> VpnState.CONNECTED
            RuntimeStatus.RECONNECTING -> VpnState.CONNECTING
            RuntimeStatus.DEGRADED -> VpnState.CONNECTED
            RuntimeStatus.STOPPING -> VpnState.DISCONNECTING
            RuntimeStatus.FAILED -> VpnState.ERROR
            RuntimeStatus.STOPPED_BY_USER -> VpnState.DISCONNECTED
        }
        if (newStatus == RuntimeStatus.STARTING ||
            newStatus == RuntimeStatus.RUNNING ||
            newStatus == RuntimeStatus.RECONNECTING ||
            newStatus == RuntimeStatus.DEGRADED ||
            newStatus == RuntimeStatus.STOPPING ||
            newStatus == RuntimeStatus.STOPPED_BY_USER
        ) {
            _errorMessage.value = null
        }
    }

    fun reconcileRuntimeSnapshot(snapshot: RuntimeStateSnapshot) {
        val effectiveStatus = if (snapshot.isFresh()) {
            snapshot.status
        } else {
            RuntimeStatus.IDLE
        }
        val expectedState = stateForRuntimeStatus(effectiveStatus)
        _runtimeMode.value = snapshot.mode
        if (_runtimeStatus.value != effectiveStatus || _state.value != expectedState) {
            updateRuntimeStatus(effectiveStatus)
        }
        if (effectiveStatus == RuntimeStatus.FAILED && !snapshot.error.isNullOrBlank()) {
            _errorMessage.value = snapshot.error
            _metrics.value = _metrics.value.copy(lastError = snapshot.error)
        }
    }

    private fun stateForRuntimeStatus(status: RuntimeStatus): VpnState {
        return when (status) {
            RuntimeStatus.IDLE -> VpnState.DISCONNECTED
            RuntimeStatus.STARTING -> VpnState.CONNECTING
            RuntimeStatus.RUNNING -> VpnState.CONNECTED
            RuntimeStatus.RECONNECTING -> VpnState.CONNECTING
            RuntimeStatus.DEGRADED -> VpnState.CONNECTED
            RuntimeStatus.STOPPING -> VpnState.DISCONNECTING
            RuntimeStatus.FAILED -> VpnState.ERROR
            RuntimeStatus.STOPPED_BY_USER -> VpnState.DISCONNECTED
        }
    }

    fun updateState(newState: VpnState) {
        _state.value = newState
        if (newState != VpnState.ERROR) {
            _errorMessage.value = null
        }
        _runtimeStatus.value = when (newState) {
            VpnState.DISCONNECTED -> RuntimeStatus.IDLE
            VpnState.CONNECTING -> RuntimeStatus.STARTING
            VpnState.CONNECTED -> RuntimeStatus.RUNNING
            VpnState.DISCONNECTING -> RuntimeStatus.STOPPING
            VpnState.ERROR -> RuntimeStatus.FAILED
        }
    }

    fun markStarted(startedAt: Long = System.currentTimeMillis()) {
        _metrics.value = _metrics.value.copy(startedAt = startedAt)
    }

    fun markHandshake(handshakeAt: Long = System.currentTimeMillis()) {
        _metrics.value = _metrics.value.copy(lastHandshakeAt = handshakeAt)
    }

    fun updateUsage(downloaded: Long, uploaded: Long) {
        _bytesIn.value += downloaded
        _bytesOut.value += uploaded
        _metrics.value = _metrics.value.copy(
            bytesIn = _bytesIn.value,
            bytesOut = _bytesOut.value,
        )
    }

    fun resetUsage() {
        _bytesIn.value = 0L
        _bytesOut.value = 0L
        _metrics.value = _metrics.value.copy(bytesIn = 0L, bytesOut = 0L)
    }

    fun setError(message: String) {
        _errorMessage.value = message
        _metrics.value = _metrics.value.copy(lastError = message)
        updateRuntimeStatus(RuntimeStatus.FAILED)
    }

    fun clearError() {
        _errorMessage.value = null
        _metrics.value = _metrics.value.copy(lastError = null)
    }

    fun setRuntimeDiagnostics(
        activeMode: String? = null,
        xraySessionId: String? = null,
        xrayLogPath: String? = null,
            tun2SocksSessionId: String? = null,
            tun2SocksLogPath: String? = null,
            lastDisconnectCause: DisconnectCause? = null,
            reconnectCount: Int? = null,
            sessionStartedAt: Long? = null,
    ) {
        _metrics.value = _metrics.value.copy(
            activeMode = activeMode,
            xraySessionId = xraySessionId,
            xrayLogPath = xrayLogPath,
            tun2SocksSessionId = tun2SocksSessionId,
            tun2SocksLogPath = tun2SocksLogPath,
            lastDisconnectCause = lastDisconnectCause ?: _metrics.value.lastDisconnectCause,
            reconnectCount = reconnectCount ?: _metrics.value.reconnectCount,
            sessionStartedAt = sessionStartedAt ?: _metrics.value.sessionStartedAt,
        )
    }

    fun clearRuntimeDiagnostics() {
        _metrics.value = _metrics.value.copy(
            activeMode = null,
            xraySessionId = null,
            xrayLogPath = null,
            tun2SocksSessionId = null,
            tun2SocksLogPath = null,
        )
    }
}
