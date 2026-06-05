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
        assertEquals(listOf("auto", "chrome", "firefox", "safari", "ios", "randomized"), ids)
        // AUTO intentionally has a blank fingerprint (no override); browser profiles match their id.
        assertEquals("", CamouflageProfileRepository.AUTO.fingerprint)
        CamouflageProfileRepository.all()
            .filter { it.id != "auto" }
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
    fun `fallback order starts with auto and contains every profile`() {
        assertEquals("auto", CamouflageProfileRepository.fallbackOrder.first().id)
        assertTrue(CamouflageProfileRepository.fallbackOrder.containsAll(CamouflageProfileRepository.all()))
    }
}
