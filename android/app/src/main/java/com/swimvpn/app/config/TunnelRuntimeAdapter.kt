package com.swimvpn.app.config

import android.content.Intent
import android.util.Log
import com.google.gson.GsonBuilder
import com.google.gson.JsonArray
import com.google.gson.JsonElement
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import com.swimvpn.app.SwimVpnService
import com.swimvpn.app.vpn.RuntimeMode

/**
 * Tunnel Runtime Adapter Engine
 *
 * Responsibilities:
 * - Convert normalized SwimVpnProfile to internal runtime format
 * - Prepare runtime-safe configuration for tunnel execution
 * - Separate parsing logic from execution logic
 * - Generate appropriate service intents based on profile
 */
object TunnelRuntimeAdapter {

    private const val TAG = "TunnelRuntimeAdapter"
    private const val DEFAULT_SOCKS_PORT = 10808
    private const val DEFAULT_HTTP_PORT = 10809
    private const val DEFAULT_DNS_PORT = 1053
    private val LEGACY_PROXY_DNS_SERVERS: List<String> = listOf(
        "1.1.1.1",
        "8.8.8.8",
    )
    val DEFAULT_IPV4_DNS_SERVERS: List<String> = listOf(
        "1.1.1.1",
        "1.0.0.1",
        "8.8.8.8",
        "8.8.4.4",
    )

    private val gson = GsonBuilder()
        .disableHtmlEscaping()
        .setPrettyPrinting()
        .create()

    data class RuntimePorts(
        val socksPort: Int = DEFAULT_SOCKS_PORT,
        val httpPort: Int = DEFAULT_HTTP_PORT,
        val dnsPort: Int = DEFAULT_DNS_PORT,
    )

    data class RuntimePreparationResult(
        val profile: SwimVpnProfile,
        val runtimeConfig: String,
        val runtimeDocument: JsonObject,
        val ports: RuntimePorts,
        val summary: String,
    )

    private data class RuntimeNetworkPolicy(
        val dnsServers: List<String>,
        val queryStrategy: String,
        val domainStrategy: String,
        val routing: RoutingOptions = RoutingOptions(),
    )

    fun prepareRuntimeFromRawConfig(
        rawConfig: String,
        sourceType: SourceType = SourceType.BACKEND_API,
        runtimeMode: RuntimeMode = RuntimeMode.FULL_TUNNEL,
        routingOptions: RoutingOptions = RoutingOptions(),
        camouflageFingerprint: String? = null,
    ): Result<RuntimePreparationResult> {
        val parseResult = ConfigParserEngine.parseConfig(rawConfig, sourceType)
        if (!parseResult.isValid) {
            return Result.failure(IllegalArgumentException(parseResult.errors.joinToString("; ")))
        }

        val normalized = ConfigNormalizationEngine.normalizeProfile(parseResult)
            ?: return Result.failure(IllegalStateException("Failed to normalize runtime profile"))

        val support = isProfileSupported(normalized)
        if (!support.first) {
            return Result.failure(IllegalStateException(support.second))
        }

        val runtimeDocument = generateXrayRuntimeDocument(normalized, runtimeMode, routingOptions, camouflageFingerprint)
            ?: return Result.failure(IllegalStateException("Runtime config generation failed"))

        return Result.success(
            RuntimePreparationResult(
                profile = normalized,
                runtimeConfig = gson.toJson(runtimeDocument),
                runtimeDocument = runtimeDocument,
                ports = RuntimePorts(),
                summary = getConnectionSummary(normalized),
            )
        )
    }

