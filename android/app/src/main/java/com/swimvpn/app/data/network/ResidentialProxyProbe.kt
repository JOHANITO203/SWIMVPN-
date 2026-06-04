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
 * "Works-here" probe for a BYO residential proxy. Calls our own backend echo endpoint
 * (https://api.swimvpn.pro/api/v1/status/caller-ip) THROUGH the pasted proxy over HTTPS: confirms it
 * actually relays, and the backend echoes the exit IP + country (resolved server-side via geoip-lite,
 * ISO-2 code). No third party and no plaintext leak of the exit IP from the device. Runs OFF the VPN
 * (a direct proxy connection from the app), so the user gets confidence BEFORE routing through it.
 */
object ResidentialProxyProbe {

    data class Result(
        val ok: Boolean,
        val country: String? = null,
        val ip: String? = null,
        val latencyMs: Int? = null,
        @StringRes val errorRes: Int? = null,
    )

    suspend fun probe(host: String, port: Int, user: String?, password: String?, useHttp: Boolean = false): Result =
        withContext(Dispatchers.IO) {
            val hasAuth = !user.isNullOrEmpty()
            try {
                if (hasAuth) {
                    Authenticator.setDefault(object : Authenticator() {
                        override fun getPasswordAuthentication(): PasswordAuthentication =
                            PasswordAuthentication(user, (password ?: "").toCharArray())
                    })
                }
                // SOCKS5 by default; HTTP proxies are probed via Proxy.Type.HTTP (auth handled by the
                // same Authenticator, which answers RequestorType.PROXY too).
                val proxy = Proxy(if (useHttp) Proxy.Type.HTTP else Proxy.Type.SOCKS, InetSocketAddress(host, port))
                var body = ""
                val elapsed = measureTimeMillis {
                    val conn = URL("https://api.swimvpn.pro/api/v1/status/caller-ip")
                        .openConnection(proxy) as HttpURLConnection
                    conn.connectTimeout = 7000
                    conn.readTimeout = 7000
                    conn.requestMethod = "GET"
                    body = conn.inputStream.bufferedReader().use { it.readText() }
                    conn.disconnect()
                }
                val json = JsonParser.parseString(body).asJsonObject
                val exitIp = json.get("ip")?.asString
                if (!exitIp.isNullOrBlank()) {
                    Result(
                        ok = true,
                        country = json.get("country")?.takeIf { !it.isJsonNull }?.asString,
                        ip = exitIp,
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
