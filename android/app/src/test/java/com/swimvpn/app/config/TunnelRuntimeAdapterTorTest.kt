package com.swimvpn.app.config

import com.swimvpn.app.tor.TorRuntimeConfig
import com.swimvpn.app.vpn.RuntimeMode
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Onion Stealth config-layer contract: TOR_TUNNEL inserts the embedded-Tor hop (app → Tor → REALITY)
 * without disturbing the other modes' documents.
 */
class TunnelRuntimeAdapterTorTest {

    private fun torDoc() = TunnelRuntimeAdapter.generateXrayRuntimeDocument(
        profile = vlessRealityProfile(),
        runtimeMode = RuntimeMode.TOR_TUNNEL,
    ) ?: error("TOR runtime document must be generated")

    @Test
    fun `tor outbound points at the embedded Tor SOCKS port`() {
        val tor = torDoc().getAsJsonArray("outbounds")
            .map { it.asJsonObject }
            .firstOrNull { it.get("tag")?.asString == "tor" }
            ?: error("a 'tor' outbound must exist")
        assertEquals("socks", tor.get("protocol").asString)
        val server = tor.getAsJsonObject("settings").getAsJsonArray("servers").first().asJsonObject
        assertEquals("127.0.0.1", server.get("address").asString)
        assertEquals(TorRuntimeConfig.SOCKS_PORT, server.get("port").asInt)
    }

    @Test
    fun `REALITY proxy outbound is preserved as Tor's exit transport`() {
        val outbounds = torDoc().getAsJsonArray("outbounds").map { it.asJsonObject }
        assertTrue("proxy (REALITY) outbound must still exist for Tor to exit through",
            outbounds.any { it.get("tag")?.asString == "proxy" })
    }

    @Test
    fun `tor egress inbound is exposed for Tor to route its guard connections through`() {
        val egress = torDoc().getAsJsonArray("inbounds")
            .map { it.asJsonObject }
            .firstOrNull { it.get("tag")?.asString == "tor-egress" }
            ?: error("a 'tor-egress' inbound must exist")
        assertEquals(TorRuntimeConfig.EGRESS_SOCKS_PORT, egress.get("port").asInt)
        assertEquals("socks", egress.get("protocol").asString)
    }

    @Test
    fun `routing sends app traffic to tor and tor egress to REALITY`() {
        val rules = torDoc().getAsJsonObject("routing").getAsJsonArray("rules").map { it.asJsonObject }

        val appToTor = rules.firstOrNull { it.get("outboundTag")?.asString == "tor" }
            ?: error("app traffic must route to 'tor'")
        val appInbounds = appToTor.getAsJsonArray("inboundTag").map { it.asString }
        assertTrue("socks-in must route to Tor", appInbounds.contains("socks-in"))
        // http-in must also route to Tor so it can never leak straight out via the default proxy.
        assertTrue("http-in must route to Tor (anti-leak)", appInbounds.contains("http-in"))

        val egressToProxy = rules.firstOrNull { r ->
            r.get("inboundTag")?.asJsonArray?.any { it.asString == "tor-egress" } == true
        } ?: error("tor-egress must route somewhere")
        assertEquals("proxy", egressToProxy.get("outboundTag").asString)
    }

    @Test
    fun `DNS is answered by FakeDNS and hostnames are passed as domains to Tor`() {
        val doc = torDoc()
        val rules = doc.getAsJsonObject("routing").getAsJsonArray("rules").map { it.asJsonObject }
        assertTrue("FakeDNS :53 rule must survive so no DNS query is tunnelled to the server",
            rules.any { it.get("outboundTag")?.asString == "dns-out" })
        // AsIs = never resolve destinations locally; the domain is handed to Tor which resolves it at
        // the exit. This is what keeps the user's destinations off your REALITY server.
        assertEquals("AsIs", doc.getAsJsonObject("routing").get("domainStrategy").asString)
    }

    @Test
    fun `full tunnel document is unaffected by the Tor chaining`() {
        val full = TunnelRuntimeAdapter.generateXrayRuntimeDocument(
            profile = vlessRealityProfile(),
            runtimeMode = RuntimeMode.FULL_TUNNEL,
        ) ?: error("full tunnel document must be generated")
        assertFalse("FULL_TUNNEL must NOT gain a tor outbound",
            full.getAsJsonArray("outbounds").any { it.asJsonObject.get("tag")?.asString == "tor" })
        assertFalse("FULL_TUNNEL must NOT gain a tor-egress inbound",
            full.getAsJsonArray("inbounds").any { it.asJsonObject.get("tag")?.asString == "tor-egress" })
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
}