    /**
     * Public API for the native rollout: produce a complete Xray runtime document
     * from a normalized profile while preserving raw config on the profile.
     */
    fun generateXrayRuntimeDocument(
        profile: SwimVpnProfile,
        runtimeMode: RuntimeMode = RuntimeMode.FULL_TUNNEL,
        routingOptions: RoutingOptions = RoutingOptions(),
        // Camouflage: optional uTLS fingerprint override (chrome/firefox/safari/ios/randomized),
        // applied post-build ONLY to outbounds whose streamSettings already use TLS/Reality. Null
        // (default) leaves the document byte-for-byte unchanged — no regression.
        camouflageFingerprint: String? = null,
    ): JsonObject? {
        return try {
            val networkPolicy = policyForMode(runtimeMode).copy(routing = routingOptions)
            val parsedConfig = parseRuntimeConfig(profile.normalizedRuntimeConfig)
            val document = when {
                parsedConfig != null && parsedConfig.has("outbounds") -> {
                    augmentFullDocument(parsedConfig.deepCopy(), networkPolicy)
                }
                parsedConfig != null && parsedConfig.has("protocol") -> {
                    wrapOutboundIntoRuntime(parsedConfig.deepCopy(), networkPolicy)
                }
                else -> {
                    val outbound = createOutboundFromProfile(profile) ?: return null
                    wrapOutboundIntoRuntime(outbound, networkPolicy)
                }
            }
            camouflageFingerprint?.takeIf { it.isNotBlank() }?.let { applyFingerprintOverride(document, it) }
            document
        } catch (e: Exception) {
            Log.e(TAG, "Error generating full Xray runtime document", e)
            null
        }
    }

    /**
     * Override the uTLS fingerprint on every outbound that ALREADY negotiates TLS or Reality, leaving
     * plaintext outbounds untouched (so a no-TLS transport is never given a meaningless fingerprint,
     * and nothing else in the config changes). This runs on the final document so it covers all
     * production paths (normalized fragment, full document, fallback) uniformly.
     */
    private fun applyFingerprintOverride(document: JsonObject, fingerprint: String) {
        val outbounds = document.getAsJsonArray("outbounds") ?: return
        outbounds.forEach { element ->
            val stream = element.takeIf { it.isJsonObject }
                ?.asJsonObject?.getAsJsonObject("streamSettings") ?: return@forEach
            stream.getAsJsonObject("tlsSettings")?.addProperty("fingerprint", fingerprint)
            stream.getAsJsonObject("realitySettings")?.addProperty("fingerprint", fingerprint)
        }
    }

    /**
     * Prepare VPN service intent from a SwimVpnProfile
     */
    fun prepareVpnIntent(profile: SwimVpnProfile): Intent {
        return Intent().apply {
            action = SwimVpnService.ACTION_START

            putExtra(SwimVpnService.EXTRA_SERVER_HOST, profile.address)
            putExtra(SwimVpnService.EXTRA_SERVER_PORT, profile.port)
            putExtra(SwimVpnService.EXTRA_PROTOCOL, profile.protocol.name)

            generateXrayConfig(profile)?.let {
                putExtra("NORMALIZED_CONFIG", it)
            }

            putExtra("RAW_CONFIG", profile.rawConfig)

            when (profile.protocol) {
                Protocol.VLESS -> {
                    putExtra("USER_ID", profile.userId)
                    putExtra("SECURITY_MODE", profile.securityMode.name)
                    putExtra("TRANSPORT", profile.transport.name)

                    profile.tlsSettings?.let { tls ->
                        putExtra("TLS_SNI", tls.sni)
                        putExtra("TLS_ALLOW_INSECURE", tls.allowInsecure)
                        if (tls.alpn.isNotEmpty()) {
                            putExtra("TLS_ALPN", tls.alpn.joinToString(","))
                        }
                    }

                    profile.websocketSettings?.let { ws ->
                        putExtra("WS_PATH", ws.path)
                        ws.host?.let { host ->
                            putExtra("WS_HOST", host)
                        }
                    }

                    profile.realitySettings?.let { reality ->
                        putExtra("REALITY_PUBLIC_KEY", reality.publicKey)
                        putExtra("REALITY_SHORT_ID", reality.shortId)
                        reality.spiderX?.let { spiderX ->
                            putExtra("REALITY_SPIDER_X", spiderX)
                        }
                    }
                }

                Protocol.VMESS -> {
                    putExtra("USER_ID", profile.userId)
                    putExtra("SECURITY_MODE", profile.securityMode.name)
                    putExtra("TRANSPORT", profile.transport.name)
                }

                Protocol.TROJAN -> {
                    putExtra("PASSWORD", profile.password)
                    putExtra("SECURITY_MODE", profile.securityMode.name)
                    putExtra("TRANSPORT", profile.transport.name)

                    profile.tlsSettings?.let { tls ->
                        putExtra("TLS_SNI", tls.sni)
                        putExtra("TLS_ALLOW_INSECURE", tls.allowInsecure)
                    }

                    profile.websocketSettings?.let { ws ->
                        putExtra("WS_PATH", ws.path)
                        ws.host?.let { host ->
                            putExtra("WS_HOST", host)
                        }
                    }
                }

                Protocol.SHADOWSOCKS -> {
                    putExtra("PASSWORD", profile.password)
                    putExtra("METHOD", profile.method)
                }

                Protocol.SOCKS5, Protocol.HTTP -> {
                    putExtra("USER_ID", profile.userId)
                    putExtra("PASSWORD", profile.password)
                }

                Protocol.UNKNOWN -> {
                    Log.w(TAG, "Unknown protocol, using raw config fallback")
                }
            }

            putExtra("PROFILE_ID", profile.id)
            putExtra("DISPLAY_NAME", profile.displayName)
            putExtra("SOURCE_FORMAT", profile.sourceFormat.name)
        }
    }

