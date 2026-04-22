package com.swimvpn.app.runtime

import android.content.Context
import com.swimvpn.app.BuildConfig
import java.io.File

class Tun2SocksRuntimeFilePreparer(
    private val context: Context,
) {
    fun prepare(
        launchSpec: Tun2SocksLaunchSpec,
        sessionId: String = defaultSessionId(),
    ): Result<PreparedTun2SocksRuntime> {
        val availability = Tun2SocksAssetCatalog.availability(context)
        if (!availability.isAvailable || availability.executableAssetPath.isNullOrBlank() || availability.abi.isNullOrBlank()) {
            return Result.failure(
                IllegalStateException(availability.reason)
            )
        }

        val rootDir = File(context.noBackupFilesDir, "runtime")
        val binariesDir = File(rootDir, "tun2socks/${availability.abi}")
        val sessionsDir = File(rootDir, "tun2socks-sessions/$sessionId")
        val logsDir = File(sessionsDir, "logs")

        binariesDir.mkdirs()
        if (sessionsDir.exists()) {
            sessionsDir.deleteRecursively()
        }
        sessionsDir.mkdirs()
        logsDir.mkdirs()

        val executableFile = File(binariesDir, BuildConfig.TUN2SOCKS_EXECUTABLE_NAME)
        if (!executableFile.exists()) {
            context.assets.open(availability.executableAssetPath).use { input ->
                executableFile.outputStream().use { output ->
                    input.copyTo(output)
                }
            }
        }
        executableFile.setReadable(true, true)
        executableFile.setExecutable(true, true)

        val stdoutLogFile = File(logsDir, "tun2socks.stdout.log")
        val stderrLogFile = File(logsDir, "tun2socks.stderr.log")
        val exitStateFile = File(logsDir, "tun2socks.exit")

        return Result.success(
            PreparedTun2SocksRuntime(
                sessionId = sessionId,
                workingDirectory = sessionsDir,
                executableFile = executableFile,
                stdoutLogFile = stdoutLogFile,
                stderrLogFile = stderrLogFile,
                exitStateFile = exitStateFile,
                launchSpec = launchSpec,
                abi = availability.abi,
            )
        )
    }

    private fun defaultSessionId(): String = "tun2socks-${System.currentTimeMillis()}"
}
