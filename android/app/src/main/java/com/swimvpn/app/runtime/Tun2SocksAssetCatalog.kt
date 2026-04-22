package com.swimvpn.app.runtime

import android.content.Context
import android.os.Build
import com.google.gson.JsonParser
import com.swimvpn.app.BuildConfig

object Tun2SocksAssetCatalog {

    fun availability(context: Context): Tun2SocksAvailability {
        val manifestEntry = loadManifest(context).firstOrNull { entry ->
            Build.SUPPORTED_ABIS.any { abi -> abi == entry.abi }
        }

        if (manifestEntry == null) {
            return Tun2SocksAvailability(
                abi = null,
                isAvailable = false,
                executableAssetPath = null,
                reason = "No tun2socks ABI entry is available for this device",
            )
        }

        return Tun2SocksAvailability(
            abi = manifestEntry.abi,
            isAvailable = manifestEntry.available,
            executableAssetPath = manifestEntry.executable.takeIf { it.isNotBlank() },
            reason = manifestEntry.reason,
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
                        executable = json.get("executable")?.asString ?: "",
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
        val executable: String,
        val reason: String,
    )
}
