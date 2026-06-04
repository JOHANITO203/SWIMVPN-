package com.swimvpn.app.data.network

import androidx.annotation.StringRes
import com.google.gson.JsonParser
import com.swimvpn.app.R
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.net.Authenticator
import java.net.HttpURLConnection
import java.net.InetSocketAddress
import java.net.PasswordAuthentication
import java.net.Proxy
import java.net.URL
import kotlin.system.measureTimeMillis

/**
 * "Works-here" probe for a BYO residential proxy. Fetches a geo-IP endpoint THROUGH the pasted
 * SOCKS5 proxy: confirms it actually relays, reveals the real exit country/IP, and measures latency.
 * Runs OFF the VPN (a direct SOCKS connection from the app), so the user gets confidence BEFORE
 * routing the whole device through it.
 */
object ResidentialProxyProbe {

    data class Result(
        val ok: Boolean,
        val country: String? = null,
        val ip: String? = null,
        val latencyMs: Int? = null,
        @StringRes val errorRes: Int? = null,
    )

    suspend fun probe(host: String, port: Int, user: String?, password: String?): Result =
        withContext(Dispatchers.IO) {
            val hasAuth = !user.isNullOrEmpty()
            try {
                if (hasAuth) {
                    Authenticator.setDefault(object : Authenticator() {
                        override fun getPasswordAuthentication(): PasswordAuthentication =
                            PasswordAuthentication(user, (password ?: "").toCharArray())
                    })
                }
                val proxy = Proxy(Proxy.Type.SOCKS, InetSocketAddress(host, port))
                var body = ""
                val elapsed = measureTimeMillis {
                    val conn = URL("http://ip-api.com/json/?fields=status,message,country,query")
                        .openConnection(proxy) as HttpURLConnection
                    conn.connectTimeout = 7000
                    conn.readTimeout = 7000
                    conn.requestMethod = "GET"
                    body = conn.inputStream.bufferedReader().use { it.readText() }
                    conn.disconnect()
                }
                val json = JsonParser.parseString(body).asJsonObject
                if (json.get("status")?.asString == "success") {
                    Result(
                        ok = true,
                        country = json.get("country")?.asString,
                        ip = json.get("query")?.asString,
                        latencyMs = elapsed.coerceAtMost(Int.MAX_VALUE.toLong()).toInt(),
                    )
                } else {
                    Result(ok = false, errorRes = R.string.proxy_error_refused)
                }
            } catch (e: Exception) {
                Result(ok = false, errorRes = mapError(e))
            } finally {
                if (hasAuth) Authenticator.setDefault(null)
            }
        }

    @StringRes
    private fun mapError(e: Exception): Int = when {
        (e.message ?: "").contains("authentication", ignoreCase = true) -> R.string.proxy_error_auth
        e is java.net.SocketTimeoutException -> R.string.proxy_error_timeout
        e is java.net.ConnectException -> R.string.proxy_error_connect
        e is java.net.UnknownHostException -> R.string.proxy_error_host
        else -> R.string.proxy_error_unreachable
    }
}
