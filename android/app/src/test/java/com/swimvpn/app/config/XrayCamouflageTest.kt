package com.swimvpn.app.config

import com.swimvpn.app.vpn.RuntimeMode
import org.junit.Assert.assertEquals
import org.junit.Test

/** The camouflage fingerprint override is applied post-build, only to TLS/Reality streamSettings. */
class XrayCamouflageTest {

    private fun streamOf(profile: SwimVpnProfile, fingerprint: String?) =
        TunnelRuntimeAdapter.generateXrayRuntimeDocument(profile, RuntimeMode.FULL_TUNNEL, camouflageProfileId = fingerprint)
            ?.getAsJsonArray("outbounds")?.first()?.asJsonObject?.getAsJsonObject("streamSettings")
            ?: error("runtime document / streamSettings must exist")

    @Test
    fun `override sets reality fingerprint`() {
        val stream = streamOf(realityProfile(), "firefox")
        assertEquals("firefox", stream.getAsJsonObject("realitySettings")["fingerprint"].asString)
    }

    @Test
    fun `override sets tls fingerprint`() {
        val stream = streamOf(wsTlsProfile(), "safari")
        assertEquals("safari", stream.getAsJsonObject("tlsSettings")["fingerprint"].asString)
    }

    @Test
    fun `no override keeps reality default`() {
        val stream = streamOf(realityProfile(), null)
        assertEquals("chrome", stream.getAsJsonObject("realitySettings")["fingerprint"].asString)
    }

    @Test
    fun `override is a no-op on a plaintext transport`() {
        // securityMode NONE => no tlsSettings/realitySettings block exists => nothing to override.
        val stream = streamOf(plainProfile(), "firefox")
        assertEquals("none", stream["security"].asString)
        assert(!stream.has("tlsSettings"))
        assert(!stream.has("realitySettings"))
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

    private fun wsTlsProfile() = base(SecurityMode.TLS, Transport.WEBSOCKET).copy(
        websocketSettings = WebsocketSettings(path = "/p", host = "cdn.example.com"),
        tlsSettings = TlsSettings(sni = "cdn.example.com"),
    )

    private fun plainProfile() = base(SecurityMode.NONE, Transport.TCP).copy(tcpSettings = TcpSettings())
}
