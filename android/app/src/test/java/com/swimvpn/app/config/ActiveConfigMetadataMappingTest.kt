package com.swimvpn.app.config

import com.swimvpn.app.config.subscriptionparser.ParsedSubscription
import com.swimvpn.app.config.subscriptionparser.ParsedVpnProfile
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ActiveConfigMetadataMappingTest {

    @Test
    fun `maps explicit profile metadata with imported source`() {
        val profile = ParsedVpnProfile(
            displayName = "NL Edge",
            protocol = "vless",
            providerName = "Provider Demo",
            trafficUsedBytes = 15L,
            trafficTotalBytes = 100L,
            expiresAt = "2026-05-15T00:00:00Z",
            raw = "vless://demo"
        )

        val metadata = ActiveConfigMetadata.fromParsedProfile(
            profile = profile,
            fallback = ActiveConfigFallbackMetadata(),
            source = ActiveConfigSource.IMPORTED_CONFIG,
            isActive = true,
        )

        assertEquals(ActiveConfigSource.IMPORTED_CONFIG, metadata.source)
        assertEquals("NL Edge", metadata.displayName)
        assertEquals("Provider Demo", metadata.providerName)
        assertEquals(15L, metadata.trafficUsedBytes)
        assertEquals(100L, metadata.trafficTotalBytes)
        assertEquals("2026-05-15T00:00:00Z", metadata.expiresAt)
    }

    @Test
    fun `profile fields take precedence over fallback metadata`() {
        val profile = ParsedVpnProfile(
            displayName = "NL Edge",
            protocol = "vless",
            providerName = "Profile Provider",
            serverHost = "profile.example.com",
            trafficUsedBytes = 15L,
            trafficTotalBytes = 100L,
            expiresAt = "2026-05-15T00:00:00Z",
            raw = "vless://demo"
        )

        val metadata = ActiveConfigMetadata.fromParsedProfile(
            profile = profile,
            fallback = ActiveConfigFallbackMetadata(
                providerName = "Fallback Provider",
                trafficUsedBytes = 30L,
                trafficTotalBytes = 200L,
                expiresAt = "2026-06-01T00:00:00Z",
            ),
            source = ActiveConfigSource.IMPORTED_CONFIG,
            isActive = true,
        )

        assertEquals("Profile Provider", metadata.providerName)
        assertEquals("profile.example.com", metadata.serverHost)
        assertEquals(15L, metadata.trafficUsedBytes)
        assertEquals(100L, metadata.trafficTotalBytes)
        assertEquals("2026-05-15T00:00:00Z", metadata.expiresAt)
    }

    @Test
    fun `warnings are merged and deduplicated`() {
        val profile = ParsedVpnProfile(
            displayName = "NL Edge",
            protocol = "vless",
            warnings = listOf("shared-warning", "profile-warning"),
            raw = "vless://demo"
        )

        val metadata = ActiveConfigMetadata.fromParsedProfile(
            profile = profile,
            fallback = ActiveConfigFallbackMetadata(
                warnings = listOf("shared-warning", "subscription-warning")
            ),
            source = ActiveConfigSource.IMPORTED_CONFIG,
            isActive = true,
        )

        assertEquals(
            listOf("shared-warning", "subscription-warning", "profile-warning"),
            metadata.warnings
        )
    }

    @Test
    fun `returns null when first-profile convenience path gets empty subscription`() {
        val parsed = ParsedSubscription(
            profiles = emptyList(),
            raw = "payload"
        )

        val metadata = ActiveConfigMetadata.fromFirstProfileInSubscription(
            parsed = parsed,
            source = ActiveConfigSource.IMPORTED_CONFIG,
            isActive = true,
        )

        assertNull(metadata)
    }
}
