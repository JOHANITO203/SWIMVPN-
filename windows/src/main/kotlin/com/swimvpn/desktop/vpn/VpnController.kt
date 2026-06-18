package com.swimvpn.desktop.vpn

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.net.InetSocketAddress
import java.net.Proxy
import java.net.Socket

/**
 * Orchestrates the Windows VPN lifecycle, mirroring the Android engine:
 * start xray.exe → bring up the data path (TUN or system proxy) → prove traffic → CONNECTED,
 * then a liveness SENTINEL keeps watch and auto-recovers (reconnect with backoff, then server
 * failover) — the desktop analog of TunnelHealthSentinel + the adaptive switch.
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

    /** Server failover: when the current server is exhausted, returns the NEXT config link to try
     *  (or null to give up). Wired by AppController to rotate through the imported list. */
    var onExhausted: (() -> String?)? = null

    private val xray = XrayProcess()
    private val tunnel = WintunTunnel()

    private var lastLink: String? = null
    private var manualStop = false
    private var sentinelJob: Job? = null
    private var recoveryAttempts = 0
    private val backoffMs = longArrayOf(2_000, 5_000, 10_000)

    fun isBinaryAvailable(): Boolean = xray.isBinaryAvailable()
    fun isElevated(): Boolean = tunnel.isElevated()

    fun connect(link: String) {
        if (state == VpnState.CONNECTING || state == VpnState.CONNECTED) return
        manualStop = false
        recoveryAttempts = 0
        lastLink = link
        scope.launch { doConnect(link) }
    }

    fun disconnect() {
        manualStop = true
        sentinelJob?.cancel()
        scope.launch {
            withContext(Dispatchers.IO) { teardown() }
            state = VpnState.DISCONNECTED
            statusDetail = ""
        }
    }

    private suspend fun doConnect(link: String) {
        sentinelJob?.cancel()
        state = VpnState.CONNECTING
        statusDetail = "Démarrage du moteur…"
        try {
            val built = EngineConfig.build(link, fullTunnel)
            activeLabel = built.label
            withContext(Dispatchers.IO) { teardown() } // clean any prior data path before re-arming
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
            if (ok && xray.isAlive() && (!fullTunnel || tunnel.isAlive())) {
                state = VpnState.CONNECTED
                statusDetail = built.label
                recoveryAttempts = 0
                startSentinel()
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

    // --- Liveness sentinel + auto-recovery -------------------------------------------------------
    private fun startSentinel() {
        sentinelJob?.cancel()
        sentinelJob = scope.launch {
            var fails = 0
            while (state == VpnState.CONNECTED && !manualStop) {
                delay(10_000)
                if (state != VpnState.CONNECTED || manualStop) break
                val healthy = withContext(Dispatchers.IO) { tunnelHealthy() }
                if (healthy) {
                    fails = 0
                } else {
                    fails++
                    if (fails >= 2) { recover(); break }
                }
            }
        }
    }

    private suspend fun tunnelHealthy(): Boolean {
        if (!xray.isAlive() || (fullTunnel && !tunnel.isAlive())) return false
        // Real reachability through the tunnel (Cloudflare 1.1.1.1:443) — proves carried traffic.
        return runCatching {
            Socket(Proxy(Proxy.Type.SOCKS, InetSocketAddress("127.0.0.1", LocalPorts.SOCKS))).use {
                it.connect(InetSocketAddress("1.1.1.1", 443), 4_000)
            }
        }.isSuccess
    }

    private suspend fun recover() {
        if (manualStop) return
        recoveryAttempts++
        // After exhausting same-server reconnects, fail over to the next server (if any).
        if (recoveryAttempts > backoffMs.size) {
            val next = onExhausted?.invoke()
            if (next != null) {
                statusDetail = "Bascule de serveur…"
                recoveryAttempts = 0
                lastLink = next
                doConnect(next)
            } else {
                fail("Connexion perdue — aucun autre serveur")
            }
            return
        }
        state = VpnState.CONNECTING
        statusDetail = "Reconnexion… ($recoveryAttempts)"
        withContext(Dispatchers.IO) { teardown() }
        delay(backoffMs[(recoveryAttempts - 1).coerceIn(0, backoffMs.size - 1)])
        if (manualStop) return
        lastLink?.let { doConnect(it) }
    }

    /** Startup traffic proof — short SOCKS-tunneled TCP connect to the server. */
    private suspend fun probeTraffic(host: String, port: Int): Boolean {
        val proxy = Proxy(Proxy.Type.SOCKS, InetSocketAddress("127.0.0.1", LocalPorts.SOCKS))
        repeat(5) { attempt ->
            val ok = runCatching {
                Socket(proxy).use { it.connect(InetSocketAddress(host, port), 2_800) }
            }.isSuccess
            if (ok) return true
            if (attempt < 4) delay(600)
        }
        return false
    }
}
