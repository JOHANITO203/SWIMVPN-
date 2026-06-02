package com.swimvpn.app.adaptive

import com.swimvpn.app.vpn.NetworkType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ServerScoreCodecTest {

    @Test
    fun `v3 round trip preserves all fields including multi entry network failures`() {
        val original = ServerQualityScore(
            serverId = "server-a",
            successCount = 3,
            failureCount = 2,
            consecutiveFailures = 1,
            lastSuccessAtMs = 111L,
            lastFailureAtMs = 222L,
            avoidUntilMs = 333L,
            manualSelectionCount = 4,
            lastManualSelectionAtMs = 444L,
            networkFailures = mapOf(
                NetworkType.WIFI to 2,
                NetworkType.CELLULAR to 1,
            ),
        )

        val encoded = ServerScoreCodec.encode(original)
        assertEquals(ServerScoreCodec.VERSION, encoded.split(ServerScoreCodec.SEPARATOR).first())

        val decoded = ServerScoreCodec.decode(encoded)
        assertEquals(original, decoded)
    }

    @Test
    fun `v2 row decodes with new fields at defaults`() {
        // A genuine v2 row: leading "v2" token + the original 7 score fields, no new trailing fields.
        val v2 = listOf(
            "v2",
            "server-v2",
            3,
            2,
            1,
            111L,
            222L,
            333L,
        ).joinToString(ServerScoreCodec.SEPARATOR)

        val decoded = ServerScoreCodec.decode(v2)

        assertEquals(
            ServerQualityScore(
                serverId = "server-v2",
                successCount = 3,
                failureCount = 2,
                consecutiveFailures = 1,
                lastSuccessAtMs = 111L,
                lastFailureAtMs = 222L,
                avoidUntilMs = 333L,
                // new v3 fields default when absent.
                manualSelectionCount = 0,
                lastManualSelectionAtMs = 0L,
                networkFailures = emptyMap(),
            ),
            decoded,
        )
    }

    @Test
    fun `network failures field with unknown or garbage tokens is skipped without throwing`() {
        val row = listOf(
            "v3",
            "server-garbage",
            0,
            0,
            0,
            0L,
            0L,
            0L,
            0,
            0L,
            // valid WIFI entry, unknown enum name, malformed (no value), non-numeric value.
            "WIFI:2;MARS:9;CELLULAR;ETHERNET:abc",
        ).joinToString(ServerScoreCodec.SEPARATOR)

        val decoded = ServerScoreCodec.decode(row)

        assertEquals(mapOf(NetworkType.WIFI to 2), decoded?.networkFailures)
    }

    @Test
    fun `legacy seven field row still decodes`() {
        val legacy = listOf(
            "server-legacy",
            5,
            4,
            2,
            900L,
            800L,
            700L,
        ).joinToString(ServerScoreCodec.SEPARATOR)

        val decoded = ServerScoreCodec.decode(legacy)

        assertEquals(
            ServerQualityScore(
                serverId = "server-legacy",
                successCount = 5,
                failureCount = 4,
                consecutiveFailures = 2,
                lastSuccessAtMs = 900L,
                lastFailureAtMs = 800L,
                avoidUntilMs = 700L,
            ),
            decoded,
        )
    }

    @Test
    fun `future versioned row with extra trailing fields still decodes known fields`() {
        // Simulates a future version (e.g. v4) that appended fields this build does not know about.
        // The known v3 fields are decoded; the unknown trailing fields are ignored, never rejected.
        val futureRow = listOf(
            "v4",
            "server-future",
            7,
            1,
            0,
            10L,
            20L,
            30L,
            5,
            55L,
            "WIFI:3",
            "future-flag",
            "999",
        ).joinToString(ServerScoreCodec.SEPARATOR)

        val decoded = ServerScoreCodec.decode(futureRow)

        assertEquals(
            ServerQualityScore(
                serverId = "server-future",
                successCount = 7,
                failureCount = 1,
                consecutiveFailures = 0,
                lastSuccessAtMs = 10L,
                lastFailureAtMs = 20L,
                avoidUntilMs = 30L,
                manualSelectionCount = 5,
                lastManualSelectionAtMs = 55L,
                networkFailures = mapOf(NetworkType.WIFI to 3),
            ),
            decoded,
        )
    }

    @Test
    fun `malformed row decodes to null`() {
        assertNull(ServerScoreCodec.decode("not${ServerScoreCodec.SEPARATOR}enough"))
    }
}
