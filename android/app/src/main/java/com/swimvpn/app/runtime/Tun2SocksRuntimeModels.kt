package com.swimvpn.app.runtime

import java.io.File

data class Tun2SocksAvailability(
    val abi: String?,
    val isAvailable: Boolean,
    val executableAssetPath: String?,
    val reason: String,
)

data class Tun2SocksLaunchSpec(
    val deviceArgument: String,
    val proxyUrl: String,
    val mtu: Int = 1500,
    val logLevel: String = "info",
    val interfaceName: String? = null,
    val extraArgs: List<String> = emptyList(),
)

data class PreparedTun2SocksRuntime(
    val sessionId: String,
    val workingDirectory: File,
    val executableFile: File,
    val stdoutLogFile: File,
    val stderrLogFile: File,
    val exitStateFile: File,
    val launchSpec: Tun2SocksLaunchSpec,
    val abi: String,
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
