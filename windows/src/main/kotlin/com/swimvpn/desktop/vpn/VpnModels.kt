package com.swimvpn.desktop.vpn

/** Mirrors the Android connection lifecycle (DISCONNECTED→CONNECTING→CONNECTED→ERROR). */
enum class VpnState { DISCONNECTED, CONNECTING, CONNECTED, ERROR }

/** Local inbound ports xray exposes; the Windows system proxy points at the HTTP one. */
object LocalPorts {
    const val SOCKS = 10808
    const val HTTP = 10809
}

/** A parsed VLESS endpoint (the subset v1 supports: TCP + REALITY/TLS/none). */
data class VlessConfig(
    val raw: String,
    val uuid: String,
    val host: String,
    val port: Int,
    val security: String,   // "reality" | "tls" | "none"
    val network: String,    // "tcp" | "ws" | "grpc"
    val sni: String?,
    val publicKey: String?, // REALITY pbk
    val shortId: String?,   // REALITY sid
    val fingerprint: String?, // uTLS fp
    val flow: String?,
    val label: String,
)
