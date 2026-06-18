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
 * start xray.exe → set the system proxy → prove traffic (SOCKS probe) → CONNECTED.
 * Disconnect tears down in reverse and always restores the proxy.
 */
class VpnController(private val scope: CoroutineScope) {
    var state by mutableStateOf(VpnState.DISCONNECTED)
        private set
    var statusDetail by mutableStateOf("")
        private set
    var activeConfig by mutableStateOf<VlessConfig?>(null)
        private set

    private val xray = XrayProcess()

    fun isBinaryAvailable(): Boolean = xray.isBinaryAvailable()

    fun connect(vlessUrl: String) {
        if (state == VpnState.CONNECTING || state == VpnState.CONNECTED) return
        scope.launch {
            state = VpnState.CONNECTING
            statusDetail = "Démarrage du moteur…"
            try {
                val cfg = XrayConfig.parseVless(vlessUrl)
                activeConfig = cfg
                withContext(Dispatchers.IO) { xray.start(XrayConfig.render(cfg)) }
                statusDetail = "Application du proxy système…"
                withContext(Dispatchers.IO) { SystemProxy.enable() }
                statusDetail = "Vérification du tunnel…"
                val ok = withContext(Dispatchers.IO) { probeTraffic(cfg.host, cfg.port) }
                if (ok && xray.isAlive()) {
                    state = VpnState.CONNECTED
                    statusDetail = cfg.label
                } else {
                    fail(if (!xray.isAlive()) "Le moteur s'est arrêté" else "Aucun trafic à travers le tunnel")
                }
            } catch (e: Exception) {
                fail(e.message ?: "Échec de connexion")
            }
        }
    }

    fun disconnect() {
        scope.launch {
            withContext(Dispatchers.IO) {
                SystemProxy.disable()
                xray.stop()
            }
            state = VpnState.DISCONNECTED
            statusDetail = ""
        }
    }

    private fun fail(reason: String) {
        scope.launch {
            withContext(Dispatchers.IO) {
                SystemProxy.disable()
                xray.stop()
            }
            state = VpnState.ERROR
            statusDetail = reason
        }
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
