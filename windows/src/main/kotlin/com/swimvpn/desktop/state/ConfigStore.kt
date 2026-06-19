package com.swimvpn.desktop.state

import com.google.gson.Gson
import java.io.File

/** Locally-persisted desktop state (imported configs + selection + settings), %LOCALAPPDATA%. */
data class PersistedState(
    val configs: List<String> = emptyList(), // raw share links (vless:// etc.)
    val selectedIndex: Int = -1,
    val fullTunnel: Boolean = true,
    val subTitle: String? = null,
    val subUsed: Long? = null,
    val subTotal: Long? = null,
    val subExpire: String? = null,
    val lang: String? = null, // UI language code (fr/en/ru); null = follow OS on first run
)

object ConfigStore {
    private val file = File(
        File(System.getenv("LOCALAPPDATA") ?: System.getProperty("java.io.tmpdir"), "SWIMVPN"),
        "state.json",
    ).apply { parentFile.mkdirs() }
    private val gson = Gson()

    fun load(): PersistedState =
        runCatching { gson.fromJson(file.readText(), PersistedState::class.java) }.getOrNull() ?: PersistedState()

    fun save(state: PersistedState) {
        runCatching { file.writeText(gson.toJson(state)) }
    }
}
