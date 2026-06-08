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
    fun `generated full tunnel runtime enables sniffing for FakeDNS hostname recovery`() {
        // FakeDNS serves synthetic IPs, so xray must sniff the TLS/HTTP SNI to recover the real hostname
        // and resolve it at the exit. Sniffing in the default full-tunnel path is therefore required, not
        // a perf waste; this test previously asserted the obsolete pre-FakeDNS no-sniff contract.
        val document = TunnelRuntimeAdapter.generateXrayRuntimeDocument(
            profile = vlessRealityProfile(),
            runtimeMode = RuntimeMode.FULL_TUNNEL,
        ) ?: error("runtime document must be generated")

        val inbounds = document.getAsJsonArray("inbounds")
        assertTrue("expected standard inbounds", inbounds.size() > 0)
        inbounds.forEach { inbound ->
            val sniffing = inbound.asJsonObject.getAsJsonObject("sniffing")
            assertTrue("FakeDNS requires sniffing to recover the hostname from SNI", sniffing["enabled"].asBoolean)
            assertTrue(
                "fakedns must be a destOverride target",
                sniffing.getAsJsonArray("destOverride").any { it.asString == "fakedns" },
            )
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
