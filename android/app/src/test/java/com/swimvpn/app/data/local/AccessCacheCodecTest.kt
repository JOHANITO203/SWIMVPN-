package com.swimvpn.app.data.local

import com.swimvpn.app.data.network.AccessProfileResponse
import com.swimvpn.app.data.network.ProbeFailureReason
import com.swimvpn.app.data.network.ServerNode
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

class AccessCacheCodecTest {

    private fun profile() = AccessProfileResponse(
        userNumber = "user-42",
        email = null,
        phone = null,
        accessType = "PAID",
        offerCode = null,
        planType = "MONTH",
        status = "ACTIVE",
        trialStartedAt = null,
        trialExpiresAt = null,
        subscriptionExpiresAt = "2099-01-01T00:00:00Z",
        subscriptionUrl = null,
        devicesAllowed = 3,
        dataLimitGB = 100.0,
        dataUsedBytes = "12345",
        profileCompletionRequired = false,
        trialEligible = false,
    )

    private fun server(id: String) = ServerNode(
        id = id,
        country = "Poland",
        city = "Warsaw",
        host = "node.example.com",
        port = 443,
        protocol = "vless",
        tags = listOf("reality"),
        planScope = "premium",
        rawConfig = "vless://secret@node.example.com:443?security=reality#x",
        latencyProbeFailureReason = ProbeFailureReason.TIMEOUT,
        source = "backend",
    )

    @Test
    fun `round-trips profile + servers (incl rawConfig and enum) losslessly`() {
        val original = CachedAccess(
            profile = profile(),
            servers = listOf(server("a"), server("b")),
            savedAtMs = 1_700_000_000_000L,
        )
        val decoded = AccessCacheCodec.decode(AccessCacheCodec.encode(original))
        assertEquals(original, decoded)
        // The secret rawConfig survives the round-trip (it is what offline connect needs).
        assertEquals(
            "vless://secret@node.example.com:443?security=reality#x",
            decoded?.servers?.first()?.rawConfig,
        )
    }

    @Test
    fun `empty server list round-trips`() {
        val original = CachedAccess(profile(), emptyList(), 1L)
        assertEquals(original, AccessCacheCodec.decode(AccessCacheCodec.encode(original)))
    }

    @Test
    fun `null, blank and garbage decode to null without throwing`() {
        assertNull(AccessCacheCodec.decode(null))
        assertNull(AccessCacheCodec.decode(""))
        assertNull(AccessCacheCodec.decode("   "))
        assertNull(AccessCacheCodec.decode("{not valid json"))
        assertNull(AccessCacheCodec.decode("[]")) // wrong shape -> parse fails -> null
        assertNull(AccessCacheCodec.decode("{}")) // object with no profile -> guarded -> null
        assertNull(AccessCacheCodec.decode("{\"servers\":[],\"savedAtMs\":1}")) // missing profile -> null
    }

    @Test
    fun `unknown enum constant decodes via fallback instead of throwing`() {
        // Simulate a cache written by a newer build that added a ProbeFailureReason this build doesn't know.
        val original = CachedAccess(profile(), listOf(server("a")), 1L)
        val tampered = AccessCacheCodec.encode(original)
            .replace("\"TIMEOUT\"", "\"SOME_FUTURE_REASON\"")
        val decoded = AccessCacheCodec.decode(tampered)
        assertNotNull(decoded) // tolerant: cache survives the unknown enum, not wiped
        assertEquals(1, decoded?.servers?.size)
    }
}
