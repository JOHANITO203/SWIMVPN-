package com.swimvpn.app.vpn

enum class RuntimeMode {
    FULL_TUNNEL,
    LOCAL_PROXY,
    SPLIT_TUNNEL,

    // Onion Stealth: device traffic is chained through an embedded Tor client whose own
    // guard connections exit through the REALITY tunnel (Tor-over-REALITY). The config layer
    // (xray document + torrc) is implemented and unit-tested; the SwimVpnService data-plane
    // binding (tor-android process + tun2socks/health-proof tuning) is device-gate pending.
    TOR_TUNNEL;

    companion object {
        fun fromPersisted(value: String?): RuntimeMode =
            entries.firstOrNull { it.name == value } ?: FULL_TUNNEL
    }
}

enum class RuntimeStatus {
    IDLE,
    STARTING,
    RUNNING,
    RECONNECTING,
    DEGRADED,
    NO_NETWORK,
    STOPPING,
    FAILED,
    STOPPED_BY_USER
}

enum class DisconnectCause {
    USER_STOPPED,
    NETWORK_LOST,
    NETWORK_NOT_VALIDATED,
    SERVER_UNREACHABLE,
    NO_TRAFFIC,
    DNS_FAILURE,
    HANDSHAKE_FAILURE,
    ENGINE_CRASH,
    SERVICE_KILLED,
    PERMISSION_REVOKED,
    BATTERY_RESTRICTION,
    CONFIG_INVALID,
    QUOTA_EXHAUSTED,
    UNKNOWN;

    companion object {
        fun fromPersisted(value: String?): DisconnectCause =
            entries.firstOrNull { it.name == value } ?: UNKNOWN
    }
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
    val lastDisconnectCause: DisconnectCause? = null,
    val reconnectCount: Int = 0,
    val sessionStartedAt: Long? = null,
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
