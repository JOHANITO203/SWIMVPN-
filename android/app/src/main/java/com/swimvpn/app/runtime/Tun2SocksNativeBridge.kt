package com.swimvpn.app.runtime

data class Tun2SocksNativeBridgeStats(
    val txPackets: Long,
    val txBytes: Long,
    val rxPackets: Long,
    val rxBytes: Long,
)

data class Tun2SocksNativeBridgeContract(
    val sharedLibraryName: String,
    val sharedLibraryPath: String,
    val configPath: String,
    val tunFd: Int,
)

/**
 * Android upstream integration for hev-socks5-tunnel is JNI-based.
 * The shim intentionally loads the packaged shared library at runtime so the existing optional
 * packaged-library model remains intact.
 */
object Tun2SocksNativeBridge {
    private const val SHIM_LIBRARY_NAME = "swimvpn_tun2socks_jni"

    private val shimLoadFailure: Throwable? by lazy {
        runCatching {
            System.loadLibrary(SHIM_LIBRARY_NAME)
        }.exceptionOrNull()
    }

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

    fun isShimAvailable(): Boolean = shimLoadFailure == null

    /**
     * Calls into the upstream tun2socks main loop and blocks until the native tunnel exits.
     * Invoke this from a worker thread, not the main thread.
     */
    fun start(contract: Tun2SocksNativeBridgeContract): Int {
        ensureShimLoaded(contract)
        return nativeStart(contract.sharedLibraryPath, contract.configPath, contract.tunFd)
    }

    fun stop(contract: Tun2SocksNativeBridgeContract) {
        ensureShimLoaded(contract)
        nativeStop(contract.sharedLibraryPath)
    }

    fun stats(contract: Tun2SocksNativeBridgeContract): Tun2SocksNativeBridgeStats {
        ensureShimLoaded(contract)
        val values = nativeStats(contract.sharedLibraryPath)
        require(values.size == 4) {
            "tun2socks native stats response is malformed for ${contract.sharedLibraryName}"
        }
        return Tun2SocksNativeBridgeStats(
            txPackets = values[0],
            txBytes = values[1],
            rxPackets = values[2],
            rxBytes = values[3],
        )
    }

    fun launchError(contract: Tun2SocksNativeBridgeContract): IllegalStateException {
        val shimFailure = shimLoadFailure
        return IllegalStateException(
            buildString {
                if (shimFailure != null) {
                    append("tun2socks JNI shim failed to load")
                    append(" (shim=")
                    append(SHIM_LIBRARY_NAME)
                    append(", reason=")
                    append(shimFailure.message ?: shimFailure::class.java.simpleName)
                } else {
                    append("tun2socks JNI shim is available but not wired into the service lifecycle yet")
                    append(" (invoke Tun2SocksNativeBridge.start() from a worker thread")
                }
                append(", library=")
                append(contract.sharedLibraryName)
                append(", path=")
                append(contract.sharedLibraryPath)
                append(")")
            }
        )
    }

    private fun ensureShimLoaded(contract: Tun2SocksNativeBridgeContract) {
        val shimFailure = shimLoadFailure
        check(shimFailure == null) {
            buildString {
                append("tun2socks JNI shim ")
                append(SHIM_LIBRARY_NAME)
                append(" is unavailable for ")
                append(contract.sharedLibraryName)
                append(": ")
                append(shimFailure?.message ?: shimFailure!!::class.java.simpleName)
            }
        }
    }

    private external fun nativeStart(
        sharedLibraryPath: String,
        configPath: String,
        tunFd: Int,
    ): Int

    private external fun nativeStop(sharedLibraryPath: String)

    private external fun nativeStats(sharedLibraryPath: String): LongArray
}
