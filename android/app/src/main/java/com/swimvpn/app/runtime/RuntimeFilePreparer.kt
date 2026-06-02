package com.swimvpn.app.runtime

import android.content.Context
import android.content.pm.ApplicationInfo
import java.io.File
import java.util.zip.ZipFile

/**
 * Catchable failure raised when no packaged Xray artifact matches the device ABI.
 * Extends [Exception] (not a raw process-killing path) so the service maps it to a
 * visible FAILED state with a clear UNSUPPORTED-ARCH message.
 */
class UnsupportedDeviceAbiException(message: String) : Exception(message)

/**
 * Catchable failure raised when the packaged Xray binary for a supported ABI is missing
 * from the APK (extraction failed). Mapped to a visible FAILED state, never a process crash.
 */
class PackagedRuntimeMissingException(message: String) : Exception(message)

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
            ?: throw UnsupportedDeviceAbiException(
                "No packaged Xray artifact matches this device ABI; the VPN runtime cannot start on this device architecture",
            )

        val rootDir = File(context.noBackupFilesDir, "runtime")
        val sessionsDir = File(rootDir, "sessions/$sessionId")
        val logsDir = File(sessionsDir, "logs")

        if (sessionsDir.exists()) {
            sessionsDir.deleteRecursively()
        }
        sessionsDir.mkdirs()
        logsDir.mkdirs()

        val executableFile = prepareExecutableFile(
            descriptor = descriptor,
            targetDirectory = sessionsDir,
        )

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

    private fun prepareExecutableFile(
        descriptor: NativeBinaryDescriptor,
        targetDirectory: File,
    ): File {
        val nativeLibraryFile = File(
            context.applicationInfo.nativeLibraryDir,
            RuntimeAssetCatalog.xrayExecutableLibraryName(),
        )

        if (nativeLibraryFile.exists()) {
            nativeLibraryFile.setReadable(true, true)
            nativeLibraryFile.setExecutable(true, true)
            return nativeLibraryFile
        }

        val executableDir = File(targetDirectory, "bin").apply { mkdirs() }
        val targetFile = File(executableDir, RuntimeAssetCatalog.XRAY_EXECUTABLE_NAME)
        if (!extractBundledLibraryFromApk(descriptor, targetFile)) {
            throw PackagedRuntimeMissingException("Packaged Xray runtime is missing for ABI ${descriptor.abi}")
        }
        targetFile.setReadable(true, true)
        targetFile.setWritable(true, true)
        targetFile.setExecutable(true, true)
        return targetFile
    }

    private fun extractBundledLibraryFromApk(
        descriptor: NativeBinaryDescriptor,
        targetFile: File,
    ): Boolean {
        val candidateApks = buildList {
            add(context.applicationInfo.sourceDir)
            context.applicationInfo.splitSourceDirs?.let { addAll(it) }
        }

        for (apkPath in candidateApks) {
            if (apkPath.isNullOrBlank()) continue

            val extracted = runCatching {
                ZipFile(apkPath).use { zip ->
                    val entry = zip.getEntry("lib/${descriptor.assetAbi}/${RuntimeAssetCatalog.xrayExecutableLibraryName()}")
                        ?: return@use false
                    zip.getInputStream(entry).use { input ->
                        targetFile.outputStream().use { output ->
                            input.copyTo(output)
                        }
                    }
                    true
                }
            }.getOrElse { false }

            if (extracted) {
                return true
            }
        }

        return false
    }

    private fun defaultSessionId(): String = "xray-${System.currentTimeMillis()}"
}
