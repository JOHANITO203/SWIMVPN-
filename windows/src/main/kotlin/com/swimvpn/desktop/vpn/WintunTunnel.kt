package com.swimvpn.desktop.vpn

import java.io.File
import java.net.InetAddress
import java.util.concurrent.TimeUnit

/**
 * Full-traffic TUN mode (parity with Android's VpnService + tun2socks): tun2socks bridges a WinTUN
 * virtual adapter to xray's local SOCKS, so ALL traffic is routed through the tunnel — not only
 * proxy-aware apps. Requires Administrator (adapter + routing).
 *
 * Safety: uses the WireGuard split-default trick (0.0.0.0/1 + 128.0.0.0/1) to OVERRIDE the default
 * route without deleting it, and a /32 bypass route for the server via the real gateway. If any step
 * fails, the original default route still exists; [stop] removes only what we added and restores DNS.
 */
class WintunTunnel {
    private var proc: Process? = null

    private val appDir = File(System.getenv("LOCALAPPDATA") ?: System.getProperty("java.io.tmpdir"), "SWIMVPN")
    private val binDir = File(appDir, "bin").apply { mkdirs() }
    private val t2s = File(binDir, "tun2socks.exe")
    private val wintun = File(binDir, "wintun.dll")

    private val adapter = "swimvpn"
    private val tunIp = "10.27.0.2"
    private val tunGw = "10.27.0.1"
    private val tunMask = "255.255.255.0"

    private var serverIp: String? = null
    private var realGateway: String? = null

    fun isAvailable(): Boolean = ensureBinaries()

    private fun ensureBinaries(): Boolean {
        copyRes("bin/tun2socks.exe", t2s)
        copyRes("bin/wintun.dll", wintun)
        return t2s.exists() && t2s.length() > 0 && wintun.exists() && wintun.length() > 0
    }

    private fun copyRes(res: String, target: File) {
        if (target.exists() && target.length() > 0) return
        javaClass.classLoader.getResourceAsStream(res)?.use { i -> target.outputStream().use { i.copyTo(it) } }
    }

    /** True when the process can create adapters / edit routes (TUN mode needs this). */
    fun isElevated(): Boolean = try {
        val p = ProcessBuilder("net", "session").redirectErrorStream(true).start()
        p.inputStream.readBytes(); p.waitFor(5, TimeUnit.SECONDS) && p.exitValue() == 0
    } catch (e: Exception) { false }

    fun start(serverHost: String) {
        check(ensureBinaries()) { "tun2socks.exe / wintun.dll absents (build avec fetchTunnel)" }
        check(isElevated()) { "Le mode TUN (tout le trafic) requiert de lancer SWIMVPN en administrateur." }

        serverIp = resolveIp(serverHost) ?: throw IllegalStateException("Résolution DNS impossible: $serverHost")
        realGateway = defaultGateway() ?: throw IllegalStateException("Passerelle par défaut introuvable")

        // Clean any orphaned tun2socks (ours only) + stale split routes so the new adapter always gets
        // the canonical name "swimvpn" (a lingering one forces "swimvpn 1" → routing misses → no traffic).
        EngineCleanup.killStray(includeXray = false)
        Thread.sleep(400) // let a released WinTUN adapter disappear before re-creating it

        // 1. Launch tun2socks — creates the WinTUN adapter "swimvpn" and bridges it to local SOCKS.
        proc = ProcessBuilder(
            t2s.absolutePath,
            "-device", "tun://$adapter",
            "-proxy", "socks5://127.0.0.1:${LocalPorts.SOCKS}",
            "-loglevel", "warning",
        ).directory(binDir).redirectErrorStream(true)
            .redirectOutput(File(appDir, "tun2socks.log")).start()

        if (!waitForAdapter(adapter, 10_000)) {
            stop(); throw IllegalStateException("L'adaptateur WinTUN n'est pas apparu")
        }

        // 2. Address the adapter.
        netsh("interface", "ip", "set", "address", "name=$adapter", "static", tunIp, tunMask, tunGw)
        // 3. Bypass route: reach the VPN server via the real gateway (avoids the tunnel loop).
        route("add", serverIp!!, "mask", "255.255.255.255", realGateway!!, "metric", "1")
        // 4. Split-default override (does NOT touch the existing 0.0.0.0/0 default route).
        route("add", "0.0.0.0", "mask", "128.0.0.0", tunGw, "metric", "1")
        route("add", "128.0.0.0", "mask", "128.0.0.0", tunGw, "metric", "1")
        // 5. DNS through the tunnel.
        netsh("interface", "ip", "set", "dns", "name=$adapter", "static", "1.1.1.1")
    }

    fun isAlive(): Boolean = proc?.isAlive == true

    /** The resolved VPN server IP for the active tunnel (kill-switch allow rule). */
    fun serverIp(): String? = serverIp

    /** Removes only what we added; the original default route was never deleted. Best-effort. */
    fun stop() {
        runCatching { serverIp?.let { route("delete", it) } }
        // Delete only OUR split-default routes (via our TUN gateway) — never another tunnel's (Happ).
        runCatching { route("delete", "0.0.0.0", "mask", "128.0.0.0", tunGw) }
        runCatching { route("delete", "128.0.0.0", "mask", "128.0.0.0", tunGw) }
        proc?.let { p ->
            p.destroy()
            if (!p.waitFor(3, TimeUnit.SECONDS)) p.destroyForcibly()
        }
        proc = null; serverIp = null; realGateway = null
    }

    // --- helpers -----------------------------------------------------------------------------
    private fun resolveIp(host: String): String? =
        if (host.matches(Regex("""\d{1,3}(\.\d{1,3}){3}"""))) host
        else runCatching { InetAddress.getByName(host).hostAddress }.getOrNull()

    private fun defaultGateway(): String? = runCatching {
        val p = ProcessBuilder(
            "powershell.exe", "-NoProfile", "-Command",
            "(Get-NetRoute -DestinationPrefix '0.0.0.0/0' -ErrorAction SilentlyContinue | " +
                "Sort-Object RouteMetric | Select-Object -First 1).NextHop",
        ).redirectErrorStream(true).start()
        val out = p.inputStream.readBytes().toString(Charsets.UTF_8).trim()
        p.waitFor(8, TimeUnit.SECONDS)
        out.lineSequence().map { it.trim() }.firstOrNull { it.matches(Regex("""\d{1,3}(\.\d{1,3}){3}""")) }
    }.getOrNull()

    private fun waitForAdapter(name: String, timeoutMs: Long): Boolean {
        val deadline = System.nanoTime() + timeoutMs * 1_000_000
        while (System.nanoTime() < deadline) {
            if (proc?.isAlive != true) return false
            val ok = runCatching {
                val p = ProcessBuilder(
                    "powershell.exe", "-NoProfile", "-Command",
                    "if (Get-NetAdapter -Name '$name' -ErrorAction SilentlyContinue) { 'yes' } else { 'no' }",
                ).redirectErrorStream(true).start()
                val out = p.inputStream.readBytes().toString(Charsets.UTF_8)
                p.waitFor(4, TimeUnit.SECONDS); out.contains("yes")
            }.getOrDefault(false)
            if (ok) return true
            Thread.sleep(400)
        }
        return false
    }

    private fun netsh(vararg args: String) = run("netsh", *args)
    private fun route(vararg args: String) = run("route", *args)
    private fun run(vararg args: String): Int = runCatching {
        val p = ProcessBuilder(*args).redirectErrorStream(true).start()
        p.inputStream.readBytes(); p.waitFor(10, TimeUnit.SECONDS); p.exitValue()
    }.getOrDefault(-1)
}
