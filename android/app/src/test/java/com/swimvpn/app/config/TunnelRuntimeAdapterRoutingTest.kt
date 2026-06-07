package com.swimvpn.app.config

import com.swimvpn.app.vpn.RuntimeMode
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Regression guard + feature check for the routing/sniffing threading, plus a production-path
 * parity test proving the normalized-fragment path (VLESS/VMESS) now emits complete streamSettings.
 */
class TunnelRuntimeAdapterRoutingTest {

    @Test
    fun `default full tunnel routes dns to fakedns, fast-rejects ipv6, and sniffs for SNI recovery`() {
        // FakeDNS (shipped in the "connected but no internet" fix) answers DNS locally and recovers the
        // real hostname from the TLS/HTTP SNI, so the default full-tunnel path now ALWAYS carries a
        // dns-out (:53) rule, an IPv6 fast-reject (::/0 -> block) rule, and sniffing enabled. This guards
        // that contract; it previously asserted the obsolete pre-FakeDNS empty-routing / no-sniff behavior.
        val doc = TunnelRuntimeAdapter.generateXrayRuntimeDocument(
            profile = vlessRealityProfile(),
            runtimeMode = RuntimeMode.FULL_TUNNEL,
        ) ?: error("runtime document must be generated")

        val rules = doc.getAsJsonObject("routing").getAsJsonArray("rules")
        assertTrue(
            "default full tunnel must route :53 to dns-out (FakeDNS)",
            rules.any { it.asJsonObject.get("outboundTag")?.asString == "dns-out" },
        )
        assertTrue(
            "default full tunnel must blackhole literal IPv6 (::/0 -> block)",
            rules.any { r ->
                r.asJsonObject.get("outboundTag")?.asString == "block" &&
                    r.asJsonObject.getAsJsonArray("ip")?.any { it.asString == "::/0" } == true
            },
        )
        doc.getAsJsonArray("inbounds").forEach { inbound ->
            assertTrue(
                "FakeDNS recovers the hostname from SNI, so full tunnel must sniff",
                inbound.asJsonObject.getAsJsonObject("sniffing")["enabled"].asBoolean,
            )
        }
    }

    @Test
    fun `bypass geo injects rules and enables sniffing`() {
        val doc = TunnelRuntimeAdapter.generateXrayRuntimeDocument(
            profile = vlessRealityProfile(),
            runtimeMode = RuntimeMode.FULL_TUNNEL,
            routingOptions = RoutingOptions(bypassGeo = true),
        ) ?: error("runtime document must be generated")

        assertTrue(doc.getAsJsonObject("routing").getAsJsonArray("rules").size() > 0)
        doc.getAsJsonArray("inbounds").forEach { inbound ->
            assertTrue(
                "bypass-geo runtime must sniff for domain routing",
                inbound.asJsonObject.getAsJsonObject("sniffing")["enabled"].asBoolean,
            )
        }
        val socks = doc.getAsJsonArray("inbounds")
            .first { it.asJsonObject["tag"].asString == "socks-in" }.asJsonObject
        assertTrue(
            socks.getAsJsonObject("sniffing").getAsJsonArray("destOverride").any { it.asString == "quic" },
        )
    }

    @Test
    fun `normalized vless ws+tls path emits complete streamSettings`() {
        val parsed = ParseResult(profile = vlessWsTlsProfile(), errors = emptyList(), warnings = emptyList())
        val normalized = ConfigNormalizationEngine.normalizeProfile(parsed)
            ?: error("profile must normalize")
        val doc = TunnelRuntimeAdapter.generateXrayRuntimeDocument(normalized, RuntimeMode.FULL_TUNNEL)
            ?: error("runtime document must be generated")

        val proxy = doc.getAsJsonArray("outbounds").first().asJsonObject
        assertEquals("proxy", proxy["tag"].asString)
        assertEquals("vless", proxy["protocol"].asString)
        val stream = proxy.getAsJsonObject("streamSettings")
        assertEquals("ws", stream["network"].asString)
        assertEquals("tls", stream["security"].asString)
        assertEquals("/wspath", stream.getAsJsonObject("wsSettings")["path"].asString)
    }

    private fun vlessRealityProfile() = SwimVpnProfile(
        sourceType = SourceType.MANUAL_ENTRY,
        rawConfig = "vless://redacted@example.com:443?security=reality",
        sourceFormat = SourceFormat.VLESS_URL,
        protocol = Protocol.VLESS,
        transport = Transport.TCP,
        securityMode = SecurityMode.REALITY,
        address = "example.com",
        port = 443,
        userId = "00000000-0000-0000-0000-000000000000",
        flow = "xtls-rprx-vision",
        realitySettings = RealitySettings(publicKey = "public-key", shortId = "abcd", spiderX = "/"),
        tlsSettings = TlsSettings(sni = "www.microsoft.com", fingerprint = "chrome"),
        tcpSettings = TcpSettings(),
        displayName = "Reality node",
        displaySubtitle = "example.com:443",
    )

    private fun vlessWsTlsProfile() = SwimVpnProfile(
        sourceType = SourceType.MANUAL_ENTRY,
        rawConfig = "vless://redacted@example.com:443?type=ws&security=tls",
        sourceFormat = SourceFormat.VLESS_URL,
        protocol = Protocol.VLESS,
        transport = Transport.WEBSOCKET,
        securityMode = SecurityMode.TLS,
        address = "example.com",
        port = 443,
        userId = "00000000-0000-0000-0000-000000000000",
        websocketSettings = WebsocketSettings(path = "/wspath", host = "cdn.example.com"),
        tlsSettings = TlsSettings(sni = "cdn.example.com", alpn = listOf("h2", "http/1.1")),
        displayName = "WS node",
        displaySubtitle = "example.com:443",
    )
}
