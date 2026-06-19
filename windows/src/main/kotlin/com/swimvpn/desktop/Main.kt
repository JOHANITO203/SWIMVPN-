package com.swimvpn.desktop

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.application
import androidx.compose.ui.window.rememberWindowState
import com.swimvpn.desktop.state.AppController
import com.swimvpn.desktop.theme.SwimVpnTheme
import com.swimvpn.desktop.ui.AppShell
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import java.io.File

fun main() {
    // Crash net: any uncaught exception (incl. a Compose recomposition crash) is written to
    // %LOCALAPPDATA%\SWIMVPN\crash.log so failures are diagnosable instead of a silent "plante".
    val appDir = File(System.getenv("LOCALAPPDATA") ?: System.getProperty("java.io.tmpdir"), "SWIMVPN")
        .apply { mkdirs() }
    Thread.setDefaultUncaughtExceptionHandler { t, e ->
        runCatching {
            File(appDir, "crash.log").appendText(
                "[${System.currentTimeMillis()}] thread=${t.name}\n${e.stackTraceToString()}\n\n",
            )
        }
        e.printStackTrace()
    }

    application {
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
        val app = AppController(scope)
        val windowState = rememberWindowState(width = 420.dp, height = 860.dp)

        Window(
            onCloseRequest = {
                app.vpn.disconnect() // best-effort: restore routing/proxy on exit
                exitApplication()
            },
            state = windowState,
            title = "SWIMVPN",
            icon = painterResource("icons/app.png"),
        ) {
            SwimVpnTheme(dark = true) {
                Surface(modifier = Modifier.fillMaxSize()) {
                    AppShell(app)
                }
            }
        }
    }
}
