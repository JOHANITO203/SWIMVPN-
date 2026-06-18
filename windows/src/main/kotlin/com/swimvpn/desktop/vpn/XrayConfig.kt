package com.swimvpn.desktop.vpn

import java.net.URI
import java.net.URLDecoder

/**
 * Parses a `vless://` share link and renders an Xray-core config.json with a SOCKS + HTTP
 * inbound (local) and the VLESS outbound. v1 supports TCP transport with REALITY / TLS / none —
 * the SWIMVPN configs observed in the field are VLESS-TCP-REALITY.
 */
object XrayConfig {

    fun parseVless(url: String): VlessConfig {
        val trimmed = url.trim()
        require(trimmed.startsWith("vless://")) { "Not a vless:// link" }
        val uri = URI(trimmed)
        val uuid = uri.userInfo ?: error("vless: missing uuid")
        val host = uri.host ?: error("vless: missing host")
        val port = if (uri.port > 0) uri.port else 443
        val q = (uri.rawQuery ?: "").split("&").filter { it.isNotBlank() }.associate {
            val (k, v) = (it.split("=", limit = 2) + "").let { p -> p[0] to p.getOrElse(1) { "" } }
            k to URLDecoder.decode(v, Charsets.UTF_8)
        }
        val label = uri.fragment?.let { URLDecoder.decode(it, Charsets.UTF_8) } ?: host
        return VlessConfig(
            raw = trimmed,
            uuid = uuid,
            host = host,
            port = port,
            security = q["security"] ?: "none",
            network = q["type"] ?: "tcp",
            sni = q["sni"] ?: q["peer"],
            publicKey = q["pbk"],
            shortId = q["sid"],
            fingerprint = q["fp"],
            flow = q["flow"],
            label = label,
        )
    }

    fun render(cfg: VlessConfig): String {
        fun str(v: String?) = if (v == null) "null" else "\"$v\""
        val streamSecurity = when (cfg.security) {
            "reality" -> """
                "security": "reality",
                "realitySettings": {
                    "serverName": ${str(cfg.sni)},
                    "publicKey": ${str(cfg.publicKey)},
                    "shortId": ${str(cfg.shortId ?: "")},
                    "fingerprint": ${str(cfg.fingerprint ?: "chrome")}
                }
            """.trimIndent()
            "tls" -> """
                "security": "tls",
                "tlsSettings": {
                    "serverName": ${str(cfg.sni)},
                    "fingerprint": ${str(cfg.fingerprint ?: "chrome")}
                }
            """.trimIndent()
            else -> """"security": "none""""
        }
        val flowLine = if (!cfg.flow.isNullOrBlank()) """, "flow": "${cfg.flow}"""" else ""
        return """
        {
          "log": { "loglevel": "warning" },
          "inbounds": [
            {
              "tag": "socks",
              "listen": "127.0.0.1",
              "port": ${LocalPorts.SOCKS},
              "protocol": "socks",
              "settings": { "udp": true, "auth": "noauth" }
            },
            {
              "tag": "http",
              "listen": "127.0.0.1",
              "port": ${LocalPorts.HTTP},
              "protocol": "http",
              "settings": {}
            }
          ],
          "outbounds": [
            {
              "tag": "proxy",
              "protocol": "vless",
              "settings": {
                "vnext": [
                  {
                    "address": "${cfg.host}",
                    "port": ${cfg.port},
                    "users": [
                      { "id": "${cfg.uuid}", "encryption": "none"$flowLine }
                    ]
                  }
                ]
              },
              "streamSettings": {
                "network": "${cfg.network}",
                $streamSecurity
              }
            },
            { "tag": "direct", "protocol": "freedom" },
            { "tag": "block", "protocol": "blackhole" }
          ]
        }
        """.trimIndent()
    }
}
