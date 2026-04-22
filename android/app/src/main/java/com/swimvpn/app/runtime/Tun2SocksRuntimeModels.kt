package com.swimvpn.app.runtime

import java.io.File

enum class Tun2SocksLaunchMode {
    JNI,
    EXECUTABLE,
    MISSING,
}

data class Tun2SocksAvailability(
    val abi: String?,
    val isAvailable: Boolean,
    val preferredLaunchMode: Tun2SocksLaunchMode,
    val executableAssetPath: String?,
    val executableFallbackAvailable: Boolean,
    val packagedSharedLibraryAvailable: Boolean,
    val packagedSharedLibraryName: String?,
    val packagedSharedLibraryFile: File?,
    val reason: String,
)

data class Tun2SocksLaunchSpec(
    val deviceArgument: String,
    val proxyUrl: String,
    val mtu: Int = 1500,
    val logLevel: String = "info",
    val interfaceName: String? = null,
    val extraArgs: List<String> = emptyList(),
    val tunFd: Int? = null,
    val tunnelIpv4: String = "198.18.0.1",
    val tunnelIpv6: String = "fc00::1",
    val udpMode: String = "enabled",
    val mapDnsAddress: String? = null,
    val mapDnsPort: Int? = null,
)

data class PreparedTun2SocksRuntime(
    val sessionId: String,
    val workingDirectory: File,
    val executableFile: File,
    val configFile: File,
    val stdoutLogFile: File,
    val stderrLogFile: File,
    val exitStateFile: File,
    val launchSpec: Tun2SocksLaunchSpec,
    val abi: String,
)

data class PreparedTun2SocksNativeRuntime(
    val sessionId: String,
    val workingDirectory: File,
    val configFile: File,
    val stdoutLogFile: File,
    val stderrLogFile: File,
    val exitStateFile: File,
    val launchSpec: Tun2SocksLaunchSpec,
    val abi: String,
    val sharedLibraryName: String,
    val sharedLibraryFile: File,
)

data class Tun2SocksProcessSnapshot(
    val sessionId: String,
    val isAlive: Boolean,
    val exitCode: Int?,
    val stdoutLogFile: File,
    val stderrLogFile: File,
    val exitStateFile: File,
    val workingDirectory: File,
)
