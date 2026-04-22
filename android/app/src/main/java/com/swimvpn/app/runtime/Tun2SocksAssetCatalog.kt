package com.swimvpn.app.runtime

import android.content.Context
import android.os.Build
import com.google.gson.JsonParser
import com.swimvpn.app.BuildConfig
import java.io.File

object Tun2SocksAssetCatalog {

    fun availability(context: Context): Tun2SocksAvailability {
        val manifestEntry = loadManifest(context).firstOrNull { entry ->
            Build.SUPPORTED_ABIS.any { abi -> abi == entry.abi }
        }

        if (manifestEntry == null) {
            return Tun2SocksAvailability(
                abi = null,
                isAvailable = false,
                preferredLaunchMode = Tun2SocksLaunchMode.MISSING,
                executableAssetPath = null,
                executableFallbackAvailable = false,
                packagedSharedLibraryAvailable = false,
                packagedSharedLibraryName = null,
                packagedSharedLibraryFile = null,
                reason = "No tun2socks ABI entry is available for this device",
            )
        }

        val sharedLibraryName = manifestEntry.packagedSharedLibraryName
            .takeIf { it.isNotBlank() }
            ?: BuildConfig.TUN2SOCKS_SHARED_LIBRARY_NAME
        val sharedLibraryFile = File(context.applicationInfo.nativeLibraryDir, sharedLibraryName)
            .takeIf { it.exists() }
        val packagedSharedLibraryAvailable = manifestEntry.packagedSharedLibraryAvailable && sharedLibraryFile != null
        val executableFallbackAvailable = manifestEntry.executableAvailable &&
            !manifestEntry.executableAssetPath.isNullOrBlank()

        return Tun2SocksAvailability(
            abi = manifestEntry.abi,
            isAvailable = packagedSharedLibraryAvailable || executableFallbackAvailable,
            preferredLaunchMode = when {
                packagedSharedLibraryAvailable -> Tun2SocksLaunchMode.JNI
                executableFallbackAvailable -> Tun2SocksLaunchMode.EXECUTABLE
                else -> Tun2SocksLaunchMode.MISSING
            },
            executableAssetPath = manifestEntry.executableAssetPath.takeIf { !it.isNullOrBlank() },
            executableFallbackAvailable = executableFallbackAvailable,
            packagedSharedLibraryAvailable = packagedSharedLibraryAvailable,
            packagedSharedLibraryName = sharedLibraryName.takeIf { packagedSharedLibraryAvailable },
            packagedSharedLibraryFile = sharedLibraryFile,
            reason = when {
                packagedSharedLibraryAvailable -> manifestEntry.reason
                executableFallbackAvailable -> manifestEntry.reason
                manifestEntry.available -> "tun2socks metadata exists but packaged artifacts are missing at runtime"
                else -> manifestEntry.reason
            },
        )
    }

    private fun loadManifest(context: Context): List<Tun2SocksManifestEntry> {
        val manifestPath = "${BuildConfig.TUN2SOCKS_ASSET_ROOT}/availability.json"
        return try {
            context.assets.open(manifestPath).use { input ->
                val root = JsonParser.parseReader(input.reader()).asJsonObject
                val entries = root.getAsJsonArray("entries") ?: return emptyList()
                entries.mapNotNull { element ->
                    val json = element.asJsonObject
                    Tun2SocksManifestEntry(
                        abi = json.get("abi")?.asString ?: return@mapNotNull null,
                        available = json.get("available")?.asBoolean ?: false,
                        preferredLaunchMode = json.get("preferredLaunchMode")?.asString ?: "missing",
                        executableAvailable = json.get("executableAvailable")?.asBoolean ?: false,
                        executableAssetPath = json.get("executableAssetPath")?.asString ?: "",
                        packagedSharedLibraryAvailable = json.get("packagedSharedLibraryAvailable")?.asBoolean ?: false,
                        packagedSharedLibraryName = json.get("packagedSharedLibraryName")?.asString ?: "",
                        reason = json.get("reason")?.asString ?: "Unknown tun2socks availability state",
                    )
                }
            }
        } catch (_: Exception) {
            emptyList()
        }
    }

    private data class Tun2SocksManifestEntry(
        val abi: String,
        val available: Boolean,
        val preferredLaunchMode: String,
        val executableAvailable: Boolean,
        val executableAssetPath: String,
        val packagedSharedLibraryAvailable: Boolean,
        val packagedSharedLibraryName: String,
        val reason: String,
    )
}
