package com.swimvpn.desktop.vpn

import java.net.InetSocketAddress
import java.net.Socket

/** Rough server latency: time to open a TCP connection to host:port (ms), or null if unreachable. */
object LatencyProbe {
    fun ping(host: String, port: Int, timeoutMs: Int = 3000): Int? = runCatching {
        val start = System.nanoTime()
        Socket().use { it.connect(InetSocketAddress(host, port), timeoutMs) }
        ((System.nanoTime() - start) / 1_000_000).toInt()
    }.getOrNull()
}
