package com.swimvpn.app.config

import com.swimvpn.app.config.subscriptionparser.ParsedSubscription
import com.swimvpn.app.config.subscriptionparser.ParsedVpnProfile
import com.swimvpn.app.data.network.ServerNode
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

class ActiveConfigMetadataMappingTest {

    @Test
    fun `returns imported active config metadata from active profile raw config`() {
        val raw = """
            Provider Demo
            15.3GB/1000.0GB
            Expires: 15.05.2026
            vless://11111111-1111-1111-1111-111111111111@example.com:443?security=tls&type=tcp#Node
        """.trimIndent()

        val metadata = ActiveConfigMetadata.fromRawConfig(
            rawConfig = raw,
            source = ActiveConfigSource.IMPORTED_CONFIG,
            displayNameFallback = "Node"
        )

        assertNotNull(metadata)
        assertEquals(ActiveConfigSource.IMPORTED_CONFIG, metadata?.source)
        assertEquals("Provider Demo", metadata?.providerName)
        assertEquals(15.3 * 1024 * 1024 * 1024, metadata!!.trafficUsedBytes!!.toDouble(), 1024.0)
    }

    @Test
    fun `raw config fallback preserves subscription metadata when no profile is parsed`() {
        val raw = """
            Provider Demo
            15.3GB/1000.0GB
            Expires: 15.05.2026
        """.trimIndent()

        val metadata = ActiveConfigMetadata.fromRawConfig(
            rawConfig = raw,
            source = ActiveConfigSource.IMPORTED_CONFIG,
            displayNameFallback = "Imported fallback",
        )

        assertNotNull(metadata)
        assertEquals("Imported fallback", metadata?.displayName)
        assertEquals("Provider Demo", metadata?.providerName)
        assertEquals(15.3 * 1024 * 1024 * 1024, metadata!!.trafficUsedBytes!!.toDouble(), 1024.0)
        assertEquals(1000.0 * 1024 * 1024 * 1024, metadata.trafficTotalBytes!!.toDouble(), 1024.0)
        assertEquals("2026-05-15T00:00:00Z", metadata.expiresAt)
    }

    @Test
    fun `repository source classification treats all user imported sources as imported config`() {
        val importedSources = listOf(
            SourceType.CLIPBOARD,
            SourceType.FILE_IMPORT,
            SourceType.QR_CODE,
            SourceType.MANUAL_ENTRY,
            SourceType.SUBSCRIPTION_URL,
        )

        importedSources.forEach { sourceType ->
            assertEquals(
                ActiveConfigSource.IMPORTED_CONFIG,
                ConfigRepository.activeConfigSourceFor(sourceType)
            )
        }

        assertEquals(
            ActiveConfigSource.SWIMVPN_MANAGED,
            ConfigRepository.activeConfigSourceFor(SourceType.BACKEND_API)
        )
    }

    @Test
    fun `imported server id helpers encode and decode consistently`() {
        val profileId = "profile-123"
        val serverId = ConfigRepository.importedServerIdFor(profileId)

        assertEquals("imported:profile-123", serverId)
        assertEquals(profileId, ConfigRepository.importedProfileIdFromServerId(serverId))
        assertNull(ConfigRepository.importedProfileIdFromServerId(profileId))
        assertNull(ConfigRepository.importedProfileIdFromServerId("imported:"))
    }

    @Test
    fun `maps backend selected server to managed active config metadata without parser-only fields`() {
        val metadata = ActiveConfigMetadata.fromManagedServer(
            server = ServerNode(
                id = "backend-ru-1",
                country = "Russia",
                city = "Moscow",
                host = "ru-msk.swimvpn.pro",
                port = 443,
                protocol = "vless",
                tags = listOf("managed"),
                planScope = "month",
                source = "backend",
            ),
            isActive = true,
        )

        assertEquals(ActiveConfigSource.SWIMVPN_MANAGED, metadata.source)
        assertEquals("Moscow", metadata.displayName)
        assertEquals("vless", metadata.protocol)
        assertEquals("ru-msk.swimvpn.pro", metadata.serverHost)
        assertNull(metadata.providerName)
        assertNull(metadata.trafficUsedBytes)
        assertNull(metadata.trafficTotalBytes)
        assertNull(metadata.expiresAt)
        assertEquals(emptyList<String>(), metadata.warnings)
    }

    @Test
    fun `managed server metadata includes fulfillment node health hints`() {
        val metadata = ActiveConfigMetadata.fromManagedServer(
            server = ServerNode(
                id = "backend-node-health",
                country = "Premium Servers",
                city = "NL Edge",
                host = "nl.example.com",
                port = 443,
                protocol = "vless",
                tags = listOf("managed"),
                planScope = "month",
                source = "backend",
                providerName = "VlessWB",
                availabilityStatus = "CONGESTED",
                load = 92,
            ),
            isActive = true,
        )

        assertEquals("nl.example.com", metadata.serverHost)
        assertEquals("VlessWB", metadata.providerName)
        assertEquals("CONGESTED", metadata.availabilityStatus)
        assertEquals(92, metadata.loadPercent)
    }

