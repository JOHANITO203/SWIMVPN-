package com.swimvpn.app.runtime

import android.content.Context
import java.lang.IllegalThreadStateException
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicReference

class RunningTun2SocksProcess internal constructor(
    private val preparedRuntime: PreparedTun2SocksRuntime,
    private val process: Process,
    private val exitCodeRef: AtomicReference<Int?>,
) {
    fun sessionId(): String = preparedRuntime.sessionId

    fun stop(gracePeriodMs: Long = 1_500): Tun2SocksProcessSnapshot {
        process.destroy()
        if (!process.waitFor(gracePeriodMs, TimeUnit.MILLISECONDS)) {
            process.destroyForcibly()
            process.waitFor()
        }
        return snapshot()
    }

    fun snapshot(): Tun2SocksProcessSnapshot {
        val exitCode = exitCodeRef.get() ?: safeExitCode()
        return Tun2SocksProcessSnapshot(
            sessionId = preparedRuntime.sessionId,
            isAlive = process.isAlive,
            exitCode = exitCode,
            stdoutLogFile = preparedRuntime.stdoutLogFile,
            stderrLogFile = preparedRuntime.stderrLogFile,
            exitStateFile = preparedRuntime.exitStateFile,
            workingDirectory = preparedRuntime.workingDirectory,
        )
    }

    private fun safeExitCode(): Int? {
        return try {
            process.exitValue()
        } catch (_: IllegalThreadStateException) {
            null
        }
    }
}

class Tun2SocksProcessBridge(
    private val context: Context,
    private val filePreparer: Tun2SocksRuntimeFilePreparer = Tun2SocksRuntimeFilePreparer(context),
) {
    private val activeProcesses = ConcurrentHashMap<String, RunningTun2SocksProcess>()

    fun availability(): Tun2SocksAvailability = Tun2SocksAssetCatalog.availability(context)

    fun prepare(
        launchSpec: Tun2SocksLaunchSpec,
        sessionId: String = defaultSessionId(),
    ): Result<PreparedTun2SocksRuntime> {
        return filePreparer.prepareExecutable(
            launchSpec = launchSpec,
            sessionId = sessionId,
        )
    }

    fun start(preparedRuntime: PreparedTun2SocksRuntime): RunningTun2SocksProcess {
        val command = mutableListOf(
            preparedRuntime.executableFile?.absolutePath
                ?: throw IllegalStateException("tun2socks executable runtime is missing"),
            preparedRuntime.configFile.absolutePath,
        )

        val processBuilder = ProcessBuilder(command)
            .directory(preparedRuntime.workingDirectory)
            .redirectOutput(ProcessBuilder.Redirect.appendTo(preparedRuntime.stdoutLogFile))
            .redirectError(ProcessBuilder.Redirect.appendTo(preparedRuntime.stderrLogFile))

        val process = processBuilder.start()
        val exitCodeRef = AtomicReference<Int?>(null)
        val handle = RunningTun2SocksProcess(preparedRuntime, process, exitCodeRef)
        activeProcesses[preparedRuntime.sessionId] = handle

        val waiter = Thread {
            val exitCode = process.waitFor()
            exitCodeRef.set(exitCode)
            preparedRuntime.exitStateFile.writeText(exitCode.toString())
            activeProcesses.remove(preparedRuntime.sessionId)
        }
        waiter.isDaemon = true
        waiter.name = "tun2socks-waiter-${preparedRuntime.sessionId}"
        waiter.start()

        return handle
    }

    fun snapshot(sessionId: String): Tun2SocksProcessSnapshot? = activeProcesses[sessionId]?.snapshot()

    fun stop(sessionId: String, gracePeriodMs: Long = 1_500): Tun2SocksProcessSnapshot? {
        return activeProcesses.remove(sessionId)?.stop(gracePeriodMs)
    }

    fun stopAll(gracePeriodMs: Long = 1_500): List<Tun2SocksProcessSnapshot> {
        return activeProcesses.keys.toList().mapNotNull { stop(it, gracePeriodMs) }
    }

    private fun defaultSessionId(): String = "tun2socks-${System.currentTimeMillis()}"
}
