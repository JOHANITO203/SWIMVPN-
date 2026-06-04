package com.swimvpn.app.config

import com.google.gson.JsonArray
import com.google.gson.JsonObject

/**
 * Builds a complete, tagged Xray `proxy` outbound for a [SwimVpnProfile], reusing the shared
 * [XrayStreamSettingsBuilder]. This is the single producer used by BOTH config paths:
 *  - [ConfigNormalizationEngine] serializes [forProfile] into `normalizedRuntimeConfig`, and
 *  - [TunnelRuntimeAdapter]'s fallback builders delegate here too,
 * removing the two-path divergence that left VLESS/VMESS with TCP/WS-only transports.
 *
 * Pure (Gson only) so it is unit-testable without Robolectric.
 */
object XrayOutboundBuilder {

    /** Returns the proxy outbound for runtime-capable protocols, or null otherwise (SOCKS/HTTP BYO
     *  proxies keep their dedicated builders in [TunnelRuntimeAdapter]). */
    fun forProfile(profile: SwimVpnProfile): JsonObject? = when (profile.protocol) {
        Protocol.VLESS -> vless(profile)
        Protocol.VMESS -> vmess(profile)
        Protocol.TROJAN -> trojan(profile)
        Protocol.SHADOWSOCKS -> shadowsocks(profile)
        else -> null
    }

    fun vless(profile: SwimVpnProfile): JsonObject = JsonObject().apply {
        addProperty("tag", "proxy")
        addProperty("protocol", "vless")
        add("settings", JsonObject().apply {
            add("vnext", JsonArray().apply {
                add(JsonObject().apply {
                    addProperty("address", profile.address)
                    addProperty("port", profile.port)
                    add("users", JsonArray().apply {
                        add(JsonObject().apply {
                            addProperty("id", profile.userId)
                            addProperty("encryption", "none")
                            // Omit flow entirely when absent — never emit "flow": "".
                            profile.flow?.takeIf { it.isNotBlank() }?.let { addProperty("flow", it) }
                        })
                    })
                })
            })
        })
        add("streamSettings", XrayStreamSettingsBuilder.build(profile))
    }

    fun vmess(profile: SwimVpnProfile): JsonObject = JsonObject().apply {
        addProperty("tag", "proxy")
        addProperty("protocol", "vmess")
        add("settings", JsonObject().apply {
            add("vnext", JsonArray().apply {
                add(JsonObject().apply {
                    addProperty("address", profile.address)
                    addProperty("port", profile.port)
                    add("users", JsonArray().apply {
                        add(JsonObject().apply {
                            addProperty("id", profile.userId)
                            addProperty("alterId", 0)
                            addProperty("security", "auto")
                        })
                    })
                })
            })
        })
        add("streamSettings", XrayStreamSettingsBuilder.build(profile))
    }

    fun trojan(profile: SwimVpnProfile): JsonObject = JsonObject().apply {
        addProperty("tag", "proxy")
        addProperty("protocol", "trojan")
        add("settings", JsonObject().apply {
            add("servers", JsonArray().apply {
                add(JsonObject().apply {
                    addProperty("address", profile.address)
                    addProperty("port", profile.port)
                    addProperty("password", profile.password)
                    profile.flow?.takeIf { it.isNotBlank() }?.let { addProperty("flow", it) }
                })
            })
        })
        add("streamSettings", XrayStreamSettingsBuilder.build(profile))
    }

    // Shadowsocks rides plain TCP in this app (no streamSettings, matching the prior runtime).
    fun shadowsocks(profile: SwimVpnProfile): JsonObject = JsonObject().apply {
        addProperty("tag", "proxy")
        addProperty("protocol", "shadowsocks")
        add("settings", JsonObject().apply {
            add("servers", JsonArray().apply {
                add(JsonObject().apply {
                    addProperty("address", profile.address)
                    addProperty("port", profile.port)
                    addProperty("method", profile.method)
                    addProperty("password", profile.password)
                })
            })
        })
    }
}
