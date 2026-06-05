package com.swimvpn.app.config

import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

class CamouflageProfileRepositoryTest {

    @Test
    fun `default is auto and carries a blank fingerprint so the link's own fp is preserved`() {
        assertEquals("auto", CamouflageProfileRepository.DEFAULT.id)
        assertEquals("", CamouflageProfileRepository.DEFAULT.fingerprint)
    }

    @Test
    fun `all presets present with matching fingerprints (auto excepted)`() {
        val ids = CamouflageProfileRepository.all().map { it.id }
        assertEquals(listOf("auto", "chrome", "firefox", "safari", "ios", "randomized", "frag_light", "frag_aggressive"), ids)
        // AUTO intentionally has a blank fingerprint (no override); browser profiles match their id.
        assertEquals("", CamouflageProfileRepository.AUTO.fingerprint)
        CamouflageProfileRepository.all()
            .filter { it.id != "auto" && !it.id.startsWith("frag") }
            .forEach { assertEquals(it.id, it.fingerprint) }
    }

    @Test
    fun `byId resolves known and falls back to default on unknown or null`() {
        assertEquals("firefox", CamouflageProfileRepository.byId("firefox").id)
        assertEquals("auto", CamouflageProfileRepository.byId("auto").id)
        assertSame(CamouflageProfileRepository.DEFAULT, CamouflageProfileRepository.byId(null))
        assertSame(CamouflageProfileRepository.DEFAULT, CamouflageProfileRepository.byId("nope"))
    }

    @Test
    fun `fallback order starts with auto and contains cascade profiles (not shaping presets)`() {
        assertEquals("auto", CamouflageProfileRepository.fallbackOrder.first().id)
        val cascadeIds = listOf("auto", "chrome", "firefox", "safari", "ios", "randomized")
        assertEquals(cascadeIds, CamouflageProfileRepository.fallbackOrder.map { it.id })
    }

    @Test
    fun `auto carries no shaping (fragment and noises null)`() {
        assertEquals(null, CamouflageProfileRepository.AUTO.fragment)
        assertEquals(null, CamouflageProfileRepository.AUTO.noises)
    }

    @Test
    fun `fragment presets respect the link fp and carry a fragment spec`() {
        val light = CamouflageProfileRepository.byId("frag_light")
        assertEquals("frag_light", light.id)
        assertEquals("", light.fingerprint) // respect the link's fp — only add fragmentation
        assertEquals("tlshello", light.fragment?.packets)
        val aggressive = CamouflageProfileRepository.byId("frag_aggressive")
        assertEquals("", aggressive.fingerprint)
        assertEquals("1-3", aggressive.fragment?.packets)
    }

    @Test
    fun `all includes the fragment presets for the manual picker`() {
        val ids = CamouflageProfileRepository.all().map { it.id }
        assertTrue(ids.containsAll(listOf("frag_light", "frag_aggressive")))
    }
}
