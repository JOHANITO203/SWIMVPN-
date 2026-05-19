package com.swimvpn.app.ui.screens

import java.util.Locale
import org.junit.Assert.assertEquals
import org.junit.Test

class ProfileScreenAvailabilityStatusTest {
    @Test
    fun `availability status normalization is locale independent`() {
        val previousLocale = Locale.getDefault()
        Locale.setDefault(Locale("tr", "TR"))

        try {
            assertEquals("AVAILABLE", normalizeAvailabilityStatus("available"))
            assertEquals("CONGESTED", normalizeAvailabilityStatus(" congested "))
        } finally {
            Locale.setDefault(previousLocale)
        }
    }
}
