package com.swimvpn.app.config

import com.google.gson.JsonObject
import com.swimvpn.app.vpn.RuntimeMode
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class TunnelRuntimeAdapterSnapshotTest {

    @Test
    fun `vless reality tcp runtime preserves reality and flow fields`() {
        val document = requireRuntimeDocument(vlessRealityProfile())
        val proxy = proxyOutbound(document)
        val stream = proxy.getAsJsonObject("streamSettings")
        val reality = stream.getAsJsonObject("realitySettings")
        val user = proxy.getAsJsonObject("settings")
            .getAsJsonArray("vnext")[0].asJsonObject
            .getAsJsonArray("users")[0].asJsonObject

        assertEquals("vless", proxy["protocol"].asString)
        assertEquals("tcp", stream["network"].asString)
        assertEquals("reality", stream["security"].asString)
        assertEquals("PUBLICKEY123", reality["publicKey"].asString)
        assertEquals("50", reality["shortId"].asString)
        assertEquals("tradingview.com", reality["serverName"].asString)
        assertEquals("chrome", reality["fingerprint"].asString)
        assertEquals("xtls-rprx-vision", user["flow"].asString)
    }

    @Test
    fun `vmess websocket tls runtime preserves path host and sni`() {
        val document = requireRuntimeDocument(vmessWsTlsProfile())
        val proxy = proxyOutbound(document)
        val stream = proxy.getAsJsonObject("streamSettings")
        val ws = stream.getAsJsonObject("wsSettings")
        val tls = stream.getAsJsonObject("tlsSettings")

        assertEquals("vmess", proxy["protocol"].asString)
        assertEquals("ws", stream["network"].asString)
        assertEquals("tls", stream["security"].asString)
        assertEquals("/ws", ws["path"].asString)
        assertEquals("front.example.com", ws.getAsJsonObject("headers")["Host"].asString)
        assertEquals("sni.example.com", tls["serverName"].asString)
        assertEquals("firefox", tls["fingerprint"].asString)
    }

    @Test
    fun `trojan websocket tls runtime preserves password and transport metadata`() {
        val document = requireRuntimeDocument(trojanWsTlsProfile())
        val proxy = proxyOutbound(document)
        val server = proxy.getAsJsonObject("settings")
            .getAsJsonArray("servers")[0].asJsonObject
        val stream = proxy.getAsJsonObject("streamSettings")
        val ws = stream.getAsJsonObject("wsSettings")
        val tls = stream.getAsJsonObject("tlsSettings")

        assertEquals("trojan", proxy["protocol"].asString)
        assertEquals("secret-password", server["password"].asString)
        assertEquals("ws", stream["network"].asString)
        assertEquals("/trojan", ws["path"].asString)
        assertEquals("front.example.com", ws.getAsJsonObject("headers")["Host"].asString)
        assertEquals("tls.example.com", tls["serverName"].asString)
    }

    @Test
    fun `basic shadowsocks runtime excludes unsupported plugin fields`() {
        val document = requireRuntimeDocument(shadowsocksProfile())
        val proxy = proxyOutbound(document)
        val server = proxy.getAsJsonObject("settings")
            .getAsJsonArray("servers")[0].asJsonObject

        assertEquals("shadowsocks", proxy["protocol"].asString)
        assertEquals("aes-256-gcm", server["method"].asString)
        assertEquals("secret", server["password"].asString)
        assertFalse(server.has("plugin"))
        assertFalse(server.has("plugin_opts"))
    }

    @Test
    fun `imported xray json is augmented without replacing existing proxy outbound`() {
        val imported = """
            {
              "outbounds": [
                {
                  "tag": "proxy",
                  "protocol": "vless",
                  "settings": {
                    "vnext": [
                      {
                        "address": "json.example.com",
                        "port": 443,
                        "users": [{ "id": "33333333-3333-4333-8333-333333333333", "encryption": "none" }]
                      }
                    ]
                  },
                  "streamSettings": { "network": "tcp", "security": "none" }
                }
              ],
              "routing": { "rules": [{ "type": "field", "outboundTag": "proxy" }] }
            }
        """.trimIndent()
        val profile = vlessRealityProfile().copy(
            sourceFormat = SourceFormat.JSON_XRAY,
            rawConfig = imported,
            normalizedRuntimeConfig = imported,
        )

        val document = requireRuntimeDocument(profile)
        val outbounds = document.getAsJsonArray("outbounds")
        val inbounds = document.getAsJsonArray("inbounds")
        val routingRules = document.getAsJsonObject("routing").getAsJsonArray("rules")

        assertEquals("json.example.com", proxyOutbound(document)
            .getAsJsonObject("settings")
            .getAsJsonArray("vnext")[0].asJsonObject["address"].asString)
        assertTrue("standard inbounds should be added", inbounds.size() >= 2)
        assertTrue("direct outbound should be added", outbounds.any { it.asJsonObject["tag"].asString == "direct" })
        assertTrue("block outbound should be added", outbounds.any { it.asJsonObject["tag"].asString == "block" })
        assertEquals(1, routingRules.size())
    }

    private fun requireRuntimeDocument(profile: SwimVpnProfile): JsonObject {
        val support = TunnelRuntimeAdapter.isProfileSupported(profile)
        assertTrue(support.second, support.first)
        return TunnelRuntimeAdapter.generateXrayRuntimeDocument(profile, RuntimeMode.FULL_TUNNEL)
            ?: error("runtime document must be generated")
    }

    private fun proxyOutbound(document: JsonObject): JsonObject {
        val outbounds = document.getAsJsonArray("outbounds")
        assertNotNull(outbounds)
        return outbounds.first { it.asJsonObject["tag"].asString == "proxy" }.asJsonObject
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
        userId = "11111111-1111-4111-8111-111111111111",
        flow = "xtls-rprx-vision",
        realitySettings = RealitySettings(
            publicKey = "PUBLICKEY123",
            shortId = "50",
            spiderX = "/",
        ),
        tlsSettings = TlsSettings(
            sni = "tradingview.com",
            fingerprint = "chrome",
        ),
        tcpSettings = TcpSettings(),
        displayName = "Reality node",
        displaySubtitle = "example.com:443",
    )

    private fun vmessWsTlsProfile() = SwimVpnProfile(
        sourceType = SourceType.MANUAL_ENTRY,
        rawConfig = "vmess://redacted",
        sourceFormat = SourceFormat.VMESS_URL,
        protocol = Protocol.VMESS,
        transport = Transport.WEBSOCKET,
        securityMode = SecurityMode.TLS,
        address = "vmess.example.com",
        port = 443,
        userId = "22222222-2222-4222-8222-222222222222",
        websocketSettings = WebsocketSettings(
            path = "/ws",
            host = "front.example.com",
        ),
        tlsSettings = TlsSettings(
            sni = "sni.example.com",
            fingerprint = "firefox",
        ),
        displayName = "VMess WS",
        displaySubtitle = "vmess.example.com:443",
    )

    private fun trojanWsTlsProfile() = SwimVpnProfile(
        sourceType = SourceType.MANUAL_ENTRY,
        rawConfig = "trojan://redacted",
        sourceFormat = SourceFormat.TROJAN_URL,
        protocol = Protocol.TROJAN,
        transport = Transport.WEBSOCKET,
        securityMode = SecurityMode.TLS,
        address = "trojan.example.com",
        port = 443,
        password = "secret-password",
        websocketSettings = WebsocketSettings(
            path = "/trojan",
            host = "front.example.com",
        ),
        tlsSettings = TlsSettings(
            sni = "tls.example.com",
        ),
        displayName = "Trojan WS",
        displaySubtitle = "trojan.example.com:443",
    )

    private fun shadowsocksProfile() = SwimVpnProfile(
        sourceType = SourceType.MANUAL_ENTRY,
        rawConfig = "ss://redacted",
        sourceFormat = SourceFormat.SHADOWSOCKS_URL,
        protocol = Protocol.SHADOWSOCKS,
        transport = Transport.TCP,
        securityMode = SecurityMode.NONE,
        address = "ss.example.com",
        port = 8388,
        method = "aes-256-gcm",
        password = "secret",
        displayName = "SS",
        displaySubtitle = "ss.example.com:8388",
    )
}
