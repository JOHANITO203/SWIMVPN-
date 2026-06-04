package com.swimvpn.app.config

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class XrayRoutingBuilderTest {

    @Test
    fun `default options are a no-op`() {
        val options = RoutingOptions()
        assertFalse(XrayRoutingBuilder.hasActiveRules(options))
        assertEquals(0, XrayRoutingBuilder.buildRules(options).size())
    }

    @Test
    fun `bypass on routes private space direct`() {
        val options = RoutingOptions(bypassGeo = true)
        assertTrue(XrayRoutingBuilder.hasActiveRules(options))
        val rules = XrayRoutingBuilder.buildRules(options)
        assertTrue(rules.size() >= 1)
        val first = rules[0].asJsonObject
        assertEquals("direct", first["outboundTag"].asString)
        assertTrue(first.getAsJsonArray("ip").any { it.asString == "geoip:private" })
    }

    @Test
    fun `block domains produce a block rule`() {
        val rules = XrayRoutingBuilder.buildRules(RoutingOptions(bypassGeo = true, blockDomains = listOf("ads.example")))
        assertTrue(rules.any { it.asJsonObject["outboundTag"].asString == "block" })
    }

    @Test
    fun `explicit direct domains activate rules even with bypass off`() {
        val options = RoutingOptions(bypassGeo = false, directDomains = listOf("intra.corp"))
        assertTrue(XrayRoutingBuilder.hasActiveRules(options))
        assertTrue(XrayRoutingBuilder.buildRules(options).size() >= 1)
    }

    @Test
    fun `sanitizeEntry trims and rejects blank or spaced entries`() {
        assertEquals("example.com", XrayRoutingBuilder.sanitizeEntry("  example.com "))
        assertNull(XrayRoutingBuilder.sanitizeEntry("   "))
        assertNull(XrayRoutingBuilder.sanitizeEntry("a b.com"))
    }

    @Test
    fun `isIpEntry classifies ip, cidr and geoip but not domains`() {
        assertTrue(XrayRoutingBuilder.isIpEntry("1.2.3.4"))
        assertTrue(XrayRoutingBuilder.isIpEntry("10.0.0.0/8"))
        assertTrue(XrayRoutingBuilder.isIpEntry("geoip:ru"))
        assertTrue(XrayRoutingBuilder.isIpEntry("2001:db8::1"))
        assertFalse(XrayRoutingBuilder.isIpEntry("example.com"))
        assertFalse(XrayRoutingBuilder.isIpEntry("domain:example.com"))
        assertFalse(XrayRoutingBuilder.isIpEntry("geosite:google"))
    }

    @Test
    fun `partitionDirectEntries splits domains from ips and dedupes`() {
        val (domains, ips) = XrayRoutingBuilder.partitionDirectEntries(
            listOf("example.com", "1.2.3.0/24", "example.com", "geoip:ru", "  ", "bad host"),
        )
        assertEquals(listOf("example.com"), domains)
        assertEquals(listOf("1.2.3.0/24", "geoip:ru"), ips)
    }

    @Test
    fun `buildRules emits user domains and ips as direct`() {
        val rules = XrayRoutingBuilder.buildRules(
            RoutingOptions(
                bypassGeo = true,
                directDomains = listOf("example.com"),
                directIps = listOf("geoip:private", "1.2.3.0/24"),
            ),
        )
        val directIps = rules.first {
            it.asJsonObject["outboundTag"].asString == "direct" && it.asJsonObject.has("ip")
        }.asJsonObject.getAsJsonArray("ip")
        assertTrue(directIps.any { it.asString == "1.2.3.0/24" })
        assertTrue(
            rules.any { r -> r.asJsonObject.has("domain") && r.asJsonObject.getAsJsonArray("domain").any { it.asString == "example.com" } },
        )
    }
}
