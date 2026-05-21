package com.swimvpn.app.ui.orb3d

enum class SwimParticleOrbState {
    DISCONNECTED,
    CONNECTING,
    CONNECTED,
    UNSTABLE,
    FAILED,
}

enum class SwimParticleDensity(val count: Int) {
    LOW(520),
    MEDIUM(1100),
    HIGH(1800),
    ULTRA(5600),
}
