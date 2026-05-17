package com.swimvpn.app.data.network

import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Before
import org.junit.Assert.assertEquals
import org.junit.Test
import java.util.concurrent.atomic.AtomicInteger

class ServerLatencyEvaluatorTest {

    @Before
    fun setUp() {
        ServerLatencyEvaluator.clearCache()
    }

    @After
    fun tearDown() {
        ServerLatencyEvaluator.clearCache()
    }

    @Test
    fun `uses cached latency within ttl`() = runBlocking {
        val server = server(id = "a", host = "Cache.Example.com", ping = 500)
        var calls = 0

        val first = ServerLatencyEvaluator.enrichWithLatency(
            servers = listOf(server),
            nowMs = 1_000L,
            measureLatency = { _, _ ->
                calls += 1
                42
            },
        )
        val second = ServerLatencyEvaluator.enrichWithLatency(
            servers = listOf(server.copy(ping = 700)),
            nowMs = 1_500L,
            cacheTtlMs = 60_000L,
            measureLatency = { _, _ ->
                calls += 1
                99
            },
        )

        assertEquals(42, first.single().ping)
        assertEquals(42, second.single().ping)
        assertEquals(1, calls)
    }

    @Test
    fun `expired cache is measured again`() = runBlocking {
        val server = server(id = "a", host = "cache.example.com", ping = 500)
        var calls = 0

        ServerLatencyEvaluator.enrichWithLatency(
            servers = listOf(server),
            nowMs = 1_000L,
            cacheTtlMs = 100L,
            measureLatency = { _, _ ->
                calls += 1
                42
            },
        )
        val refreshed = ServerLatencyEvaluator.enrichWithLatency(
            servers = listOf(server),
            nowMs = 1_500L,
            cacheTtlMs = 100L,
            measureLatency = { _, _ ->
                calls += 1
                84
            },
        )

        assertEquals(84, refreshed.single().ping)
        assertEquals(2, calls)
    }

    @Test
    fun `failed measurement keeps existing ping`() = runBlocking {
        val measured = ServerLatencyEvaluator.enrichWithLatency(
            servers = listOf(server(id = "a", ping = 321)),
            measureLatency = { _, _ -> null },
        )

        assertEquals(321, measured.single().ping)
    }

    @Test
    fun `limits concurrent probes`() = runBlocking {
        val active = AtomicInteger(0)
        val maxActive = AtomicInteger(0)
        val servers = (1..8).map { index -> server(id = "server-$index", host = "server-$index.example.com") }

        ServerLatencyEvaluator.enrichWithLatency(
            servers = servers,
            maxConcurrentProbes = 2,
            cacheTtlMs = 0L,
            measureLatency = { _, _ ->
                val current = active.incrementAndGet()
                maxActive.updateAndGet { previous -> maxOf(previous, current) }
                delay(25)
                active.decrementAndGet()
                50
            },
        )

        assertEquals(2, maxActive.get())
    }

    private fun server(
        id: String,
        host: String = "server.example.com",
        ping: Int = 0,
    ) = ServerNode(
        id = id,
        country = "Test",
        city = "Test City",
        host = host,
        port = 443,
        protocol = "vless",
        tags = emptyList(),
        planScope = "premium",
        ping = ping,
    )
}
