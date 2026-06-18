package com.swimvpn.desktop.vpn

import java.io.File

/**
 * Runs the bundled `xray.exe` as a child process. The binary + geo assets ship under
 * resources/bin and are extracted once to %LOCALAPPDATA%\SWIMVPN\bin on first run.
 */
class XrayProcess {
    private var process: Process? = null

    private val appDir: File =
        File(System.getenv("LOCALAPPDATA") ?: System.getProperty("java.io.tmpdir"), "SWIMVPN")
    private val binDir = File(appDir, "bin").apply { mkdirs() }
    private val xrayExe = File(binDir, "xray.exe")
    private val configFile = File(appDir, "config.json")

    /** Copies a bundled resource to [target] if not already present. Returns true if available. */
    private fun ensureResource(resource: String, target: File): Boolean {
        if (target.exists() && target.length() > 0) return true
        val stream = javaClass.classLoader.getResourceAsStream(resource) ?: return false
        stream.use { input -> target.outputStream().use { input.copyTo(it) } }
        return target.exists() && target.length() > 0
    }

    /** True when xray.exe is bundled/extracted and ready to launch. */
    fun isBinaryAvailable(): Boolean = ensureResource("bin/xray.exe", xrayExe)

    fun start(configJson: String) {
        check(isBinaryAvailable()) {
            "xray.exe is not bundled yet (expected resources/bin/xray.exe). Add the Xray-core " +
                "Windows binary to windows/src/main/resources/bin/ to enable connecting."
        }
        ensureResource("bin/geoip.dat", File(binDir, "geoip.dat"))
        ensureResource("bin/geosite.dat", File(binDir, "geosite.dat"))
        configFile.writeText(configJson)
        process = ProcessBuilder(xrayExe.absolutePath, "run", "-config", configFile.absolutePath)
            .directory(binDir)
            .redirectErrorStream(true)
            .redirectOutput(File(appDir, "xray.log"))
            .start()
    }

    fun isAlive(): Boolean = process?.isAlive == true

    fun stop() {
        process?.let { p ->
            p.destroy()
            if (!p.waitFor(3, java.util.concurrent.TimeUnit.SECONDS)) p.destroyForcibly()
        }
        process = null
    }
}
