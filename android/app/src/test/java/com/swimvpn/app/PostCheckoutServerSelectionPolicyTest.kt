package com.swimvpn.app

import org.junit.Assert.assertEquals
import org.junit.Test

class PostCheckoutServerSelectionPolicyTest {
    @Test
    fun `keeps active imported server when backend fulfillment appears`() {
        val choice = PostCheckoutServerSelectionPolicy.chooseActiveServerId(
            previousActiveId = "imported:manual",
            previousActiveSource = "imported",
            savedSelectedId = null,
            rebuiltActiveId = "backend:new",
            firstBackendId = "backend:new",
            previousImportedStillAvailable = true,
        )

        assertEquals("imported:manual", choice)
    }

    @Test
    fun `auto-selects first backend fulfillment when no server was active`() {
        val choice = PostCheckoutServerSelectionPolicy.chooseActiveServerId(
            previousActiveId = null,
            previousActiveSource = null,
            savedSelectedId = null,
            rebuiltActiveId = null,
            firstBackendId = "backend:new",
            previousImportedStillAvailable = false,
        )

        assertEquals("backend:new", choice)
    }

    @Test
    fun `keeps rebuilt choice when backend was already selected`() {
        val choice = PostCheckoutServerSelectionPolicy.chooseActiveServerId(
            previousActiveId = "backend:old",
            previousActiveSource = "backend",
            savedSelectedId = "backend:old",
            rebuiltActiveId = "backend:new",
            firstBackendId = "backend:new",
            previousImportedStillAvailable = false,
        )

        assertEquals("backend:new", choice)
    }
}