    /**
     * Generate Xray-core compatible JSON configuration from profile.
     * Returned document is now a complete runtime document, not just an outbound fragment.
     */
    fun generateXrayConfig(profile: SwimVpnProfile): String? {
        return generateXrayRuntimeDocument(profile)?.let(gson::toJson)
    }

    /**
     * Check if profile is compatible with current runtime capabilities
     */
    fun isProfileSupported(profile: SwimVpnProfile): Pair<Boolean, String> {
        if (profile.address.isBlank()) {
            return Pair(false, "Missing server address")
        }

        if (profile.port <= 0 || profile.port > 65535) {
            return Pair(false, "Invalid port number")
        }

        return when (profile.protocol) {
            Protocol.VLESS -> {
                if (profile.userId.isNullOrBlank()) {
                    Pair(false, "VLESS requires user ID")
                } else {
                    Pair(true, "VLESS protocol supported")
                }
            }

            Protocol.VMESS -> {
                if (profile.userId.isNullOrBlank()) {
                    Pair(false, "VMess requires user ID")
                } else {
                    Pair(true, "VMess protocol supported")
                }
            }

            Protocol.TROJAN -> {
                if (profile.password.isNullOrBlank()) {
                    Pair(false, "Trojan requires password")
                } else {
                    Pair(true, "Trojan protocol supported")
                }
            }

            Protocol.SHADOWSOCKS -> {
                if (profile.password.isNullOrBlank()) {
                    Pair(false, "Shadowsocks requires password")
                } else if (profile.method.isNullOrBlank()) {
                    Pair(false, "Shadowsocks requires encryption method")
                } else {
                    Pair(true, "Shadowsocks protocol supported")
                }
            }

            Protocol.SOCKS5, Protocol.HTTP -> Pair(true, "Proxy supported")
            Protocol.UNKNOWN -> Pair(false, "Unknown protocol")
        }
    }

    /**
     * Extract basic connection info for UI display
     */
    fun getConnectionSummary(profile: SwimVpnProfile): String {
        return buildString {
            append("${profile.protocol.name} to ${profile.address}:${profile.port}")

            if (profile.transport != Transport.UNKNOWN) {
                append(" via ${profile.transport.name}")
            }

            if (profile.securityMode != SecurityMode.NONE) {
                append(" with ${profile.securityMode.name} security")
            }

            profile.tlsSettings?.let {
                if (it.sni.isNotBlank() && it.sni != profile.address) {
                    append(" (SNI: ${it.sni})")
                }
            }
        }
    }

