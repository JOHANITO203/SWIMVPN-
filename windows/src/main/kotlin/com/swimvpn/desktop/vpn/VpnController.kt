package com.swimvpn.desktop.vpn

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.swimvpn.desktop.system.KillSwitch
import kotlin.random.Random
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.net.InetSocketAddress
import java.net.Proxy
import java.net.Socket

/** One auto-heal attempt: which server link to dial, with which camouflage fingerprint. */
data class HealAttempt(val link: String, val fingerprint: String?)

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
    /** Kill-switch (TUN mode): block all non-tunnel traffic so a tunnel drop can't leak. Off default. */
    var killSwitch by mutableStateOf(false)
    /** Anti-DPI: fragment the TLS ClientHello (defeats SNI-based DPI). Off by default. */
    var tlsFragment: Boolean = false

    /** Toggle the kill-switch at runtime: engage now if we're already connected via TUN, else lift it. */
    fun applyKillSwitch(enabled: Boolean) {
        killSwitch = enabled
        scope.launch(Dispatchers.IO) {
            if (enabled && state == VpnState.CONNECTED && fullTunnel) KillSwitch.engage(tunnel.serverIp())
            else if (!enabled) KillSwitch.disengage()
        }
    }

    // Live session traffic (TUN mode) + uptime.
    var bytesIn by mutableStateOf(0L); private set
    var bytesOut by mutableStateOf(0L); private set
    var downBps by mutableStateOf(0L); private set
    var upBps by mutableStateOf(0L); private set
    var connectedSinceMs by mutableStateOf(0L); private set

    /** Server failover: when the current server is exhausted, returns the NEXT config link to try
     *  (or null to give up). Wired by AppController to rotate through the imported list. */
    var onExhausted: (() -> String?)? = null

    // --- Adaptive camouflage (gated by aiOn; OFF ⇒ behaviour identical to before) ---------------
    /** When true, recovery delegates to [planNextAttempt] (cycle camouflage profile, then server). */
    var aiOn: Boolean = false
    /** uTLS fingerprint used for the current connect (null = AUTO / link default). */
    var currentFingerprint: String? = null
        private set
    /** AI heal: returns the next attempt (link + fingerprint) to try, or null to give up. */
    var planNextAttempt: (() -> HealAttempt?)? = null
    /** Called on CONNECTED so the agent can record the working (server, profile). */
    var onConnectedOk: (() -> Unit)? = null

    private val xray = XrayProcess()
    private val tunnel = WintunTunnel()

    private var lastLink: String? = null
    private var manualStop = false
    private var sentinelJob: Job? = null
    private var statsJob: Job? = null
    private var recoveryAttempts = 0
    private val backoffMs = longArrayOf(2_000, 5_000, 10_000)

    fun isBinaryAvailable(): Boolean = xray.isBinaryAvailable()
    fun isElevated(): Boolean = tunnel.isElevated()

    fun connect(link: String, fingerprint: String? = null) {
        if (state == VpnState.CONNECTING || state == VpnState.CONNECTED) return
        manualStop = false
        recoveryAttempts = 0
        lastLink = link
        currentFingerprint = fingerprint
        scope.launch { doConnect(link) }
    }

    fun disconnect() {
        manualStop = true
        sentinelJob?.cancel()
        scope.launch {
            withContext(Dispatchers.IO) { teardown() }
            // User-initiated stop → always lift the kill-switch (restore normal internet).
            withContext(Dispatchers.IO) { KillSwitch.disengage() }
            state = VpnState.DISCONNECTED
            statusDetail = ""
        }
    }

    private suspend fun doConnect(link: String) {
        sentinelJob?.cancel()
        state = VpnState.CONNECTING
        statusDetail = "Démarrage du moteur…"
        try {
            val built = EngineConfig.build(link, fullTunnel, currentFingerprint, tlsFragment)
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
                connectedSinceMs = System.currentTimeMillis()
                bytesIn = 0; bytesOut = 0; downBps = 0; upBps = 0
                // Kill-switch (TUN only): (re)arm with the current server allowed; held through any
                // later drop so non-tunnel traffic stays blocked until reconnect or user disconnect.
                if (fullTunnel && killSwitch) withContext(Dispatchers.IO) { KillSwitch.engage(tunnel.serverIp()) }
                onConnectedOk?.invoke() // agent records the working (server, camouflage profile)
                startSentinel()
                startStats()
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
            if (aiOn && !manualStop && planNextAttempt != null) healOnce()
            else { state = VpnState.ERROR; statusDetail = reason }
        }
    }

    /** One AI heal step: ask the agent for the next (server, camouflage) attempt and try it. The
     *  attempt counter hard-caps the whole cascade so it can never loop forever. */
    private suspend fun healOnce() {
        if (manualStop) return
        recoveryAttempts++
        if (recoveryAttempts > 15) { state = VpnState.ERROR; statusDetail = "Échec après plusieurs tentatives"; return }
        val next = planNextAttempt?.invoke()
        if (next == null) { state = VpnState.ERROR; statusDetail = "Connexion perdue — options épuisées"; return }
        state = VpnState.CONNECTING
        statusDetail = "Optimisation (camouflage/serveur)…"
        delay(backoffMs[(recoveryAttempts - 1).coerceIn(0, backoffMs.size - 1)] + Random.nextLong(0, 1200))
        if (manualStop) return
        currentFingerprint = next.fingerprint
        lastLink = next.link
        doConnect(next.link)
    }

    /** Restore everything we touched — order-independent, best-effort. */
    private fun teardown() {
        statsJob?.cancel()
        runCatching { tunnel.stop() }
        runCatching { SystemProxy.disable() }
        runCatching { xray.stop() }
    }

    /** Polls the WinTUN adapter byte counters (TUN mode) → session totals + live speed. */
    private fun startStats() {
        statsJob?.cancel()
        if (!fullTunnel) return
        statsJob = scope.launch {
            var base: Pair<Long, Long>? = null
            var prev: Pair<Long, Long>? = null
            var prevT = System.currentTimeMillis()
            while (state == VpnState.CONNECTED && !manualStop) {
                val cur = withContext(Dispatchers.IO) { TrafficStats.adapterBytes("swimvpn") }
                if (cur != null) {
                    if (base == null) { base = cur; prev = cur; prevT = System.currentTimeMillis() }
                    bytesIn = (cur.first - base!!.first).coerceAtLeast(0)
                    bytesOut = (cur.second - base!!.second).coerceAtLeast(0)
                    val now = System.currentTimeMillis()
                    val dt = ((now - prevT) / 1000.0).coerceAtLeast(0.5)
                    prev?.let {
                        downBps = ((cur.first - it.first) / dt).toLong().coerceAtLeast(0)
                        upBps = ((cur.second - it.second) / dt).toLong().coerceAtLeast(0)
                    }
                    prev = cur; prevT = now
                }
                delay(2_000)
            }
        }
    }

    // --- Liveness sentinel + auto-recovery -------------------------------------------------------
    private fun startSentinel() {
        sentinelJob?.cancel()
        sentinelJob = scope.launch {
            var softFails = 0
            while (state == VpnState.CONNECTED && !manualStop) {
                delay(12_000)
                if (state != VpnState.CONNECTED || manualStop) break
                // A dead engine/adapter = real failure → recover immediately.
                if (!xray.isAlive() || (fullTunnel && !tunnel.isAlive())) { recover(); break }
                // Otherwise only a SUSTAINED loss of external reachability triggers recovery — a
                // single probe blip never tears down a working tunnel (that was the false-disconnect).
                val reachable = withContext(Dispatchers.IO) { probeExternal() }
                if (reachable) {
                    softFails = 0
                } else {
                    softFails++
                    if (softFails >= 4) { recover(); break } // ~48s of no reachability
                }
            }
        }
    }

    /** Real reachability through the tunnel (Cloudflare 1.1.1.1:443) — generous timeout + one retry. */
    private suspend fun probeExternal(): Boolean {
        repeat(2) { attempt ->
            val ok = runCatching {
                Socket(Proxy(Proxy.Type.SOCKS, InetSocketAddress("127.0.0.1", LocalPorts.SOCKS))).use {
                    it.connect(InetSocketAddress("1.1.1.1", 443), 6_000)
                }
            }.isSuccess
            if (ok) return true
            if (attempt == 0) delay(1_000)
        }
        return false
    }

    private suspend fun recover() {
        if (manualStop) return
        // AI on → delegate recovery to the heal cascade (camouflage profiles, then server failover).
        if (aiOn && planNextAttempt != null) { healOnce(); return }
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
        delay(backoffMs[(recoveryAttempts - 1).coerceIn(0, backoffMs.size - 1)] + Random.nextLong(0, 1200))
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
