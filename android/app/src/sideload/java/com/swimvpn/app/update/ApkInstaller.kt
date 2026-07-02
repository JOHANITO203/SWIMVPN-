package com.swimvpn.app.update

import android.app.DownloadManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.Settings
import androidx.core.content.FileProvider
import java.io.File
import java.security.MessageDigest
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext

/** Issue d'une tentative de téléchargement + installation. */
sealed class InstallResult {
    /** Le dialog d'installation système a été lancé (l'user confirme en un tap). */
    object InstallerLaunched : InstallResult()

    /** L'app n'a pas encore le droit « installer des apps inconnues » — réglage ouvert, réessayer. */
    object NeedsInstallPermission : InstallResult()

    object DownloadFailed : InstallResult()

    /** SHA-256 du fichier ≠ manifest : APK écarté, ne JAMAIS installer (design §5). */
    object VerifyFailed : InstallResult()
}

/**
 * Télécharge l'APK (DownloadManager — survit à l'app en arrière-plan), vérifie son SHA-256
 * contre le manifest AVANT toute installation, puis lance le dialog d'installation système
 * via le FileProvider du flavor sideload. Pas d'installation silencieuse — impossible en
 * sideload, et on ne le prétend pas.
 */
class ApkInstaller(private val context: Context) {

    suspend fun downloadAndInstall(
        manifest: UpdateManifest,
        onProgress: (Int) -> Unit = {},
    ): InstallResult = withContext(Dispatchers.IO) {
        // Le toggle « sources inconnues » d'abord : sans lui l'installeur échoue en silence.
        if (!context.packageManager.canRequestPackageInstalls()) {
            val settings = Intent(
                Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES,
                Uri.parse("package:${context.packageName}"),
            ).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            context.startActivity(settings)
            return@withContext InstallResult.NeedsInstallPermission
        }

        val target = File(context.getExternalFilesDir(null), "update/swimvpn-update.apk")
        target.parentFile?.mkdirs()
        if (target.exists()) target.delete()

        val dm = context.getSystemService(Context.DOWNLOAD_SERVICE) as DownloadManager
        val request = DownloadManager.Request(Uri.parse(manifest.apkUrl))
            .setTitle("SWIMVPN ${manifest.versionName}")
            .setDestinationInExternalFilesDir(context, null, "update/swimvpn-update.apk")
            .setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE)
            .setAllowedOverMetered(true)
        val downloadId = dm.enqueue(request)

        if (!awaitDownload(dm, downloadId, onProgress)) {
            return@withContext InstallResult.DownloadFailed
        }

        if (!sha256Matches(target, manifest.sha256)) {
            target.delete() // défense en profondeur : fichier altéré → écarté
            return@withContext InstallResult.VerifyFailed
        }

        val uri = FileProvider.getUriForFile(context, "${context.packageName}.updateprovider", target)
        val install = Intent(Intent.ACTION_VIEW)
            .setDataAndType(uri, "application/vnd.android.package-archive")
            .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK)
        context.startActivity(install)
        InstallResult.InstallerLaunched
    }

    /** Poll du DownloadManager (annulable) ; true = fichier complet. */
    private suspend fun awaitDownload(
        dm: DownloadManager,
        id: Long,
        onProgress: (Int) -> Unit,
    ): Boolean {
        while (true) {
            val query = DownloadManager.Query().setFilterById(id)
            dm.query(query)?.use { cursor ->
                if (!cursor.moveToFirst()) return false
                val status = cursor.getInt(cursor.getColumnIndexOrThrow(DownloadManager.COLUMN_STATUS))
                val done = cursor.getLong(cursor.getColumnIndexOrThrow(DownloadManager.COLUMN_BYTES_DOWNLOADED_SO_FAR))
                val total = cursor.getLong(cursor.getColumnIndexOrThrow(DownloadManager.COLUMN_TOTAL_SIZE_BYTES))
                if (total > 0) onProgress(((done * 100) / total).toInt().coerceIn(0, 100))
                when (status) {
                    DownloadManager.STATUS_SUCCESSFUL -> return true
                    DownloadManager.STATUS_FAILED -> return false
                    else -> Unit // pending/running/paused → continuer à attendre
                }
            } ?: return false
            delay(500)
        }
    }

    private fun sha256Matches(file: File, expectedHex: String): Boolean = runCatching {
        val digest = MessageDigest.getInstance("SHA-256")
        file.inputStream().use { input ->
            val buffer = ByteArray(64 * 1024)
            while (true) {
                val read = input.read(buffer)
                if (read < 0) break
                digest.update(buffer, 0, read)
            }
        }
        val hex = digest.digest().joinToString("") { "%02x".format(it) }
        hex.equals(expectedHex.trim(), ignoreCase = true)
    }.getOrDefault(false)
}
