package com.swimvpn.desktop.state

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.swimvpn.desktop.i18n.Lang
import com.swimvpn.desktop.i18n.Strings
import com.swimvpn.desktop.i18n.stringsFor
import com.swimvpn.desktop.system.Autostart
import com.swimvpn.desktop.system.Elevation
import com.swimvpn.desktop.system.KillSwitch
import com.swimvpn.desktop.system.Updater
import com.swimvpn.desktop.adaptive.AdaptiveAgent
import com.swimvpn.desktop.vpn.CamouflageProfile
import com.swimvpn.desktop.vpn.EngineCleanup
import com.swimvpn.desktop.vpn.HealAttempt
import com.swimvpn.desktop.vpn.LatencyProbe
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import com.swimvpn.app.config.ConfigParserEngine
import com.swimvpn.app.config.SourceType
import com.swimvpn.app.config.SwimVpnProfile
import com.swimvpn.app.config.subscriptionparser.SubscriptionMetadataParser
import com.swimvpn.app.config.subscriptionparser.SubscriptionPayloadDecoder
import com.swimvpn.desktop.vpn.VpnController
import com.swimvpn.desktop.vpn.VpnState
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.swing.Swing
import kotlinx.coroutines.withContext

enum class NavTab { HOME, SERVERS, SUBSCRIPTION, ACCOUNT }

/**
 * The desktop "view-model": owns navigation, the imported config list (parsed by the SHARED
 * Android engine), selection, settings, and the VPN lifecycle — persisted locally.
 */
class AppController(private val scope: CoroutineScope) {
    val vpn = VpnController(scope)

    var tab by mutableStateOf(NavTab.HOME)
    var showSettings by mutableStateOf(false)

    /** Active UI language. Changing it re-provides LocalStrings → the whole UI updates in place. */
    var lang by mutableStateOf(Lang.FR)
        private set
    /** Current strings, kept in sync with [lang] for non-composable use (import messages). */
    var strings: Strings by mutableStateOf(stringsFor(Lang.FR))
        private set

    fun selectLang(value: Lang) {
        lang = value
        strings = stringsFor(value)
        persist()
    }

    /** Last subscription URL — enables one-tap refresh of quota + servers. */
    var subUrl by mutableStateOf<String?>(null)
        private set

    /** Desktop integration prefs (persisted). */
    var autostart by mutableStateOf(false)
        private set
    var startMinimized by mutableStateOf(false)
        private set
    var killSwitch by mutableStateOf(false)
        private set

    fun applyKillSwitch(value: Boolean) {
        killSwitch = value
        vpn.applyKillSwitch(value) // engage now if connected via TUN, else lift
        persist()
    }

    // --- Adaptive camouflage (honest: learns reliability per server, not "stealth") --------------
    val agent = AdaptiveAgent()
    var aiEnabled by mutableStateOf(false)
        private set
    var manualProfile by mutableStateOf(CamouflageProfile.AUTO)
        private set
    /** Camouflage profile in effect for the current connection (display only). */
    var activeProfile by mutableStateOf(CamouflageProfile.AUTO)
        private set
    /** Heal cursor: the profile of the in-flight attempt; advanced by [planNextAttempt]. */
    private var healProfile = CamouflageProfile.AUTO

    fun applyAiEnabled(value: Boolean) { aiEnabled = value; vpn.aiOn = value; persist() }
    fun applyManualProfile(p: CamouflageProfile) { manualProfile = p; persist() }

    /** Anti-DPI TLS fragmentation (applies on next connect). */
    var tlsFragment by mutableStateOf(false)
        private set
    fun applyTlsFragment(value: Boolean) { tlsFragment = value; vpn.tlsFragment = value; persist() }

    /** Split routing: LAN + RU traffic direct (velocity + only foreign/blocked tunneled). Next connect. */
    var splitLocal by mutableStateOf(false)
        private set
    fun applySplitLocal(value: Boolean) { splitLocal = value; vpn.splitLocal = value; persist() }

    // --- Self-update ----------------------------------------------------------------------------
    var updateInfo by mutableStateOf<Updater.Info?>(null)
        private set
    var updateChecking by mutableStateOf(false)
        private set

    fun checkForUpdates() {
        if (updateChecking) return
        updateChecking = true
        scope.launch {
            val r = withContext(Dispatchers.IO) { Updater.check() }
            withContext(Dispatchers.Swing) { updateInfo = r; updateChecking = false }
        }
    }

    fun runUpdate() {
        updateInfo?.url?.let { url -> scope.launch(Dispatchers.IO) { Updater.downloadAndLaunch(url) } }
    }

    /** Relaunch elevated (so TUN works) then quit this non-elevated instance. */
    fun relaunchAsAdmin() {
        if (Elevation.relaunchAsAdmin()) {
            vpn.disconnect()
            scope.launch { kotlinx.coroutines.delay(400); kotlin.system.exitProcess(0) }
        }
    }

