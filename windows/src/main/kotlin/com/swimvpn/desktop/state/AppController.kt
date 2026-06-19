package com.swimvpn.desktop.state

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
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
                    fetched.body
                } else input
            }.getOrElse { e ->
                runCatching { java.io.File(appDir, "import.log").appendText("FETCH FAIL $input: ${e.message}\n") }
                importResult = ImportResult(0, 1, "Échec téléchargement abonnement : ${e.message}")
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
        latency.remove(id)
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
            subTitle = subscription?.title,
            subUsed = subscription?.usedBytes,
            subTotal = subscription?.totalBytes,
            subExpire = subscription?.expiresAt,
        )
    )
}
