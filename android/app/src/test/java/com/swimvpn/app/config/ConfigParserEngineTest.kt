package com.swimvpn.app.config

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ConfigParserEngineTest {

    @Test
    fun `vless reality tcp treats empty tcp header params as absent`() {
        val input = "vless://11111111-1111-1111-1111-111111111111@test.example.com:443?security=reality&type=tcp&headerType=&path=&host=&flow=xtls-rprx-vision&sni=tradingview.com&fp=qq&pbk=PUBLICKEY123&sid=50#Provider%20Node"

        val result = ConfigParserEngine.parseConfig(input, SourceType.SUBSCRIPTION_URL)
        val profile = result.profile

        assertTrue(result.errors.joinToString(), result.isValid)
        assertNotNull(profile)
        assertEquals(Protocol.VLESS, profile!!.protocol)
        assertEquals(SecurityMode.REALITY, profile.securityMode)
        assertEquals(Transport.TCP, profile.transport)
        assertEquals("none", profile.tcpSettings?.headerType)
        assertEquals(null, profile.tcpSettings?.host)
        assertEquals(input, profile.rawConfig)
    }

    @Test
    fun `shadowsocks plugin metadata is preserved but runtime unsupported`() {
        val input = "ss://YWVzLTI1Ni1nY206c2VjcmV0@ss.example.com:8388?plugin=v2ray-plugin&plugin-opts=tls%3Bhost%3Dfront.example.com#SS"

        val result = ConfigParserEngine.parseConfig(input, SourceType.SUBSCRIPTION_URL)
        val normalized = ConfigNormalizationEngine.normalizeProfile(result)

        assertTrue(result.errors.joinToString(), result.isValid)
        assertNotNull(normalized)
        assertNotNull(normalized!!.shadowsocksPluginSettings)
        assertEquals("v2ray-plugin", normalized.shadowsocksPluginSettings?.plugin)

        val support = TunnelRuntimeAdapter.isProfileSupported(normalized)
        assertEquals(false, support.first)
        assertTrue(support.second.contains("plugin obfuscation"))
    }

    @Test
    fun `vless reality requires public key before runtime generation`() {
        val input = "vless://11111111-1111-1111-1111-111111111111@test.example.com:443?security=reality&type=tcp&sni=tradingview.com&fp=chrome&sid=50#MissingKey"

        val result = ConfigParserEngine.parseConfig(input, SourceType.SUBSCRIPTION_URL)
        val normalized = ConfigNormalizationEngine.normalizeProfile(result)

        assertTrue(result.errors.joinToString(), result.isValid)
        assertNotNull(normalized)

        val support = TunnelRuntimeAdapter.isProfileSupported(normalized!!)
        assertEquals(false, support.first)
        assertTrue(support.second.contains("public key"))
    }

    @Test
    fun `complete vless reality profile is runtime supported`() {
        val input = "vless://11111111-1111-1111-1111-111111111111@test.example.com:443?security=reality&type=tcp&sni=tradingview.com&fp=chrome&pbk=PUBLICKEY123&sid=50#Reality"

        val result = ConfigParserEngine.parseConfig(input, SourceType.SUBSCRIPTION_URL)
        val normalized = ConfigNormalizationEngine.normalizeProfile(result)

        assertTrue(result.errors.joinToString(), result.isValid)
        assertNotNull(normalized)

        val support = TunnelRuntimeAdapter.isProfileSupported(normalized!!)
        assertEquals(true, support.first)
    }

}