    /** AI heal plan: cycle the camouflage cascade on the current server, then rotate to the next. */
    private fun planNextAttempt(): HealAttempt? {
        if (configs.isEmpty()) return null
        agent.nextInCascade(healProfile)?.let { next ->
            healProfile = next; activeProfile = next
            val server = selected ?: return null
            return HealAttempt(server.rawConfig, next.fingerprint)
        }
        // cascade exhausted on this server → rotate to the next server, restart the cascade at AUTO
        val cur = configs.indexOfFirst { it.id == selectedId }.coerceAtLeast(0)
        val nextServer = configs[(cur + 1).mod(configs.size)]
        selectedId = nextServer.id
        persist()
        healProfile = CamouflageProfile.AUTO; activeProfile = healProfile
        return HealAttempt(nextServer.rawConfig, healProfile.fingerprint)
    }

    private fun onConnectedRecord() {
        agent.recordWorking(selectedId, healProfile)
        activeProfile = healProfile
    }

    fun applyStartMinimized(value: Boolean) { startMinimized = value; persist() }

    fun applyAutostart(value: Boolean) {
        autostart = value
        Autostart.set(value) // HKCU Run key → launch at sign-in (no-op if launcher path unknown)
        persist()
    }

    /** Re-download the stored subscription URL and merge any new servers + fresh metadata. */
    fun refreshSubscription() {
        val url = subUrl ?: return
        importConfig(url)
    }

    /** Auto-pick the lowest-latency server: ping all, then select the best reachable one. */
    fun autoSelectBest() {
        latencyJob?.cancel()
        latencyJob = scope.launch {
            val snapshot = configs.toList()
            val results = snapshot.map { cfg ->
                async(Dispatchers.IO) { cfg.id to LatencyProbe.ping(cfg.address, cfg.port) }
            }.awaitAll()
            results.forEach { (id, ms) -> latency[id] = ms }
            val best = results.filter { it.second != null }.minByOrNull { it.second!! }
            if (best != null) { selectedId = best.first; persist() }
        }
    }

    val configs = mutableStateListOf<SwimVpnProfile>()
    var selectedId by mutableStateOf<String?>(null)
        private set

    val selected: SwimVpnProfile?
        get() = configs.firstOrNull { it.id == selectedId } ?: configs.firstOrNull()

    /** Subscription metadata parsed from the response headers (quota / expiry / title). */
    var subscription by mutableStateOf<SubscriptionInfo?>(null)
        private set

    /** Per-config server latency in ms (null = unreachable, absent = not measured yet). */
    val latency = mutableStateMapOf<String, Int?>()
    private var latencyJob: Job? = null

    /** Measures TCP latency to every imported server in parallel. */
    fun refreshLatencies() {
        latencyJob?.cancel()
        latencyJob = scope.launch {
            val snapshot = configs.toList()
            val results = snapshot.map { cfg ->
                async(Dispatchers.IO) { cfg.id to LatencyProbe.ping(cfg.address, cfg.port) }
            }.awaitAll()
            results.forEach { (id, ms) -> latency[id] = ms }
        }
    }

    init {
        val s = ConfigStore.load()
        lang = Lang.fromCode(s.lang) // null on first run → follows OS language
        strings = stringsFor(lang)
        subUrl = s.subUrl
        autostart = s.autostart
        startMinimized = s.startMinimized
        killSwitch = s.killSwitch
        // Backstop: clear any kill-switch firewall state left by a previous crash/force-quit.
        KillSwitch.cleanupStale()
        // Backstop: kill orphaned SWIMVPN engine procs (xray/tun2socks) + stale routes from a prior
        // hard-kill, so a fresh connect gets a clean "swimvpn" adapter (no "swimvpn 1" collision).
        scope.launch(Dispatchers.IO) { EngineCleanup.killStray(includeXray = true) }
        vpn.killSwitch = killSwitch
        aiEnabled = s.aiEnabled
        manualProfile = CamouflageProfile.byId(s.camProfile)
        tlsFragment = s.tlsFragment
        vpn.tlsFragment = tlsFragment
        splitLocal = s.splitLocal
        vpn.splitLocal = splitLocal
        vpn.aiOn = aiEnabled
        vpn.planNextAttempt = { planNextAttempt() }
        vpn.onConnectedOk = { onConnectedRecord() }
        vpn.fullTunnel = s.fullTunnel
        s.configs.forEach { addParsed(it) }
        selectedId = configs.getOrNull(s.selectedIndex)?.id ?: configs.firstOrNull()?.id
        if (s.subTitle != null || s.subTotal != null || s.subExpire != null) {
            subscription = SubscriptionInfo(s.subTitle, s.subUsed, s.subTotal, s.subExpire)
        }

        // Server failover: when a server is exhausted, rotate to the next imported config.
        vpn.onExhausted = {
            if (configs.size <= 1) {
                null
            } else {
                val cur = configs.indexOfFirst { it.id == selectedId }.coerceAtLeast(0)
                val next = configs[(cur + 1).mod(configs.size)]
                selectedId = next.id
                persist()
                next.rawConfig
            }
        }
    }

    data class ImportResult(val added: Int, val failed: Int, val message: String)

    private val appDir = java.io.File(
        System.getenv("LOCALAPPDATA") ?: System.getProperty("java.io.tmpdir"), "SWIMVPN",
    ).apply { mkdirs() }

