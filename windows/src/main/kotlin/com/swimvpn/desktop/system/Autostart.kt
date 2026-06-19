package com.swimvpn.desktop.system

/**
 * Launch-at-sign-in. Prefers an ELEVATED logon Scheduled Task (`/rl highest`) so the app starts with
 * admin rights at login — no UAC prompt, and TUN mode works straight away (the pragmatic core of a
 * Windows service). Creating that task needs admin; if it fails (not elevated), falls back to the
 * per-user HKCU Run key (non-elevated). The launcher path comes from jpackage's `jpackage.app-path`
 * (the installed SWIMVPN.exe); absent in dev builds → safe no-op.
 */
object Autostart {
    private const val RUN_KEY = "HKCU\\Software\\Microsoft\\Windows\\CurrentVersion\\Run"
    private const val VALUE = "SWIMVPN"
    private const val TASK = "SWIMVPN-Autostart"

    private fun launcherPath(): String? = System.getProperty("jpackage.app-path")?.takeIf { it.isNotBlank() }

    fun set(enabled: Boolean) {
        val path = launcherPath() ?: return
        if (enabled) {
            val taskOk = runCatching {
                run("schtasks", "/create", "/tn", TASK, "/tr", "\"$path\"", "/sc", "onlogon", "/rl", "highest", "/f") == 0
            }.getOrDefault(false)
            // Keep exactly one mechanism: elevated task if it took, else the non-elevated Run fallback.
            if (taskOk) regDelete() else regAdd(path)
        } else {
            runCatching { run("schtasks", "/delete", "/tn", TASK, "/f") }
            regDelete()
        }
    }

    private fun regAdd(path: String) =
        runCatching { run("reg", "add", RUN_KEY, "/v", VALUE, "/t", "REG_SZ", "/d", path, "/f") }
    private fun regDelete() = runCatching { run("reg", "delete", RUN_KEY, "/v", VALUE, "/f") }

    private fun run(vararg args: String): Int =
        ProcessBuilder(*args).redirectErrorStream(true).start().waitFor()
}
