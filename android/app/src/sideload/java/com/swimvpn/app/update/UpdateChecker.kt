package com.swimvpn.app.update

import com.swimvpn.app.BuildConfig
import com.swimvpn.app.data.local.PreferencesManager
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.CacheControl
import okhttp3.OkHttpClient
import okhttp3.Request

/**
 * Vérifie la présence d'une mise à jour (flavor sideload uniquement).
 *
 * - Throttle : au plus un fetch réussi par [CHECK_INTERVAL_MS] (design §3) ; un échec réseau ne
 *   consomme PAS le quota (offline au launch ≠ check du jour), et reste un no-op silencieux —
 *   jamais de nag ni d'erreur au démarrage (cohérent avec l'accès offline).
 * - Une version optionnelle déjà refusée (versionCode mémorisé) n'est pas re-proposée.
 * - Retourne null quand il n'y a rien à montrer.
 */
class UpdateChecker(
    private val prefs: PreferencesManager,
    private val client: OkHttpClient = defaultClient,
) {
    companion object {
        val CHECK_INTERVAL_MS: Long = TimeUnit.HOURS.toMillis(24)

        private val defaultClient: OkHttpClient by lazy {
            OkHttpClient.Builder()
                .connectTimeout(10, TimeUnit.SECONDS)
                .readTimeout(10, TimeUnit.SECONDS)
                .build()
        }
    }

    suspend fun checkIfDue(nowMs: Long = System.currentTimeMillis()): UpdateDecision? = withContext(Dispatchers.IO) {
        if (!BuildConfig.AUTO_UPDATE_ENABLED) return@withContext null
        val last = prefs.getUpdateLastCheckAt()
        if (nowMs - last in 0 until CHECK_INTERVAL_MS) return@withContext null

        val manifest = fetchManifest() ?: return@withContext null
        prefs.setUpdateLastCheckAt(nowMs)

        when (val decision = UpdatePolicy.decide(BuildConfig.VERSION_CODE, manifest)) {
            is UpdateDecision.UpToDate -> null
            is UpdateDecision.Optional ->
                if (prefs.getUpdateDismissedCode() >= manifest.latestVersionCode) null else decision
            is UpdateDecision.Mandatory -> decision // un plancher de sécurité ignore le dismiss
        }
    }

    private fun fetchManifest(): UpdateManifest? = runCatching {
        val request = Request.Builder()
            .url(BuildConfig.UPDATE_MANIFEST_URL)
            .cacheControl(CacheControl.FORCE_NETWORK) // le manifest doit refléter la release courante
            .build()
        client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) return@runCatching null
            UpdateManifestCodec.decode(response.body?.string())
        }
    }.getOrNull()
}
