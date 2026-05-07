package com.swimvpn.app.vpn

import org.junit.Assert.assertEquals
import org.junit.Test

class RuntimeModePreferenceTest {

    @Test
    fun `legacy tunnel values remain full tunnel`() {
        assertEquals(RuntimeMode.FULL_TUNNEL, RuntimeModePreference.fromUserSelectable("TUNNEL"))
        assertEquals(RuntimeMode.FULL_TUNNEL, RuntimeModePreference.fromUserSelectable("FULL_TUNNEL"))
    }

    @Test
    fun `legacy proxy values remain local proxy for advanced routing`() {
        assertEquals(RuntimeMode.LOCAL_PROXY, RuntimeModePreference.fromUserSelectable("PROXY"))
        assertEquals(RuntimeMode.LOCAL_PROXY, RuntimeModePreference.fromUserSelectable("LOCAL_PROXY"))
    }

    @Test
    fun `user selectable values tolerate whitespace and casing`() {
        assertEquals(RuntimeMode.FULL_TUNNEL, RuntimeModePreference.fromUserSelectable(" tunnel "))
        assertEquals(RuntimeMode.LOCAL_PROXY, RuntimeModePreference.fromUserSelectable(" local_proxy "))
    }
}
