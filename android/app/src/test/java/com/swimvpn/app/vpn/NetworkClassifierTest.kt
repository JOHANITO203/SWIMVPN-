package com.swimvpn.app.vpn

import com.swimvpn.app.vpn.NetworkClassifier.NetworkClass
import org.junit.Assert.assertEquals
import org.junit.Test

class NetworkClassifierTest {

    @Test
    fun `no internet classifies as none`() {
        assertEquals(
            NetworkClass.NONE,
            NetworkClassifier.classify(
                hasInternet = false,
                notVpn = true,
                hasUsableTransport = true,
                validated = true,
            ),
        )
    }

    @Test
    fun `vpn transport classifies as none`() {
        assertEquals(
            NetworkClass.NONE,
            NetworkClassifier.classify(
                hasInternet = true,
                notVpn = false,
                hasUsableTransport = true,
                validated = true,
            ),
        )
    }

    @Test
    fun `no usable transport classifies as none`() {
        assertEquals(
            NetworkClass.NONE,
            NetworkClassifier.classify(
                hasInternet = true,
                notVpn = true,
                hasUsableTransport = false,
                validated = true,
            ),
        )
    }

    @Test
    fun `captive portal classifies as not validated`() {
        assertEquals(
            NetworkClass.NOT_VALIDATED,
            NetworkClassifier.classify(
                hasInternet = true,
                notVpn = true,
                hasUsableTransport = true,
                validated = false,
            ),
        )
    }

    @Test
    fun `validated transport classifies as usable`() {
        assertEquals(
            NetworkClass.USABLE,
            NetworkClassifier.classify(
                hasInternet = true,
                notVpn = true,
                hasUsableTransport = true,
                validated = true,
            ),
        )
    }
}
