package com.swimvpn.desktop.system

import java.net.InetAddress
import java.net.ServerSocket
import java.net.Socket

/**
 * Single-instance guard. The first instance binds a fixed loopback port and listens; any later
 * launch (Start-menu, autostart, double-click) fails to bind, connects to the running instance to
 * signal "show your window", and exits. So the app never opens twice in the taskbar.
 */
object SingleInstance {
    private const val PORT = 49517 // loopback only; distinct from xray's 10808/10809
    private var server: ServerSocket? = null

    /** Invoked (on a background thread) when another launch asks us to surface the window. */
    @Volatile
    var onActivate: (() -> Unit)? = null

    /** @return true if THIS is the primary instance (continue starting); false if one is already
     *  running (it was signaled to show — this process should exit immediately). */
    fun acquire(): Boolean = try {
        val s = ServerSocket(PORT, 4, InetAddress.getLoopbackAddress())
        server = s
        Thread({
            while (!s.isClosed) {
                runCatching { s.accept().close(); onActivate?.invoke() }.onFailure { return@Thread }
            }
        }, "single-instance").apply { isDaemon = true }.start()
        true
    } catch (e: Exception) {
        // Port already bound → an instance is running. Poke it to surface, then we bow out.
        runCatching {
            Socket(InetAddress.getLoopbackAddress(), PORT).use { it.getOutputStream().write(1) }
        }
        false
    }

    /** Release the port (used before relaunching elevated, so the new instance can take over). */
    fun release() {
        runCatching { server?.close() }
        server = null
    }
}
