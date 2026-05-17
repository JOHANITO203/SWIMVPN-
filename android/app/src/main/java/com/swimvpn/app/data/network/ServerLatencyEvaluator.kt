package com.swimvpn.app.data.network

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withContext
import java.net.InetSocketAddress
import java.net.Socket
import kotlin.system.measureTimeMillis

object ServerLatencyEvaluator {
    private const val DEFAULT_CONNECT_TIMEOUT_MS = 1500
    private const val DEFAULT_MAX_CONCURRENT_PROBES = 4
    private const val DEFAULT_CACHE_TTL_MS = 60_000L

    private data class CacheEntry(
        val latencyMs: Int,
        val measuredAtMs: Long,
    )

    private val latencyCache = mutableMapOf<String, CacheEntry>()

    suspend fun enrichWithLatency(
        servers: List<ServerNode>,
        connectTimeoutMs: Int = DEFAULT_CONNECT_TIMEOUT_MS,
        maxConcurrentProbes: Int = DEFAULT_MAX_CONCURRENT_PROBES,
        cacheTtlMs: Long = DEFAULT_CACHE_TTL_MS,
        nowMs: Long = System.currentTimeMillis(),
        measureLatency: suspend (ServerNode, Int) -> Int? = { server, timeout ->
            measureTcpLatency(server.host, server.port, timeout)
        },
    ): List<ServerNode> = coroutineScope {
        val semaphore = Semaphore(maxConcurrentProbes.coerceAtLeast(1))
        servers.map { server ->
            async {
                val measured = readCachedLatency(server, nowMs, cacheTtlMs)
                    ?: semaphore.withPermit {
                        measureLatency(server, connectTimeoutMs)?.also { latency ->
                            writeCachedLatency(server, latency, nowMs)
                        }
                    }
                server.copy(ping = measured ?: server.ping)
            }
        }.awaitAll()
    }

    fun clearCache() {
        synchronized(latencyCache) {
            latencyCache.clear()
        }
    }

    private fun readCachedLatency(server: ServerNode, nowMs: Long, cacheTtlMs: Long): Int? {
        if (cacheTtlMs <= 0L) {
            return null
        }
        return synchronized(latencyCache) {
            latencyCache[cacheKey(server)]
                ?.takeIf { nowMs - it.measuredAtMs <= cacheTtlMs }
                ?.latencyMs
        }
    }

    private fun writeCachedLatency(server: ServerNode, latencyMs: Int, nowMs: Long) {
        synchronized(latencyCache) {
            latencyCache[cacheKey(server)] = CacheEntry(latencyMs = latencyMs, measuredAtMs = nowMs)
        }
    }

    private fun cacheKey(server: ServerNode): String = "${server.host.lowercase()}:${server.port}"

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
