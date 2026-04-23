package com.swimvpn.app.data.local

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.core.stringSetPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.swimvpn.app.vpn.RuntimeMode
import com.swimvpn.app.vpn.ThemeMode
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.flow.map

val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "swimvpn_prefs")

data class AutoConnectPayload(
    val host: String,
    val port: Int,
    val protocol: String,
    val runtimeConfig: String,
    val runtimeMode: RuntimeMode,
)

class PreferencesManager(private val context: Context) {
    companion object {
        val USER_NUMBER_KEY = stringPreferencesKey("user_number")
        val ONBOARDING_DONE_KEY = androidx.datastore.preferences.core.booleanPreferencesKey("onboarding_done")
        
        // Technical Settings
        val ROUTING_MODE_KEY = stringPreferencesKey("routing_mode")
        val AUTO_CONNECT_KEY = androidx.datastore.preferences.core.booleanPreferencesKey("auto_connect")
        val LANGUAGE_KEY = stringPreferencesKey("language") // "en", "fr", "ru"
        val THEME_MODE_KEY = stringPreferencesKey("theme_mode")
        val SELECTED_SERVER_ID_KEY = stringPreferencesKey("selected_server_id")
        val PINNED_SERVER_IDS_KEY = stringSetPreferencesKey("pinned_server_ids")
        val LAST_RUNTIME_HOST_KEY = stringPreferencesKey("last_runtime_host")
        val LAST_RUNTIME_PORT_KEY = intPreferencesKey("last_runtime_port")
        val LAST_RUNTIME_PROTOCOL_KEY = stringPreferencesKey("last_runtime_protocol")
        val LAST_RUNTIME_CONFIG_KEY = stringPreferencesKey("last_runtime_config")
        val LAST_RUNTIME_MODE_KEY = stringPreferencesKey("last_runtime_mode")
    }

    val userNumberFlow: Flow<String?> = context.dataStore.data
        .map { preferences -> preferences[USER_NUMBER_KEY] }
        
    val onboardingDoneFlow: Flow<Boolean> = context.dataStore.data
        .map { preferences -> preferences[ONBOARDING_DONE_KEY] ?: false }

    val runtimeModeFlow: Flow<RuntimeMode> = context.dataStore.data
        .map { preferences ->
            when (preferences[ROUTING_MODE_KEY]) {
                "TUNNEL" -> RuntimeMode.FULL_TUNNEL
                "PROXY" -> RuntimeMode.LOCAL_PROXY
                else -> RuntimeMode.fromPersisted(preferences[ROUTING_MODE_KEY])
            }
        }

    val routingModeFlow: Flow<String> = runtimeModeFlow
        .map { it.name }

    val autoConnectFlow: Flow<Boolean> = context.dataStore.data
        .map { preferences -> preferences[AUTO_CONNECT_KEY] ?: false }

    val languageFlow: Flow<String> = context.dataStore.data
        .map { preferences -> preferences[LANGUAGE_KEY] ?: "en" }

    val themeModeFlow: Flow<ThemeMode> = context.dataStore.data
        .map { preferences -> ThemeMode.fromPersisted(preferences[THEME_MODE_KEY]) }

    val selectedServerIdFlow: Flow<String?> = context.dataStore.data
        .map { preferences -> preferences[SELECTED_SERVER_ID_KEY] }

    val pinnedServerIdsFlow: Flow<Set<String>> = context.dataStore.data
        .map { preferences -> preferences[PINNED_SERVER_IDS_KEY] ?: emptySet() }

    suspend fun saveUserNumber(userNumber: String) {
        context.dataStore.edit { preferences -> preferences[USER_NUMBER_KEY] = userNumber }
    }
    
    suspend fun setOnboardingDone(done: Boolean = true) {
        context.dataStore.edit { preferences -> preferences[ONBOARDING_DONE_KEY] = done }
    }

    suspend fun setRuntimeMode(mode: RuntimeMode) {
        context.dataStore.edit { preferences -> preferences[ROUTING_MODE_KEY] = mode.name }
    }

