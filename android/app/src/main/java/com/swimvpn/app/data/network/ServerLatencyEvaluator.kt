package com.swimvpn.app.data.network

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.withContext
import java.net.ConnectException
import java.net.InetSocketAddress
import java.net.Socket
import java.net.SocketTimeoutException
import java.net.UnknownHostException
import javax.net.SocketFactory
import kotlin.system.measureTimeMillis

object ServerLatencyEvaluator {

    data class ProbeResult(
        val latencyMs: Int?,
        val failureReason: ProbeFailureReason?,
    )

    suspend fun enrichWithLatency(
        servers: List<ServerNode>,
        connectTimeoutMs: Int = 1500,
        probeSocketFactory: SocketFactory? = null,
    ): List<ServerNode> = coroutineScope {
        servers.map { server ->
            async {
                val result = measureTcpLatency(
                    host = server.host,
                    port = server.port,
                    connectTimeoutMs = connectTimeoutMs,
                    probeSocketFactory = probeSocketFactory,
                )
                server.copy(
                    ping = result.latencyMs ?: server.ping,
                    latencyMeasuredAtMs = System.currentTimeMillis(),
                    latencyProbeFailed = result.latencyMs == null,
                    latencyProbeFailureReason = result.failureReason,
                )
            }
        }.awaitAll()
    }

    private suspend fun measureTcpLatency(
        host: String,
        port: Int,
        connectTimeoutMs: Int,
        probeSocketFactory: SocketFactory?,
    ): ProbeResult = withContext(Dispatchers.IO) {
        try {
            val socket = probeSocketFactory?.createSocket() ?: Socket()
            socket.use { active ->
                val elapsed = measureTimeMillis {
                    active.connect(InetSocketAddress(host, port), connectTimeoutMs)
                }
                ProbeResult(
                    latencyMs = elapsed.coerceAtMost(Int.MAX_VALUE.toLong()).toInt(),
                    failureReason = null,
                )
            }
        } catch (timeout: SocketTimeoutException) {
            ProbeResult(latencyMs = null, failureReason = ProbeFailureReason.TIMEOUT)
        } catch (unknownHost: UnknownHostException) {
            ProbeResult(latencyMs = null, failureReason = ProbeFailureReason.DNS_FAILURE)
        } catch (connect: ConnectException) {
            val refused = connect.message?.contains("refused", ignoreCase = true) == true
            ProbeResult(
                latencyMs = null,
                failureReason = if (refused) {
                    ProbeFailureReason.CONNECTION_REFUSED
                } else {
                    ProbeFailureReason.NETWORK_UNREACHABLE
                },
            )
        } catch (throwable: Throwable) {
            ProbeResult(latencyMs = null, failureReason = ProbeFailureReason.UNKNOWN)
        }
    }
}
