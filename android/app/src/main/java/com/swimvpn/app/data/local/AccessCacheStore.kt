package com.swimvpn.app.data.local

import android.content.Context
import android.util.Log
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import com.swimvpn.app.data.network.AccessProfileResponse
import com.swimvpn.app.data.network.ServerNode
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.flow.map

/**
 * Persists the last successfully-resolved backend access snapshot (profile + servers) so the app
 * stays usable when api.swimvpn.pro is unreachable or there is no network (offline-access feature).
 *
 * The snapshot contains SECRETS (server `rawConfig`, REALITY keys) → it is **encrypted at rest**
 * ([SecureCrypto]) before hitting DataStore. JSON (de)serialization is the pure [AccessCacheCodec];
 * this class only adds Android storage + crypto. The backend remains the real entitlement gate — a
 * cached "active" access can be stale, so callers must surface it as cached/unverified, never as a
 * fresh guarantee.
 */
class AccessCacheStore(context: Context) {
    private val dataStore = context.applicationContext.dataStore

    suspend fun save(profile: AccessProfileResponse, servers: List<ServerNode>, nowMs: Long) {
        val payload = runCatching {
            SecureCrypto.encrypt(AccessCacheCodec.encode(CachedAccess(profile, servers, nowMs)))
        }.getOrElse { e ->
            Log.e(TAG, "Failed to encode/encrypt access cache; skipping save", e)
            return
        }
        runCatching { dataStore.edit { it[KEY] = payload } }
            .onFailure { Log.e(TAG, "Failed to persist access cache", it) }
    }

    /** Returns the decrypted snapshot, or null if absent/undecryptable/corrupt (never throws). */
    suspend fun load(): CachedAccess? {
        val stored = runCatching { dataStore.data.map { it[KEY] }.firstOrNull() }.getOrNull() ?: return null
        if (stored.isBlank()) return null
        val json = runCatching {
            if (SecureCrypto.isEncrypted(stored)) SecureCrypto.decrypt(stored) else stored
        }.getOrElse { e ->
            Log.e(TAG, "Failed to decrypt access cache", e)
            return null
        }
        return AccessCacheCodec.decode(json)
    }

    suspend fun clear() {
        runCatching { dataStore.edit { it.remove(KEY) } }
    }

    private companion object {
        const val TAG = "AccessCacheStore"
        val KEY = stringPreferencesKey("cached_access_v1")
    }
}
