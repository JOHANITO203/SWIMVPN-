package com.swimvpn.app.config

import com.swimvpn.app.config.subscriptionparser.SubscriptionHeaderMetadata
import com.swimvpn.app.config.subscriptionparser.SubscriptionMetadataParser
import com.swimvpn.app.config.subscriptionparser.SubscriptionPayloadDecoder
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.IOException
import java.util.concurrent.TimeUnit

internal class SubscriptionFetcher {
    suspend fun fetch(url: String): SubscriptionFetchResult = withContext(Dispatchers.IO) {
        val failures = mutableListOf<String>()
        var fallbackResult: SubscriptionFetchResult? = null
        var bestResult: SubscriptionFetchResult? = null
        var bestScore = 0

        fetchAttempts.forEach { attempt ->
            val request = buildRequest(url, attempt)

            try {
                newSubscriptionClient().newCall(request).execute().use { response ->
                    if (!response.isSuccessful) {
                        failures += "${attempt.label}: HTTP ${response.code}"
                        return@use
                    }

                    val body = response.body ?: run {
                        failures += "${attempt.label}: empty response body"
                        return@use
                    }
                    val raw = body.string().take(MAX_SUBSCRIPTION_CHARS)
                    val normalized = normalizeSubscriptionPayload(raw)
                    val headerMetadata = SubscriptionMetadataParser.parseHttpHeaders(
                        subscriptionUserInfo = response.header("subscription-userinfo"),
                        profileUpdateInterval = response.header("profile-update-interval"),
                        sourceUrl = url,
                    )
                    val result = SubscriptionFetchResult(
                        payload = normalized.payload,
                        headerMetadata = headerMetadata.takeIf { it.hasValues },
                        warnings = buildList {
                            add("Fetched remote subscription URL")
                            add("Subscription fetched with ${attempt.label}")
                            addAll(normalized.warnings)
                            addAll(headerMetadata.warnings)
                            if (raw.length >= MAX_SUBSCRIPTION_CHARS) {
                                add("Subscription response was truncated to $MAX_SUBSCRIPTION_CHARS characters")
                            }
                        },
                    )

                    val score = subscriptionPayloadScore(normalized.payload)
                    if (score > bestScore) {
                        bestResult = result
                        bestScore = score
                    }

                    fallbackResult = fallbackResult ?: result.copy(
                        warnings = result.warnings + "Subscription response did not contain directly importable supported entries",
                    )
                }
            } catch (error: Exception) {
                failures += "${attempt.label}: ${error.localizedMessage ?: error::class.java.simpleName}"
            }
        }

        bestResult ?: fallbackResult ?: throw IOException("Subscription fetch failed: ${failures.joinToString("; ")}")
    }

    private fun buildRequest(url: String, attempt: SubscriptionFetchAttempt): Request {
        val builder = Request.Builder()
            .url(url)
            .header("User-Agent", attempt.userAgent)

        attempt.headers.forEach { (name, value) ->
            builder.header(name, value)
        }

        return builder.build()
    }

    private fun newSubscriptionClient(): OkHttpClient {
        return OkHttpClient.Builder()
            .connectTimeout(10, TimeUnit.SECONDS)
            .readTimeout(20, TimeUnit.SECONDS)
            .cookieJar(SubscriptionCookieJar())
            .followRedirects(true)
            .followSslRedirects(true)
            .build()
    }

    private fun normalizeSubscriptionPayload(raw: String): SubscriptionPayload {
        val trimmed = raw.trim()
        if (trimmed.isBlank()) {
            return SubscriptionPayload("")
        }

        val decoded = SubscriptionPayloadDecoder.decode(trimmed)
        val payload = decoded.payload.trim()
        return if (containsSupportedEntry(payload) || payload.startsWith("{") || payload.startsWith("[")) {
            SubscriptionPayload(
                payload = payload,
                warnings = decoded.warnings,
            )
        } else {
            SubscriptionPayload(trimmed)
        }
    }

    private fun containsSupportedEntry(input: String): Boolean {
        return VpnConfigLinkExtractor.containsRecognizedLink(input)
    }

    private fun subscriptionPayloadScore(input: String): Int {
        val trimmed = input.trim()
        return when {
            containsSupportedEntry(trimmed) -> 3
            trimmed.startsWith("{") -> 2
            trimmed.startsWith("[") -> 1
            else -> 0
        }
    }

    private companion object {
        private const val MAX_SUBSCRIPTION_CHARS = 1_000_000
        private val defaultHeaders = mapOf(
            "Accept" to "*/*",
            "Cache-Control" to "no-cache",
            "Pragma" to "no-cache",
        )
        private val fetchAttempts = listOf(
            SubscriptionFetchAttempt(
                userAgent = "SWIMVPN-Android/1.0",
                label = "SWIMVPN default subscription client",
                headers = defaultHeaders,
            ),
            SubscriptionFetchAttempt(
                userAgent = "v2rayNG/1.9.0",
                label = "v2rayNG-compatible subscription client",
                headers = defaultHeaders,
            ),
            SubscriptionFetchAttempt(
                userAgent = "Happ/1.0",
                label = "Happ-compatible subscription client",
                headers = defaultHeaders,
            ),
            SubscriptionFetchAttempt(
                userAgent = "Happ/1.0 (Android)",
                label = "Happ Android-compatible subscription client",
                headers = defaultHeaders,
            ),
        )
    }
}

internal data class SubscriptionFetchResult(
    val payload: String,
    val headerMetadata: SubscriptionHeaderMetadata? = null,
    val warnings: List<String> = emptyList(),
)

private data class SubscriptionFetchAttempt(
    val userAgent: String,
    val label: String,
    val headers: Map<String, String>,
)

private data class SubscriptionPayload(
    val payload: String,
    val warnings: List<String> = emptyList(),
)
