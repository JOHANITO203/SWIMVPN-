package com.swimvpn.app.data.network

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.withContext
import java.net.InetSocketAddress
import java.net.Socket
import kotlin.system.measureTimeMillis

object ServerLatencyEvaluator {

    suspend fun enrichWithLatency(
        servers: List<ServerNode>,
        connectTimeoutMs: Int = 1500,
    ): List<ServerNode> = coroutineScope {
        servers.map { server ->
            async {
                val measured = measureTcpLatency(server.host, server.port, connectTimeoutMs)
                server.copy(
                    ping = measured ?: server.ping,
                    latencyMeasuredAtMs = System.currentTimeMillis(),
                    latencyProbeFailed = measured == null,
                )
            }
        }.awaitAll()
    }

    private suspend fun measureTcpLatency(
        host: String,
        port: Int,
        connectTimeoutMs: Int,
    ): Int? = withContext(Dispatchers.IO) {
        runCatching {
            Socket().use { socket ->
                val elapsed = measureTimeMillis {
                    socket.connect(InetSocketAddress(host, port), connectTimeoutMs)
                }
                elapsed.coerceAtMost(Int.MAX_VALUE.toLong()).toInt()
            }
        }.getOrNull()
    }
}
