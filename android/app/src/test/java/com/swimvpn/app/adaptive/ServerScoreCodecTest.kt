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
    fun `v4 round trip preserves both network failure and success maps`() {
        val original = ServerQualityScore(
            serverId = "server-v4",
            successCount = 9,
            failureCount = 3,
            consecutiveFailures = 0,
            lastSuccessAtMs = 111L,
            lastFailureAtMs = 222L,
            avoidUntilMs = 333L,
            manualSelectionCount = 2,
            lastManualSelectionAtMs = 444L,
            networkFailures = mapOf(
                NetworkType.WIFI to 1,
                NetworkType.CELLULAR to 2,
            ),
            networkSuccesses = mapOf(
                NetworkType.WIFI to 5,
                NetworkType.ETHERNET to 4,
            ),
        )

        val encoded = ServerScoreCodec.encode(original)
        // The current encoder always writes the latest VERSION token; this v4-shaped score still
        // round-trips losslessly through it.
        assertEquals(ServerScoreCodec.VERSION, encoded.split(ServerScoreCodec.SEPARATOR).first())

        val decoded = ServerScoreCodec.decode(encoded)
        assertEquals(original, decoded)
    }

    @Test
    fun `v3 row decodes with network successes defaulted to empty`() {
        // A genuine v3 row: "v3" token + score fields + networkFailures, but NO networkSuccesses field.
        val v3 = listOf(
            "v3",
            "server-v3",
            6,
            2,
            0,
            10L,
            20L,
            0L,
            1,
            55L,
            "WIFI:2;CELLULAR:1",
        ).joinToString(ServerScoreCodec.SEPARATOR)

        val decoded = ServerScoreCodec.decode(v3)

        assertEquals(
            ServerQualityScore(
                serverId = "server-v3",
                successCount = 6,
                failureCount = 2,
                consecutiveFailures = 0,
                lastSuccessAtMs = 10L,
                lastFailureAtMs = 20L,
                avoidUntilMs = 0L,
                manualSelectionCount = 1,
                lastManualSelectionAtMs = 55L,
                networkFailures = mapOf(
                    NetworkType.WIFI to 2,
                    NetworkType.CELLULAR to 1,
                ),
                // v4 field defaults losslessly when absent from a v3 row.
                networkSuccesses = emptyMap(),
            ),
            decoded,
        )
    }

    @Test
    fun `network successes field with unknown or garbage tokens is skipped without throwing`() {
        val row = listOf(
            "v4",
            "server-garbage-success",
            0,
            0,
            0,
            0L,
            0L,
            0L,
            0,
            0L,
            "",
            // valid CELLULAR entry, unknown enum name, malformed (no value), non-numeric value.
            "CELLULAR:4;MARS:9;WIFI;ETHERNET:xyz",
        ).joinToString(ServerScoreCodec.SEPARATOR)

        val decoded = ServerScoreCodec.decode(row)

        assertEquals(mapOf(NetworkType.CELLULAR to 4), decoded?.networkSuccesses)
        assertEquals(emptyMap<NetworkType, Int>(), decoded?.networkFailures)
    }

    @Test
    fun `v5 round trip preserves both hour maps`() {
        val original = ServerQualityScore(
            serverId = "server-v5",
            successCount = 9,
            failureCount = 3,
            consecutiveFailures = 0,
            lastSuccessAtMs = 111L,
            lastFailureAtMs = 222L,
            avoidUntilMs = 333L,
            manualSelectionCount = 2,
            lastManualSelectionAtMs = 444L,
            networkFailures = mapOf(NetworkType.CELLULAR to 2),
            networkSuccesses = mapOf(NetworkType.WIFI to 5),
            successByHour = mapOf(9 to 2, 18 to 5),
            failureByHour = mapOf(3 to 1, 22 to 4),
        )

        val encoded = ServerScoreCodec.encode(original)
        // Current codec version (bumped to v6 in Phase 3); round-trip semantics below are unchanged.
        assertEquals("v6", encoded.split(ServerScoreCodec.SEPARATOR).first())

        val decoded = ServerScoreCodec.decode(encoded)
        assertEquals(original, decoded)
    }

    @Test
    fun `v4 row decodes with hour maps defaulted to empty`() {
        // A genuine v4 row: token + score fields + networkFailures + networkSuccesses, NO hour fields.
        val v4 = listOf(
            "v4",
            "server-v4row",
            6,
            2,
            0,
            10L,
            20L,
            0L,
            1,
            55L,
            "WIFI:2;CELLULAR:1",
            "WIFI:4",
        ).joinToString(ServerScoreCodec.SEPARATOR)

        val decoded = ServerScoreCodec.decode(v4)

        assertEquals(
            ServerQualityScore(
                serverId = "server-v4row",
                successCount = 6,
                failureCount = 2,
                consecutiveFailures = 0,
                lastSuccessAtMs = 10L,
                lastFailureAtMs = 20L,
                avoidUntilMs = 0L,
                manualSelectionCount = 1,
                lastManualSelectionAtMs = 55L,
                networkFailures = mapOf(
                    NetworkType.WIFI to 2,
                    NetworkType.CELLULAR to 1,
                ),
                networkSuccesses = mapOf(NetworkType.WIFI to 4),
                // v5 fields default losslessly when absent from a v4 row.
                successByHour = emptyMap(),
                failureByHour = emptyMap(),
            ),
            decoded,
        )
    }

    @Test
    fun `hour maps with garbage or out of range tokens are skipped without throwing`() {
        val row = listOf(
            "v5",
            "server-hour-garbage",
            0,
            0,
            0,
            0L,
            0L,
            0L,
            0,
            0L,
            "",
            "",
            // valid hour 9, out-of-range hour 24, malformed (no value), non-numeric value, non-numeric hour.
            "9:2;24:9;5;7:abc;xx:3",
            // valid hour 0, negative hour, valid hour 23.
            "0:1;-1:5;23:7",
        ).joinToString(ServerScoreCodec.SEPARATOR)

        val decoded = ServerScoreCodec.decode(row)

        assertEquals(mapOf(9 to 2), decoded?.successByHour)
        assertEquals(mapOf(0 to 1, 23 to 7), decoded?.failureByHour)
    }

    @Test
    fun `malformed row decodes to null`() {
        assertNull(ServerScoreCodec.decode("not${ServerScoreCodec.SEPARATOR}enough"))
    }
}
