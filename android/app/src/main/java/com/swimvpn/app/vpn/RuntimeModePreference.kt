package com.swimvpn.app.vpn

import java.util.Locale

object RuntimeModePreference {
    fun fromUserSelectable(value: String?): RuntimeMode {
        val normalized = value?.trim()?.uppercase(Locale.ROOT)
        return when (normalized) {
            "TUNNEL", RuntimeMode.FULL_TUNNEL.name -> RuntimeMode.FULL_TUNNEL
            "PROXY", RuntimeMode.LOCAL_PROXY.name -> RuntimeMode.LOCAL_PROXY
            else -> RuntimeMode.fromPersisted(normalized)
        }
    }
}
