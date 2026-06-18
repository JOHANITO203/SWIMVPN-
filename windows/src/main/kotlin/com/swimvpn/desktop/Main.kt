package com.swimvpn.desktop

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.application
import androidx.compose.ui.window.rememberWindowState
import com.swimvpn.desktop.theme.SwimVpnTheme
import com.swimvpn.desktop.ui.HomeScreen
import com.swimvpn.desktop.vpn.VpnController
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob

fun main() = application {
    val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    val controller = VpnController(scope)
    val windowState = rememberWindowState(width = 420.dp, height = 860.dp)

    Window(
        onCloseRequest = {
            controller.disconnect() // best-effort: restore the system proxy on exit
            exitApplication()
        },
        state = windowState,
        title = "SWIMVPN",
    ) {
        SwimVpnTheme(dark = true) {
            Surface(modifier = Modifier.fillMaxSize()) {
                HomeScreen(controller)
            }
        }
    }
}