    private fun parseRuntimeConfig(runtimeConfig: String?): JsonObject? {
        if (runtimeConfig.isNullOrBlank()) {
            return null
        }

        return try {
            JsonParser.parseString(runtimeConfig).asJsonObject
        } catch (error: Exception) {
            Log.w(TAG, "Ignoring non-JSON normalized runtime config", error)
            null
        }
    }

    private fun wrapOutboundIntoRuntime(
        primaryOutbound: JsonObject,
        networkPolicy: RuntimeNetworkPolicy,
    ): JsonObject {
        val document = JsonObject()
        // Sniffing is required for domain-based routing; enable it only when bypass rules exist
        // (OFF by default ⇒ false ⇒ identical to the prior full-tunnel runtime).
        val enableSniffing = XrayRoutingBuilder.hasActiveRules(networkPolicy.routing)
        document.add("log", buildLogSection())
        document.add("dns", buildDnsSection(networkPolicy))
        document.add("policy", buildPolicySection(enableStats = false))
        document.add("inbounds", buildStandardInbounds(enableSniffing = enableSniffing))
        document.add("outbounds", buildStandardOutbounds(primaryOutbound))
        document.add("routing", buildRoutingSection(networkPolicy))
        return document
    }

    private fun augmentFullDocument(
        document: JsonObject,
        networkPolicy: RuntimeNetworkPolicy,
    ): JsonObject {
        if (!document.has("log")) {
            document.add("log", buildLogSection())
        }
        if (!document.has("dns")) {
            document.add("dns", buildDnsSection(networkPolicy))
        }
        if (!document.has("policy")) {
            document.add("policy", buildPolicySection(enableStats = true))
        }
        if (!document.has("stats")) {
            document.add("stats", JsonObject())
        }

        val hasRoutingRules = (
            document.getAsJsonObject("routing")
                ?.getAsJsonArray("rules")
                ?.let { it.size() > 0 }
                ?: false
            ) || XrayRoutingBuilder.hasActiveRules(networkPolicy.routing)
        val inbounds = if (document.has("inbounds") && document.get("inbounds").isJsonArray) {
            document.getAsJsonArray("inbounds")
        } else {
            JsonArray()
        }
        ensureInbound(inbounds, "socks-in") { buildSocksInbound(enableSniffing = hasRoutingRules) }
        ensureInbound(inbounds, "http-in") { buildHttpInbound(enableSniffing = hasRoutingRules) }
        document.add("inbounds", inbounds)

        val outbounds = if (document.has("outbounds") && document.get("outbounds").isJsonArray) {
            document.getAsJsonArray("outbounds")
        } else {
            JsonArray()
        }

        if (outbounds.size() == 0) {
            return wrapOutboundIntoRuntime(buildDirectOutbound(), networkPolicy)
        }

        val primaryOutbound = outbounds.firstOrNull()?.asJsonObject ?: buildDirectOutbound()
        ensureOutboundTag(primaryOutbound, "proxy")
        outbounds.set(0, primaryOutbound)
        ensureOutbound(outbounds, "direct") { buildDirectOutbound() }
        ensureOutbound(outbounds, "block") { buildBlockOutbound() }
        document.add("outbounds", outbounds)

        if (!document.has("routing")) {
            document.add("routing", buildRoutingSection(networkPolicy))
        }

        return document
    }

    private fun createOutboundFromProfile(profile: SwimVpnProfile): JsonObject? {
        return when (profile.protocol) {
            Protocol.VLESS -> createVlessOutbound(profile)
            Protocol.VMESS -> createVmessOutbound(profile)
            Protocol.TROJAN -> createTrojanOutbound(profile)
            Protocol.SHADOWSOCKS -> createShadowsocksOutbound(profile)
            Protocol.SOCKS5 -> createSocksOutbound(profile)
            Protocol.HTTP -> createHttpOutbound(profile)
            Protocol.UNKNOWN -> null
        }
    }

