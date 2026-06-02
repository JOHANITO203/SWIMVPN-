package com.swimvpn.app.vpn

object NetworkClassifier {
    enum class NetworkClass {
        NONE,
        NOT_VALIDATED,
        USABLE,
    }

    fun classify(
        hasInternet: Boolean,
        notVpn: Boolean,
        hasUsableTransport: Boolean,
        validated: Boolean,
    ): NetworkClass {
        if (!hasInternet || !notVpn || !hasUsableTransport) {
            return NetworkClass.NONE
        }

        if (!validated) {
            return NetworkClass.NOT_VALIDATED
        }

        return NetworkClass.USABLE
    }
}
