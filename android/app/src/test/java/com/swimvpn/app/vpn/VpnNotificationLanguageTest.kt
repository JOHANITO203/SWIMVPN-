package com.swimvpn.app.vpn

import org.junit.Assert.assertEquals
import org.junit.Test

class VpnNotificationLanguageTest {

    @Test
    fun `keeps supported notification languages`() {
        assertEquals("en", VpnNotificationLanguage.normalize("en"))
        assertEquals("fr", VpnNotificationLanguage.normalize("fr"))
        assertEquals("ru", VpnNotificationLanguage.normalize("ru"))
    }

    @Test
    fun `normalizes regional and uppercase language tags`() {
        assertEquals("fr", VpnNotificationLanguage.normalize("FR-FR"))
        assertEquals("ru", VpnNotificationLanguage.normalize("ru_RU"))
    }

    @Test
    fun `falls back to russian for missing or unsupported language`() {
        assertEquals("ru", VpnNotificationLanguage.normalize(null))
        assertEquals("ru", VpnNotificationLanguage.normalize(""))
        assertEquals("ru", VpnNotificationLanguage.normalize("de"))
    }
}
