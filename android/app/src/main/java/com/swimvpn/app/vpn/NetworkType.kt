package com.swimvpn.app.vpn

/**
 * Coarse classification of the underlying transport the device is currently using.
 *
 * Stage 1 plumbing only: this value is carried into the adaptive decision pipeline but is NOT
 * yet used in scoring. Stage 2 will use it to weight server selection (e.g. Wi-Fi vs cellular).
 */
enum class NetworkType {
    WIFI,
    CELLULAR,
    ETHERNET,
    OTHER,
    NONE,
    UNKNOWN,
}

/**
 * Pure classifier mapping the boolean transport/capability flags reported by ConnectivityManager
 * into a single [NetworkType]. Kept side-effect free so it can be unit tested in isolation.
 */
object NetworkTypeClassifier {
    fun classify(
        hasWifi: Boolean,
        hasCellular: Boolean,
        hasEthernet: Boolean,
        hasInternet: Boolean,
    ): NetworkType {
        if (!hasInternet) {
            return NetworkType.NONE
        }
        return when {
            hasWifi -> NetworkType.WIFI
            hasCellular -> NetworkType.CELLULAR
            hasEthernet -> NetworkType.ETHERNET
            else -> NetworkType.OTHER
        }
    }
}
