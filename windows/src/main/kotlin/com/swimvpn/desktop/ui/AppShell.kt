package com.swimvpn.desktop.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.unit.dp
import com.swimvpn.desktop.state.AppController
import com.swimvpn.desktop.state.NavTab
import com.swimvpn.desktop.theme.SwimDesignTokens

/** Root: background + current screen + the navigation dock (hidden inside full-screen Settings). */
@Composable
fun AppShell(app: AppController) {
    val tokens = SwimDesignTokens.Current
    Box(
        Modifier.fillMaxSize().background(
            Brush.verticalGradient(
                0f to tokens.color.homeBackgroundDeep,
                1f to tokens.color.homeBackgroundBase,
            )
        )
    ) {
        if (app.showSettings) {
            SettingsScreen(app)
        } else {
            Box(Modifier.fillMaxSize().padding(bottom = 124.dp)) {
                when (app.tab) {
                    NavTab.HOME -> HomeScreen(app)
                    NavTab.SERVERS -> ServersScreen(app)
                    NavTab.SUBSCRIPTION -> SubscriptionScreen()
                    NavTab.ACCOUNT -> AccountScreen(app)
                }
            }
            Box(Modifier.align(Alignment.BottomCenter).padding(bottom = 24.dp)) {
                SwimDock(active = app.tab) { app.tab = it }
            }
        }
    }
}
