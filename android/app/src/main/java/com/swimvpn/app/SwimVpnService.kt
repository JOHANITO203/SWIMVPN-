package com.swimvpn.app

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager.NameNotFoundException
import android.content.pm.PackageManager
import android.content.pm.ServiceInfo
import android.net.VpnService
import android.os.Build
import android.os.ParcelFileDescriptor
import android.util.Log
import androidx.core.app.NotificationCompat
import com.swimvpn.app.BuildConfig
import com.swimvpn.app.config.SourceType
import com.swimvpn.app.config.TunnelRuntimeAdapter
import com.swimvpn.app.runtime.Tun2SocksAssetCatalog
import com.swimvpn.app.runtime.Tun2SocksLaunchMode
import com.swimvpn.app.runtime.Tun2SocksLaunchSpec
import com.swimvpn.app.runtime.Tun2SocksNativeBridge
import com.swimvpn.app.runtime.Tun2SocksNativeBridgeContract
import com.swimvpn.app.runtime.Tun2SocksRuntimeFilePreparer
import com.swimvpn.app.runtime.XrayProcessBridge
import com.swimvpn.app.vpn.RuntimeMode
import com.swimvpn.app.vpn.RuntimeStatus
import com.swimvpn.app.vpn.RuntimeStateStore
import com.swimvpn.app.vpn.VpnManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

class SwimVpnService : VpnService() {
    private var vpnInterface: ParcelFileDescriptor? = null
    private var activeXraySessionId: String? = null
    private var activeTun2SocksSessionId: String? = null
    private var activeTun2SocksContract: Tun2SocksNativeBridgeContract? = null
    private var activeTun2SocksJob: Job? = null
    private var activeTrafficStatsJob: Job? = null
    private var activeRuntimeHeartbeatJob: Job? = null
    private var activeStartupJob: Job? = null
    private val serviceJob = Job()
    private val serviceScope = CoroutineScope(Dispatchers.IO + serviceJob)
    private val xrayBridge by lazy { XrayProcessBridge(applicationContext) }
    private val tun2SocksFilePreparer by lazy { Tun2SocksRuntimeFilePreparer(applicationContext) }

    private val channelId = "swim_vpn_status"
    private val notificationId = 1

    companion object {
        private const val DEFAULT_VPN_MTU = 1280
        const val ACTION_START = "com.swimvpn.app.START_VPN"
        const val ACTION_STOP = "com.swimvpn.app.STOP_VPN"

        const val EXTRA_SERVER_HOST = "SERVER_HOST"
        const val EXTRA_SERVER_PORT = "SERVER_PORT"
        const val EXTRA_PROTOCOL = "PROTOCOL"
        const val EXTRA_URL = "SUBSCRIPTION_URL"
        const val EXTRA_DATA_LIMIT = "DATA_LIMIT_BYTES"
        const val EXTRA_DATA_USED = "DATA_USED_BYTES"
        const val EXTRA_RUNTIME_MODE = "RUNTIME_MODE"
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        createNotificationChannel()

        when (intent?.action) {
            ACTION_START -> {
                val host = intent.getStringExtra(EXTRA_SERVER_HOST) ?: "unknown"
                val port = intent.getIntExtra(EXTRA_SERVER_PORT, 443)
                val requestedMode = RuntimeMode.fromPersisted(intent.getStringExtra(EXTRA_RUNTIME_MODE))

                startAsForeground("Preparing tunnel to $host...")
                startVpn(
                    host = host,
                    port = port,
                    requestedMode = requestedMode,
                    rawConfig = intent.getStringExtra(EXTRA_URL),
                )
            }

            ACTION_STOP -> stopVpn()
        }

        return START_NOT_STICKY
    }

