package com.swimvpn.app.config

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class VpnConfigLinkExtractorTest {
    @Test
    fun `does not split ss scheme inside vless scheme`() {
        val vless = "vless://uuid@example.com:443?security=reality&type=tcp#Example"

        val entries = VpnConfigLinkExtractor.extractEntries(vless)

        assertEquals(listOf(vless), entries)
    }

    @Test
    fun `extracts mixed multiline provider links`() {
        val vless = "vless://uuid@example.com:443?security=reality&type=tcp#VLESS"
        val trojan = "trojan://password@example.net:443?security=tls&type=tcp#Trojan"
        val shadowsocks = "ss://YWVzLTI1Ni1nY206cGFzcw@example.org:8388#SS"
        val payload = "$vless\n$trojan\r\n$shadowsocks"

        val entries = VpnConfigLinkExtractor.extractEntries(payload)

        assertEquals(listOf(vless, trojan, shadowsocks), entries)
    }

    @Test
    fun `extracts links embedded in json strings`() {
        val vless = "vless://uuid@example.com:443?security=reality&type=tcp#VLESS"
        val trojan = "trojan://password@example.net:443?security=tls&type=tcp#Trojan"
        val payload = """{"servers":["$vless","$trojan"]}"""

        val entries = VpnConfigLinkExtractor.extractEntries(payload)

        assertEquals(listOf(vless, trojan), entries)
    }

    @Test
    fun `recognizes unsupported modern links without runtime support`() {
        val payload = "hy2://password@example.com:443?obfs=salamander#HY2"

        assertTrue(VpnConfigLinkExtractor.containsRecognizedLink(payload))
        assertEquals(listOf(payload), VpnConfigLinkExtractor.extractEntries(payload))
    }
}
