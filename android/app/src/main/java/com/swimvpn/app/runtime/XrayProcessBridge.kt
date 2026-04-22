package com.swimvpn.app.runtime

import android.content.Context
import java.io.File
import java.lang.IllegalThreadStateException
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicReference

data class NativeProcessSnapshot(
    val sessionId: String,
    val pid: Long?,
    val isAlive: Boolean,
    val exitCode: Int?,
    val stdoutLogFile: File,
    val stderrLogFile: File,
    val exitStateFile: File,
    val workingDirectory: File,
)

class RunningXrayProcess internal constructor(
    private val preparedRuntime: PreparedXrayRuntime,
    private val process: Process,
    private val exitCodeRef: AtomicReference<Int?>,
) {
    fun sessionId(): String = preparedRuntime.sessionId

    fun stop(gracePeriodMs: Long = 1_500): NativeProcessSnapshot {
        process.destroy()
        if (!process.waitFor(gracePeriodMs, TimeUnit.MILLISECONDS)) {
            process.destroyForcibly()
            process.waitFor()
        }
        return snapshot()
    }

    fun snapshot(): NativeProcessSnapshot {
        val exitCode = exitCodeRef.get() ?: safeExitCode(process)
        return NativeProcessSnapshot(
            sessionId = preparedRuntime.sessionId,
            pid = null,
            isAlive = process.isAlive,
            exitCode = exitCode,
            stdoutLogFile = preparedRuntime.stdoutLogFile,
            stderrLogFile = preparedRuntime.stderrLogFile,
            exitStateFile = preparedRuntime.exitStateFile,
            workingDirectory = preparedRuntime.workingDirectory,
        )
    }

    private fun safeExitCode(process: Process): Int? {
        return try {
            process.exitValue()
        } catch (_: IllegalThreadStateException) {
            null
        }
    }
}

class XrayProcessBridge(
    private val context: Context,
    private val filePreparer: RuntimeFilePreparer = RuntimeFilePreparer(context),
) {
    private val activeProcesses = ConcurrentHashMap<String, RunningXrayProcess>()

    fun prepare(configJson: String, sessionId: String = defaultSessionId()): PreparedXrayRuntime {
        return filePreparer.prepareXrayRuntime(
            configJson = configJson,
            sessionId = sessionId,
        )
    }

    fun start(preparedRuntime: PreparedXrayRuntime): RunningXrayProcess {
        val processBuilder = ProcessBuilder(
            preparedRuntime.executableFile.absolutePath,
            "run",
            "-config",
            preparedRuntime.configFile.absolutePath,
        )
            .directory(preparedRuntime.workingDirectory)
            .redirectOutput(ProcessBuilder.Redirect.appendTo(preparedRuntime.stdoutLogFile))
            .redirectError(ProcessBuilder.Redirect.appendTo(preparedRuntime.stderrLogFile))

        val process = processBuilder.start()
        val exitCodeRef = AtomicReference<Int?>(null)
        val handle = RunningXrayProcess(preparedRuntime, process, exitCodeRef)
        activeProcesses[preparedRuntime.sessionId] = handle

        val waiter = Thread {
            val exitCode = process.waitFor()
            exitCodeRef.set(exitCode)
            preparedRuntime.exitStateFile.writeText(exitCode.toString())
            activeProcesses.remove(preparedRuntime.sessionId)
        }
        waiter.isDaemon = true
        waiter.name = "xray-waiter-${preparedRuntime.sessionId}"
        waiter.start()

        return handle
    }

    fun snapshot(sessionId: String): NativeProcessSnapshot? = activeProcesses[sessionId]?.snapshot()

    fun stop(sessionId: String, gracePeriodMs: Long = 1_500): NativeProcessSnapshot? {
        return activeProcesses.remove(sessionId)?.stop(gracePeriodMs)
    }

    fun stopAll(gracePeriodMs: Long = 1_500): List<NativeProcessSnapshot> {
        return activeProcesses.keys.toList().mapNotNull { stop(it, gracePeriodMs) }
    }

    private fun defaultSessionId(): String = "xray-${System.currentTimeMillis()}"
}
