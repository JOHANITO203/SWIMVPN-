package com.swimvpn.app.runtime

import android.content.Context
import java.io.File

data class PreparedXrayRuntime(
    val sessionId: String,
    val workingDirectory: File,
    val executableFile: File,
    val configFile: File,
    val stdoutLogFile: File,
    val stderrLogFile: File,
    val exitStateFile: File,
    val geoipFile: File,
    val geositeFile: File,
    val abi: String,
)

class RuntimeFilePreparer(
    private val context: Context,
) {
    fun prepareXrayRuntime(
        configJson: String,
        sessionId: String = defaultSessionId(),
    ): PreparedXrayRuntime {
        val descriptor = RuntimeAssetCatalog.preferredXrayDescriptor()
            ?: error("No packaged Xray artifact matches this device ABI")

        val rootDir = File(context.noBackupFilesDir, "runtime")
        val sessionsDir = File(rootDir, "sessions/$sessionId")
        val logsDir = File(sessionsDir, "logs")

        if (sessionsDir.exists()) {
            sessionsDir.deleteRecursively()
        }
        sessionsDir.mkdirs()
        logsDir.mkdirs()

        val executableFile = File(
            context.applicationInfo.nativeLibraryDir,
            RuntimeAssetCatalog.xrayExecutableLibraryName(),
        )
        if (!executableFile.exists()) {
            error("Packaged Xray runtime is missing for ABI ${descriptor.abi}")
        }
        executableFile.setReadable(true, true)
        executableFile.setExecutable(true, true)

        val geoipFile = copyAssetToWorkingDirectory(
            RuntimeAssetCatalog.commonAssetPath(RuntimeAssetCatalog.GEOIP_ASSET_NAME),
            sessionsDir,
            RuntimeAssetCatalog.GEOIP_ASSET_NAME,
        )
        val geositeFile = copyAssetToWorkingDirectory(
            RuntimeAssetCatalog.commonAssetPath(RuntimeAssetCatalog.GEOSITE_ASSET_NAME),
            sessionsDir,
            RuntimeAssetCatalog.GEOSITE_ASSET_NAME,
        )

        val configFile = File(sessionsDir, "xray-config.json").apply {
            writeText(configJson)
        }
        val stdoutLogFile = File(logsDir, "xray.stdout.log")
        val stderrLogFile = File(logsDir, "xray.stderr.log")
        val exitStateFile = File(logsDir, "xray.exit")

        return PreparedXrayRuntime(
            sessionId = sessionId,
            workingDirectory = sessionsDir,
            executableFile = executableFile,
            configFile = configFile,
            stdoutLogFile = stdoutLogFile,
            stderrLogFile = stderrLogFile,
            exitStateFile = exitStateFile,
            geoipFile = geoipFile,
            geositeFile = geositeFile,
            abi = descriptor.abi,
        )
    }

    private fun copyAssetToWorkingDirectory(
        assetPath: String,
        targetDirectory: File,
        targetName: String,
    ): File {
        val targetFile = File(targetDirectory, targetName)
        context.assets.open(assetPath).use { input ->
            targetFile.outputStream().use { output ->
                input.copyTo(output)
            }
        }
        return targetFile
    }

    private fun defaultSessionId(): String = "xray-${System.currentTimeMillis()}"
}
