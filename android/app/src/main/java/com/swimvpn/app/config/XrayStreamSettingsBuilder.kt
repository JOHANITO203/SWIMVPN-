package com.swimvpn.app.config

import com.google.gson.JsonArray
import com.google.gson.JsonObject

/**
 * Single source of truth for an Xray outbound's `streamSettings`, shared by both config
 * producers ([ConfigNormalizationEngine]'s normalized-outbound path and [TunnelRuntimeAdapter]'s
 * fallback builders) so the transport/security emission can no longer diverge between them.
 *
 * Targets Xray-core v26.x. Notable correctness points (fixing the audited gaps):
 *  - **XTLS** uses stream security `"tls"` (NOT `"xtls"`, which v26 rejects); the xtls flow lives
 *    on the outbound user object (see [XrayOutboundBuilder]), never here.
 *  - **Reality** always carries a `fingerprint` (Xray requires one — defaults to "chrome") plus
 *    `serverName`/`spiderX`, instead of just publicKey+shortId.
 *  - **gRPC / HTTP2** emit their settings instead of being dropped on the VLESS/VMESS path.
 *  - **KCP / QUIC** emit functional settings instead of empty objects.
 *  - `flow` is never emitted as an empty string (handled by the outbound builder).
 *
 * Pure (Gson only, no Android deps) so it is unit-testable without Robolectric.
 */
object XrayStreamSettingsBuilder {

    private const val DEFAULT_FINGERPRINT = "chrome"

    fun build(profile: SwimVpnProfile): JsonObject = JsonObject().apply {
        addProperty("network", mapTransport(profile.transport))
        addProperty("security", mapSecurity(profile.securityMode))

        when (profile.transport) {
            // TCP with the default ("none") header needs no tcpSettings — Xray applies defaults.
            Transport.TCP -> profile.tcpSettings
                ?.takeIf { it.headerType != "none" }
                ?.let { add("tcpSettings", tcpSettings(it)) }
            Transport.WEBSOCKET -> add("wsSettings", wsSettings(profile))
            Transport.GRPC -> add("grpcSettings", grpcSettings(profile))
            Transport.HTTP2 -> add("httpSettings", http2Settings(profile))
            Transport.KCP -> add("kcpSettings", kcpSettings(profile))
            Transport.QUIC -> add("quicSettings", quicSettings(profile))
            Transport.UNKNOWN -> Unit
        }

        when (profile.securityMode) {
            // XTLS-Vision rides over a TLS stream in modern Xray; the flow is set on the user.
            SecurityMode.TLS, SecurityMode.XTLS -> add("tlsSettings", tlsSettings(profile))
            SecurityMode.REALITY -> add("realitySettings", realitySettings(profile))
            SecurityMode.NONE, SecurityMode.UNKNOWN -> Unit
        }
    }

    fun mapTransport(transport: Transport): String = when (transport) {
        Transport.TCP, Transport.UNKNOWN -> "tcp"
        Transport.WEBSOCKET -> "ws"
        Transport.GRPC -> "grpc"
        Transport.HTTP2 -> "http"
        Transport.KCP -> "kcp"
        Transport.QUIC -> "quic"
    }

    fun mapSecurity(securityMode: SecurityMode): String = when (securityMode) {
        SecurityMode.NONE, SecurityMode.UNKNOWN -> "none"
        // XTLS is expressed as a TLS stream + a flow on the user (Xray v26 has no "xtls" security).
        SecurityMode.TLS, SecurityMode.XTLS -> "tls"
        SecurityMode.REALITY -> "reality"
    }

    private fun tcpSettings(settings: TcpSettings): JsonObject = JsonObject().apply {
        add("header", JsonObject().apply {
            addProperty("type", settings.headerType)
            settings.host?.takeIf { it.isNotBlank() }?.let { addProperty("host", it) }
        })
    }

