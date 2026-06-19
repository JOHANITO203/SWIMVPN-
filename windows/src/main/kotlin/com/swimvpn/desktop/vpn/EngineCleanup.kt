package com.swimvpn.desktop.vpn

import java.util.concurrent.TimeUnit

/**
 * Kills orphaned SWIMVPN engine processes (xray.exe / tun2socks.exe) and removes the split-default
 * routes they leave behind. Orphans happen when the JVM dies WITHOUT teardown (force-quit, crash,
 * Task-Manager kill): the engine keeps running and the WinTUN adapter stays "Up", so the next
 * tun2socks gets the name "swimvpn 1" while routing still targets the stale "swimvpn" → the tunnel
 * looks up but passes no traffic → recovery loops → ERROR. Purging at launch + before each connect
 * makes every connection start from a clean "swimvpn" adapter (killing tun2socks releases it).
 *
 * CRITICAL: only OUR engine is touched — processes are filtered by executable path under \SWIMVPN\,
 * and routes by our TUN gateway — so other Xray clients on the machine (e.g. Happ) are never killed.
 */
object EngineCleanup {
    private const val TUN_GW = "10.27.0.1"

    /** @param includeXray also kill stray xray — safe ONLY at app launch (our xray isn't up yet).
     *  During a live reconnect pass false: our xray is already running. */
    fun killStray(includeXray: Boolean) {
        val images = if (includeXray) "'tun2socks','xray'" else "'tun2socks'"
        val ps = "Get-Process $images -ErrorAction SilentlyContinue | " +
            "Where-Object { \$_.Path -like '*\\SWIMVPN\\*' } | Stop-Process -Force -ErrorAction SilentlyContinue"
        runCatching {
            ProcessBuilder("powershell", "-NoProfile", "-Command", ps).redirectErrorStream(true)
                .start().waitFor(8, TimeUnit.SECONDS)
        }
        runCatching { routeDelete("0.0.0.0") }
        runCatching { routeDelete("128.0.0.0") }
    }

    private fun routeDelete(dest: String) {
        ProcessBuilder("route", "delete", dest, "mask", "128.0.0.0", TUN_GW)
            .redirectErrorStream(true).start().waitFor(5, TimeUnit.SECONDS)
    }
}
