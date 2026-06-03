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
    fun `socks5 url with auth parses to a SOCKS5 proxy profile`() {
        val result = ConfigParserEngine.parseConfig("socks5://user1:pass1@1.2.3.4:1080#Home", SourceType.CLIPBOARD)
        val profile = result.profile

        assertTrue(result.errors.joinToString(), result.isValid)
        assertNotNull(profile)
        assertEquals(Protocol.SOCKS5, profile!!.protocol)
        assertEquals("1.2.3.4", profile.address)
        assertEquals(1080, profile.port)
        assertEquals("user1", profile.userId)
        assertEquals("pass1", profile.password)
        assertEquals("Home", profile.displayName)
    }

    @Test
    fun `bare host-port-user-pass parses to a SOCKS5 proxy profile`() {
        val result = ConfigParserEngine.parseConfig("proxy.example.com:8000:bob:secret", SourceType.CLIPBOARD)
        val profile = result.profile

        assertTrue(result.errors.joinToString(), result.isValid)
        assertNotNull(profile)
        assertEquals(Protocol.SOCKS5, profile!!.protocol)
        assertEquals("proxy.example.com", profile.address)
        assertEquals(8000, profile.port)
        assertEquals("bob", profile.userId)
        assertEquals("secret", profile.password)
    }

    @Test
    fun `socks5 url without auth parses with null credentials`() {
        val result = ConfigParserEngine.parseConfig("socks5://10.0.0.9:1080", SourceType.CLIPBOARD)
        val profile = result.profile

        assertTrue(result.isValid)
        assertNotNull(profile)
        assertEquals(Protocol.SOCKS5, profile!!.protocol)
        assertEquals("10.0.0.9", profile.address)
        assertEquals(1080, profile.port)
        assertEquals(null, profile.userId)
    }
}
