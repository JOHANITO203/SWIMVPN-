package com.swimvpn.desktop.vpn

import java.util.concurrent.TimeUnit

/**
 * Live traffic counters for the tunnel. In TUN mode all traffic flows through the WinTUN adapter,
 * so its RX/TX byte counters are the session usage — read via Get-NetAdapterStatistics.
 */
object TrafficStats {
    /** (receivedBytes, sentBytes) for the adapter, or null if unavailable (e.g. proxy mode). */
    fun adapterBytes(adapter: String): Pair<Long, Long>? = runCatching {
        val p = ProcessBuilder(
            "powershell.exe", "-NoProfile", "-NonInteractive", "-Command",
            "\$s=Get-NetAdapterStatistics -Name '$adapter' -ErrorAction SilentlyContinue; " +
                "if(\$s){ \"\$(\$s.ReceivedBytes) \$(\$s.SentBytes)\" }",
        ).redirectErrorStream(true).start()
        val out = p.inputStream.bufferedReader().readText().trim()
        p.waitFor(5, TimeUnit.SECONDS)
        val parts = out.split(Regex("\\s+")).filter { it.matches(Regex("\\d+")) }
        if (parts.size < 2) return null
        parts[0].toLong() to parts[1].toLong()
    }.getOrNull()

    /** Human-readable bytes (B / KB / MB / GB). */
    fun human(bytes: Long): String {
        if (bytes < 1024) return "$bytes o"
        val kb = bytes / 1024.0
        if (kb < 1024) return String.format("%.0f Ko", kb)
        val mb = kb / 1024.0
        if (mb < 1024) return String.format("%.1f Mo", mb)
        return String.format("%.2f Go", mb / 1024.0)
    }
}
