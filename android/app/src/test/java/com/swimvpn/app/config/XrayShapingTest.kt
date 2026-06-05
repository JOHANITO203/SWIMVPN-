package com.swimvpn.app.config

import com.swimvpn.app.vpn.RuntimeMode
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test

/** Stage A: TLS-shaping (sockopt.fragment/noises) is applied post-build; AUTO is a no-op. */
class XrayShapingTest {

    private fun streamOf(profileId: String?) =
        TunnelRuntimeAdapter.generateXrayRuntimeDocument(realityProfile(), RuntimeMode.FULL_TUNNEL, camouflageProfileId = profileId)
            ?.getAsJsonArray("outbounds")?.first()?.asJsonObject?.getAsJsonObject("streamSettings")
            ?: error("runtime document / streamSettings must exist")

    @Test
    fun `auto adds no sockopt (byte-identical regression guard)`() {
        assertFalse(streamOf("auto").has("sockopt"))
    }

    @Test
    fun `frag_light injects sockopt fragment and keeps the link reality fp`() {
        val stream = streamOf("frag_light")
        val fragment = stream.getAsJsonObject("sockopt").getAsJsonObject("fragment")
        assertEquals("tlshello", fragment["packets"].asString)
        assertEquals("100-200", fragment["length"].asString)
        assertEquals("chrome", stream.getAsJsonObject("realitySettings")["fingerprint"].asString)
    }

    @Test
    fun `null profile adds no sockopt`() {
        assertFalse(streamOf(null).has("sockopt"))
    }

    private fun base(security: SecurityMode, transport: Transport) = SwimVpnProfile(
        sourceType = SourceType.MANUAL_ENTRY,
        rawConfig = "x",
        sourceFormat = SourceFormat.VLESS_URL,
        protocol = Protocol.VLESS,
        transport = transport,
        securityMode = security,
        address = "example.com",
        port = 443,
        userId = "00000000-0000-0000-0000-000000000000",
        displayName = "n",
        displaySubtitle = "s",
    )

    private fun realityProfile() = base(SecurityMode.REALITY, Transport.TCP).copy(
        realitySettings = RealitySettings("pk", "sid", "/"),
        tlsSettings = TlsSettings(sni = "www.microsoft.com"),
        tcpSettings = TcpSettings(),
    )
}
