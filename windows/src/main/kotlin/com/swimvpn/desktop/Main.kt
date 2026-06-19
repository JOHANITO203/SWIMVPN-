package com.swimvpn.desktop

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import com.swimvpn.desktop.i18n.LocalStrings
import com.swimvpn.desktop.i18n.stringsFor
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Tray
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.application
import androidx.compose.ui.window.rememberTrayState
import androidx.compose.ui.window.rememberWindowState
import com.swimvpn.desktop.state.AppController
import com.swimvpn.desktop.theme.SwimVpnTheme
import com.swimvpn.desktop.ui.AppShell
import com.swimvpn.desktop.vpn.EngineCleanup
import com.swimvpn.desktop.vpn.VpnState
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
    // Best-effort teardown when the JVM exits (normal close / Quit): kill our engine + drop our
    // split routes so nothing is orphaned. (A hard kill skips this — the launch-time purge covers that.)
    Runtime.getRuntime().addShutdownHook(Thread { runCatching { EngineCleanup.killStray(includeXray = true) } })

    application {
        // remember: create the scope + controller ONCE. Without it, every recomposition rebuilt a
        // whole new AppController (reload state.json, re-parse configs, re-init the tunnel) — an
        // infinite recompose loop that pegged the UI thread and made the app unstable on any action.
        val scope = remember { CoroutineScope(SupervisorJob() + Dispatchers.Default) }
        val app = remember { AppController(scope) }
        val windowState = rememberWindowState(width = 420.dp, height = 860.dp)
        val trayState = rememberTrayState()
        // Start hidden in the tray when the user enabled it; otherwise show the window.
        var windowVisible by remember { mutableStateOf(!app.startMinimized) }
        val s = stringsFor(app.lang)
        val connected = app.vpn.state == VpnState.CONNECTED || app.vpn.state == VpnState.CONNECTING

        // System-tray presence: keeps the app alive while the window is hidden, with quick actions.
        Tray(
            state = trayState,
            icon = painterResource("icons/app.png"),
            tooltip = "SWIMVPN",
            onAction = { windowVisible = true },
            menu = {
                Item(if (connected) s.trayDisconnect else s.trayConnect, onClick = { app.toggleConnect() })
                Item(s.trayOpen, onClick = { windowVisible = true })
                Separator()
                Item(s.trayQuit, onClick = { app.vpn.disconnect(); exitApplication() })
            },
        )

        Window(
            // Closing the window hides it to the tray (VPN keeps running); real exit = tray ▸ Quit.
            onCloseRequest = { windowVisible = false },
            visible = windowVisible,
            state = windowState,
            title = "SWIMVPN",
            icon = painterResource("icons/app.png"),
        ) {
            SwimVpnTheme(dark = true) {
                // Re-provide strings from the active language; switching language recomposes here.
                CompositionLocalProvider(LocalStrings provides stringsFor(app.lang)) {
                    Surface(modifier = Modifier.fillMaxSize()) {
                        AppShell(app)
                    }
                }
            }
        }
    }
}
