package com.swimvpn.app.adaptive

import com.swimvpn.app.data.network.ProbeFailureReason
import com.swimvpn.app.vpn.NetworkType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class AdaptiveDecisionAgentTest {

    @Test
    fun `first failure retries same server with short backoff`() {
        val action = AdaptiveDecisionAgent.planAfterFailure(
            currentServerId = "server-a",
            candidates = listOf(candidate("server-a"), candidate("server-b", ping = 90)),
            scores = emptyMap(),
            reconnectAttempt = 0,
            nowMs = 1_000L,
        )

        assertEquals(DecisionActionType.RECONNECT_SAME, action.type)
        assertEquals("server-a", action.targetServerId)
        assertEquals(1_000L, action.delayMs)
    }

    @Test
    fun `repeated failure switches away from avoided server`() {
        val failedScore = AdaptiveDecisionAgent.recordFailure(
            score = ServerQualityScore(serverId = "server-a", consecutiveFailures = 1),
            nowMs = 10_000L,
        )

        assertTrue(failedScore.avoidUntilMs > 10_000L)

        val action = AdaptiveDecisionAgent.planAfterFailure(
            currentServerId = "server-a",
            candidates = listOf(
                candidate("server-a", ping = 30),
                candidate("server-b", ping = 80),
            ),
            scores = mapOf("server-a" to failedScore),
            reconnectAttempt = 2,
            nowMs = 10_000L,
        )

        assertEquals(DecisionActionType.SWITCH_SERVER, action.type)
        assertEquals("server-b", action.targetServerId)
    }

    @Test
    fun `premium blocked and configless candidates are ignored`() {
        val selected = AdaptiveDecisionAgent.selectBestServer(
            candidates = listOf(
                candidate("premium", ping = 10, premiumBlocked = true),
                candidate("empty", ping = 20, hasRuntimeConfig = false),
                candidate("imported", ping = 120),
            ),
            scores = emptyMap(),
            currentServerId = null,
            nowMs = 1_000L,
        )

        assertEquals("imported", selected?.serverId)
    }

    @Test
    fun `fresh probe failure loses to healthy candidate`() {
        val now = 60_000L

        val selected = AdaptiveDecisionAgent.selectBestServer(
            candidates = listOf(
                candidate(
                    id = "failed-pinned-fast",
                    ping = 15,
                    isPinned = true,
                    latencyMeasuredAtMs = now - 1_000L,
                    latencyProbeFailed = true,
                ),
                candidate(
                    id = "healthy",
                    ping = 85,
                    latencyMeasuredAtMs = now - 1_000L,
                ),
            ),
            scores = emptyMap(),
            currentServerId = null,
            nowMs = now,
        )

        assertEquals("healthy", selected?.serverId)
    }

    @Test
    fun `fresh probe failure is not selected when it is the only candidate`() {
        val now = 90_000L

        val selected = AdaptiveDecisionAgent.selectBestServer(
            candidates = listOf(
                candidate(
                    id = "failed-only",
                    ping = 15,
                    latencyMeasuredAtMs = now - 1_000L,
                    latencyProbeFailed = true,
                ),
            ),
            scores = emptyMap(),
            currentServerId = null,
            nowMs = now,
        )

        assertNull(selected)
    }

    @Test
    fun `recommend server exposes fresh quality state for validated candidate`() {
        val now = 100_000L

        val result = AdaptiveDecisionAgent.recommendServer(
            candidates = listOf(
                candidate(
                    id = "fresh",
                    ping = 45,
                    latencyMeasuredAtMs = now - 1_000L,
                ),
            ),
            scores = emptyMap(),
            currentServerId = null,
            nowMs = now,
        )

        assertEquals("fresh", result?.candidate?.serverId)
        assertEquals(ServerRuntimeQualityState.FRESH, result?.qualityState)
    }

    @Test
    fun `fresh low ping is preferred over stale faster ping`() {
        val now = 120_000L

        val selected = AdaptiveDecisionAgent.selectBestServer(
            candidates = listOf(
                candidate(
                    id = "stale-fast",
                    ping = 10,
                    latencyMeasuredAtMs = now - 20 * 60 * 1_000L,
                ),
                candidate(
                    id = "fresh-low",
                    ping = 45,
                    latencyMeasuredAtMs = now - 5_000L,
                ),
                candidate(
                    id = "missing",
                    ping = 0,
                    latencyMeasuredAtMs = 0L,
                ),
            ),
            scores = emptyMap(),
            currentServerId = null,
            nowMs = now,
        )

        assertEquals("fresh-low", selected?.serverId)
    }

    @Test
    fun `pin breaks healthy tie`() {
        val now = 180_000L

        val selected = AdaptiveDecisionAgent.selectBestServer(
            candidates = listOf(
                candidate(
                    id = "unpinned",
                    ping = 60,
                    latencyMeasuredAtMs = now - 2_000L,
                ),
                candidate(
                    id = "pinned",
                    ping = 60,
                    isPinned = true,
                    latencyMeasuredAtMs = now - 2_000L,
                ),
            ),
            scores = emptyMap(),
            currentServerId = null,
            nowMs = now,
        )

        assertEquals("pinned", selected?.serverId)
    }

    @Test
    fun `success history beats recent failure history`() {
        val now = 240_000L

        val selected = AdaptiveDecisionAgent.selectBestServer(
            candidates = listOf(
                candidate(
                    id = "recent-failures",
                    ping = 40,
                    latencyMeasuredAtMs = now - 2_000L,
                ),
                candidate(
                    id = "proven",
                    ping = 55,
                    latencyMeasuredAtMs = now - 2_000L,
                ),
            ),
            scores = mapOf(
                "recent-failures" to ServerQualityScore(
                    serverId = "recent-failures",
                    failureCount = 3,
                    consecutiveFailures = 1,
                    lastFailureAtMs = now - 5_000L,
                ),
                "proven" to ServerQualityScore(
                    serverId = "proven",
                    successCount = 4,
                    lastSuccessAtMs = now - 4_000L,
                ),
            ),
            currentServerId = null,
            nowMs = now,
        )

        assertEquals("proven", selected?.serverId)
    }

    @Test
    fun `backend load breaks close latency tie toward less congested node`() {
        val now = 260_000L

        val selected = AdaptiveDecisionAgent.selectBestServer(
            candidates = listOf(
                candidate(
                    id = "high-load",
                    ping = 40,
                    latencyMeasuredAtMs = now - 2_000L,
                    load = 95,
                ),
                candidate(
                    id = "low-load",
                    ping = 55,
                    latencyMeasuredAtMs = now - 2_000L,
                    load = 10,
                ),
            ),
            scores = emptyMap(),
            currentServerId = null,
            nowMs = now,
        )

        assertEquals("low-load", selected?.serverId)
    }

    @Test
    fun `congested backend node loses to available node when latency is close`() {
        val now = 270_000L

        val selected = AdaptiveDecisionAgent.selectBestServer(
            candidates = listOf(
                candidate(
                    id = "congested",
                    ping = 40,
                    latencyMeasuredAtMs = now - 2_000L,
                    load = 0,
                    availabilityStatus = "CONGESTED",
                ),
                candidate(
                    id = "available",
                    ping = 60,
                    latencyMeasuredAtMs = now - 2_000L,
                    load = 10,
                    availabilityStatus = "AVAILABLE",
                ),
            ),
            scores = emptyMap(),
            currentServerId = null,
            nowMs = now,
        )

        assertEquals("available", selected?.serverId)
    }

    @Test
    fun `unknown backend load is not treated as zero load`() {
        val now = 280_000L

        val selected = AdaptiveDecisionAgent.selectBestServer(
            candidates = listOf(
                candidate(
                    id = "unknown-load",
                    ping = 42,
                    latencyMeasuredAtMs = now - 2_000L,
                    load = null,
                ),
                candidate(
                    id = "known-low-load",
                    ping = 55,
                    latencyMeasuredAtMs = now - 2_000L,
                    load = 10,
                ),
            ),
            scores = emptyMap(),
            currentServerId = null,
            nowMs = now,
        )

        assertEquals("known-low-load", selected?.serverId)
    }

    @Test
    fun `old failure history decays so recovered low latency server can be selected`() {
        val selected = AdaptiveDecisionAgent.selectBestServer(
            candidates = listOf(
                candidate("recovered", ping = 30),
                candidate("slow", ping = 220),
            ),
            scores = mapOf(
                "recovered" to ServerQualityScore(
                    serverId = "recovered",
                    failureCount = 6,
                    consecutiveFailures = 3,
                    lastFailureAtMs = 10_000L,
                    avoidUntilMs = 610_000L,
                ),
            ),
            currentServerId = null,
            nowMs = 3_700_000L,
        )

        assertEquals("recovered", selected?.serverId)
    }

    @Test
    fun `max reconnect attempts stops adaptive loop`() {
        val action = AdaptiveDecisionAgent.planAfterFailure(
            currentServerId = "server-a",
            candidates = listOf(candidate("server-a"), candidate("server-b")),
            scores = emptyMap(),
            reconnectAttempt = AdaptiveDecisionAgent.MAX_RECONNECT_ATTEMPTS,
            nowMs = 1_000L,
        )

        assertEquals(DecisionActionType.GIVE_UP, action.type)
        assertNull(action.targetServerId)
    }

    @Test
    fun `single-server account retries current instead of giving up (H11)`() {
        // Only the current server exists (no fallback) and it isn't in an avoid window. Past the
        // same-server early-retry limit but below MAX, the agent must keep retrying it, not give up.
        val action = AdaptiveDecisionAgent.planAfterFailure(
            currentServerId = "server-a",
            candidates = listOf(candidate("server-a")),
            scores = emptyMap(),
            reconnectAttempt = 3,
            nowMs = 1_000L,
        )

        assertEquals(DecisionActionType.RECONNECT_SAME, action.type)
        assertEquals("server-a", action.targetServerId)
    }

    @Test
    fun `backoff grows through production retry sequence`() {
        val delays = (0 until AdaptiveDecisionAgent.MAX_RECONNECT_ATTEMPTS).map { attempt ->
            AdaptiveDecisionAgent.planAfterFailure(
                currentServerId = "server-a",
                candidates = listOf(candidate("server-a"), candidate("server-b")),
                scores = emptyMap(),
                reconnectAttempt = attempt,
                nowMs = 1_000L,
            ).delayMs
        }

        assertEquals(listOf(1_000L, 3_000L, 5_000L, 10_000L, 30_000L), delays)
    }

    @Test
    fun `global outage true when majority probes timeout or unreachable`() {
        val now = 300_000L
        val candidates = listOf(
            candidate(
                id = "a",
                ping = 40,
                latencyMeasuredAtMs = now - 1_000L,
                latencyProbeFailed = true,
                latencyProbeFailureReason = ProbeFailureReason.TIMEOUT,
            ),
            candidate(
                id = "b",
                ping = 50,
                latencyMeasuredAtMs = now - 1_000L,
                latencyProbeFailed = true,
                latencyProbeFailureReason = ProbeFailureReason.NETWORK_UNREACHABLE,
            ),
            candidate(
                id = "c",
                ping = 60,
                latencyMeasuredAtMs = now - 1_000L,
            ),
        )

        assertTrue(AdaptiveDecisionAgent.isLikelyGlobalOutage(candidates))
    }

    @Test
    fun `global outage false for lone timeout among healthy servers`() {
        val now = 310_000L
        val candidates = listOf(
            candidate(
                id = "a",
                ping = 40,
                latencyMeasuredAtMs = now - 1_000L,
                latencyProbeFailed = true,
                latencyProbeFailureReason = ProbeFailureReason.TIMEOUT,
            ),
            candidate(id = "b", ping = 50, latencyMeasuredAtMs = now - 1_000L),
            candidate(id = "c", ping = 60, latencyMeasuredAtMs = now - 1_000L),
        )

        assertFalse(AdaptiveDecisionAgent.isLikelyGlobalOutage(candidates))
    }

    @Test
    fun `global outage false when failures are server side refusals`() {
        val now = 320_000L
        val candidates = listOf(
            candidate(
                id = "a",
                ping = 40,
                latencyMeasuredAtMs = now - 1_000L,
                latencyProbeFailed = true,
                latencyProbeFailureReason = ProbeFailureReason.CONNECTION_REFUSED,
            ),
            candidate(
                id = "b",
                ping = 50,
                latencyMeasuredAtMs = now - 1_000L,
                latencyProbeFailed = true,
                latencyProbeFailureReason = ProbeFailureReason.DNS_FAILURE,
            ),
        )

        assertFalse(AdaptiveDecisionAgent.isLikelyGlobalOutage(candidates))
    }

    @Test
    fun `connection refused server is avoided while healthy server is chosen`() {
        val now = 330_000L
        val selected = AdaptiveDecisionAgent.selectBestServer(
            candidates = listOf(
                candidate(
                    id = "refused-fast",
                    ping = 10,
                    latencyMeasuredAtMs = now - 1_000L,
                    latencyProbeFailed = true,
                    latencyProbeFailureReason = ProbeFailureReason.CONNECTION_REFUSED,
                ),
                candidate(id = "healthy", ping = 90, latencyMeasuredAtMs = now - 1_000L),
            ),
            scores = emptyMap(),
            currentServerId = null,
            nowMs = now,
        )

        assertEquals("healthy", selected?.serverId)
    }

    @Test
    fun `connection refused server is excluded when it is the only candidate`() {
        val now = 335_000L
        val selected = AdaptiveDecisionAgent.selectBestServer(
            candidates = listOf(
                candidate(
                    id = "refused-only",
                    ping = 10,
                    latencyMeasuredAtMs = now - 1_000L,
                    latencyProbeFailed = true,
                    latencyProbeFailureReason = ProbeFailureReason.CONNECTION_REFUSED,
                ),
            ),
            scores = emptyMap(),
            currentServerId = null,
            nowMs = now,
        )

        assertNull(selected)
    }

    @Test
    fun `lone timeout server stays selectable and is not blacklisted`() {
        val now = 340_000L
        // A single timeout among healthy servers is not a global outage, but the timed-out server
        // must remain selectable (light penalty), unlike a server-side refusal.
        val selected = AdaptiveDecisionAgent.selectBestServer(
            candidates = listOf(
                candidate(
                    id = "timeout-only",
                    ping = 30,
                    latencyMeasuredAtMs = now - 1_000L,
                    latencyProbeFailed = true,
                    latencyProbeFailureReason = ProbeFailureReason.TIMEOUT,
                ),
            ),
            scores = emptyMap(),
            currentServerId = null,
            nowMs = now,
        )

        assertEquals("timeout-only", selected?.serverId)
    }

    @Test
    fun `global outage suppresses avoidance and ranks best available server`() {
        val now = 350_000L
        val selected = AdaptiveDecisionAgent.recommendServer(
            candidates = listOf(
                candidate(
                    id = "fast-avoided",
                    ping = 20,
                    latencyMeasuredAtMs = now - 1_000L,
                    latencyProbeFailed = true,
                    latencyProbeFailureReason = ProbeFailureReason.TIMEOUT,
                ),
                candidate(
                    id = "slow",
                    ping = 200,
                    latencyMeasuredAtMs = now - 1_000L,
                    latencyProbeFailed = true,
                    latencyProbeFailureReason = ProbeFailureReason.NETWORK_UNREACHABLE,
                ),
            ),
            // fast-avoided would normally be blacklisted by its score, but the global outage
            // suppresses per-server avoidance so it is still rankable and wins on ping.
            scores = mapOf(
                "fast-avoided" to ServerQualityScore(
                    serverId = "fast-avoided",
                    avoidUntilMs = now + 600_000L,
                ),
            ),
            currentServerId = null,
            nowMs = now,
        )

        assertEquals("fast-avoided", selected?.candidate?.serverId)
    }

    @Test
    fun `latency age penalty is zero at and below fresh floor`() {
        assertEquals(0, AdaptiveDecisionAgent.latencyAgePenalty(0L))
        assertEquals(0, AdaptiveDecisionAgent.latencyAgePenalty(-5_000L))
        assertEquals(0, AdaptiveDecisionAgent.latencyAgePenalty(30_000L))
    }

    @Test
    fun `latency age penalty matches legacy endpoints and is monotonic non-decreasing`() {
        val samples = listOf(0L, 30_000L, 60_000L, 120_000L, 300_000L)
        val penalties = samples.map { AdaptiveDecisionAgent.latencyAgePenalty(it) }

        // ~0 at the fresh floor, ~120 at the legacy 120s fresh/stale boundary.
        assertEquals(0, penalties[0])
        assertEquals(0, penalties[1])
        assertEquals(120, penalties[3])

        // 60s sits on the linear ramp between floor (0) and boundary (120): 120 * 30000 / 90000 = 40.
        assertEquals(40, penalties[2])

        // Monotonic non-decreasing across the sample ages.
        penalties.zipWithNext().forEach { (a, b) -> assertTrue(b >= a) }

        // Beyond the boundary it keeps growing modestly but stays well below MISSING_PING (300).
        assertTrue(penalties[4] > penalties[3])
        assertTrue(penalties[4] < 300)
    }

    @Test
    fun `latency age penalty is capped below missing ping for very stale pings`() {
        val veryStale = AdaptiveDecisionAgent.latencyAgePenalty(60 * 60 * 1_000L)
        assertEquals(200, veryStale)
        assertTrue(veryStale < 300)
    }

    @Test
    fun `missing ping path is unchanged and loses to a graded stale ping`() {
        val now = 1_000_000L
        // moderately stale (~150 penalty) still beats a missing ping (300 penalty).
        val selected = AdaptiveDecisionAgent.selectBestServer(
            candidates = listOf(
                candidate(
                    id = "stale-but-pinged",
                    ping = 50,
                    latencyMeasuredAtMs = now - 5 * 60 * 1_000L,
                ),
                candidate(id = "no-ping", ping = 0, latencyMeasuredAtMs = 0L),
            ),
            scores = emptyMap(),
            currentServerId = null,
            nowMs = now,
        )

        assertEquals("stale-but-pinged", selected?.serverId)
    }

    @Test
    fun `probe failed path is unchanged by graduated penalty`() {
        val now = 400_000L
        // A fresh server-side probe failure must still lose to a moderately stale healthy server,
        // confirming the graduated penalty did not weaken the probe-failed handling.
        val selected = AdaptiveDecisionAgent.selectBestServer(
            candidates = listOf(
                candidate(
                    id = "refused-fast",
                    ping = 10,
                    latencyMeasuredAtMs = now - 1_000L,
                    latencyProbeFailed = true,
                    latencyProbeFailureReason = ProbeFailureReason.CONNECTION_REFUSED,
                ),
                candidate(
                    id = "stale-healthy",
                    ping = 60,
                    latencyMeasuredAtMs = now - 4 * 60 * 1_000L,
                ),
            ),
            scores = emptyMap(),
            currentServerId = null,
            nowMs = now,
        )

        assertEquals("stale-healthy", selected?.serverId)
    }

    @Test
    fun `recommend server outcome is unchanged when networkType is omitted versus unknown`() {
        val now = 100_000L
        val candidates = listOf(
            candidate(id = "a", ping = 45, latencyMeasuredAtMs = now - 1_000L),
            candidate(id = "b", ping = 80, latencyMeasuredAtMs = now - 1_000L),
        )

        val omitted = AdaptiveDecisionAgent.recommendServer(
            candidates = candidates,
            scores = emptyMap(),
            currentServerId = null,
            nowMs = now,
        )
        val explicitUnknown = AdaptiveDecisionAgent.recommendServer(
            candidates = candidates,
            scores = emptyMap(),
            currentServerId = null,
            nowMs = now,
            networkType = com.swimvpn.app.vpn.NetworkType.UNKNOWN,
        )

        assertEquals(omitted?.candidate?.serverId, explicitUnknown?.candidate?.serverId)
        assertEquals(omitted?.score, explicitUnknown?.score)
    }

    @Test
    fun `manual override reward nudges ranking between two otherwise equal servers`() {
        val now = 500_000L
        val selected = AdaptiveDecisionAgent.selectBestServer(
            candidates = listOf(
                candidate(id = "plain", ping = 50, latencyMeasuredAtMs = now - 2_000L),
                candidate(id = "preferred", ping = 50, latencyMeasuredAtMs = now - 2_000L),
            ),
            scores = mapOf(
                "preferred" to ServerQualityScore(
                    serverId = "preferred",
                    manualSelectionCount = 3,
                    lastManualSelectionAtMs = now - 1_000L,
                ),
            ),
            currentServerId = null,
            nowMs = now,
        )

        assertEquals("preferred", selected?.serverId)
    }

    @Test
    fun `recordManualSelection increments count and stamps time without touching failure or success`() {
        val updated = AdaptiveDecisionAgent.recordManualSelection(
            score = ServerQualityScore(
                serverId = "s",
                successCount = 2,
                failureCount = 1,
                consecutiveFailures = 1,
            ),
            nowMs = 700_000L,
        )

        assertEquals(1, updated.manualSelectionCount)
        assertEquals(700_000L, updated.lastManualSelectionAtMs)
        assertEquals(2, updated.successCount)
        assertEquals(1, updated.failureCount)
        assertEquals(1, updated.consecutiveFailures)
    }

    @Test
    fun `manual override reward does not resurrect an avoided server`() {
        val now = 510_000L
        val selected = AdaptiveDecisionAgent.selectBestServer(
            candidates = listOf(
                candidate(id = "manual-avoided", ping = 10, latencyMeasuredAtMs = now - 1_000L),
                candidate(id = "healthy", ping = 90, latencyMeasuredAtMs = now - 1_000L),
            ),
            scores = mapOf(
                "manual-avoided" to ServerQualityScore(
                    serverId = "manual-avoided",
                    manualSelectionCount = 999,
                    lastManualSelectionAtMs = now - 1_000L,
                    avoidUntilMs = now + 600_000L,
                ),
            ),
            currentServerId = null,
            nowMs = now,
        )

        assertEquals("healthy", selected?.serverId)
    }

    @Test
    fun `manual override reward does not resurrect a fresh probe failed server`() {
        val now = 520_000L
        val selected = AdaptiveDecisionAgent.selectBestServer(
            candidates = listOf(
                candidate(
                    id = "manual-refused",
                    ping = 10,
                    latencyMeasuredAtMs = now - 1_000L,
                    latencyProbeFailed = true,
                    latencyProbeFailureReason = ProbeFailureReason.CONNECTION_REFUSED,
                ),
                candidate(id = "healthy", ping = 90, latencyMeasuredAtMs = now - 1_000L),
            ),
            scores = mapOf(
                "manual-refused" to ServerQualityScore(
                    serverId = "manual-refused",
                    manualSelectionCount = 999,
                    lastManualSelectionAtMs = now - 1_000L,
                ),
            ),
            currentServerId = null,
            nowMs = now,
        )

        assertEquals("healthy", selected?.serverId)
    }

    @Test
    fun `per network penalty loses on the failing network but is unaffected on other networks`() {
        val now = 600_000L
        val scores = mapOf(
            "cell-bad" to ServerQualityScore(
                serverId = "cell-bad",
                networkFailures = mapOf(NetworkType.CELLULAR to 3),
            ),
        )

        val onCellular = AdaptiveDecisionAgent.recommendServer(
            candidates = listOf(
                candidate(id = "cell-bad", ping = 40, latencyMeasuredAtMs = now - 1_000L),
                candidate(id = "neutral", ping = 60, latencyMeasuredAtMs = now - 1_000L),
            ),
            scores = scores,
            currentServerId = null,
            nowMs = now,
            networkType = NetworkType.CELLULAR,
        )
        // 3 CELLULAR failures -> +150 penalty pushes cell-bad behind the slower neutral server.
        assertEquals("neutral", onCellular?.candidate?.serverId)

        val onWifi = AdaptiveDecisionAgent.recommendServer(
            candidates = listOf(
                candidate(id = "cell-bad", ping = 40, latencyMeasuredAtMs = now - 1_000L),
                candidate(id = "neutral", ping = 60, latencyMeasuredAtMs = now - 1_000L),
            ),
            scores = scores,
            currentServerId = null,
            nowMs = now,
            networkType = NetworkType.WIFI,
        )
        // On WIFI the CELLULAR failures do not apply, so the faster cell-bad wins on ping.
        assertEquals("cell-bad", onWifi?.candidate?.serverId)
    }

    @Test
    fun `per network penalty is suppressed under global outage`() {
        val now = 610_000L
        val selected = AdaptiveDecisionAgent.recommendServer(
            candidates = listOf(
                candidate(
                    id = "cell-bad-fast",
                    ping = 20,
                    latencyMeasuredAtMs = now - 1_000L,
                    latencyProbeFailed = true,
                    latencyProbeFailureReason = ProbeFailureReason.TIMEOUT,
                ),
                candidate(
                    id = "slow",
                    ping = 200,
                    latencyMeasuredAtMs = now - 1_000L,
                    latencyProbeFailed = true,
                    latencyProbeFailureReason = ProbeFailureReason.NETWORK_UNREACHABLE,
                ),
            ),
            scores = mapOf(
                "cell-bad-fast" to ServerQualityScore(
                    serverId = "cell-bad-fast",
                    networkFailures = mapOf(NetworkType.CELLULAR to 5),
                ),
            ),
            currentServerId = null,
            nowMs = now,
            networkType = NetworkType.CELLULAR,
        )
        // Global outage suppresses the per-network penalty, so cell-bad-fast still wins on ping.
        assertEquals("cell-bad-fast", selected?.candidate?.serverId)
    }

    @Test
    fun `recordFailure buckets only concrete transports`() {
        val base = ServerQualityScore(serverId = "s")

        val onCellular = AdaptiveDecisionAgent.recordFailure(base, 1_000L, NetworkType.CELLULAR)
        assertEquals(mapOf(NetworkType.CELLULAR to 1), onCellular.networkFailures)

        val onUnknown = AdaptiveDecisionAgent.recordFailure(onCellular, 2_000L, NetworkType.UNKNOWN)
        assertEquals(mapOf(NetworkType.CELLULAR to 1), onUnknown.networkFailures)
        assertEquals(2, onUnknown.failureCount)

        val onNone = AdaptiveDecisionAgent.recordFailure(onCellular, 3_000L, NetworkType.NONE)
        assertEquals(mapOf(NetworkType.CELLULAR to 1), onNone.networkFailures)
    }

    @Test
    fun `failover fallback is network-aware via planAfterFailure`() {
        val now = 1_000_000L
        val candidates = listOf(
            candidate(id = "cell-bad", ping = 20, latencyMeasuredAtMs = now - 1_000L),
            candidate(id = "clean", ping = 60, latencyMeasuredAtMs = now - 1_000L),
        )
        val scores = mapOf(
            "cell-bad" to ServerQualityScore(
                serverId = "cell-bad",
                networkFailures = mapOf(NetworkType.CELLULAR to 3),
            ),
        )

        // On cellular the per-network penalty pushes the fast-but-cellular-bad server below the clean one.
        val onCellular = AdaptiveDecisionAgent.planAfterFailure(
            currentServerId = "current-dead",
            candidates = candidates,
            scores = scores,
            reconnectAttempt = 2,
            nowMs = now,
            networkType = NetworkType.CELLULAR,
        )
        assertEquals(DecisionActionType.SWITCH_SERVER, onCellular.type)
        assertEquals("clean", onCellular.targetServerId)

        // Without a known network the penalty does not apply, so the faster server wins (legacy behavior).
        val onUnknown = AdaptiveDecisionAgent.planAfterFailure(
            currentServerId = "current-dead",
            candidates = candidates,
            scores = scores,
            reconnectAttempt = 2,
            nowMs = now,
            networkType = NetworkType.UNKNOWN,
        )
        assertEquals("cell-bad", onUnknown.targetServerId)
    }

    @Test
    fun `recordSuccess on cellular increments network success and sheds one cellular failure`() {
        val base = ServerQualityScore(
            serverId = "s",
            networkFailures = mapOf(
                NetworkType.CELLULAR to 2,
                NetworkType.WIFI to 1,
            ),
        )

        val updated = AdaptiveDecisionAgent.recordSuccess(base, 1_000L, NetworkType.CELLULAR)

        assertEquals(mapOf(NetworkType.CELLULAR to 1), updated.networkSuccesses)
        // CELLULAR failure decremented 2 -> 1; WIFI failure untouched.
        assertEquals(
            mapOf(NetworkType.CELLULAR to 1, NetworkType.WIFI to 1),
            updated.networkFailures,
        )
        assertEquals(1, updated.successCount)
        assertEquals(0, updated.consecutiveFailures)
        assertEquals(1_000L, updated.lastSuccessAtMs)
    }

    @Test
    fun `recordSuccess sheds the last cellular failure entry down to floor zero`() {
        val base = ServerQualityScore(
            serverId = "s",
            networkFailures = mapOf(NetworkType.CELLULAR to 1),
        )

        val updated = AdaptiveDecisionAgent.recordSuccess(base, 2_000L, NetworkType.CELLULAR)

        // 1 -> 0 removes the entry entirely (floor 0, no negative counts).
        assertEquals(emptyMap<NetworkType, Int>(), updated.networkFailures)
        assertEquals(mapOf(NetworkType.CELLULAR to 1), updated.networkSuccesses)
    }

    @Test
    fun `recordSuccess on unknown network buckets nothing and preserves legacy behavior`() {
        val base = ServerQualityScore(
            serverId = "s",
            consecutiveFailures = 3,
            avoidUntilMs = 9_999L,
            networkFailures = mapOf(NetworkType.WIFI to 2),
        )

        val viaUnknown = AdaptiveDecisionAgent.recordSuccess(base, 3_000L, NetworkType.UNKNOWN)
        val viaDefault = AdaptiveDecisionAgent.recordSuccess(base, 3_000L)

        assertEquals(emptyMap<NetworkType, Int>(), viaUnknown.networkSuccesses)
        assertEquals(mapOf(NetworkType.WIFI to 2), viaUnknown.networkFailures)
        assertEquals(0, viaUnknown.consecutiveFailures)
        assertEquals(0L, viaUnknown.avoidUntilMs)
        // Default param equals explicit UNKNOWN: existing behavior unchanged.
        assertEquals(viaDefault, viaUnknown)
    }

    @Test
    fun `works-here reward prefers the server proven on the current wifi network`() {
        val now = 800_000L
        val selected = AdaptiveDecisionAgent.selectBestServer(
            candidates = listOf(
                candidate(id = "plain", ping = 50, latencyMeasuredAtMs = now - 2_000L),
                candidate(id = "works-here", ping = 50, latencyMeasuredAtMs = now - 2_000L),
            ),
            scores = mapOf(
                "works-here" to ServerQualityScore(
                    serverId = "works-here",
                    networkSuccesses = mapOf(NetworkType.WIFI to 3),
                ),
            ),
            currentServerId = null,
            nowMs = now,
            networkType = NetworkType.WIFI,
        )

        assertEquals("works-here", selected?.serverId)
    }

    @Test
    fun `works-here reward does not resurrect an avoided server`() {
        val now = 810_000L
        val selected = AdaptiveDecisionAgent.selectBestServer(
            candidates = listOf(
                candidate(id = "works-but-avoided", ping = 10, latencyMeasuredAtMs = now - 1_000L),
                candidate(id = "healthy", ping = 90, latencyMeasuredAtMs = now - 1_000L),
            ),
            scores = mapOf(
                "works-but-avoided" to ServerQualityScore(
                    serverId = "works-but-avoided",
                    networkSuccesses = mapOf(NetworkType.WIFI to 999),
                    avoidUntilMs = now + 600_000L,
                ),
            ),
            currentServerId = null,
            nowMs = now,
            networkType = NetworkType.WIFI,
        )

        assertEquals("healthy", selected?.serverId)
    }

    @Test
    fun `works-here reward does not resurrect a fresh probe failed server`() {
        val now = 820_000L
        val selected = AdaptiveDecisionAgent.selectBestServer(
            candidates = listOf(
                candidate(
                    id = "works-but-refused",
                    ping = 10,
                    latencyMeasuredAtMs = now - 1_000L,
                    latencyProbeFailed = true,
                    latencyProbeFailureReason = ProbeFailureReason.CONNECTION_REFUSED,
                ),
                candidate(id = "healthy", ping = 90, latencyMeasuredAtMs = now - 1_000L),
            ),
            scores = mapOf(
                "works-but-refused" to ServerQualityScore(
                    serverId = "works-but-refused",
                    networkSuccesses = mapOf(NetworkType.WIFI to 999),
                ),
            ),
            currentServerId = null,
            nowMs = now,
            networkType = NetworkType.WIFI,
        )

        assertEquals("healthy", selected?.serverId)
    }

    @Test
    fun `works-here reward is suppressed under global outage`() {
        val now = 830_000L
        val selected = AdaptiveDecisionAgent.recommendServer(
            candidates = listOf(
                candidate(
                    id = "slow-works-here",
                    ping = 100,
                    latencyMeasuredAtMs = now - 1_000L,
                    latencyProbeFailed = true,
                    latencyProbeFailureReason = ProbeFailureReason.TIMEOUT,
                ),
                candidate(
                    id = "fast-plain",
                    ping = 20,
                    latencyMeasuredAtMs = now - 1_000L,
                    latencyProbeFailed = true,
                    latencyProbeFailureReason = ProbeFailureReason.NETWORK_UNREACHABLE,
                ),
            ),
            scores = mapOf(
                "slow-works-here" to ServerQualityScore(
                    serverId = "slow-works-here",
                    // 4 WIFI successes -> max reward 120; ping gap is only 80, so WITHOUT outage
                    // suppression the works-here reward would flip the ranking. The outage must
                    // suppress it so the faster plain server still wins on ping.
                    networkSuccesses = mapOf(NetworkType.WIFI to 4),
                ),
            ),
            currentServerId = null,
            nowMs = now,
            networkType = NetworkType.WIFI,
        )
        // Global outage suppresses the works-here reward, so the faster plain server wins on ping.
        assertEquals("fast-plain", selected?.candidate?.serverId)
    }

    @Test
    fun `preferredNetwork returns wifi when clearly better`() {
        val score = ServerQualityScore(
            serverId = "s",
            networkSuccesses = mapOf(NetworkType.WIFI to 5, NetworkType.CELLULAR to 1),
            networkFailures = mapOf(NetworkType.CELLULAR to 2),
        )

        assertEquals(NetworkType.WIFI, AdaptiveDecisionAgent.preferredNetwork(score))
    }

    @Test
    fun `preferredNetwork returns null on insufficient sample`() {
        val score = ServerQualityScore(
            serverId = "s",
            networkSuccesses = mapOf(NetworkType.WIFI to 2),
        )

        assertNull(AdaptiveDecisionAgent.preferredNetwork(score))
    }

    @Test
    fun `preferredNetwork returns null on ambiguous near-tie`() {
        val score = ServerQualityScore(
            serverId = "s",
            networkSuccesses = mapOf(NetworkType.WIFI to 4, NetworkType.CELLULAR to 4),
        )

        assertNull(AdaptiveDecisionAgent.preferredNetwork(score))
    }

    @Test
    fun `preferredNetwork returns null for null score`() {
        assertNull(AdaptiveDecisionAgent.preferredNetwork(null))
    }

    @Test
    fun `recordFailure and recordSuccess bucket by hour only for valid hours`() {
        val base = ServerQualityScore(serverId = "s")

        val failAt9 = AdaptiveDecisionAgent.recordFailure(base, 1_000L, NetworkType.UNKNOWN, hourOfDay = 9)
        assertEquals(mapOf(9 to 1), failAt9.failureByHour)
        assertEquals(emptyMap<Int, Int>(), failAt9.successByHour)

        val succAt9 = AdaptiveDecisionAgent.recordSuccess(failAt9, 2_000L, NetworkType.UNKNOWN, hourOfDay = 9)
        assertEquals(mapOf(9 to 1), succAt9.successByHour)
        // Failure-by-hour is independent of the network failure-shedding recovery.
        assertEquals(mapOf(9 to 1), succAt9.failureByHour)

        // Out-of-range hours bucket nothing but preserve all other logic.
        val failNoHour = AdaptiveDecisionAgent.recordFailure(succAt9, 3_000L, NetworkType.UNKNOWN, hourOfDay = -1)
        assertEquals(mapOf(9 to 1), failNoHour.failureByHour)
        assertEquals(2, failNoHour.failureCount)

        val failOob = AdaptiveDecisionAgent.recordFailure(succAt9, 4_000L, NetworkType.UNKNOWN, hourOfDay = 24)
        assertEquals(mapOf(9 to 1), failOob.failureByHour)

        // Default (no hour arg) buckets nothing and matches explicit -1.
        val failDefault = AdaptiveDecisionAgent.recordFailure(succAt9, 3_000L, NetworkType.UNKNOWN)
        assertEquals(failNoHour, failDefault)
    }

    @Test
    fun `hourly nudge prefers the server proven at this hour with sufficient samples`() {
        val now = 900_000L
        val selected = AdaptiveDecisionAgent.recommendServer(
            candidates = listOf(
                candidate(id = "plain", ping = 50, latencyMeasuredAtMs = now - 2_000L),
                candidate(id = "good-at-hour", ping = 55, latencyMeasuredAtMs = now - 2_000L),
            ),
            scores = mapOf(
                "good-at-hour" to ServerQualityScore(
                    serverId = "good-at-hour",
                    successByHour = mapOf(14 to 8),
                ),
            ),
            currentServerId = null,
            nowMs = now,
            hourOfDay = 14,
        )
        // -8 reward (capped at 30) on a 5ms ping gap flips ranking toward the proven server.
        assertEquals("good-at-hour", selected?.candidate?.serverId)
    }

    @Test
    fun `hourly nudge does nothing below the minimum sample`() {
        val now = 910_000L
        val selected = AdaptiveDecisionAgent.recommendServer(
            candidates = listOf(
                candidate(id = "plain", ping = 50, latencyMeasuredAtMs = now - 2_000L),
                candidate(id = "thin-history", ping = 55, latencyMeasuredAtMs = now - 2_000L),
            ),
            scores = mapOf(
                "thin-history" to ServerQualityScore(
                    serverId = "thin-history",
                    // Only 2 events at this hour: below the min sample, so no nudge applies.
                    successByHour = mapOf(14 to 2),
                ),
            ),
            currentServerId = null,
            nowMs = now,
            hourOfDay = 14,
        )
        // No nudge -> the faster plain server wins on ping.
        assertEquals("plain", selected?.candidate?.serverId)
    }

    @Test
    fun `hourly nudge magnitude is small and cannot overpower the per-network signal`() {
        val now = 920_000L
        // A server bad at this hour (failures dominate) AND bad on the current network. The hourly
        // penalty is capped at 30, far smaller than the per-network penalty, so it stays secondary.
        val selected = AdaptiveDecisionAgent.recommendServer(
            candidates = listOf(
                candidate(id = "bad-hour-net", ping = 40, latencyMeasuredAtMs = now - 1_000L),
                candidate(id = "neutral", ping = 60, latencyMeasuredAtMs = now - 1_000L),
            ),
            scores = mapOf(
                "bad-hour-net" to ServerQualityScore(
                    serverId = "bad-hour-net",
                    networkFailures = mapOf(NetworkType.CELLULAR to 3),
                    failureByHour = mapOf(14 to 100),
                ),
            ),
            currentServerId = null,
            nowMs = now,
            networkType = NetworkType.CELLULAR,
            hourOfDay = 14,
        )
        // Both signals push bad-hour-net behind neutral; verify it loses (and the +30 cap means the
        // hourly penalty alone, 20ms gap, would NOT have been enough — the per-network signal leads).
        assertEquals("neutral", selected?.candidate?.serverId)
    }

    @Test
    fun `hourly nudge does not resurrect an avoided server`() {
        val now = 930_000L
        val selected = AdaptiveDecisionAgent.recommendServer(
            candidates = listOf(
                candidate(id = "good-hour-avoided", ping = 10, latencyMeasuredAtMs = now - 1_000L),
                candidate(id = "healthy", ping = 90, latencyMeasuredAtMs = now - 1_000L),
            ),
            scores = mapOf(
                "good-hour-avoided" to ServerQualityScore(
                    serverId = "good-hour-avoided",
                    successByHour = mapOf(14 to 999),
                    avoidUntilMs = now + 600_000L,
                ),
            ),
            currentServerId = null,
            nowMs = now,
            hourOfDay = 14,
        )
        assertEquals("healthy", selected?.candidate?.serverId)
    }

    @Test
    fun `hourly nudge is suppressed under global outage`() {
        val now = 940_000L
        val selected = AdaptiveDecisionAgent.recommendServer(
            candidates = listOf(
                candidate(
                    id = "slow-good-hour",
                    ping = 100,
                    latencyMeasuredAtMs = now - 1_000L,
                    latencyProbeFailed = true,
                    latencyProbeFailureReason = ProbeFailureReason.TIMEOUT,
                ),
                candidate(
                    id = "fast-plain",
                    ping = 90,
                    latencyMeasuredAtMs = now - 1_000L,
                    latencyProbeFailed = true,
                    latencyProbeFailureReason = ProbeFailureReason.NETWORK_UNREACHABLE,
                ),
            ),
            scores = mapOf(
                "slow-good-hour" to ServerQualityScore(
                    serverId = "slow-good-hour",
                    successByHour = mapOf(14 to 50),
                ),
            ),
            currentServerId = null,
            nowMs = now,
            hourOfDay = 14,
        )
        // Outage suppresses the hourly reward, so the faster plain server wins on ping.
        assertEquals("fast-plain", selected?.candidate?.serverId)
    }

    @Test
    fun `hourOfDay default leaves recommendation byte-for-byte unchanged`() {
        val now = 950_000L
        val candidates = listOf(
            candidate(id = "a", ping = 45, latencyMeasuredAtMs = now - 1_000L),
            candidate(id = "b", ping = 80, latencyMeasuredAtMs = now - 1_000L),
        )
        val scores = mapOf(
            "a" to ServerQualityScore(
                serverId = "a",
                successByHour = mapOf(14 to 10),
                failureByHour = mapOf(14 to 1),
            ),
        )

        val omitted = AdaptiveDecisionAgent.recommendServer(
            candidates = candidates,
            scores = scores,
            currentServerId = null,
            nowMs = now,
        )
        val explicitDefault = AdaptiveDecisionAgent.recommendServer(
            candidates = candidates,
            scores = scores,
            currentServerId = null,
            nowMs = now,
            hourOfDay = -1,
        )

        assertEquals(omitted?.candidate?.serverId, explicitDefault?.candidate?.serverId)
        assertEquals(omitted?.score, explicitDefault?.score)
    }

    private fun candidate(
        id: String,
        ping: Int = 50,
        isPinned: Boolean = false,
        hasRuntimeConfig: Boolean = true,
        premiumBlocked: Boolean = false,
        latencyMeasuredAtMs: Long = 0L,
        latencyProbeFailed: Boolean = false,
        latencyProbeFailureReason: ProbeFailureReason? = null,
        load: Int? = null,
        availabilityStatus: String? = null,
    ) = ServerDecisionCandidate(
        serverId = id,
        pingMs = ping,
        isPinned = isPinned,
        hasRuntimeConfig = hasRuntimeConfig,
        premiumBlocked = premiumBlocked,
        latencyMeasuredAtMs = latencyMeasuredAtMs,
        latencyProbeFailed = latencyProbeFailed,
        latencyProbeFailureReason = latencyProbeFailureReason,
        load = load,
        availabilityStatus = availabilityStatus,
    )
}
