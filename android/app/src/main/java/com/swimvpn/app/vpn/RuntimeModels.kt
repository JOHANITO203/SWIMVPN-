package com.swimvpn.app.vpn

enum class RuntimeMode {
    FULL_TUNNEL,
    LOCAL_PROXY,
    SPLIT_TUNNEL;

    companion object {
        fun fromPersisted(value: String?): RuntimeMode =
            entries.firstOrNull { it.name == value } ?: FULL_TUNNEL
    }
}

enum class RuntimeStatus {
    IDLE,
    STARTING,
    RUNNING,
    STOPPING,
    FAILED
}

data class RuntimeMetrics(
    val bytesIn: Long = 0L,
    val bytesOut: Long = 0L,
    val startedAt: Long? = null,
    val lastHandshakeAt: Long? = null,
    val lastError: String? = null,
    val activeMode: String? = null,
    val xraySessionId: String? = null,
    val xrayLogPath: String? = null,
    val tun2SocksSessionId: String? = null,
    val tun2SocksLogPath: String? = null,
)

enum class ThemeMode {
    SYSTEM,
    LIGHT,
    DARK;

    companion object {
        fun fromPersisted(value: String?): ThemeMode =
            entries.firstOrNull { it.name == value } ?: SYSTEM
    }
}
