package com.swimvpn.app.ui

/**
 * Pure decision for whether a periodic latency probe tick should fire.
 *
 * Keeps the battery/screen-aware logic testable in isolation from the Compose
 * effect that drives it. A tick is skipped when the device screen is not
 * interactive (off / locked-display) or the device is in power-save mode, so we
 * never burn battery probing servers the user is not actively looking at.
 */
object PeriodicProbeGate {
    fun shouldProbe(isInteractive: Boolean, powerSaveMode: Boolean): Boolean =
        isInteractive && !powerSaveMode
}
