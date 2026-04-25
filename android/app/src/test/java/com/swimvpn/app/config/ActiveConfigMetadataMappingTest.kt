package com.swimvpn.app.config

import com.swimvpn.app.config.subscriptionparser.ParsedSubscription
import com.swimvpn.app.config.subscriptionparser.ParsedVpnProfile
import org.junit.Assert.assertEquals
import org.junit.Test

class ActiveConfigMetadataMappingTest {

    @Test
    fun `maps imported config metadata with imported source`() {
        val parsed = ParsedSubscription(
            profiles = listOf(
                ParsedVpnProfile(
                    displayName = "NL Edge",
                    protocol = "vless",
                    providerName = "Provider Demo",
                    trafficUsedBytes = 15L,
                    trafficTotalBytes = 100L,
                    expiresAt = "2026-05-15T00:00:00Z",
                    raw = "vless://demo"
                )
            ),
            raw = "payload"
        )

        val metadata = ActiveConfigMetadata.fromParsedSubscription(
            parsed = parsed,
            source = ActiveConfigSource.IMPORTED_CONFIG,
            isActive = true,
        )

        assertEquals(ActiveConfigSource.IMPORTED_CONFIG, metadata?.source)
        assertEquals("NL Edge", metadata?.displayName)
        assertEquals("Provider Demo", metadata?.providerName)
        assertEquals(15L, metadata?.trafficUsedBytes)
        assertEquals(100L, metadata?.trafficTotalBytes)
        assertEquals("2026-05-15T00:00:00Z", metadata?.expiresAt)
    }
}
