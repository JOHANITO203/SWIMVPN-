package com.swimvpn.app.adaptive

import com.swimvpn.app.vpn.NetworkType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class CamouflageAdaptiveTest {

    // --- selectBestCamouflageProfile ---

    @Test
    fun `defaults to chrome with no history`() {
        assertEquals("chrome", AdaptiveDecisionAgent.selectBestCamouflageProfile(null, NetworkType.WIFI).id)
        assertEquals(
            "chrome",
            AdaptiveDecisionAgent.selectBestCamouflageProfile(ServerQualityScore("s"), NetworkType.WIFI).id,
        )
    }

    @Test
    fun `unbucketed network returns default even with data`() {
        val score = ServerQualityScore("s", profileSuccesses = mapOf("WIFI|firefox" to 9))
        assertEquals("chrome", AdaptiveDecisionAgent.selectBestCamouflageProfile(score, NetworkType.UNKNOWN).id)
    }

    @Test
    fun `prefers best margin on the current network`() {
        val score = ServerQualityScore(
            "s",
            profileSuccesses = mapOf("WIFI|firefox" to 3),
            profileFailures = mapOf("WIFI|chrome" to 2),
        )
        assertEquals("firefox", AdaptiveDecisionAgent.selectBestCamouflageProfile(score, NetworkType.WIFI).id)
    }

    @Test
    fun `history is network-scoped`() {
        // Strong safari history on CELLULAR must NOT influence the WIFI choice.
        val score = ServerQualityScore("s", profileSuccesses = mapOf("CELLULAR|safari" to 9))
        assertEquals("chrome", AdaptiveDecisionAgent.selectBestCamouflageProfile(score, NetworkType.WIFI).id)
    }

    @Test
    fun `failing profile is superseded - implicit cascade`() {
        // chrome failed twice (margin -2), firefox once (margin -1) -> firefox wins (less bad), with the
        // remaining no-data profiles at margin 0 actually preferred: safari (0) beats both.
        val score = ServerQualityScore(
            "s",
            profileFailures = mapOf("WIFI|chrome" to 2, "WIFI|firefox" to 1),
        )
        // safari/ios/randomized have margin 0 (no data) > firefox(-1) > chrome(-2); fallback order
        // tie-breaks to the first 0-margin profile = safari.
        assertEquals("safari", AdaptiveDecisionAgent.selectBestCamouflageProfile(score, NetworkType.WIFI).id)
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
}
