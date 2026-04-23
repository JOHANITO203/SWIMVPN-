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
        launchSpec: Tun2SocksLaunchSpec,
        sessionsDir: File,
        logsDir: File,
    ): File {
        val configFile = File(sessionsDir, "tun2socks-main.yml")
        val proxyUri = runCatching { URI(launchSpec.proxyUrl) }.getOrNull()
        val proxyHost = proxyUri?.host?.takeIf { it.isNotBlank() } ?: "127.0.0.1"
        val proxyPort = proxyUri?.port?.takeIf { it > 0 } ?: 10808
        val tunnelName = launchSpec.interfaceName?.takeIf { it.isNotBlank() } ?: launchSpec.deviceArgument
        val socksUdpMode = when (launchSpec.udpMode.lowercase()) {
            "enabled", "udp", "true" -> "udp"
            else -> "tcp"
        }
        val mapDnsBlock = if (!launchSpec.mapDnsAddress.isNullOrBlank() && launchSpec.mapDnsPort != null) {
            """

            mapdns:
              address: ${quoteYaml(launchSpec.mapDnsAddress)}
              port: ${launchSpec.mapDnsPort}
              network: 100.64.0.0
              netmask: 255.192.0.0
              cache-size: 10000
            """.trimIndent()
        } else {
            ""
        }
        val logLevel = launchSpec.logLevel.lowercase().takeIf { it in setOf("debug", "info", "warn", "error") }
            ?: "warn"
        configFile.writeText(
            """
            tunnel:
              name: ${quoteYaml(tunnelName)}
              mtu: ${launchSpec.mtu}
              multi-queue: false
              ipv4: ${quoteYaml(launchSpec.tunnelIpv4)}
              ipv6: ${quoteYaml(launchSpec.tunnelIpv6)}

            socks5:
              address: ${quoteYaml(proxyHost)}
              port: $proxyPort
              udp: ${quoteYaml(socksUdpMode)}
            $mapDnsBlock

            misc:
              task-stack-size: 86016
              tcp-buffer-size: 65536
              udp-recv-buffer-size: 524288
              udp-copy-buffer-nums: 10
              connect-timeout: 10000
              tcp-read-write-timeout: 300000
              udp-read-write-timeout: 60000
              log-file: ${quoteYaml(File(logsDir, "tun2socks.stdout.log").absolutePath)}
              log-level: ${quoteYaml(logLevel)}
            """.trimIndent()
        )

        return configFile
    }

    private fun quoteYaml(value: String): String {
        return "'${value.replace("'", "''")}'"
    }

    private fun defaultSessionId(): String = "tun2socks-${System.currentTimeMillis()}"

    private data class Tun2SocksRuntimeDirectories(
        val binariesDir: File,
        val sessionsDir: File,
        val logsDir: File,
    )
}
