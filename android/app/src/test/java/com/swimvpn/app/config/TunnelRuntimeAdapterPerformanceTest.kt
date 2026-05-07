package com.swimvpn.app.config

import com.swimvpn.app.vpn.RuntimeMode
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class TunnelRuntimeAdapterPerformanceTest {

    @Test
    fun `generated full tunnel runtime does not enable unused xray stats`() {
        val document = TunnelRuntimeAdapter.generateXrayRuntimeDocument(
            profile = vlessRealityProfile(),
            runtimeMode = RuntimeMode.FULL_TUNNEL,
        ) ?: error("runtime document must be generated")

        assertFalse("generated runtime should not include unused xray stats", document.has("stats"))
        val policy = document.getAsJsonObject("policy")
        assertFalse("generated runtime should not request inbound stats", policy.toString().contains("statsInbound"))
        assertFalse("generated runtime should not request outbound stats", policy.toString().contains("statsOutbound"))
    }

    @Test
    fun `generated full tunnel runtime disables inbound sniffing when routing rules are empty`() {
        val document = TunnelRuntimeAdapter.generateXrayRuntimeDocument(
            profile = vlessRealityProfile(),
            runtimeMode = RuntimeMode.FULL_TUNNEL,
        ) ?: error("runtime document must be generated")

        val inbounds = document.getAsJsonArray("inbounds")
        assertTrue("expected standard inbounds", inbounds.size() > 0)
        inbounds.forEach { inbound ->
            val sniffing = inbound.asJsonObject.getAsJsonObject("sniffing")
            assertFalse("empty-routing runtime should not sniff inbound traffic", sniffing["enabled"].asBoolean)
        }
    }

    @Test
    fun `generated runtime keeps proxy outbound valid`() {
        val document = TunnelRuntimeAdapter.generateXrayRuntimeDocument(
            profile = vlessRealityProfile(),
            runtimeMode = RuntimeMode.FULL_TUNNEL,
        ) ?: error("runtime document must be generated")

        val outbounds = document.getAsJsonArray("outbounds")
        assertTrue(outbounds.size() >= 1)
        val proxy = outbounds.first().asJsonObject
        assertTrue(proxy["tag"].asString == "proxy")
        assertTrue(proxy["protocol"].asString == "vless")
        assertTrue(proxy.getAsJsonObject("streamSettings")["security"].asString == "reality")
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
        realitySettings = RealitySettings(
            publicKey = "public-key",
            shortId = "abcd",
            spiderX = "/",
        ),
        tlsSettings = TlsSettings(
            sni = "www.microsoft.com",
            fingerprint = "chrome",
        ),
        tcpSettings = TcpSettings(),
        displayName = "Reality node",
        displaySubtitle = "example.com:443",
    )
}