    var importBusy by mutableStateOf(false)
        private set
    var importResult by mutableStateOf<ImportResult?>(null)

    /** Subscription metadata parsed from the response headers (quota / expiry / title). */
    data class SubscriptionInfo(
        val title: String?,
        val usedBytes: Long?,
        val totalBytes: Long?,
        val expiresAt: String?, // ISO-8601
    )
    private data class FetchResult(val body: String, val info: SubscriptionInfo?)

    /** Import from a pasted link/blob OR a subscription URL (http(s):// is fetched first). Async;
     *  exposes importBusy + importResult as state. Never silent (logs to import.log). */
    fun importConfig(input: String) {
        if (importBusy) return
        importBusy = true
        importResult = null
        // Run on the UI (Swing) dispatcher so Compose state mutations (configs list, result)
        // recompose the screen; only the blocking network fetch hops to IO.
        scope.launch(Dispatchers.Swing) {
            val payload = runCatching {
                if (input.startsWith("http://", true) || input.startsWith("https://", true)) {
                    val fetched = withContext(Dispatchers.IO) { fetchSubscription(input) }
                    subscription = fetched.info
                    subUrl = input // remember the source for one-tap refresh
                    fetched.body
                } else input
            }.getOrElse { e ->
                runCatching { java.io.File(appDir, "import.log").appendText("FETCH FAIL $input: ${e.message}\n") }
                importResult = ImportResult(0, 1, strings.fetchFailFmt.format(e.message ?: ""))
                importBusy = false
                return@launch
            }
            importResult = processImport(payload)
            importBusy = false
        }
    }

    private fun fetchSubscription(url: String): FetchResult {
        val conn = java.net.URI(url).toURL().openConnection() as java.net.HttpURLConnection
        conn.connectTimeout = 12_000
        conn.readTimeout = 12_000
        conn.instanceFollowRedirects = true
        conn.setRequestProperty("User-Agent", "SWIMVPN-Windows/1.0")
        val body = conn.inputStream.bufferedReader().use { it.readText() }
        // Same metadata parsing as Android (Subscription-Userinfo: upload/download/total/expire).
        val meta = SubscriptionMetadataParser.parseHttpHeaders(
            conn.getHeaderField("Subscription-Userinfo"),
            conn.getHeaderField("Profile-Update-Interval"),
            url,
        )
        val title = conn.getHeaderField("Profile-Title")?.let { decodeTitle(it) }
        val info = if (meta.hasValues || !title.isNullOrBlank()) {
            SubscriptionInfo(title, meta.trafficUsedBytes, meta.trafficTotalBytes, meta.expiresAt)
        } else null
        return FetchResult(body, info)
    }

    private fun decodeTitle(raw: String): String? = when {
        raw.startsWith("base64:") ->
            runCatching { String(java.util.Base64.getDecoder().decode(raw.removePrefix("base64:")), Charsets.UTF_8) }.getOrNull()
        else -> raw.ifBlank { null }
    }

    /** Parses a pasted/fetched payload (links, base64 blob, or subscription body). Never silent. */
    private fun processImport(blob: String): ImportResult {
        // Same subscription parsing level as Android: carrier-decode (base64 multi-pass /
        // URL-encoded / Happ) then extract entries (SIP008 JSON, Clash YAML, sing-box, links).
        val decoded = SubscriptionPayloadDecoder.decode(blob).payload
        val entries = SubscriptionPayloadDecoder.extractEntries(decoded).ifEmpty { listOf(blob.trim()) }
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
            added > 0 && failed == 0 -> strings.importOkFmt.format(added)
            added > 0 -> strings.importPartialFmt.format(added, failed)
            else -> strings.importFailFmt.format(errors.firstOrNull() ?: strings.formatUnrecognized)
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
        latency.remove(id)
        persist()
    }

    fun setFullTunnel(value: Boolean) { vpn.fullTunnel = value; persist() }

    fun toggleConnect() {
        when (vpn.state) {
            VpnState.CONNECTED, VpnState.CONNECTING -> vpn.disconnect()
            else -> {
                val server = selected ?: return
                // AI on → start from the server's known-good profile; off → the manual choice.
                val profile = if (aiEnabled) agent.startProfile(server.id) else manualProfile
                healProfile = profile
                activeProfile = profile
                vpn.aiOn = aiEnabled
                vpn.connect(server.rawConfig, profile.fingerprint)
            }
        }
    }

    private fun persist() = ConfigStore.save(
        PersistedState(
            configs = configs.map { it.rawConfig },
            selectedIndex = configs.indexOfFirst { it.id == selectedId },
            fullTunnel = vpn.fullTunnel,
            subTitle = subscription?.title,
            subUsed = subscription?.usedBytes,
            subTotal = subscription?.totalBytes,
            subExpire = subscription?.expiresAt,
            lang = lang.code,
            subUrl = subUrl,
            autostart = autostart,
            startMinimized = startMinimized,
            killSwitch = killSwitch,
            aiEnabled = aiEnabled,
            camProfile = manualProfile.id,
            tlsFragment = tlsFragment,
            splitLocal = splitLocal,
        )
    )
}
