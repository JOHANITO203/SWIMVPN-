package com.swimvpn.desktop.system

/**
 * Relaunch the installed app elevated (UAC "runas") so TUN mode works without the user having to
 * right-click → Run as administrator. The full Windows-service model (run elevated always, survive
 * logoff, boot-connect) is a separate, larger effort; this is the safe immediate win.
 */
object Elevation {
    fun relaunchAsAdmin(): Boolean {
        val path = System.getProperty("jpackage.app-path")?.takeIf { it.isNotBlank() } ?: return false
        return runCatching {
            ProcessBuilder("powershell", "-NoProfile", "-Command", "Start-Process -FilePath '$path' -Verb RunAs").start()
            true
        }.getOrDefault(false)
    }
}
