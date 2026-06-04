package com.swimvpn.app.config

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Pin the streamSettings emission for every (transport × security) combination, including the
 * Phase-2 fixes: complete Reality, functional KCP/QUIC, and XTLS expressed as a TLS stream.
 */
class XrayStreamSettingsBuilderTest {

    private fun profile(
        transport: Transport,
        security: SecurityMode,
        tcp: TcpSettings? = null,
        ws: WebsocketSettings? = null,
        grpc: GrpcSettings? = null,
        tls: TlsSettings? = null,
        reality: RealitySettings? = null,
        flow: String? = null,
        advanced: Map<String, String> = emptyMap(),
    ) = SwimVpnProfile(
        sourceType = SourceType.MANUAL_ENTRY,
        rawConfig = "test",
        sourceFormat = SourceFormat.UNKNOWN,
        protocol = Protocol.VLESS,
        transport = transport,
        securityMode = security,
        address = "example.com",
        port = 443,
        tcpSettings = tcp,
        websocketSettings = ws,
        grpcSettings = grpc,
        tlsSettings = tls,
        realitySettings = reality,
        flow = flow,
        advancedSettings = advanced,
        displayName = "n",
        displaySubtitle = "s",
    )

    @Test
    fun `tcp with default header omits tcpSettings`() {
        val s = XrayStreamSettingsBuilder.build(profile(Transport.TCP, SecurityMode.NONE))
        assertEquals("tcp", s["network"].asString)
        assertEquals("none", s["security"].asString)
        assertFalse("default-header TCP should not emit tcpSettings", s.has("tcpSettings"))
    }

    @Test
    fun `tcp with obfuscation header emits header type`() {
        val s = XrayStreamSettingsBuilder.build(
            profile(Transport.TCP, SecurityMode.NONE, tcp = TcpSettings(headerType = "http", host = "a.com")),
        )
        assertEquals("http", s.getAsJsonObject("tcpSettings").getAsJsonObject("header")["type"].asString)
        assertEquals("a.com", s.getAsJsonObject("tcpSettings").getAsJsonObject("header")["host"].asString)
    }

    @Test
    fun `websocket over tls emits ws and tls settings`() {
        val s = XrayStreamSettingsBuilder.build(
            profile(
                Transport.WEBSOCKET,
                SecurityMode.TLS,
                ws = WebsocketSettings(path = "/p", host = "cdn.com"),
                tls = TlsSettings(sni = "cdn.com", alpn = listOf("h2", "http/1.1")),
            ),
        )
        assertEquals("ws", s["network"].asString)
        assertEquals("tls", s["security"].asString)
        assertEquals("/p", s.getAsJsonObject("wsSettings")["path"].asString)
        assertEquals("cdn.com", s.getAsJsonObject("wsSettings").getAsJsonObject("headers")["Host"].asString)
        assertEquals("cdn.com", s.getAsJsonObject("tlsSettings")["serverName"].asString)
        assertEquals(2, s.getAsJsonObject("tlsSettings").getAsJsonArray("alpn").size())
    }

    @Test
    fun `grpc emits multiMode from mode`() {
        val gun = XrayStreamSettingsBuilder.build(
            profile(Transport.GRPC, SecurityMode.TLS, grpc = GrpcSettings("svc", "gun"), tls = TlsSettings(sni = "g.com")),
        )
        assertEquals("grpc", gun["network"].asString)
        assertEquals("svc", gun.getAsJsonObject("grpcSettings")["serviceName"].asString)
        assertFalse(gun.getAsJsonObject("grpcSettings")["multiMode"].asBoolean)

        val multi = XrayStreamSettingsBuilder.build(
            profile(Transport.GRPC, SecurityMode.TLS, grpc = GrpcSettings("svc", "multi"), tls = TlsSettings(sni = "g.com")),
        )
        assertTrue(multi.getAsJsonObject("grpcSettings")["multiMode"].asBoolean)
    }