    private fun wsSettings(profile: SwimVpnProfile): JsonObject {
        val ws = profile.websocketSettings
        return JsonObject().apply {
            addProperty("path", ws?.path ?: "/")
            val headers = JsonObject()
            ws?.host?.takeIf { it.isNotBlank() }?.let { headers.addProperty("Host", it) }
            ws?.headers?.forEach { (key, value) ->
                if (value.isNotBlank()) headers.addProperty(key, value)
            }
            if (headers.size() > 0) add("headers", headers)
        }
    }

    private fun grpcSettings(profile: SwimVpnProfile): JsonObject {
        val grpc = profile.grpcSettings
        return JsonObject().apply {
            addProperty("serviceName", grpc?.serviceName ?: "")
            addProperty("multiMode", grpc?.mode?.equals("multi", ignoreCase = true) ?: false)
        }
    }

    private fun http2Settings(profile: SwimVpnProfile): JsonObject = JsonObject().apply {
        addProperty("path", profile.websocketSettings?.path ?: "/")
        val hostArray = JsonArray()
        profile.websocketSettings?.host?.takeIf { it.isNotBlank() }?.let { hostArray.add(it) }
        profile.tlsSettings?.sni?.takeIf { it.isNotBlank() }?.let { sni ->
            if (hostArray.none { it.asString == sni }) hostArray.add(sni)
        }
        if (hostArray.size() > 0) add("host", hostArray)
    }

    // mKCP: Xray's documented defaults, with optional obfuscation header + seed when supplied.
    private fun kcpSettings(profile: SwimVpnProfile): JsonObject = JsonObject().apply {
        addProperty("mtu", 1350)
        addProperty("tti", 50)
        addProperty("uplinkCapacity", 12)
        addProperty("downlinkCapacity", 100)
        addProperty("congestion", false)
        addProperty("readBufferSize", 2)
        addProperty("writeBufferSize", 2)
        add("header", JsonObject().apply {
            addProperty("type", profile.advancedSettings["headerType"]?.takeIf { it.isNotBlank() } ?: "none")
        })
        profile.advancedSettings["seed"]?.takeIf { it.isNotBlank() }?.let { addProperty("seed", it) }
    }

    private fun quicSettings(profile: SwimVpnProfile): JsonObject = JsonObject().apply {
        addProperty("security", profile.advancedSettings["quicSecurity"]?.takeIf { it.isNotBlank() } ?: "none")
        addProperty("key", profile.advancedSettings["quicKey"] ?: "")
        add("header", JsonObject().apply {
            addProperty("type", profile.advancedSettings["headerType"]?.takeIf { it.isNotBlank() } ?: "none")
        })
    }

    private fun tlsSettings(profile: SwimVpnProfile): JsonObject {
        val tls = profile.tlsSettings
        return JsonObject().apply {
            addProperty("serverName", tls?.sni ?: profile.address)
            addProperty("allowInsecure", tls?.allowInsecure ?: false)
            tls?.fingerprint?.takeIf { it.isNotBlank() }?.let { addProperty("fingerprint", it) }
            if (!tls?.alpn.isNullOrEmpty()) {
                add("alpn", JsonArray().apply { tls?.alpn?.forEach { add(it) } })
            }
        }
    }

    private fun realitySettings(profile: SwimVpnProfile): JsonObject {
        val reality = profile.realitySettings
        return JsonObject().apply {
            addProperty("publicKey", reality?.publicKey)
            addProperty("shortId", reality?.shortId ?: "")
            addProperty("serverName", profile.tlsSettings?.sni ?: profile.address)
            reality?.spiderX?.takeIf { it.isNotBlank() }?.let { addProperty("spiderX", it) }
            // Reality requires a uTLS fingerprint to function; fall back to a common one.
            addProperty(
                "fingerprint",
                profile.tlsSettings?.fingerprint?.takeIf { it.isNotBlank() } ?: DEFAULT_FINGERPRINT,
            )
        }
    }

    private inline fun JsonArray.none(predicate: (com.google.gson.JsonElement) -> Boolean): Boolean {
        for (index in 0 until size()) {
            if (predicate(get(index))) return false
        }
        return true
    }
}
