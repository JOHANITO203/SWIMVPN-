package com.swimvpn.desktop.vpn

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.net.InetSocketAddress
import java.net.Proxy
import java.net.Socket

/**
 * Orchestrates the Windows VPN lifecycle, mirroring the Android engine's shape:
 * start xray.exe → bring up the data path → prove traffic (SOCKS probe) → CONNECTED.
 *
 * Two data paths:
 *  - [fullTunnel] = true (default, parity with Android): WinTUN adapter + tun2socks → ALL traffic.
 *    Requires Administrator.
 *  - false: Windows system proxy (WinINet) → proxy-aware apps only. No admin needed.
 *
 * Teardown always restores routing/proxy, even on failure.
 */
class VpnController(private val scope: CoroutineScope) {
    var state by mutableStateOf(VpnState.DISCONNECTED)
        private set
    var statusDetail by mutableStateOf("")
        private set
    var activeLabel by mutableStateOf<String?>(null)
        private set
    /** Full-traffic TUN mode (like Android). Defaults on; falls back to proxy when not elevated. */
    var fullTunnel by mutableStateOf(true)

    private val xray = XrayProcess()
    private val tunnel = WintunTunnel()

    fun isBinaryAvailable(): Boolean = xray.isBinaryAvailable()
    fun isElevated(): Boolean = tunnel.isElevated()

    fun connect(link: String) {
        if (state == VpnState.CONNECTING || state == VpnState.CONNECTED) return
        scope.launch {
            state = VpnState.CONNECTING
            statusDetail = "Démarrage du moteur…"
            try {
                val built = EngineConfig.build(link)
                activeLabel = built.label
                withContext(Dispatchers.IO) { xray.start(built.configJson) }

                if (fullTunnel) {
                    statusDetail = "Activation du tunnel (tout le trafic)…"
                    withContext(Dispatchers.IO) { tunnel.start(built.host) }
                } else {
                    statusDetail = "Application du proxy système…"
                    withContext(Dispatchers.IO) { SystemProxy.enable() }
                }

                statusDetail = "Vérification du tunnel…"
                val ok = withContext(Dispatchers.IO) { probeTraffic(built.host, built.port) }
                val dataPlaneAlive = !fullTunnel || tunnel.isAlive()
                if (ok && xray.isAlive() && dataPlaneAlive) {
                    state = VpnState.CONNECTED
                    statusDetail = built.label
                } else {
                    fail(
                        when {
                            !xray.isAlive() -> "Le moteur s'est arrêté"
                            fullTunnel && !tunnel.isAlive() -> "Le tunnel TUN s'est arrêté"
                            else -> "Aucun trafic à travers le tunnel"
                        }
                    )
                }
            } catch (e: Exception) {
                fail(e.message ?: "Échec de connexion")
            }
        }
    }

    fun disconnect() {
        scope.launch {
            withContext(Dispatchers.IO) { teardown() }
            state = VpnState.DISCONNECTED
            statusDetail = ""
        }
    }

    private fun fail(reason: String) {
        scope.launch {
            withContext(Dispatchers.IO) { teardown() }
            state = VpnState.ERROR
            statusDetail = reason
        }
    }

    /** Restore everything we touched — order-independent, best-effort. */
    private fun teardown() {
        runCatching { tunnel.stop() }
        runCatching { SystemProxy.disable() }
        runCatching { xray.stop() }
    }

    /** Proves the data plane carries traffic via a SOCKS-tunneled TCP connect to the server. */
    private suspend fun probeTraffic(host: String, port: Int): Boolean {
        val proxy = Proxy(Proxy.Type.SOCKS, InetSocketAddress("127.0.0.1", LocalPorts.SOCKS))
        repeat(5) { attempt ->
            val ok = runCatching {
                Socket(proxy).use { it.connect(InetSocketAddress(host, port), 2800) }
            }.isSuccess
            if (ok) return true
            if (attempt < 4) delay(600)
        }
        return false
    }
}
