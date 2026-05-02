package com.swimvpn.app.config

import kotlinx.coroutines.runBlocking
import okhttp3.mockwebserver.Dispatcher
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import okhttp3.mockwebserver.RecordedRequest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SubscriptionFetcherTest {
    @Test
    fun `does not reuse failed attempt cookies across fallback user agents`() = runBlocking {
        val payload = "vless://11111111-1111-1111-1111-111111111111@example.com:443?security=tls&type=tcp#Node"
        val seenCookies = mutableListOf<String>()
        val server = MockWebServer()
        server.dispatcher = object : Dispatcher() {
            override fun dispatch(request: RecordedRequest): MockResponse {
                val userAgent = request.getHeader("User-Agent").orEmpty()
                val cookie = request.getHeader("Cookie").orEmpty()
                synchronized(seenCookies) {
                    seenCookies += "$userAgent|$cookie"
                }

                return when {
                    userAgent.startsWith("SWIMVPN-Android") -> MockResponse()
                        .setResponseCode(502)
                        .addHeader("Set-Cookie", "ua_token=swim; Path=/")
                    userAgent.startsWith("v2rayNG") && cookie.isBlank() -> MockResponse()
                        .setResponseCode(200)
                        .setBody(payload)
                    else -> MockResponse().setResponseCode(502)
                }
            }
        }

        server.start()
        try {
            val result = SubscriptionFetcher().fetch(server.url("/sub").toString())

            assertEquals(payload, result.payload)
            assertTrue(result.warnings.any { it.contains("v2rayNG-compatible subscription client") })
            assertTrue(seenCookies.any { it == "v2rayNG/1.9.0|" })
        } finally {
            server.shutdown()
        }
    }
}