    @Test
    fun `http2 emits path and host array`() {
        val s = XrayStreamSettingsBuilder.build(
            profile(Transport.HTTP2, SecurityMode.TLS, ws = WebsocketSettings(path = "/h"), tls = TlsSettings(sni = "h2.com")),
        )
        assertEquals("http", s["network"].asString)
        assertEquals("/h", s.getAsJsonObject("httpSettings")["path"].asString)
        assertTrue(s.getAsJsonObject("httpSettings").getAsJsonArray("host").any { it.asString == "h2.com" })
    }

    @Test
    fun `reality emits the complete field set`() {
        val s = XrayStreamSettingsBuilder.build(
            profile(
                Transport.TCP,
                SecurityMode.REALITY,
                reality = RealitySettings("PK", "sid", "/spx"),
                tls = TlsSettings(sni = "www.ms.com", fingerprint = "firefox"),
                flow = "xtls-rprx-vision",
            ),
        )
        assertEquals("reality", s["security"].asString)
        val r = s.getAsJsonObject("realitySettings")
        assertEquals("PK", r["publicKey"].asString)
        assertEquals("sid", r["shortId"].asString)
        assertEquals("www.ms.com", r["serverName"].asString)
        assertEquals("/spx", r["spiderX"].asString)
        assertEquals("firefox", r["fingerprint"].asString)
    }

    @Test
    fun `reality defaults fingerprint when absent`() {
        val s = XrayStreamSettingsBuilder.build(
            profile(Transport.TCP, SecurityMode.REALITY, reality = RealitySettings("PK", "sid"), tls = TlsSettings(sni = "x")),
        )
        assertEquals("chrome", s.getAsJsonObject("realitySettings")["fingerprint"].asString)
    }

    @Test
    fun `kcp emits functional settings`() {
        val s = XrayStreamSettingsBuilder.build(
            profile(Transport.KCP, SecurityMode.NONE, advanced = mapOf("headerType" to "wechat-video", "seed" to "sd")),
        )
        assertEquals("kcp", s["network"].asString)
        val k = s.getAsJsonObject("kcpSettings")
        assertEquals(1350, k["mtu"].asInt)
        assertEquals("wechat-video", k.getAsJsonObject("header")["type"].asString)
        assertEquals("sd", k["seed"].asString)
    }

    @Test
    fun `quic emits functional settings`() {
        val s = XrayStreamSettingsBuilder.build(
            profile(
                Transport.QUIC,
                SecurityMode.TLS,
                tls = TlsSettings(sni = "q.com"),
                advanced = mapOf("quicSecurity" to "aes-128-gcm", "quicKey" to "k"),
            ),
        )
        assertEquals("quic", s["network"].asString)
        val q = s.getAsJsonObject("quicSettings")
        assertEquals("aes-128-gcm", q["security"].asString)
        assertEquals("k", q["key"].asString)
        assertTrue(q.has("header"))
        assertTrue("QUIC keeps its declared TLS settings", s.has("tlsSettings"))
    }

    @Test
    fun `xtls is expressed as a tls stream not xtls`() {
        val s = XrayStreamSettingsBuilder.build(
            profile(Transport.TCP, SecurityMode.XTLS, tls = TlsSettings(sni = "x.com"), flow = "xtls-rprx-vision"),
        )
        assertEquals("tls", s["security"].asString)
        assertTrue(s.has("tlsSettings"))
    }

    @Test
    fun `security and transport maps are correct`() {
        assertEquals("tls", XrayStreamSettingsBuilder.mapSecurity(SecurityMode.XTLS))
        assertEquals("reality", XrayStreamSettingsBuilder.mapSecurity(SecurityMode.REALITY))
        assertEquals("none", XrayStreamSettingsBuilder.mapSecurity(SecurityMode.NONE))
        assertEquals("http", XrayStreamSettingsBuilder.mapTransport(Transport.HTTP2))
        assertEquals("ws", XrayStreamSettingsBuilder.mapTransport(Transport.WEBSOCKET))
    }
}
