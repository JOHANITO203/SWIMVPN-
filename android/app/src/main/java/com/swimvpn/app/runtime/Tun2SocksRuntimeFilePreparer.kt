package com.swimvpn.app.runtime

import android.content.Context
import com.swimvpn.app.BuildConfig
import java.io.File
import java.net.URI

class Tun2SocksRuntimeFilePreparer(
    private val context: Context,
) {
    fun prepare(
        launchSpec: Tun2SocksLaunchSpec,
        sessionId: String = defaultSessionId(),
    ): Result<PreparedTun2SocksRuntime> {
        return prepareExecutable(
            launchSpec = launchSpec,
            sessionId = sessionId,
        )
    }

    fun prepareExecutable(
        launchSpec: Tun2SocksLaunchSpec,
        sessionId: String = defaultSessionId(),
    ): Result<PreparedTun2SocksRuntime> {
        val availability = Tun2SocksAssetCatalog.availability(context)
        if (!availability.executableFallbackAvailable ||
            availability.executableAssetPath.isNullOrBlank() ||
            availability.abi.isNullOrBlank()
        ) {
            return Result.failure(
                IllegalStateException(availability.reason)
            )
        }

        val directories = prepareDirectories(availability.abi, sessionId)
        val executableFile = File(directories.binariesDir, BuildConfig.TUN2SOCKS_EXECUTABLE_NAME)
        if (!executableFile.exists()) {
            context.assets.open(availability.executableAssetPath).use { input ->
                executableFile.outputStream().use { output ->
                    input.copyTo(output)
                }
            }
        }
        executableFile.setReadable(true, true)
        executableFile.setExecutable(true, true)

        val configFile = writeConfigFile(
            sessionId = sessionId,
            launchSpec = launchSpec,
            sessionsDir = directories.sessionsDir,
            logsDir = directories.logsDir,
        )
        val stdoutLogFile = File(directories.logsDir, "tun2socks.stdout.log")
        val stderrLogFile = File(directories.logsDir, "tun2socks.stderr.log")
        val exitStateFile = File(directories.logsDir, "tun2socks.exit")

        return Result.success(
            PreparedTun2SocksRuntime(
                sessionId = sessionId,
                workingDirectory = directories.sessionsDir,
                executableFile = executableFile,
                configFile = configFile,
                stdoutLogFile = stdoutLogFile,
                stderrLogFile = stderrLogFile,
                exitStateFile = exitStateFile,
                launchSpec = launchSpec,
                abi = availability.abi,
            )
        )
    }

    fun prepareNativeRuntime(
        launchSpec: Tun2SocksLaunchSpec,
        sessionId: String = defaultSessionId(),
    ): Result<PreparedTun2SocksNativeRuntime> {
        val availability = Tun2SocksAssetCatalog.availability(context)
        if (!availability.packagedSharedLibraryAvailable ||
            availability.abi.isNullOrBlank() ||
            availability.packagedSharedLibraryName.isNullOrBlank() ||
            availability.packagedSharedLibraryFile == null
        ) {
            return Result.failure(
                IllegalStateException(availability.reason)
            )
        }

        val directories = prepareDirectories(availability.abi, sessionId)
        val configFile = writeConfigFile(
            sessionId = sessionId,
            launchSpec = launchSpec,
            sessionsDir = directories.sessionsDir,
            logsDir = directories.logsDir,
        )
        val stdoutLogFile = File(directories.logsDir, "tun2socks.stdout.log")
        val stderrLogFile = File(directories.logsDir, "tun2socks.stderr.log")
        val exitStateFile = File(directories.logsDir, "tun2socks.exit")

        return Result.success(
            PreparedTun2SocksNativeRuntime(
                sessionId = sessionId,
                workingDirectory = directories.sessionsDir,
                configFile = configFile,
                stdoutLogFile = stdoutLogFile,
                stderrLogFile = stderrLogFile,
                exitStateFile = exitStateFile,
                launchSpec = launchSpec,
                abi = availability.abi,
                sharedLibraryName = availability.packagedSharedLibraryName,
                sharedLibraryFile = availability.packagedSharedLibraryFile,
            )
        )
    }

    private fun prepareDirectories(
        abi: String,
        sessionId: String,
    ): Tun2SocksRuntimeDirectories {
        val rootDir = File(context.noBackupFilesDir, "runtime")
        val binariesDir = File(rootDir, "tun2socks/$abi")
        val sessionsDir = File(rootDir, "tun2socks-sessions/$sessionId")
        val logsDir = File(sessionsDir, "logs")

        binariesDir.mkdirs()
        if (sessionsDir.exists()) {
            sessionsDir.deleteRecursively()
        }
        sessionsDir.mkdirs()
        logsDir.mkdirs()

        return Tun2SocksRuntimeDirectories(
            binariesDir = binariesDir,
            sessionsDir = sessionsDir,
            logsDir = logsDir,
        )
    }

    private fun writeConfigFile(
        sessionId: String,
        launchSpec: Tun2SocksLaunchSpec,
        sessionsDir: File,
        logsDir: File,
    ): File {
        val configFile = File(sessionsDir, "tun2socks-android.json")
        val proxyUri = runCatching { URI(launchSpec.proxyUrl) }.getOrNull()
        val proxyScheme = proxyUri?.scheme?.ifBlank { null } ?: "socks5"
        val proxyHost = proxyUri?.host
        val proxyPort = proxyUri?.port?.takeIf { it > 0 }
        val interfaceLine = launchSpec.interfaceName
            ?.takeIf { it.isNotBlank() }
            ?.let { ",\n    \"interface_name\": \"${escapeJson(it)}\"" }
            ?: ""
        val mapDnsLine = if (!launchSpec.mapDnsAddress.isNullOrBlank() && launchSpec.mapDnsPort != null) {
            """
            ,
              "map_dns": {
                "address": "${escapeJson(launchSpec.mapDnsAddress)}",
                "port": ${launchSpec.mapDnsPort}
              }
            """.trimIndent()
        } else {
            ""
        }
        val extraArgsJson = launchSpec.extraArgs.joinToString(", ") { "\"${escapeJson(it)}\"" }

        configFile.writeText(
            """
            {
              "session_id": "${escapeJson(sessionId)}",
              "proxy": {
                "url": "${escapeJson(launchSpec.proxyUrl)}",
                "scheme": "${escapeJson(proxyScheme)}",
                "host": ${proxyHost?.let { "\"${escapeJson(it)}\"" } ?: "null"},
                "port": ${proxyPort ?: "null"}
              },
              "tunnel": {
                "device": "${escapeJson(launchSpec.deviceArgument)}",
                "fd": ${launchSpec.tunFd ?: "null"},
                "mtu": ${launchSpec.mtu},
                "ipv4": "${escapeJson(launchSpec.tunnelIpv4)}",
                "ipv6": "${escapeJson(launchSpec.tunnelIpv6)}"$interfaceLine
              },
              "runtime": {
                "log_level": "${escapeJson(launchSpec.logLevel)}",
                "udp_mode": "${escapeJson(launchSpec.udpMode)}",
                "stdout_log": "${escapeJson(File(logsDir, "tun2socks.stdout.log").absolutePath)}",
                "stderr_log": "${escapeJson(File(logsDir, "tun2socks.stderr.log").absolutePath)}",
                "exit_state_file": "${escapeJson(File(logsDir, "tun2socks.exit").absolutePath)}"
              },
              "android": {
                "jni_library_name": "${escapeJson(BuildConfig.TUN2SOCKS_SHARED_LIBRARY_NAME)}",
                "executable_fallback_name": "${escapeJson(BuildConfig.TUN2SOCKS_EXECUTABLE_NAME)}"
              }$mapDnsLine,
              "extra_args": [$extraArgsJson]
            }
            """.trimIndent()
        )

        return configFile
    }

    private fun escapeJson(value: String): String {
        return buildString(value.length + 8) {
            value.forEach { character ->
                when (character) {
                    '\\' -> append("\\\\")
                    '"' -> append("\\\"")
                    '\n' -> append("\\n")
                    '\r' -> append("\\r")
                    '\t' -> append("\\t")
                    else -> append(character)
                }
            }
        }
    }

    private fun defaultSessionId(): String = "tun2socks-${System.currentTimeMillis()}"

    private data class Tun2SocksRuntimeDirectories(
        val binariesDir: File,
        val sessionsDir: File,
        val logsDir: File,
    )
}