    @Test
    fun `managed server metadata parses JSON-safe traffic byte strings defensively`() {
        val metadata = ActiveConfigMetadata.fromManagedServer(
            server = ServerNode(
                id = "backend-node-traffic",
                country = "Premium Servers",
                city = "NL Edge",
                host = "nl.example.com",
                port = 443,
                protocol = "vless",
                tags = listOf("managed"),
                planScope = "month",
                source = "backend",
                trafficUsedBytes = "7",
                trafficTotalBytes = "9223372036854775808",
            ),
            isActive = true,
        )

        assertEquals(7L, metadata.trafficUsedBytes)
        assertNull(metadata.trafficTotalBytes)
    }

    @Test
    fun `managed active config uses resolved premium node name but plan quota truth`() {
        val profile = com.swimvpn.app.data.network.AccessProfileResponse(
            userNumber = "SW-TEST",
            email = "user@example.com",
            phone = "+1000",
            accessType = "PAID",
            offerCode = "WEEK",
            planDisplayName = "Basic",
            planType = "paid",
            status = "ACTIVE",
            entitlementState = "ACTIVE_SUBSCRIPTION",
            trialStartedAt = null,
            trialExpiresAt = null,
            subscriptionExpiresAt = "2026-05-09T00:00:00Z",
            subscriptionUrl = "https://wb.routerwb.ru/demo",
            devicesAllowed = 2,
            dataLimitGB = 50.0,
            dataUsedBytes = (2L * 1024L * 1024L * 1024L).toString(),
            supplierProviderName = "Backend Provider",
            supplierExpiresAt = "2026-05-09T00:00:00Z",
            profileCompletionRequired = false,
            trialEligible = false,
        )

        val metadata = ActiveConfigMetadata.fromManagedProfile(
            profile = profile,
            server = ServerNode(
                id = "backend-node-1",
                country = "Premium Servers",
                city = "VlessWB NL",
                host = "nl.example.com",
                port = 443,
                protocol = "vless",
                tags = listOf("premium"),
                planScope = "WEEK",
                source = "backend",
                rawConfig = "vless://11111111-1111-1111-1111-111111111111@nl.example.com:443?security=tls#VlessWB%20NL",
                providerName = "VlessWB",
                trafficUsedBytes = "7",
                trafficTotalBytes = "100",
                expiresAt = "2026-05-21T00:00:00Z",
            ),
        )

        assertEquals("VlessWB NL", metadata.displayName)
        assertEquals("vless", metadata.protocol)
        assertEquals("nl.example.com", metadata.serverHost)
        assertEquals("Backend Provider", metadata.providerName)
        assertEquals(2L * 1024L * 1024L * 1024L, metadata.trafficUsedBytes)
        assertEquals(50L * 1024L * 1024L * 1024L, metadata.trafficTotalBytes)
        assertEquals("2026-05-09T00:00:00Z", metadata.expiresAt)
    }

    @Test
    fun `stored imported profile exposes persisted subscription metadata`() {
        val profile = SwimVpnProfile(
            sourceType = SourceType.SUBSCRIPTION_URL,
            rawConfig = "vless://11111111-1111-1111-1111-111111111111@pl.cloudrt.ru:443?security=reality&type=tcp#Poland",
            sourceFormat = SourceFormat.VLESS_URL,
            protocol = Protocol.VLESS,
            transport = Transport.TCP,
            securityMode = SecurityMode.REALITY,
            address = "pl.cloudrt.ru",
            port = 443,
            displayName = "Poland",
            displaySubtitle = "TCP, REALITY, Port: 443",
            subscriptionProviderName = "VlessWB VPN",
            subscriptionTrafficUsedBytes = 18L * 1024L * 1024L * 1024L,
            subscriptionTrafficTotalBytes = 1000L * 1024L * 1024L * 1024L,
            subscriptionExpiresAt = "2026-05-22T00:00:00Z",
            subscriptionAutoUpdateIntervalHours = 1,
        )

        val metadata = ActiveConfigMetadata.fromImportedProfile(profile)

        assertEquals(ActiveConfigSource.IMPORTED_CONFIG, metadata.source)
        assertEquals("Poland", metadata.displayName)
        assertEquals("VlessWB VPN", metadata.providerName)
        assertEquals(18L * 1024L * 1024L * 1024L, metadata.trafficUsedBytes)
        assertEquals(1000L * 1024L * 1024L * 1024L, metadata.trafficTotalBytes)
        assertEquals("2026-05-22T00:00:00Z", metadata.expiresAt)
    }

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
