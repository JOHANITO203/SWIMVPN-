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

    /** Import one or many links (subscription blobs are split by the shared extractor). */
    fun importConfig(blob: String) {
        val entries = VpnConfigLinkExtractor.extractEntries(blob).ifEmpty { listOf(blob.trim()) }
        entries.forEach { addParsed(it) }
        if (selectedId == null) selectedId = configs.firstOrNull()?.id
        persist()
    }

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
