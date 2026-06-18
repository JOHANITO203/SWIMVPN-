package com.swimvpn.desktop.state

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.swimvpn.app.config.ConfigParserEngine
import com.swimvpn.app.config.SourceType
import com.swimvpn.app.config.SwimVpnProfile
import com.swimvpn.app.config.VpnConfigLinkExtractor
import com.swimvpn.desktop.vpn.VpnController
import com.swimvpn.desktop.vpn.VpnState
import kotlinx.coroutines.CoroutineScope

enum class NavTab { HOME, SERVERS, SUBSCRIPTION, ACCOUNT }

/**
 * The desktop "view-model": owns navigation, the imported config list (parsed by the SHARED
 * Android engine), selection, settings, and the VPN lifecycle — persisted locally.
 */
class AppController(scope: CoroutineScope) {
    val vpn = VpnController(scope)

    var tab by mutableStateOf(NavTab.HOME)
    var showSettings by mutableStateOf(false)

    val configs = mutableStateListOf<SwimVpnProfile>()
    var selectedId by mutableStateOf<String?>(null)
        private set

    val selected: SwimVpnProfile?
        get() = configs.firstOrNull { it.id == selectedId } ?: configs.firstOrNull()

    init {
        val s = ConfigStore.load()
        vpn.fullTunnel = s.fullTunnel
        s.configs.forEach { addParsed(it) }
        selectedId = configs.getOrNull(s.selectedIndex)?.id ?: configs.firstOrNull()?.id
    }

    data class ImportResult(val added: Int, val failed: Int, val message: String)

    private val appDir = java.io.File(
        System.getenv("LOCALAPPDATA") ?: System.getProperty("java.io.tmpdir"), "SWIMVPN",
    ).apply { mkdirs() }

    /** Import one or many links (subscription blobs are split by the shared extractor). NEVER
     *  silent: returns a result + logs the raw input and per-entry outcome to import.log. */
    fun importConfig(blob: String): ImportResult {
        val entries = VpnConfigLinkExtractor.extractEntries(blob).ifEmpty { listOf(blob.trim()) }
        val log = StringBuilder("=== import (${blob.length} chars, ${entries.size} entries) ===\n")
        log.append("raw: ${blob.take(400)}\n")
        var added = 0; var failed = 0
        val errors = mutableListOf<String>()
        entries.forEach { raw ->
            val result = runCatching { ConfigParserEngine.parseConfig(raw, SourceType.MANUAL_ENTRY) }
                .getOrElse {
                    failed++; errors.add(it.message ?: it::class.simpleName ?: "exception")
                    log.append("THREW '${raw.take(70)}': ${it.message}\n"); return@forEach
                }
            val p = result.profile
            when {
                p == null -> {
                    failed++; errors.addAll(result.errors)
                    log.append("FAIL '${raw.take(70)}': ${result.errors}\n")
                }
                configs.any { it.rawConfig == p.rawConfig } ->
                    log.append("DUP ${p.protocol} ${p.address}:${p.port}\n")
                else -> {
                    configs.add(p); added++
                    log.append("OK ${p.protocol} ${p.address}:${p.port}\n")
                }
            }
        }
        runCatching { java.io.File(appDir, "import.log").appendText(log.toString()) }
        if (selectedId == null) selectedId = configs.firstOrNull()?.id
        persist()
        val message = when {
            added > 0 && failed == 0 -> "$added configuration(s) importée(s) ✓"
            added > 0 -> "$added importée(s), $failed échec(s)"
            else -> "Échec : ${errors.firstOrNull() ?: "format non reconnu"}"
        }
        return ImportResult(added, failed, message)
    }

    /** Silent parse used when reloading already-validated configs from disk at startup. */
    private fun addParsed(raw: String) {
        val profile = runCatching { ConfigParserEngine.parseConfig(raw, SourceType.MANUAL_ENTRY).profile }
            .getOrNull() ?: return
        if (configs.none { it.rawConfig == profile.rawConfig }) configs.add(profile)
    }

    fun select(id: String) { selectedId = id; persist() }

    fun remove(id: String) {
        configs.removeAll { it.id == id }
        if (selectedId == id) selectedId = configs.firstOrNull()?.id
        persist()
    }

    fun setFullTunnel(value: Boolean) { vpn.fullTunnel = value; persist() }

    fun toggleConnect() {
        when (vpn.state) {
            VpnState.CONNECTED, VpnState.CONNECTING -> vpn.disconnect()
            else -> selected?.let { vpn.connect(it.rawConfig) }
        }
    }

    private fun persist() = ConfigStore.save(
        PersistedState(
            configs = configs.map { it.rawConfig },
            selectedIndex = configs.indexOfFirst { it.id == selectedId },
            fullTunnel = vpn.fullTunnel,
        )
    )
}
