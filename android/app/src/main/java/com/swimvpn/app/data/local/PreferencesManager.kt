package com.swimvpn.app.data.local

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "swimvpn_prefs")

class PreferencesManager(private val context: Context) {
    companion object {
        val USER_NUMBER_KEY = stringPreferencesKey("user_number")
        val ONBOARDING_DONE_KEY = androidx.datastore.preferences.core.booleanPreferencesKey("onboarding_done")
        
        // Technical Settings
        val ROUTING_MODE_KEY = stringPreferencesKey("routing_mode") // "TUNNEL" or "PROXY"
        val AUTO_CONNECT_KEY = androidx.datastore.preferences.core.booleanPreferencesKey("auto_connect")
        val LANGUAGE_KEY = stringPreferencesKey("language") // "en", "fr", "ru"
        val SELECTED_SERVER_ID_KEY = stringPreferencesKey("selected_server_id")
    }

    val userNumberFlow: Flow<String?> = context.dataStore.data
        .map { preferences -> preferences[USER_NUMBER_KEY] }
        
    val onboardingDoneFlow: Flow<Boolean> = context.dataStore.data
        .map { preferences -> preferences[ONBOARDING_DONE_KEY] ?: false }

    val routingModeFlow: Flow<String> = context.dataStore.data
        .map { preferences -> preferences[ROUTING_MODE_KEY] ?: "TUNNEL" }

    val autoConnectFlow: Flow<Boolean> = context.dataStore.data
        .map { preferences -> preferences[AUTO_CONNECT_KEY] ?: false }

    val languageFlow: Flow<String> = context.dataStore.data
        .map { preferences -> preferences[LANGUAGE_KEY] ?: "en" }

    val selectedServerIdFlow: Flow<String?> = context.dataStore.data
        .map { preferences -> preferences[SELECTED_SERVER_ID_KEY] }

    suspend fun saveUserNumber(userNumber: String) {
        context.dataStore.edit { preferences -> preferences[USER_NUMBER_KEY] = userNumber }
    }
    
    suspend fun setOnboardingDone(done: Boolean = true) {
        context.dataStore.edit { preferences -> preferences[ONBOARDING_DONE_KEY] = done }
    }

    suspend fun setRoutingMode(mode: String) {
        context.dataStore.edit { preferences -> preferences[ROUTING_MODE_KEY] = mode }
    }

    suspend fun setAutoConnect(enabled: Boolean) {
        context.dataStore.edit { preferences -> preferences[AUTO_CONNECT_KEY] = enabled }
    }

    suspend fun setLanguage(lang: String) {
        context.dataStore.edit { preferences -> preferences[LANGUAGE_KEY] = lang }
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
}
