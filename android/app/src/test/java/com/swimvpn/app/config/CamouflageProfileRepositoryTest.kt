package com.swimvpn.app.config

import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

class CamouflageProfileRepositoryTest {

    @Test
    fun `default is chrome`() {
        assertEquals("chrome", CamouflageProfileRepository.DEFAULT.id)
        assertEquals("chrome", CamouflageProfileRepository.DEFAULT.fingerprint)
    }

    @Test
    fun `all presets present with matching fingerprints`() {
        val ids = CamouflageProfileRepository.all().map { it.id }
        assertEquals(listOf("chrome", "firefox", "safari", "ios", "randomized"), ids)
        CamouflageProfileRepository.all().forEach { assertEquals(it.id, it.fingerprint) }
    }

    @Test
    fun `byId resolves known and falls back to default on unknown or null`() {
        assertEquals("firefox", CamouflageProfileRepository.byId("firefox").id)
        assertSame(CamouflageProfileRepository.DEFAULT, CamouflageProfileRepository.byId(null))
        assertSame(CamouflageProfileRepository.DEFAULT, CamouflageProfileRepository.byId("nope"))
    }

    @Test
    fun `fallback order starts with chrome and contains every profile`() {
        assertEquals("chrome", CamouflageProfileRepository.fallbackOrder.first().id)
        assertTrue(CamouflageProfileRepository.fallbackOrder.containsAll(CamouflageProfileRepository.all()))
    }
}
