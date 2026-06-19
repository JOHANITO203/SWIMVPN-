package com.swimvpn.desktop.system

/**
 * Launch-at-sign-in via the per-user Run registry key (HKCU — no admin needed). The launcher path
 * comes from jpackage's `jpackage.app-path` system property (the installed SWIMVPN.exe); when run
 * from a dev build that property is absent, so this is a safe no-op there.
 */
object Autostart {
    private const val RUN_KEY = "HKCU\\Software\\Microsoft\\Windows\\CurrentVersion\\Run"
    private const val VALUE = "SWIMVPN"

    private fun launcherPath(): String? = System.getProperty("jpackage.app-path")?.takeIf { it.isNotBlank() }

    fun set(enabled: Boolean) {
        val path = launcherPath() ?: return
        runCatching {
            val cmd = if (enabled) {
                listOf("reg", "add", RUN_KEY, "/v", VALUE, "/t", "REG_SZ", "/d", path, "/f")
            } else {
                listOf("reg", "delete", RUN_KEY, "/v", VALUE, "/f")
            }
            ProcessBuilder(cmd).redirectErrorStream(true).start().waitFor()
        }
    }
}