    private fun buildStandardInbounds(enableSniffing: Boolean): JsonArray {
        return JsonArray().apply {
            add(buildSocksInbound(enableSniffing))
            add(buildHttpInbound(enableSniffing))
        }
    }

    private fun buildSocksInbound(enableSniffing: Boolean): JsonObject {
        return JsonObject().apply {
            addProperty("tag", "socks-in")
            addProperty("listen", "127.0.0.1")
            addProperty("port", DEFAULT_SOCKS_PORT)
            addProperty("protocol", "socks")
            add("settings", JsonObject().apply {
                addProperty("udp", true)
                addProperty("auth", "noauth")
            })
            add("sniffing", JsonObject().apply {
                addProperty("enabled", enableSniffing)
                add("destOverride", JsonArray().apply {
                    add("http")
                    add("tls")
                    add("quic")
                })
            })
        }
    }

    private fun buildHttpInbound(enableSniffing: Boolean): JsonObject {
        return JsonObject().apply {
            addProperty("tag", "http-in")
            addProperty("listen", "127.0.0.1")
            addProperty("port", DEFAULT_HTTP_PORT)
            addProperty("protocol", "http")
            add("settings", JsonObject())
            add("sniffing", JsonObject().apply {
                addProperty("enabled", enableSniffing)
                add("destOverride", JsonArray().apply {
                    add("http")
                    add("tls")
                })
            })
        }
    }

    private fun buildStandardOutbounds(primaryOutbound: JsonObject): JsonArray {
        ensureOutboundTag(primaryOutbound, "proxy")
        return JsonArray().apply {
            add(primaryOutbound)
            add(buildDirectOutbound())
            add(buildBlockOutbound())
        }
    }

    private fun buildDirectOutbound(): JsonObject {
        return JsonObject().apply {
            addProperty("tag", "direct")
            addProperty("protocol", "freedom")
            add("settings", JsonObject())
        }
    }

    private fun buildBlockOutbound(): JsonObject {
        return JsonObject().apply {
            addProperty("tag", "block")
            addProperty("protocol", "blackhole")
            add("settings", JsonObject())
        }
    }

    private fun buildLogSection(): JsonObject {
        return JsonObject().apply {
            addProperty("loglevel", "warning")
        }
    }

    private fun buildDnsSection(networkPolicy: RuntimeNetworkPolicy): JsonObject {
        return JsonObject().apply {
            addProperty("queryStrategy", networkPolicy.queryStrategy)
            add("servers", JsonArray().apply {
                networkPolicy.dnsServers.forEach { add(it) }
            })
        }
    }

    private fun buildPolicySection(enableStats: Boolean): JsonObject {
        return JsonObject().apply {
            add("levels", JsonObject().apply {
                add("0", JsonObject().apply {
                    addProperty("handshake", 4)
                    addProperty("connIdle", 300)
                    addProperty("uplinkOnly", 2)
                    addProperty("downlinkOnly", 5)
                    if (enableStats) {
                        addProperty("statsUserUplink", true)
                        addProperty("statsUserDownlink", true)
                    }
                })
            })
            if (enableStats) {
                add("system", JsonObject().apply {
                    addProperty("statsInboundUplink", true)
                    addProperty("statsInboundDownlink", true)
                    addProperty("statsOutboundUplink", true)
                    addProperty("statsOutboundDownlink", true)
                })
            }
        }
    }

    private fun buildRoutingSection(networkPolicy: RuntimeNetworkPolicy): JsonObject {
        return JsonObject().apply {
            addProperty("domainStrategy", networkPolicy.domainStrategy)
            add("rules", XrayRoutingBuilder.buildRules(networkPolicy.routing))
        }
    }

