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
 * et le service VPN Android qui tourne en arrière-plan.
 */
object VpnManager {
    private val _state = MutableStateFlow(VpnState.DISCONNECTED)
    val state: StateFlow<VpnState> = _state.asStateFlow()

    private val _errorMessage = MutableStateFlow<String?>(null)
    val errorMessage: StateFlow<String?> = _errorMessage.asStateFlow()

    // Data usage tracking (in bytes)
    private val _bytesIn = MutableStateFlow(0L)
    val bytesIn: StateFlow<Long> = _bytesIn.asStateFlow()

    private val _bytesOut = MutableStateFlow(0L)
    val bytesOut: StateFlow<Long> = _bytesOut.asStateFlow()

    fun updateState(newState: VpnState) {
        _state.value = newState
        if (newState != VpnState.ERROR) {
            _errorMessage.value = null
        }
        if (newState == VpnState.DISCONNECTED) {
            // Optional: reset usage on disconnect? 
            // Better to keep it for the session or until synced with backend.
        }
    }

    fun updateUsage(downloaded: Long, uploaded: Long) {
        _bytesIn.value += downloaded
        _bytesOut.value += uploaded
    }

    fun resetUsage() {
        _bytesIn.value = 0L
        _bytesOut.value = 0L
    }

    fun setError(message: String) {
        _errorMessage.value = message
        _state.value = VpnState.ERROR
    }
}
