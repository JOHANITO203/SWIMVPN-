package com.swimvpn.app.tor

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.util.Log
import androidx.core.content.ContextCompat
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withTimeoutOrNull
import org.torproject.jni.TorService

/** Coarse bootstrap state of the embedded Tor client, derived from TorService status broadcasts. */
enum class TorBootstrap { OFF, STARTING, READY, STOPPING }

/**
 * Onion Stealth — lifecycle wrapper around guardianproject's [TorService].
 *
 * TorService owns the tor binary, its control socket, DataDirectory and CacheDirectory; we only supply
 * our app-owned torrc lines (see [TorRuntimeConfig]) by writing them into `TorService.getTorrc()` before
 * start, and observe readiness via the `ACTION_STATUS` broadcast. The pinned SOCKS port (9050) is what
 * the xray "tor" outbound dials, and `Socks5Proxy` routes Tor's guard connections back out through the
 * REALITY tunnel — so Tor is carried by REALITY and never seen by the ISP.
 *
 * Readiness gate: [awaitReady] blocks until Tor reports STATUS_ON (a circuit is established). Fine-grained
 * bootstrap percentage (via a bound control connection + `getInfo("status/bootstrap-phase")`) is a
 * follow-up; the STATUS_ON edge is a sufficient, reliable "ready" signal for the first integration.
 *
 * DEVICE-GATE: the SwimVpnService data-plane wiring that consumes this controller is not shipped yet —
 * it must first pass an on-device DNS/leak + exit proof (see docs/ONION_STEALTH_DEVICE_SPIKE.md).
 */
class TorController(private val appContext: Context) {

    private val _state = MutableStateFlow(TorBootstrap.OFF)
    val state: StateFlow<TorBootstrap> = _state.asStateFlow()

    @Volatile private var receiverRegistered = false

    private val statusReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            val next = when (intent.getStringExtra(TorService.EXTRA_STATUS)) {
                TorService.STATUS_ON -> TorBootstrap.READY
                TorService.STATUS_STARTING -> TorBootstrap.STARTING
                TorService.STATUS_STOPPING -> TorBootstrap.STOPPING
                else -> TorBootstrap.OFF
            }
            _state.value = next
        }
    }

    /** Write the app torrc, subscribe to status, and start the embedded Tor service. */
    fun start() {
        writeTorrc()
        registerReceiver()
        _state.value = TorBootstrap.STARTING
        val intent = Intent(appContext, TorService::class.java).setAction(TorService.ACTION_START)
        runCatching { ContextCompat.startForegroundService(appContext, intent) }
            .onFailure { Log.e(TAG, "Failed to start TorService", it) }
    }

    /** Suspend until Tor establishes a circuit (STATUS_ON) or the timeout elapses. */
    suspend fun awaitReady(timeoutMs: Long): Boolean =
        withTimeoutOrNull(timeoutMs) {
            state.first { it == TorBootstrap.READY }
            true
        } ?: false

    /** Stop the embedded Tor service and detach the status listener. */
    fun stop() {
        runCatching {
            val intent = Intent(appContext, TorService::class.java).setAction(TorService.ACTION_STOP)
            appContext.startService(intent)
        }.onFailure { Log.w(TAG, "Failed to stop TorService", it) }
        unregisterReceiver()
        _state.value = TorBootstrap.OFF
    }

    private fun writeTorrc() {
        runCatching { TorService.getTorrc(appContext).writeText(TorRuntimeConfig.torrc()) }
            .onFailure { Log.e(TAG, "Failed to write torrc", it) }
    }

    private fun registerReceiver() {
        if (receiverRegistered) return
        ContextCompat.registerReceiver(
            appContext,
            statusReceiver,
            IntentFilter(TorService.ACTION_STATUS),
            ContextCompat.RECEIVER_NOT_EXPORTED,
        )
        receiverRegistered = true
    }

    private fun unregisterReceiver() {
        if (!receiverRegistered) return
        runCatching { appContext.unregisterReceiver(statusReceiver) }
        receiverRegistered = false
    }

    private companion object {
        const val TAG = "TorController"
    }
}
