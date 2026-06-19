package com.swimvpn.desktop.system

import com.google.gson.Gson
import com.google.gson.JsonObject
import java.io.File
import java.net.HttpURLConnection
import java.net.URI

/**
 * Self-update client. Fetches a small JSON manifest, compares the version to this build, and (if
 * newer) downloads the installer and launches it. The manifest + installer are hosted alongside the
 * landing — the SERVER side is the user's to publish:
 *   https://app.swimvpn.pro/windows/latest.json  →  {"version":"1.0.999","url":"https://…/SWIMVPN-1.0.999.exe"}
 * Everything is best-effort + non-blocking; an unreachable manifest is a silent no-op.
 */
object Updater {
    private const val MANIFEST_URL = "https://app.swimvpn.pro/windows/latest.json"

    data class Info(val current: String, val latest: String?, val url: String?, val newer: Boolean, val error: String? = null)

    fun check(): Info {
        val cur = BuildInfo.version
        return runCatching {
            val conn = (URI(MANIFEST_URL).toURL().openConnection() as HttpURLConnection).apply {
                connectTimeout = 8_000; readTimeout = 8_000
                instanceFollowRedirects = true
                setRequestProperty("User-Agent", "SWIMVPN-Windows/$cur")
            }
            val body = conn.inputStream.bufferedReader().use { it.readText() }
            val obj = Gson().fromJson(body, JsonObject::class.java)
            val latest = obj.get("version")?.takeIf { it.isJsonPrimitive }?.asString
            val url = obj.get("url")?.takeIf { it.isJsonPrimitive }?.asString
            Info(cur, latest, url, newer = isNewer(latest, cur))
        }.getOrElse { Info(cur, null, null, false, it.message) }
    }

    /** Download the installer to %TEMP% and launch it (it self-elevates). Returns false on failure. */
    fun downloadAndLaunch(url: String): Boolean = runCatching {
        val tmp = File(System.getProperty("java.io.tmpdir"), "SWIMVPN-update.exe")
        val conn = (URI(url).toURL().openConnection() as HttpURLConnection).apply {
            connectTimeout = 15_000; readTimeout = 60_000; instanceFollowRedirects = true
        }
        conn.inputStream.use { input -> tmp.outputStream().use { input.copyTo(it) } }
        ProcessBuilder(tmp.absolutePath).start()
        true
    }.getOrDefault(false)

    private fun isNewer(latest: String?, current: String): Boolean {
        latest ?: return false
        val l = latest.split(".").map { it.toIntOrNull() ?: 0 }
        val c = current.split(".").map { it.toIntOrNull() ?: 0 }
        for (i in 0 until maxOf(l.size, c.size)) {
            val a = l.getOrElse(i) { 0 }; val b = c.getOrElse(i) { 0 }
            if (a != b) return a > b
        }
        return false
    }
}
