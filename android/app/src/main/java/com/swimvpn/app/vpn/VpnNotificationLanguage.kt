package com.swimvpn.app.vpn

import com.swimvpn.app.data.local.PreferencesManager
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
            else -> PreferencesManager.DEFAULT_LANGUAGE
        }
    }
}
