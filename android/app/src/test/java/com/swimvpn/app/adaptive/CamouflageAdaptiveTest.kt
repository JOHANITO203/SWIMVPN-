package com.swimvpn.app.adaptive

import com.swimvpn.app.vpn.NetworkType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class CamouflageAdaptiveTest {

    // --- selectBestCamouflageProfile ---

    @Test
    fun `defaults to auto (respect the link's fp) with no history`() {
        assertEquals("auto", AdaptiveDecisionAgent.selectBestCamouflageProfile(null, NetworkType.WIFI).id)
        assertEquals(
            "auto",
            AdaptiveDecisionAgent.selectBestCamouflageProfile(ServerQualityScore("s"), NetworkType.WIFI).id,
        )
    }

    @Test
    fun `unbucketed network returns default even with data`() {
        val score = ServerQualityScore("s", profileSuccesses = mapOf("WIFI|firefox" to 9))
        assertEquals("auto", AdaptiveDecisionAgent.selectBestCamouflageProfile(score, NetworkType.UNKNOWN).id)
    }

    @Test
    fun `prefers the best-margin fragmentation level on the current network`() {
        val score = ServerQualityScore(
            "s",
            profileSuccesses = mapOf("WIFI|frag_aggressive" to 3),
            profileFailures = mapOf("WIFI|auto" to 2),
        )
        assertEquals(
            "frag_aggressive",
            AdaptiveDecisionAgent.selectBestCamouflageProfile(score, NetworkType.WIFI).id,
        )
    }

    @Test
    fun `agent never picks a browser fingerprint even with strong browser history (I4)`() {
        // Browser fps override the link's validated fingerprint (broke REALITY) -> manual-only. Even an
        // overwhelming chrome/firefox history must NOT make the agent choose them: it stays in the
        // fp-preserving set {auto, frag_light, frag_aggressive}.
        val score = ServerQualityScore(
            "s",
            profileSuccesses = mapOf("WIFI|chrome" to 50, "WIFI|firefox" to 30),
        )
        val picked = AdaptiveDecisionAgent.selectBestCamouflageProfile(score, NetworkType.WIFI)
        assertEquals("auto", picked.id)
        assertEquals("", picked.fingerprint) // fp-preserving (no override)
    }

    @Test
    fun `history is network-scoped`() {
        // Strong safari history on CELLULAR must NOT influence the WIFI choice.
        val score = ServerQualityScore("s", profileSuccesses = mapOf("CELLULAR|safari" to 9))
        assertEquals("auto", AdaptiveDecisionAgent.selectBestCamouflageProfile(score, NetworkType.WIFI).id)
    }

    @Test
    fun `failing profile is superseded - implicit cascade`() {
        // chrome failed twice (margin -2), firefox once (margin -1); the no-data profiles sit at margin 0.
        val score = ServerQualityScore(
            "s",
            profileFailures = mapOf("WIFI|chrome" to 2, "WIFI|firefox" to 1),
        )
        // auto/safari/ios/randomized have margin 0 (no data) > firefox(-1) > chrome(-2); fallback order
        // tie-breaks to the first 0-margin profile = auto (respect the link rather than guess a browser).
        assertEquals("auto", AdaptiveDecisionAgent.selectBestCamouflageProfile(score, NetworkType.WIFI).id)
    }

    @Test
    fun `agent can escalate to a fragment profile that has positive margin`() {
        val score = ServerQualityScore(
            "s",
            profileSuccesses = mapOf("WIFI|frag_light" to 3),
            profileFailures = mapOf("WIFI|auto" to 2, "WIFI|chrome" to 2),
        )
        assertEquals("frag_light", AdaptiveDecisionAgent.selectBestCamouflageProfile(score, NetworkType.WIFI).id)
    }

    // --- record by profile ---

    @Test
    fun `recordFailure buckets per network and profile`() {
        val s = AdaptiveDecisionAgent.recordFailure(ServerQualityScore("s"), 1_000L, NetworkType.WIFI, profileId = "firefox")
        assertEquals(1, s.profileFailures["WIFI|firefox"])
    }

    @Test
    fun `recordSuccess credits profile and sheds one failure`() {
        val base = ServerQualityScore("s", profileFailures = mapOf("WIFI|firefox" to 2))
        val s = AdaptiveDecisionAgent.recordSuccess(base, 1_000L, NetworkType.WIFI, profileId = "firefox")
        assertEquals(1, s.profileSuccesses["WIFI|firefox"])
        assertEquals(1, s.profileFailures["WIFI|firefox"])
    }

    @Test
    fun `no profile or unbucketed network records nothing`() {
        val noProfile = AdaptiveDecisionAgent.recordFailure(ServerQualityScore("s"), 1L, NetworkType.WIFI, profileId = null)
        assertTrue(noProfile.profileFailures.isEmpty())
        val unbucketed = AdaptiveDecisionAgent.recordFailure(ServerQualityScore("s"), 1L, NetworkType.UNKNOWN, profileId = "firefox")
        assertTrue(unbucketed.profileFailures.isEmpty())
    }

    // --- codec v6 ---

    @Test
    fun `codec v6 round-trips profile maps`() {
        val score = ServerQualityScore(
            "srv",
            successCount = 1,
            networkFailures = mapOf(NetworkType.WIFI to 1),
            profileSuccesses = mapOf("WIFI|firefox" to 3, "CELLULAR|chrome" to 1),
            profileFailures = mapOf("WIFI|chrome" to 2),
        )
        val decoded = ServerScoreCodec.decode(ServerScoreCodec.encode(score))!!
        assertEquals(score.profileSuccesses, decoded.profileSuccesses)
        assertEquals(score.profileFailures, decoded.profileFailures)
        assertEquals(score.networkFailures, decoded.networkFailures)
    }

    @Test
    fun `scores without profile data decode to empty profile maps`() {
        val score = ServerQualityScore("srv", successCount = 2)
        val decoded = ServerScoreCodec.decode(ServerScoreCodec.encode(score))!!
        assertTrue(decoded.profileSuccesses.isEmpty())
        assertTrue(decoded.profileFailures.isEmpty())
    }

    // --- BenchmarkCollector record (privacy-first) ---

    @Test
    fun `benchmark record exposes only technical fields`() {
        val fields = SessionBenchmarkRecord(
            transport = "VLESS",
            profileId = "firefox",
            networkType = NetworkType.WIFI,
            success = true,
        ).toLogFields()
        assertEquals("VLESS", fields["transport"])
        assertEquals("firefox", fields["profile"])
        assertEquals("WIFI", fields["network"])
        assertEquals(true, fields["success"])
        assertFalse("no failure cause on success", fields.containsKey("cause"))
    }

    // --- MORPH_PROFILE cascade (Stage B) ---

    @Test
    fun `soft degradation skips same-server retry and morphs immediately`() {
        val action = AdaptiveDecisionAgent.planAfterFailure(
            currentServerId = "s", candidates = listOf(), scores = emptyMap(),
            reconnectAttempt = 0, nowMs = 1_000L, networkType = NetworkType.WIFI,
            currentProfileId = "auto", triedProfileIds = setOf("auto"),
            softDegradation = true,
        )
        assertEquals(DecisionActionType.MORPH_PROFILE, action.type)
        assertEquals("frag_light", action.targetProfileId) // fp-preserving escalation, never a browser fp
    }

    @Test
    fun `morph is chosen after same-server retries when an untried profile exists`() {
        val action = AdaptiveDecisionAgent.planAfterFailure(
            currentServerId = "s",
            candidates = listOf(),
            scores = emptyMap(),
            reconnectAttempt = 2,
            nowMs = 1_000L,
            networkType = NetworkType.WIFI,
            currentProfileId = "auto",
            triedProfileIds = setOf("auto"),
        )
        assertEquals(DecisionActionType.MORPH_PROFILE, action.type)
        assertEquals("s", action.targetServerId)
        assertEquals("frag_light", action.targetProfileId) // next untried fp-preserving profile after auto
    }

    @Test
    fun `morph stops after the limit and falls through to switch-or-retry`() {
        val action = AdaptiveDecisionAgent.planAfterFailure(
            currentServerId = "s",
            candidates = listOf(),
            scores = emptyMap(),
            reconnectAttempt = 2,
            nowMs = 1_000L,
            networkType = NetworkType.WIFI,
            currentProfileId = "frag_aggressive",
            triedProfileIds = setOf("auto", "frag_light", "frag_aggressive"),
        )
        assertEquals(DecisionActionType.RECONNECT_SAME, action.type) // no fallback candidate -> retry current
    }

    @Test
    fun `morph cascade only ever escalates fragmentation, never a browser fp`() {
        // Walk the untried-profile cascade from scratch: it must yield ONLY fp-preserving profiles
        // (auto -> frag_light -> frag_aggressive -> exhausted), never chrome/firefox/safari/ios/randomized.
        val tried = mutableSetOf<String>()
        val walked = mutableListOf<String>()
        while (true) {
            val next = AdaptiveDecisionAgent.nextUntriedProfile(tried) ?: break
            assertEquals("fp-preserving only", "", next.fingerprint)
            walked += next.id
            tried += next.id
        }
        assertEquals(listOf("auto", "frag_light", "frag_aggressive"), walked)
    }

    @Test
    fun `same-server retry still precedes any morph`() {
        val action = AdaptiveDecisionAgent.planAfterFailure(
            currentServerId = "s", candidates = listOf(), scores = emptyMap(),
            reconnectAttempt = 0, nowMs = 1_000L, networkType = NetworkType.WIFI,
            currentProfileId = "auto", triedProfileIds = setOf("auto"),
        )
        assertEquals(DecisionActionType.RECONNECT_SAME, action.type)
    }
}
