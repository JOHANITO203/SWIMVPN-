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
}