    private fun policyForMode(runtimeMode: RuntimeMode): RuntimeNetworkPolicy {
        return when (runtimeMode) {
            RuntimeMode.LOCAL_PROXY -> RuntimeNetworkPolicy(
                dnsServers = LEGACY_PROXY_DNS_SERVERS,
                queryStrategy = "UseIP",
                domainStrategy = "AsIs",
            )
            RuntimeMode.FULL_TUNNEL,
            RuntimeMode.SPLIT_TUNNEL,
            -> RuntimeNetworkPolicy(
                dnsServers = DEFAULT_IPV4_DNS_SERVERS,
                queryStrategy = "UseIPv4",
                domainStrategy = "IPIfNonMatch",
            )
        }
    }

    // VLESS/VMESS/Trojan/SS outbounds are produced by the shared XrayOutboundBuilder (same source
    // of truth as ConfigNormalizationEngine), so transports + Reality/XTLS stay complete and
    // consistent across both config paths.
    private fun createVlessOutbound(profile: SwimVpnProfile): JsonObject = XrayOutboundBuilder.vless(profile)

    private fun createVmessOutbound(profile: SwimVpnProfile): JsonObject = XrayOutboundBuilder.vmess(profile)

    private fun createTrojanOutbound(profile: SwimVpnProfile): JsonObject = XrayOutboundBuilder.trojan(profile)

    private fun createShadowsocksOutbound(profile: SwimVpnProfile): JsonObject = XrayOutboundBuilder.shadowsocks(profile)

    // BYO proxy (residential / user-supplied): the pasted proxy IS the Xray outbound, so the
    // FULL_TUNNEL tun routes all device traffic out through it. users[] omitted when no auth.
    private fun createSocksOutbound(profile: SwimVpnProfile): JsonObject = createProxyOutbound(profile, "socks")

    private fun createHttpOutbound(profile: SwimVpnProfile): JsonObject = createProxyOutbound(profile, "http")

    private fun createProxyOutbound(profile: SwimVpnProfile, proxyProtocol: String): JsonObject {
        return JsonObject().apply {
            addProperty("tag", "proxy")
            addProperty("protocol", proxyProtocol)
            add("settings", JsonObject().apply {
                add("servers", JsonArray().apply {
                    add(JsonObject().apply {
                        addProperty("address", profile.address)
                        addProperty("port", profile.port)
                        val user = profile.userId
                        if (!user.isNullOrEmpty()) {
                            add("users", JsonArray().apply {
                                add(JsonObject().apply {
                                    addProperty("user", user)
                                    addProperty("pass", profile.password ?: "")
                                })
                            })
                        }
                    })
                })
            })
        }
    }

    private fun ensureInbound(inbounds: JsonArray, tag: String, factory: () -> JsonObject) {
        val exists = inbounds.any { element ->
            element.isJsonObject && element.asJsonObject.get("tag")?.asString == tag
        }
        if (!exists) {
            inbounds.add(factory())
        }
    }

    private fun ensureOutbound(outbounds: JsonArray, tag: String, factory: () -> JsonObject) {
        val exists = outbounds.any { element ->
            element.isJsonObject && element.asJsonObject.get("tag")?.asString == tag
        }
        if (!exists) {
            outbounds.add(factory())
        }
    }

    private fun ensureOutboundTag(outbound: JsonObject, tag: String) {
        if (!outbound.has("tag") || outbound.get("tag").asString.isBlank()) {
            outbound.addProperty("tag", tag)
        }
    }

    private fun JsonArray.firstOrNull(): JsonElement? {
        return if (size() > 0) get(0) else null
    }

    private inline fun JsonArray.any(predicate: (JsonElement) -> Boolean): Boolean {
        for (index in 0 until size()) {
            if (predicate(get(index))) {
                return true
            }
        }
        return false
    }
}