    suspend fun setRoutingMode(mode: String) {
        val runtimeMode = when (mode) {
            "TUNNEL" -> RuntimeMode.FULL_TUNNEL
            "PROXY" -> RuntimeMode.LOCAL_PROXY
            else -> RuntimeMode.fromPersisted(mode)
        }
        setRuntimeMode(runtimeMode)
    }

    suspend fun setAutoConnect(enabled: Boolean) {
        context.dataStore.edit { preferences -> preferences[AUTO_CONNECT_KEY] = enabled }
    }

    suspend fun setLanguage(lang: String) {
        context.dataStore.edit { preferences -> preferences[LANGUAGE_KEY] = lang }
    }

    suspend fun setThemeMode(mode: ThemeMode) {
        context.dataStore.edit { preferences -> preferences[THEME_MODE_KEY] = mode.name }
    }

    suspend fun setSelectedServerId(serverId: String?) {
        context.dataStore.edit { preferences ->
            if (serverId == null) {
                preferences.remove(SELECTED_SERVER_ID_KEY)
            } else {
                preferences[SELECTED_SERVER_ID_KEY] = serverId
            }
        }
    }

    suspend fun setPinnedServerIds(serverIds: Set<String>) {
        context.dataStore.edit { preferences ->
            if (serverIds.isEmpty()) {
                preferences.remove(PINNED_SERVER_IDS_KEY)
            } else {
                preferences[PINNED_SERVER_IDS_KEY] = serverIds
            }
        }
    }

    suspend fun togglePinnedServerId(serverId: String): Set<String> {
        var updated = emptySet<String>()
        context.dataStore.edit { preferences ->
            val current = preferences[PINNED_SERVER_IDS_KEY] ?: emptySet()
            updated = if (serverId in current) current - serverId else current + serverId
            if (updated.isEmpty()) {
                preferences.remove(PINNED_SERVER_IDS_KEY)
            } else {
                preferences[PINNED_SERVER_IDS_KEY] = updated
            }
        }
        return updated
    }

    suspend fun saveAutoConnectPayload(payload: AutoConnectPayload) {
        context.dataStore.edit { preferences ->
            preferences[LAST_RUNTIME_HOST_KEY] = payload.host
            preferences[LAST_RUNTIME_PORT_KEY] = payload.port
            preferences[LAST_RUNTIME_PROTOCOL_KEY] = payload.protocol
            preferences[LAST_RUNTIME_CONFIG_KEY] = payload.runtimeConfig
            preferences[LAST_RUNTIME_MODE_KEY] = payload.runtimeMode.name
        }
    }

    suspend fun getAutoConnectPayload(): AutoConnectPayload? {
        val preferences = context.dataStore.data.map { it }.firstOrNull() ?: return null
        val host = preferences[LAST_RUNTIME_HOST_KEY] ?: return null
        val port = preferences[LAST_RUNTIME_PORT_KEY] ?: return null
        val protocol = preferences[LAST_RUNTIME_PROTOCOL_KEY] ?: return null
        val runtimeConfig = preferences[LAST_RUNTIME_CONFIG_KEY] ?: return null
        val runtimeMode = RuntimeMode.fromPersisted(preferences[LAST_RUNTIME_MODE_KEY])
        if (runtimeConfig.isBlank()) return null
        return AutoConnectPayload(
            host = host,
            port = port,
            protocol = protocol,
            runtimeConfig = runtimeConfig,
            runtimeMode = runtimeMode,
        )
    }

    suspend fun clearAutoConnectPayload() {
        context.dataStore.edit { preferences ->
            preferences.remove(LAST_RUNTIME_HOST_KEY)
            preferences.remove(LAST_RUNTIME_PORT_KEY)
            preferences.remove(LAST_RUNTIME_PROTOCOL_KEY)
            preferences.remove(LAST_RUNTIME_CONFIG_KEY)
            preferences.remove(LAST_RUNTIME_MODE_KEY)
        }
    }
}
