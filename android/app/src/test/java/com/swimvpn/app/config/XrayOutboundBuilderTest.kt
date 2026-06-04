package com.swimvpn.app.config

import com.google.gson.JsonObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class XrayOutboundBuilderTest {

    private fun profile(
        protocol: Protocol,
        transport: Transport = Transport.TCP,
        security: SecurityMode = SecurityMode.TLS,
        userId: String? = null,
        password: String? = null,
        method: String? = null,
        flow: String? = null,
        tls: TlsSettings? = TlsSettings(sni = "example.com"),
    ) = SwimVpnProfile(
        sourceType = SourceType.MANUAL_ENTRY,
        rawConfig = "test",
        sourceFormat = SourceFormat.UNKNOWN,
        protocol = protocol,
        transport = transport,
        securityMode = security,
        address = "example.com",
        port = 443,
        userId = userId,
        password = password,
        method = method,
        flow = flow,
        tlsSettings = tls,
        displayName = "n",
        displaySubtitle = "s",
    )

    private fun firstUser(outbound: JsonObject): JsonObject =
        outbound.getAsJsonObject("settings")
            .getAsJsonArray("vnext")[0].asJsonObject
            .getAsJsonArray("users")[0].asJsonObject

    private fun firstServer(outbound: JsonObject): JsonObject =
        outbound.getAsJsonObject("settings")
            .getAsJsonArray("servers")[0].asJsonObject

    @Test
    fun `vless emits flow on the user when present`() {
        val o = XrayOutboundBuilder.vless(profile(Protocol.VLESS, userId = "uuid", flow = "xtls-rprx-vision"))
        assertEquals("proxy", o["tag"].asString)
        assertEquals("vless", o["protocol"].asString)
        assertTrue(o.has("streamSettings"))
        assertEquals("xtls-rprx-vision", firstUser(o)["flow"].asString)
    }

    @Test
    fun `vless omits flow entirely when absent`() {
        val o = XrayOutboundBuilder.vless(profile(Protocol.VLESS, userId = "uuid", flow = null))
        assertFalse("must never emit an empty flow", firstUser(o).has("flow"))
    }

    @Test
    fun `vmess uses alterId 0 and security auto`() {
        val o = XrayOutboundBuilder.vmess(profile(Protocol.VMESS, userId = "uuid"))
        assertEquals("vmess", o["protocol"].asString)
        val user = firstUser(o)
        assertEquals(0, user["alterId"].asInt)
        assertEquals("auto", user["security"].asString)
    }

    @Test
    fun `trojan carries password and streamSettings`() {
        val o = XrayOutboundBuilder.trojan(profile(Protocol.TROJAN, password = "pwd"))
        assertEquals("proxy", o["tag"].asString)
        assertEquals("trojan", o["protocol"].asString)
        assertEquals("pwd", firstServer(o)["password"].asString)
        assertTrue(o.has("streamSettings"))
    }

    @Test
    fun `shadowsocks carries method+password and has no streamSettings`() {
        val o = XrayOutboundBuilder.shadowsocks(
            profile(Protocol.SHADOWSOCKS, password = "pwd", method = "aes-256-gcm", security = SecurityMode.NONE, tls = null),
        )
        assertEquals("shadowsocks", o["protocol"].asString)
        val server = firstServer(o)
        assertEquals("aes-256-gcm", server["method"].asString)
        assertEquals("pwd", server["password"].asString)
        assertFalse("shadowsocks rides plain TCP, no streamSettings", o.has("streamSettings"))
    }

    @Test
    fun `forProfile returns null for BYO proxy protocols`() {
        assertNull(XrayOutboundBuilder.forProfile(profile(Protocol.SOCKS5, security = SecurityMode.NONE, tls = null)))
        assertNull(XrayOutboundBuilder.forProfile(profile(Protocol.HTTP, security = SecurityMode.NONE, tls = null)))
    }
}
