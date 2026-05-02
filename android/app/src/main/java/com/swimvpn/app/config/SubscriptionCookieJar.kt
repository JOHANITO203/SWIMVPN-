package com.swimvpn.app.config

import okhttp3.Cookie
import okhttp3.CookieJar
import okhttp3.HttpUrl

/**
 * Short-lived cookie jar for subscription providers that gate the real payload
 * behind a redirect cookie. Cookies are kept in memory only and scoped by
 * OkHttp's domain/path matching rules.
 */
internal class SubscriptionCookieJar : CookieJar {
    private val cookies = mutableListOf<Cookie>()

    @Synchronized
    override fun saveFromResponse(url: HttpUrl, cookies: List<Cookie>) {
        val now = System.currentTimeMillis()
        this.cookies.removeAll { stored ->
            stored.expiresAt < now || cookies.any { incoming ->
                incoming.name == stored.name &&
                    incoming.domain == stored.domain &&
                    incoming.path == stored.path
            }
        }
        this.cookies.addAll(cookies.filter { it.expiresAt >= now })
    }

    @Synchronized
    override fun loadForRequest(url: HttpUrl): List<Cookie> {
        val now = System.currentTimeMillis()
        cookies.removeAll { it.expiresAt < now }
        return cookies.filter { it.matches(url) }
    }
}
