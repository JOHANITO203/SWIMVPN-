package com.swimvpn.app.update

import androidx.compose.runtime.Composable
import com.swimvpn.app.data.local.PreferencesManager

/**
 * Stub no-op du flavor PLAY : le build Play Store ne contient NI le code d'auto-install,
 * NI la permission REQUEST_INSTALL_PACKAGES (interdits par Google Play — les mises à jour
 * y passent par le Play Store). La vraie implémentation vit dans src/sideload/.
 */
@Composable
@Suppress("UNUSED_PARAMETER")
fun UpdateGate(prefs: PreferencesManager, content: @Composable () -> Unit) {
    content()
}
