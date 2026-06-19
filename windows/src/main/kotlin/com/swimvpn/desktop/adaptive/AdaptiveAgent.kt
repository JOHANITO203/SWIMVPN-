package com.swimvpn.desktop.adaptive

import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import com.swimvpn.desktop.vpn.CamouflageProfile
import java.io.File

/**
 * Honest desktop adaptive agent (parity with Android's idea, scoped to the real client knob): it
 * remembers, per server, the camouflage profile that last produced a WORKING connection. That's
 * reliability — not "stealth", which can't be measured client-side. Used to start a server on its
 * known-good profile and to drive the auto-heal cascade when a connection fails.
 */
class AdaptiveAgent {
    private val store = File(
        File(System.getenv("LOCALAPPDATA") ?: System.getProperty("java.io.tmpdir"), "SWIMVPN").apply { mkdirs() },
        "reliability.json",
    )
    private val gson = Gson()
    private val mapType = object : TypeToken<MutableMap<String, String>>() {}.type
    private val working: MutableMap<String, String> =
        runCatching { gson.fromJson<MutableMap<String, String>>(store.readText(), mapType) }.getOrNull() ?: mutableMapOf()

    /** Profile to START this server with: its last known-good one, else AUTO. */
    fun startProfile(serverId: String?): CamouflageProfile =
        CamouflageProfile.byId(serverId?.let { working[it] })

    fun recordWorking(serverId: String?, profile: CamouflageProfile) {
        serverId ?: return
        if (working[serverId] == profile.id) return
        working[serverId] = profile.id
        runCatching { store.writeText(gson.toJson(working)) }
    }

    /** Next profile after [current] in the heal cascade, or null when the cascade is exhausted. */
    fun nextInCascade(current: CamouflageProfile): CamouflageProfile? {
        val i = CamouflageProfile.cascade.indexOf(current)
        return CamouflageProfile.cascade.getOrNull(i + 1)
    }
}
