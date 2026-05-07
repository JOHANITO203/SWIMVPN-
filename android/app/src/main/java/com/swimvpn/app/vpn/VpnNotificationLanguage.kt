package com.swimvpn.app.vpn

import java.util.Locale

object VpnNotificationLanguage {
    fun normalize(languageTag: String?): String {
        val normalized = languageTag
            ?.trim()
            ?.replace('_', '-')
            ?.substringBefore('-')
            ?.lowercase(Locale.ROOT)

        return when (normalized) {
            "fr", "ru" -> normalized
            else -> "en"
        }
    }
}
