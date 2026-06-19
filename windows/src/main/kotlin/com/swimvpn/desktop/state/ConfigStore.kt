package com.swimvpn.desktop.state

import com.google.gson.Gson
import com.sun.jna.platform.win32.Crypt32Util
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
    val subUrl: String? = null, // last subscription URL (for refresh)
    val autostart: Boolean = false, // launch at Windows sign-in (HKCU Run)
    val startMinimized: Boolean = false, // start hidden in the system tray
    val autoConnect: Boolean = false, // connect automatically at app start
    val killSwitch: Boolean = false, // block non-tunnel traffic on tunnel drop (TUN mode)
    val aiEnabled: Boolean = false, // adaptive camouflage agent on/off
    val camProfile: String? = null, // manual camouflage profile id (when AI off); null = auto
    val tlsFragment: Boolean = false, // anti-DPI TLS ClientHello fragmentation
    val splitLocal: Boolean = false, // route LAN + RU traffic direct (split routing)
)

object ConfigStore {
    private val file = File(
        File(System.getenv("LOCALAPPDATA") ?: System.getProperty("java.io.tmpdir"), "SWIMVPN"),
        "state.json",
    ).apply { parentFile.mkdirs() }
    private val gson = Gson()

    /** Loads state, decrypting with DPAPI. Transparently migrates an old plaintext state.json. */
    fun load(): PersistedState {
        if (!file.exists()) return PersistedState()
        val bytes = runCatching { file.readBytes() }.getOrNull() ?: return PersistedState()
        val json = runCatching {
            val asText = bytes.toString(Charsets.UTF_8)
            // Old builds wrote plaintext JSON (starts with '{'); new builds write DPAPI ciphertext.
            if (asText.trimStart().startsWith("{")) asText
            else Crypt32Util.cryptUnprotectData(bytes).toString(Charsets.UTF_8)
        }.getOrNull() ?: return PersistedState()
        return runCatching { gson.fromJson(json, PersistedState::class.java) }.getOrNull() ?: PersistedState()
    }

    /** Saves state encrypted with DPAPI (per-user). Falls back to plaintext if DPAPI is unavailable
     *  (non-Windows / dev run) so state is never lost. */
    fun save(state: PersistedState) {
        runCatching {
            val json = gson.toJson(state)
            val enc = runCatching { Crypt32Util.cryptProtectData(json.toByteArray(Charsets.UTF_8)) }.getOrNull()
            if (enc != null) file.writeBytes(enc) else file.writeText(json)
        }
    }
}
