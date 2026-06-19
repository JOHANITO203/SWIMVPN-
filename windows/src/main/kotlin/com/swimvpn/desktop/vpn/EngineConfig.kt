package com.swimvpn.desktop.vpn

import com.google.gson.Gson
import com.google.gson.JsonArray
import com.google.gson.JsonObject
import com.swimvpn.app.config.ConfigParserEngine
import com.swimvpn.app.config.SourceType
import com.swimvpn.app.config.VpnConfigLinkExtractor
import com.swimvpn.app.config.XrayOutboundBuilder

/** Mirrors the Android connection lifecycle. */
enum class VpnState { DISCONNECTED, CONNECTING, CONNECTED, ERROR }

/** Local inbound ports xray exposes; the Windows system proxy points at the HTTP one. */
object LocalPorts {
    const val SOCKS = 10808
    const val HTTP = 10809
}

/**
 * Builds an Xray config.json from any supported share link using the SAME engine as Android:
 * [VpnConfigLinkExtractor] + [ConfigParserEngine] + [XrayOutboundBuilder] (compiled from the very
 * same source files). This guarantees identical link/config support — VLESS, VMess, Trojan,
 * Shadowsocks, in URL or JSON form, across all transports/security the Android app accepts.
 * The desktop side only adds the local SOCKS/HTTP inbounds (system-proxy mode).
 */
object EngineConfig {
    data class Built(val configJson: String, val host: String, val port: Int, val label: String)

    fun build(link: String, fullTunnel: Boolean = true, fingerprint: String? = null, fragment: Boolean = false): Built {
        val entry = VpnConfigLinkExtractor.extractEntries(link).firstOrNull() ?: link.trim()
        val result = ConfigParserEngine.parseConfig(entry, SourceType.MANUAL_ENTRY)
        val profile = result.profile
            ?: throw IllegalArgumentException(result.errors.firstOrNull() ?: "Configuration invalide")
        val outbound = XrayOutboundBuilder.forProfile(profile)
            ?: throw IllegalArgumentException("Protocole non tunnelable: ${profile.protocol}")

        val doc = JsonObject().apply {
            add("log", JsonObject().apply { addProperty("loglevel", "warning") })
            add("inbounds", JsonArray().apply {
                add(inbound("socks", LocalPorts.SOCKS, "socks", udp = true))
                add(inbound("http", LocalPorts.HTTP, "http", udp = false))
            })
            add("outbounds", JsonArray().apply {
                add(outbound) // tag = "proxy", with streamSettings from the shared builder
                add(tagged("direct", "freedom"))
                add(tagged("block", "blackhole"))
            })
        }

        // Runtime post-processing (parity with Android's TunnelRuntimeAdapter).
        // Always: dial the server by pre-resolved IPv4 (keep SNI) — fixes REALITY on censored DNS.
        RuntimeDoc.resolveOutboundServerAddresses(doc)
        // Camouflage: override the uTLS fingerprint when a profile is selected (AUTO ⇒ null ⇒ no-op).
        RuntimeDoc.applyFingerprint(doc, fingerprint)
        // Anti-DPI: fragment the TLS ClientHello when enabled (no-op otherwise).
        RuntimeDoc.applyFragmentation(doc, fragment)
        // TUN mode: answer DNS locally (FakeDNS) + block literal IPv6 so it fast-fails to IPv4.
        if (fullTunnel) {
            RuntimeDoc.applyFakeDnsInterception(doc)
            RuntimeDoc.appendIpv6BlockRule(doc)
        }
        return Built(Gson().toJson(doc), profile.address, profile.port, profile.displayName)
    }

    private fun inbound(tag: String, port: Int, protocol: String, udp: Boolean) = JsonObject().apply {
        addProperty("tag", tag)
        addProperty("listen", "127.0.0.1")
        addProperty("port", port)
        addProperty("protocol", protocol)
        add("settings", JsonObject().apply {
            if (protocol == "socks") {
                addProperty("udp", udp)
                addProperty("auth", "noauth")
            }
        })
    }

    private fun tagged(tag: String, protocol: String) = JsonObject().apply {
        addProperty("tag", tag)
        addProperty("protocol", protocol)
    }
}
