package com.swimvpn.desktop.system

import java.io.File
import java.lang.ProcessBuilder.Redirect
import java.util.concurrent.TimeUnit

/**
 * Kill-switch (TUN mode). While engaged, the Windows Firewall default OUTBOUND action is set to
 * BLOCK and only explicit allow rules pass: traffic sourced from the SWIMVPN TUN adapter
 * (10.27.0.2), the VPN server IP, loopback, the LAN, and DHCP. When the tunnel drops, app traffic
 * is once again sourced from the physical NIC — no allow rule matches → it is blocked → no leak.
 * It fails CLOSED by design (no internet until reconnect or the user disconnects).
 *
 * Safety: a marker file records that we changed the policy; [cleanupStale] (called on every launch)
 * restores it even after a crash. Manual recovery if ever stuck (PowerShell/cmd as admin):
 *   netsh advfirewall set allprofiles firewallpolicy blockinbound,allowoutbound
 */
object KillSwitch {
    private val appDir = File(System.getenv("LOCALAPPDATA") ?: System.getProperty("java.io.tmpdir"), "SWIMVPN").apply { mkdirs() }
    private val marker = File(appDir, "killswitch.engaged")
    private const val TUN_IP = "10.27.0.2"
    private val ruleSuffixes = listOf("tun", "server", "loop", "lan", "dhcp")

    val isEngaged: Boolean get() = marker.exists()

    /** Build allow rules first, THEN flip the default to block — order avoids a self-lockout gap. */
    fun engage(serverIp: String?) {
        deleteRules()
        addAllow("tun", "localip=$TUN_IP")
        serverIp?.let { addAllow("server", "remoteip=$it") }
        addAllow("loop", "remoteip=127.0.0.0/8")
        addAllow("lan", "remoteip=LocalSubnet")
        addAllow("dhcp", "protocol=udp", "localport=68", "remoteport=67")
        netsh("advfirewall", "set", "allprofiles", "firewallpolicy", "blockinbound,blockoutbound")
        runCatching { marker.writeText("1") }
    }

    /** Restore normal outbound + remove our rules. Idempotent (safe to call when not engaged). */
    fun disengage() {
        netsh("advfirewall", "set", "allprofiles", "firewallpolicy", "blockinbound,allowoutbound")
        deleteRules()
        runCatching { marker.delete() }
    }

    /** Every launch: if a prior run left the kill-switch on (e.g. a crash / force-quit), undo it. */
    fun cleanupStale() {
        if (marker.exists()) disengage() else deleteRules()
    }

    private fun addAllow(suffix: String, vararg cond: String) =
        netsh("advfirewall", "firewall", "add", "rule", "name=SWIMVPN-KS-$suffix",
            "dir=out", "action=allow", "enable=yes", *cond)

    private fun deleteRules() = ruleSuffixes.forEach { suffix ->
        runCatching { netsh("advfirewall", "firewall", "delete", "rule", "name=SWIMVPN-KS-$suffix") }
    }

    private fun netsh(vararg args: String): Int = runCatching {
        val p = ProcessBuilder(listOf("netsh") + args)
            .redirectErrorStream(true)
            .redirectOutput(Redirect.appendTo(File(appDir, "killswitch.log")))
            .start()
        p.waitFor(10, TimeUnit.SECONDS); p.exitValue()
    }.getOrDefault(-1)
}
