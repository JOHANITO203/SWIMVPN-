package com.swimvpn.app.vpn

import java.util.Locale

object VpnNotificationLanguage {
    const val DEFAULT_LANGUAGE = "ru"

    fun normalize(languageTag: String?): String {
        val normalized = languageTag
            ?.trim()
            ?.replace('_', '-')
            ?.substringBefore('-')
            ?.lowercase(Locale.ROOT)

        return when (normalized) {
            "en", "fr", "ru" -> normalized
            else -> DEFAULT_LANGUAGE
        }
    }
}
