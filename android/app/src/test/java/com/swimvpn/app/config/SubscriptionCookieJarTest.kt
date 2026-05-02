package com.swimvpn.app.config

import okhttp3.Cookie
import okhttp3.HttpUrl.Companion.toHttpUrl
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SubscriptionCookieJarTest {
    @Test
    fun `returns provider cookie on redirected subscription request`() {
        val jar = SubscriptionCookieJar()
        val url = "https://subs.eu-fffast.com/sub-token".toHttpUrl()
        val cookie = Cookie.Builder()
            .name("provider_session")
            .value("ok")
            .domain("subs.eu-fffast.com")
            .path("/")
            .build()

        jar.saveFromResponse(url, listOf(cookie))

        val loaded = jar.loadForRequest(url)

        assertEquals(1, loaded.size)
        assertEquals("provider_session", loaded.single().name)
    }

    @Test
    fun `does not leak provider cookies to another host`() {
        val jar = SubscriptionCookieJar()
        val providerUrl = "https://subs.eu-fffast.com/sub-token".toHttpUrl()
        val otherUrl = "https://example.com/sub".toHttpUrl()
        val cookie = Cookie.Builder()
            .name("provider_session")
            .value("ok")
            .domain("subs.eu-fffast.com")
            .path("/")
            .build()

        jar.saveFromResponse(providerUrl, listOf(cookie))

        assertTrue(jar.loadForRequest(otherUrl).isEmpty())
    }
}
