package com.swimvpn.app.runtime

import android.os.Build
import com.swimvpn.app.BuildConfig

data class NativeBinaryDescriptor(
    val abi: String,
    val assetAbi: String,
)

object RuntimeAssetCatalog {
    const val XRAY_EXECUTABLE_NAME = "xray"
    const val GEOIP_ASSET_NAME = "geoip.dat"
    const val GEOSITE_ASSET_NAME = "geosite.dat"

    private val xrayDescriptors = mapOf(
        "arm64-v8a" to NativeBinaryDescriptor(
            abi = "arm64-v8a",
            assetAbi = "arm64-v8a",
        ),
        "x86_64" to NativeBinaryDescriptor(
            abi = "x86_64",
            assetAbi = "x86_64",
        ),
    )

    val supportedAbis: Set<String>
        get() = xrayDescriptors.keys

    fun xrayVersion(): String = BuildConfig.XRAY_CORE_VERSION

    fun xrayExecutableLibraryName(): String = BuildConfig.XRAY_EXECUTABLE_LIBRARY_NAME

    fun commonAssetPath(fileName: String): String = "${BuildConfig.XRAY_ASSET_ROOT}/common/$fileName"

    fun descriptorForAbi(abi: String): NativeBinaryDescriptor? = xrayDescriptors[abi]

    fun preferredXrayDescriptor(): NativeBinaryDescriptor? {
        return Build.SUPPORTED_ABIS.firstNotNullOfOrNull(::descriptorForAbi)
    }
}
