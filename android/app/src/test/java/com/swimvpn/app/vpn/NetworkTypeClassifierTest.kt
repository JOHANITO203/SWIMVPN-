package com.swimvpn.app.vpn

import org.junit.Assert.assertEquals
import org.junit.Test

class NetworkTypeClassifierTest {

    @Test
    fun `no internet is classified as none regardless of transport`() {
        assertEquals(
            NetworkType.NONE,
            NetworkTypeClassifier.classify(
                hasWifi = true,
                hasCellular = true,
                hasEthernet = true,
                hasInternet = false,
            ),
        )
    }

    @Test
    fun `wifi wins over cellular and ethernet`() {
        assertEquals(
            NetworkType.WIFI,
            NetworkTypeClassifier.classify(
                hasWifi = true,
                hasCellular = true,
                hasEthernet = true,
                hasInternet = true,
            ),
        )
    }

    @Test
    fun `cellular wins over ethernet when no wifi`() {
        assertEquals(
            NetworkType.CELLULAR,
            NetworkTypeClassifier.classify(
                hasWifi = false,
                hasCellular = true,
                hasEthernet = true,
                hasInternet = true,
            ),
        )
    }

    @Test
    fun `ethernet is selected when only ethernet present`() {
        assertEquals(
            NetworkType.ETHERNET,
            NetworkTypeClassifier.classify(
                hasWifi = false,
                hasCellular = false,
                hasEthernet = true,
                hasInternet = true,
            ),
        )
    }

    @Test
    fun `internet with no known transport is other`() {
        assertEquals(
            NetworkType.OTHER,
            NetworkTypeClassifier.classify(
                hasWifi = false,
                hasCellular = false,
                hasEthernet = false,
                hasInternet = true,
            ),
        )
    }
}
