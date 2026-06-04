package com.swimvpn.app.config

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
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
}