    private fun startAsForeground(content: String) {
        val notification = createNotification(content)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            startForeground(
                notificationId,
                notification,
                ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE,
            )
        } else {
            startForeground(notificationId, notification)
        }
    }

    private fun startVpn(
        host: String,
        port: Int,
        requestedMode: RuntimeMode,
        rawConfig: String?,
    ) {
        if (VpnManager.runtimeStatus.value == RuntimeStatus.RUNNING ||
            VpnManager.runtimeStatus.value == RuntimeStatus.STARTING
        ) {
            return
        }

        VpnManager.setRuntimeMode(requestedMode)
        VpnManager.resetUsage()
        VpnManager.clearError()
        VpnManager.clearRuntimeDiagnostics()
        updateRuntimeStatus(RuntimeStatus.STARTING, requestedMode)

        activeStartupJob?.cancel()
        activeStartupJob = serviceScope.launch {
            try {
                val runtime = rawConfig?.takeIf { it.isNotBlank() }?.let {
                    TunnelRuntimeAdapter.prepareRuntimeFromRawConfig(
                        rawConfig = it,
                        sourceType = SourceType.BACKEND_API,
                        runtimeMode = requestedMode,
                    ).getOrElse { error ->
                        throw IllegalStateException(
                            "Invalid runtime config: ${error.localizedMessage}",
                            error,
                        )
                    }
                } ?: throw IllegalStateException("Missing runtime config for VPN session")

                when (requestedMode) {
                    RuntimeMode.LOCAL_PROXY -> startLocalProxy(runtime, host, port)
                    RuntimeMode.FULL_TUNNEL -> startTunnelInterface(runtime, host, port)
                    RuntimeMode.SPLIT_TUNNEL -> {
                        throw IllegalStateException("Split tunnel is not available yet")
                    }
                }
            } catch (e: Exception) {
                Log.e("SwimVpnService", "Error starting VPN", e)
                setRuntimeError("Connection failed: ${e.localizedMessage}")
                stopVpn(clearRuntimeState = false)
            } finally {
                activeStartupJob = null
            }
        }
    }

    private suspend fun startTunnelInterface(
        runtime: TunnelRuntimeAdapter.RuntimePreparationResult,
        host: String,
        port: Int,
    ) {
        val tun2SocksAvailability = Tun2SocksAssetCatalog.availability(applicationContext)
        startValidatedXrayRuntime(
            runtime = runtime,
            failurePrefix = "Xray tunnel runtime exited before tun2socks could be armed",
        )

        val builder = Builder()
            .setSession("SWIMVPN+ (${runtime.profile.displayName})")
            .addAddress("10.0.0.2", 24)
            .addRoute("0.0.0.0", 0)
            .setMtu(DEFAULT_VPN_MTU)

        TunnelRuntimeAdapter.DEFAULT_IPV4_DNS_SERVERS.forEach { dns ->
            builder.addDnsServer(dns)
        }

        try {
            builder.addDisallowedApplication(packageName)
        } catch (_: NameNotFoundException) {
            Log.w("SwimVpnService", "Unable to exclude ${packageName} from full tunnel routing")
        }

        vpnInterface = builder.establish()

        if (vpnInterface == null) {
            throw IllegalStateException("Failed to establish VPN interface. Permission missing?")
        }

        val tunFd = vpnInterface?.fd ?: throw IllegalStateException("VPN interface fd is unavailable")
        val tun2SocksLaunchSpec = Tun2SocksLaunchSpec(
            deviceArgument = "android-vpn",
            proxyUrl = "socks5://127.0.0.1:${runtime.ports.socksPort}",
            tunFd = tunFd,
            mtu = DEFAULT_VPN_MTU,
            interfaceName = "swim0",
        )
        val tun2SocksNativePrep = if (tun2SocksAvailability.packagedSharedLibraryAvailable) {
            tun2SocksFilePreparer.prepareNativeRuntime(
                launchSpec = tun2SocksLaunchSpec,
            ).getOrElse { error ->
                throw IllegalStateException(
                    "tun2socks native runtime could not be prepared: ${error.localizedMessage}",
                    error,
                )
            }
        } else {
            null
        }

        var nativeBridgeStarted = false
        tun2SocksNativePrep?.let { preparedRuntime ->
            activeTun2SocksSessionId = preparedRuntime.sessionId
            val nativeContract = Tun2SocksNativeBridge.contract(
                preparedRuntime = preparedRuntime,
                tunFd = tunFd,
            )
            if (Tun2SocksNativeBridge.isShimAvailable()) {
                activeTun2SocksContract = nativeContract
                val tunnelJob = serviceScope.launch {
                    val exitCode = runCatching {
                        Tun2SocksNativeBridge.start(nativeContract)
                    }.getOrElse { error ->
                        preparedRuntime.stderrLogFile.appendText("${error.message ?: "tun2socks native bridge failure"}\n")
                        preparedRuntime.exitStateFile.writeText("FAILED")
                        Log.e("SwimVpnService", "tun2socks native bridge failed", error)
                        if (VpnManager.runtimeStatus.value == RuntimeStatus.RUNNING) {
                            setRuntimeError("tun2socks failed: ${error.localizedMessage}")
                            stopVpn(clearRuntimeState = false)
                        }
                        return@launch
                    }

                    preparedRuntime.exitStateFile.writeText(exitCode.toString())
                    if (VpnManager.runtimeStatus.value == RuntimeStatus.RUNNING) {
                        Log.w("SwimVpnService", "tun2socks exited unexpectedly with code $exitCode")
                        setRuntimeError("tun2socks exited with code $exitCode")
                        stopVpn(clearRuntimeState = false)
                    }
                }
                activeTun2SocksJob = tunnelJob
                delay(200)
                if (!tunnelJob.isCancelled) {
                    nativeBridgeStarted = true
                }
                Log.i(
                    "SwimVpnService",
                    "Started tun2socks native bridge for ${preparedRuntime.sharedLibraryName} fd=${nativeContract.tunFd}",
                )
            } else {
                val guidance = Tun2SocksNativeBridge.launchError(nativeContract)
                preparedRuntime.stderrLogFile.appendText("${guidance.message}\n")
                preparedRuntime.exitStateFile.writeText("JNI_SHIM_UNAVAILABLE")
                Log.w("SwimVpnService", guidance.message ?: "tun2socks JNI shim unavailable")
            }
        }

        VpnManager.markStarted()
        delay(350)
        VpnManager.markHandshake()
        updateRuntimeStatus(RuntimeStatus.RUNNING, RuntimeMode.FULL_TUNNEL)
        VpnManager.setRuntimeDiagnostics(
            activeMode = RuntimeMode.FULL_TUNNEL.name,
            xraySessionId = activeXraySessionId,
            tun2SocksSessionId = activeTun2SocksSessionId,
            tun2SocksLogPath = tun2SocksNativePrep?.stderrLogFile?.absolutePath,
        )
        startTrafficStatsPolling()
        startRuntimeHeartbeat(RuntimeMode.FULL_TUNNEL)

        val runtimeMessage = when (tun2SocksAvailability.preferredLaunchMode) {
            Tun2SocksLaunchMode.JNI -> {
                if (nativeBridgeStarted) {
                    "Full tunnel active; tun2socks JNI data plane running"
                } else {
                    "Tunnel interface ready; tun2socks native library packaged, JNI shim unavailable"
                }
            }
            Tun2SocksLaunchMode.EXECUTABLE -> {
                "Tunnel interface ready; tun2socks executable packaged, Android native bridge pending"
            }
            Tun2SocksLaunchMode.MISSING -> {
                "Tunnel interface ready; tun2socks native bridge is not packaged yet, transitional mode"
            }
        }
        updateNotification(runtimeMessage)

        Log.i(
            "SwimVpnService",
            buildString {
                append("Prepared tunnel interface for ${runtime.profile.protocol} ${runtime.summary} via $host:$port")
                append(" | tun2socksLaunchMode=")
                append(tun2SocksAvailability.preferredLaunchMode.name)
                append(" | socks=")
                append("127.0.0.1:${runtime.ports.socksPort}")
                append(" | mtu=")
                append(tun2SocksLaunchSpec.mtu)
                append(" | tunFd=")
                append(tunFd)
                append(" | reason=")
                append(tun2SocksAvailability.reason)
            },
        )
    }

    private suspend fun startLocalProxy(
        runtime: TunnelRuntimeAdapter.RuntimePreparationResult,
        host: String,
        port: Int,
    ) {
        startValidatedXrayRuntime(
            runtime = runtime,
            failurePrefix = "Xray local proxy exited before becoming ready",
        )

        VpnManager.markHandshake()
        updateRuntimeStatus(RuntimeStatus.RUNNING, RuntimeMode.LOCAL_PROXY)
        VpnManager.setRuntimeDiagnostics(
            activeMode = RuntimeMode.LOCAL_PROXY.name,
            xraySessionId = activeXraySessionId,
        )
        startRuntimeHeartbeat(RuntimeMode.LOCAL_PROXY)
        updateNotification("Local proxy ready: 127.0.0.1:${runtime.ports.socksPort}")

        Log.i(
            "SwimVpnService",
            "Started local proxy for ${runtime.profile.protocol} ${runtime.summary} via $host:$port",
        )
    }

    private fun stopVpn(clearRuntimeState: Boolean = true) {
        if (!hasActiveRuntimeResources() &&
            clearRuntimeState &&
            (VpnManager.runtimeStatus.value == RuntimeStatus.IDLE ||
                VpnManager.runtimeStatus.value == RuntimeStatus.STOPPING)
        ) {
            return
        }

        if (clearRuntimeState) {
            updateRuntimeStatus(RuntimeStatus.STOPPING, VpnManager.runtimeMode.value)
        }

        try {
            activeStartupJob?.cancel()
            activeStartupJob = null
            activeTun2SocksContract?.let { contract ->
                runCatching { Tun2SocksNativeBridge.stop(contract) }
                    .onFailure { error ->
                        Log.e("SwimVpnService", "Error stopping tun2socks native bridge", error)
                    }
            }
            activeTun2SocksJob?.cancel()
            activeTun2SocksJob = null
            activeTun2SocksSessionId = null
            activeTrafficStatsJob?.cancel()
            activeTrafficStatsJob = null
            activeRuntimeHeartbeatJob?.cancel()
            activeRuntimeHeartbeatJob = null
            activeTun2SocksContract = null
            activeXraySessionId?.let { sessionId ->
                xrayBridge.stop(sessionId)
            }
            activeXraySessionId = null
            xrayBridge.stopAll()
            vpnInterface?.close()
        } catch (e: Exception) {
            Log.e("SwimVpnService", "Error closing VPN interface", e)
        } finally {
            vpnInterface = null
            VpnManager.clearRuntimeDiagnostics()
            if (clearRuntimeState) {
                updateRuntimeStatus(RuntimeStatus.IDLE, VpnManager.runtimeMode.value)
            }
            stopForeground(STOP_FOREGROUND_REMOVE)
            stopSelf()
        }
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                channelId,
                "VPN Status",
                NotificationManager.IMPORTANCE_LOW,
            ).apply {
                description = "Shows when VPN is active"
            }

            val notificationManager =
                getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            notificationManager.createNotificationChannel(channel)
        }
    }

    private fun createNotification(content: String): Notification {
        return NotificationCompat.Builder(this, channelId)
            .setContentTitle("SWIMVPN+")
            .setContentText(content)
            .setSmallIcon(R.drawable.swimvpn_logo)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setCategory(NotificationCompat.CATEGORY_SERVICE)
            .build()
    }

    private fun updateNotification(content: String) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            checkSelfPermission(android.Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) {
            Log.w("SwimVpnService", "Notification permission not granted, skipping notification update")
            return
        }

        val notificationManager =
            getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        notificationManager.notify(notificationId, createNotification(content))
    }

    override fun onDestroy() {
        super.onDestroy()
        serviceScope.cancel()
        if (VpnManager.runtimeStatus.value != RuntimeStatus.FAILED) {
            stopVpn()
        } else {
            try {
                activeStartupJob?.cancel()
                activeStartupJob = null
                activeTun2SocksContract?.let { contract ->
                    runCatching { Tun2SocksNativeBridge.stop(contract) }
                        .onFailure { error ->
                            Log.e("SwimVpnService", "Error stopping tun2socks native bridge during failure cleanup", error)
                        }
                }
                activeTun2SocksJob?.cancel()
                activeTun2SocksJob = null
                activeTun2SocksSessionId = null
                activeTrafficStatsJob?.cancel()
                activeTrafficStatsJob = null
                activeRuntimeHeartbeatJob?.cancel()
                activeRuntimeHeartbeatJob = null
                activeTun2SocksContract = null
                activeXraySessionId?.let { sessionId ->
                    xrayBridge.stop(sessionId)
                }
                activeXraySessionId = null
                xrayBridge.stopAll()
                vpnInterface?.close()
            } catch (e: Exception) {
                Log.e("SwimVpnService", "Error closing VPN interface during failure cleanup", e)
            } finally {
                vpnInterface = null
                VpnManager.clearRuntimeDiagnostics()
            }
        }
    }

    private suspend fun startValidatedXrayRuntime(
        runtime: TunnelRuntimeAdapter.RuntimePreparationResult,
        failurePrefix: String,
    ) {
        val preparedRuntime = xrayBridge.prepare(runtime.runtimeConfig)
        val running = xrayBridge.start(preparedRuntime)
        activeXraySessionId = running.sessionId()
        VpnManager.setRuntimeDiagnostics(
            activeMode = VpnManager.runtimeMode.value.name,
            xraySessionId = preparedRuntime.sessionId,
            xrayLogPath = preparedRuntime.stderrLogFile.absolutePath,
        )

        VpnManager.markStarted()
        delay(600)

        val snapshot = running.snapshot()
        if (!snapshot.isAlive) {
            val stderrTail = snapshot.stderrLogFile
                .takeIf { it.exists() }
                ?.readText()
                ?.takeLast(400)
                ?.trim()
            throw IllegalStateException(
                buildString {
                    append(failurePrefix)
                    if (!stderrTail.isNullOrBlank()) {
                        append(": ")
                        append(stderrTail)
                    }
                }
            )
        }
    }

    private fun startTrafficStatsPolling() {
        activeTrafficStatsJob?.cancel()
        val contract = activeTun2SocksContract ?: return

        activeTrafficStatsJob = serviceScope.launch {
            var lastRxBytes = 0L
            var lastTxBytes = 0L
            while (VpnManager.runtimeStatus.value == RuntimeStatus.RUNNING) {
                runCatching {
                    Tun2SocksNativeBridge.stats(contract)
                }.onSuccess { stats ->
                    val deltaIn = (stats.rxBytes - lastRxBytes).coerceAtLeast(0L)
                    val deltaOut = (stats.txBytes - lastTxBytes).coerceAtLeast(0L)
                    lastRxBytes = stats.rxBytes
                    lastTxBytes = stats.txBytes
                    if (deltaIn > 0L || deltaOut > 0L) {
                        VpnManager.updateUsage(downloaded = deltaIn, uploaded = deltaOut)
                    }
                }.onFailure { error ->
                    Log.w("SwimVpnService", "Unable to read tun2socks traffic stats", error)
                }
                delay(1000)
            }
        }
    }

    private fun startRuntimeHeartbeat(mode: RuntimeMode) {
        activeRuntimeHeartbeatJob?.cancel()
        activeRuntimeHeartbeatJob = serviceScope.launch {
            while (VpnManager.runtimeStatus.value == RuntimeStatus.RUNNING) {
                RuntimeStateStore.write(
                    context = applicationContext,
                    status = RuntimeStatus.RUNNING,
                    mode = mode,
                    error = null,
                )
                delay(2_000)
            }
        }
    }

    private fun updateRuntimeStatus(status: RuntimeStatus, mode: RuntimeMode) {
        RuntimeStateStore.write(
            context = applicationContext,
            status = status,
            mode = mode,
            error = VpnManager.errorMessage.value,
        )
        VpnManager.updateRuntimeStatus(status)
    }

    private fun setRuntimeError(message: String) {
        RuntimeStateStore.write(
            context = applicationContext,
            status = RuntimeStatus.FAILED,
            mode = VpnManager.runtimeMode.value,
            error = message,
        )
        VpnManager.setError(message)
    }

    private fun hasActiveRuntimeResources(): Boolean {
        return activeStartupJob != null ||
            activeTun2SocksJob != null ||
            activeTun2SocksContract != null ||
            activeTrafficStatsJob != null ||
            activeRuntimeHeartbeatJob != null ||
            activeXraySessionId != null ||
            activeTun2SocksSessionId != null ||
            vpnInterface != null
    }
}
