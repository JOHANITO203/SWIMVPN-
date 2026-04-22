package com.swimvpn.app.runtime

data class Tun2SocksNativeBridgeContract(
    val sharedLibraryName: String,
    val sharedLibraryPath: String,
    val configPath: String,
    val tunFd: Int,
)

/**
 * Android upstream integration for hev-socks5-tunnel is JNI-based, not ProcessBuilder-based.
 * We deliberately keep this bridge contract-only until a repo-owned JNI shim is packaged.
 */
object Tun2SocksNativeBridge {
    fun contract(
        preparedRuntime: PreparedTun2SocksNativeRuntime,
        tunFd: Int,
    ): Tun2SocksNativeBridgeContract {
        return Tun2SocksNativeBridgeContract(
            sharedLibraryName = preparedRuntime.sharedLibraryName,
            sharedLibraryPath = preparedRuntime.sharedLibraryFile.absolutePath,
            configPath = preparedRuntime.configFile.absolutePath,
            tunFd = tunFd,
        )
    }

    fun launchError(contract: Tun2SocksNativeBridgeContract): IllegalStateException {
        return IllegalStateException(
            buildString {
                append("tun2socks shared library is packaged but no repo JNI shim is wired yet")
                append(" (library=")
                append(contract.sharedLibraryName)
                append(", path=")
                append(contract.sharedLibraryPath)
                append(")")
            }
        )
    }
}
