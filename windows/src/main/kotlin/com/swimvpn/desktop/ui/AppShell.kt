package com.swimvpn.desktop.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import com.swimvpn.desktop.state.AppController
import com.swimvpn.desktop.state.NavTab
import com.swimvpn.desktop.theme.SwimDesignTokens

/** Desktop two-pane root: left navigation rail + content area (no mobile bottom dock). */
@Composable
fun AppShell(app: AppController) {
    val tokens = SwimDesignTokens.Current
    Row(
        Modifier.fillMaxSize().background(
            Brush.verticalGradient(
                0f to tokens.color.homeBackgroundDeep,
                1f to tokens.color.homeBackgroundBase,
            )
        )
    ) {
        SwimNavRail(
            active = app.tab,
            settingsActive = app.showSettings,
            onSelect = { app.tab = it; app.showSettings = false },
            onSettings = { app.showSettings = true },
        )
        Box(Modifier.weight(1f).fillMaxHeight()) {
            if (app.showSettings) {
                SettingsScreen(app)
            } else {
                when (app.tab) {
                    NavTab.HOME -> HomeScreen(app)
                    NavTab.SERVERS -> ServersScreen(app)
                    NavTab.SUBSCRIPTION -> SubscriptionScreen()
                    NavTab.ACCOUNT -> AccountScreen(app)
                }
            }
        }
    }
}
