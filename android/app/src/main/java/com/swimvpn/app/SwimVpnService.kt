package com.swimvpn.app

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager.NameNotFoundException
import android.content.pm.PackageManager
import android.content.pm.ServiceInfo
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.net.NetworkRequest
import android.net.VpnService
import android.os.Build
import android.os.ParcelFileDescriptor
import android.os.PowerManager
import android.util.Log
import androidx.core.app.NotificationCompat
import com.swimvpn.app.BuildConfig
import com.swimvpn.app.adaptive.AdaptiveEventLogger
import com.swimvpn.app.config.SourceType
import com.swimvpn.app.config.TunnelRuntimeAdapter
import com.swimvpn.app.runtime.Tun2SocksAssetCatalog
import com.swimvpn.app.runtime.Tun2SocksLaunchMode
import com.swimvpn.app.runtime.Tun2SocksLaunchSpec
import com.swimvpn.app.runtime.Tun2SocksNativeBridge
import com.swimvpn.app.runtime.Tun2SocksNativeBridgeContract
import com.swimvpn.app.runtime.Tun2SocksRuntimeFilePreparer
import com.swimvpn.app.runtime.XrayProcessBridge
import com.swimvpn.app.vpn.DisconnectCause
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
    private var activeRuntimeMonitorJob: Job? = null
    private var activeStartupJob: Job? = null
    private var activeReconnectJob: Job? = null
    private var activeSession: ActiveSession? = null
    private var activeUnderlyingNetwork: Network? = null
    private var reconnectAttempt = 0
    private var sessionStartedAt: Long? = null
    private var stoppedByUser = false
    private var networkCallback: ConnectivityManager.NetworkCallback? = null
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

        private val SERVICE_RECONNECT_BACKOFF_MS = longArrayOf(1_000L, 3_000L, 5_000L, 10_000L, 30_000L)
        private const val MAX_SERVICE_RECONNECT_ATTEMPTS = 5
    }

    private data class ActiveSession(
        val host: String,
        val port: Int,
        val requestedMode: RuntimeMode,
        val rawConfig: String?,
    )

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        createNotificationChannel()

        when (intent?.action) {
            ACTION_START -> {
                val host = intent.getStringExtra(EXTRA_SERVER_HOST) ?: "unknown"
                val port = intent.getIntExtra(EXTRA_SERVER_PORT, 443)
                val requestedMode = RuntimeMode.fromPersisted(intent.getStringExtra(EXTRA_RUNTIME_MODE))

                startAsForeground("Preparing tunnel to $host...")
                logBatteryOptimizationState()
                logRuntimeEvent("vpn_connect_requested", mapOf("mode" to requestedMode.name))
                startVpn(
                    host = host,
                    port = port,
                    requestedMode = requestedMode,
                    rawConfig = intent.getStringExtra(EXTRA_URL),
                )
            }

            ACTION_STOP -> {
                stoppedByUser = true
                logRuntimeEvent("stopped_by_user")
                stopVpn(reason = "manual_stop", cause = DisconnectCause.USER_STOPPED, finalStatus = RuntimeStatus.STOPPED_BY_USER)
            }
        }

        return START_STICKY
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
        logRuntimeEvent("foreground_service_started")
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
        stoppedByUser = false
        activeSession = ActiveSession(host, port, requestedMode, rawConfig)
        if (sessionStartedAt == null) {
            sessionStartedAt = System.currentTimeMillis()
        }
        AdaptiveEventLogger.log(
            event = "runtime_start_requested",
            details = mapOf("mode" to requestedMode),
        )
        logRuntimeEvent("vpn_service_started", mapOf("mode" to requestedMode.name))
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
                val cause = if (e.message?.contains("config", ignoreCase = true) == true) {
                    DisconnectCause.CONFIG_INVALID
                } else {
                    DisconnectCause.UNKNOWN
                }
                AdaptiveEventLogger.log(
                    event = "runtime_failed",
                    details = mapOf(
                        "reason" to "startup_failure",
                        "mode" to requestedMode,
                        "error" to e.localizedMessage,
                    ),
                )
                logRuntimeEvent("reconnect_failed", mapOf("error" to (e.localizedMessage ?: "unknown")))
                setRuntimeError("Connection failed: ${e.localizedMessage}")
                stopVpn(clearRuntimeState = false, reason = "startup_failure", cause = cause)
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
        logRuntimeEvent("engine_started", mapOf("mode" to RuntimeMode.FULL_TUNNEL.name))

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
                            scheduleReconnect(DisconnectCause.ENGINE_CRASH, "tun2socks_failure")
                        }
                        return@launch
                    }

                    preparedRuntime.exitStateFile.writeText(exitCode.toString())
                    if (VpnManager.runtimeStatus.value == RuntimeStatus.RUNNING) {
                        Log.w("SwimVpnService", "tun2socks exited unexpectedly with code $exitCode")
                        setRuntimeError("tun2socks exited with code $exitCode")
                        scheduleReconnect(DisconnectCause.ENGINE_CRASH, "tun2socks_exit_$exitCode")
                    }
                }
                activeTun2SocksJob = tunnelJob
                delay(200)
                if (tunnelJob.isActive) {
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

        if (!nativeBridgeStarted) {
            throw IllegalStateException(
                "Full tunnel data plane is unavailable: tun2socks did not start (${tun2SocksAvailability.reason})",
            )
        }

        VpnManager.markStarted()
        delay(350)
        VpnManager.markHandshake()
        updateRuntimeStatus(RuntimeStatus.RUNNING, RuntimeMode.FULL_TUNNEL)
        reconnectAttempt = 0
        VpnManager.setRuntimeDiagnostics(
            activeMode = RuntimeMode.FULL_TUNNEL.name,
            xraySessionId = activeXraySessionId,
            tun2SocksSessionId = activeTun2SocksSessionId,
            tun2SocksLogPath = tun2SocksNativePrep?.stderrLogFile?.absolutePath,
            reconnectCount = reconnectAttempt,
            sessionStartedAt = sessionStartedAt,
        )
        startTrafficStatsPolling()
        startRuntimeHeartbeat(RuntimeMode.FULL_TUNNEL)
        startRuntimeLivenessMonitor(
            mode = RuntimeMode.FULL_TUNNEL,
            requireTun2Socks = true,
        )
        registerNetworkCallback()
        logRuntimeEvent("tunnel_started", mapOf("mode" to RuntimeMode.FULL_TUNNEL.name))

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
        logRuntimeEvent("engine_started", mapOf("mode" to RuntimeMode.LOCAL_PROXY.name))

        VpnManager.markHandshake()
        updateRuntimeStatus(RuntimeStatus.RUNNING, RuntimeMode.LOCAL_PROXY)
        reconnectAttempt = 0
        VpnManager.setRuntimeDiagnostics(
            activeMode = RuntimeMode.LOCAL_PROXY.name,
            xraySessionId = activeXraySessionId,
            reconnectCount = reconnectAttempt,
            sessionStartedAt = sessionStartedAt,
        )
        startRuntimeHeartbeat(RuntimeMode.LOCAL_PROXY)
        startRuntimeLivenessMonitor(
            mode = RuntimeMode.LOCAL_PROXY,
            requireTun2Socks = false,
        )
        registerNetworkCallback()
        logRuntimeEvent("tunnel_started", mapOf("mode" to RuntimeMode.LOCAL_PROXY.name))
        updateNotification("Local proxy ready: 127.0.0.1:${runtime.ports.socksPort}")

        Log.i(
            "SwimVpnService",
            "Started local proxy for ${runtime.profile.protocol} ${runtime.summary} via $host:$port",
        )
    }

    private fun stopVpn(
        clearRuntimeState: Boolean = true,
        reason: String = "unspecified",
        cause: DisconnectCause = DisconnectCause.UNKNOWN,
        finalStatus: RuntimeStatus = RuntimeStatus.IDLE,
        stopService: Boolean = true,
    ) {
        Log.i("SwimVpnService", "Stopping VPN runtime reason=$reason clearRuntimeState=$clearRuntimeState")
        logRuntimeEvent(
            event = if (cause == DisconnectCause.USER_STOPPED) "stopped_by_user" else "stopped_by_system",
            details = mapOf("reason" to reason, "cause" to cause.name),
        )
        AdaptiveEventLogger.log(
            event = "tunnel_disconnected",
            details = mapOf(
                "reason" to reason,
                "clearRuntimeState" to clearRuntimeState,
            ),
        )
        VpnManager.setRuntimeDiagnostics(
            lastDisconnectCause = cause,
            reconnectCount = reconnectAttempt,
            sessionStartedAt = sessionStartedAt,
        )
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
            if (stopService) {
                activeReconnectJob?.cancel()
                activeReconnectJob = null
            }
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
            activeRuntimeMonitorJob?.cancel()
            activeRuntimeMonitorJob = null
            activeTun2SocksContract = null
            unregisterNetworkCallback()
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
                updateRuntimeStatus(finalStatus, VpnManager.runtimeMode.value, cause = cause)
            }
            if (stopService) {
                if (finalStatus == RuntimeStatus.IDLE || finalStatus == RuntimeStatus.STOPPED_BY_USER) {
                    activeSession = null
                    sessionStartedAt = null
                    reconnectAttempt = 0
                }
                stopForeground(STOP_FOREGROUND_REMOVE)
                stopSelf()
            }
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
        logRuntimeEvent("service_destroyed")
        serviceScope.cancel()
        if (VpnManager.runtimeStatus.value != RuntimeStatus.FAILED) {
            stopVpn(reason = "service_destroyed", cause = if (stoppedByUser) DisconnectCause.USER_STOPPED else DisconnectCause.SERVICE_KILLED)
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
                activeRuntimeMonitorJob?.cancel()
                activeRuntimeMonitorJob = null
                activeTun2SocksContract = null
                unregisterNetworkCallback()
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

    override fun onRevoke() {
        Log.w("SwimVpnService", "VPN permission was revoked by Android")
        setRuntimeError("VPN permission was revoked by the system")
        stopVpn(clearRuntimeState = false, reason = "vpn_revoked", cause = DisconnectCause.SERVICE_KILLED)
        super.onRevoke()
    }

    override fun onTaskRemoved(rootIntent: Intent?) {
        Log.w("SwimVpnService", "App task removed while VPN service is active; keeping foreground VPN alive")
        super.onTaskRemoved(rootIntent)
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

    private fun startRuntimeLivenessMonitor(mode: RuntimeMode, requireTun2Socks: Boolean) {
        activeRuntimeMonitorJob?.cancel()
        activeRuntimeMonitorJob = serviceScope.launch {
            while (VpnManager.runtimeStatus.value == RuntimeStatus.RUNNING) {
                val xraySessionId = activeXraySessionId
                val xraySnapshot = xraySessionId?.let { xrayBridge.snapshot(it) }
                if (xraySessionId == null || xraySnapshot == null || !xraySnapshot.isAlive) {
                    val exitCode = xraySnapshot?.exitCode?.toString() ?: "unknown"
                    Log.w("SwimVpnService", "Xray process is not alive for mode=$mode exitCode=$exitCode")
                    logRuntimeEvent("engine_crashed", mapOf("engine" to "xray", "exitCode" to exitCode))
                    setRuntimeError("Xray runtime stopped unexpectedly (exit=$exitCode)")
                    scheduleReconnect(DisconnectCause.ENGINE_CRASH, "xray_not_alive_$exitCode")
                    return@launch
                }

                if (requireTun2Socks) {
                    val tun2SocksJob = activeTun2SocksJob
                    val tun2SocksContract = activeTun2SocksContract
                    if (tun2SocksJob == null || tun2SocksContract == null || !tun2SocksJob.isActive) {
                        Log.w("SwimVpnService", "tun2socks monitor detected inactive data plane")
                        logRuntimeEvent("engine_crashed", mapOf("engine" to "tun2socks"))
                        setRuntimeError("tun2socks data plane stopped unexpectedly")
                        scheduleReconnect(DisconnectCause.ENGINE_CRASH, "tun2socks_not_alive")
                        return@launch
                    }
                }

                delay(2_000)
            }
        }
    }

    private fun updateRuntimeStatus(
        status: RuntimeStatus,
        mode: RuntimeMode,
        cause: DisconnectCause = DisconnectCause.UNKNOWN,
    ) {
        if (status == RuntimeStatus.RUNNING) {
            AdaptiveEventLogger.log(
                event = "handshake_success",
                details = mapOf("mode" to mode),
            )
        }
        RuntimeStateStore.write(
            context = applicationContext,
            status = status,
            mode = mode,
            error = VpnManager.errorMessage.value,
            lastDisconnectCause = cause,
            reconnectCount = reconnectAttempt,
            sessionStartedAt = sessionStartedAt,
        )
        VpnManager.updateRuntimeStatus(status)
    }

    private fun setRuntimeError(message: String) {
        RuntimeStateStore.write(
            context = applicationContext,
            status = RuntimeStatus.FAILED,
            mode = VpnManager.runtimeMode.value,
            error = message,
            lastDisconnectCause = DisconnectCause.UNKNOWN,
            reconnectCount = reconnectAttempt,
            sessionStartedAt = sessionStartedAt,
        )
        VpnManager.setError(message)
    }

    private fun hasActiveRuntimeResources(): Boolean {
        return activeStartupJob != null ||
            activeTun2SocksJob != null ||
            activeTun2SocksContract != null ||
            activeTrafficStatsJob != null ||
            activeRuntimeHeartbeatJob != null ||
            activeRuntimeMonitorJob != null ||
            activeXraySessionId != null ||
            activeTun2SocksSessionId != null ||
            vpnInterface != null
    }

    private fun registerNetworkCallback() {
        if (networkCallback != null) return
        val connectivityManager = getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        val callback = object : ConnectivityManager.NetworkCallback() {
            override fun onAvailable(network: Network) {
                val capabilities = connectivityManager.getNetworkCapabilities(network)
                if (!isUsableUnderlyingNetwork(capabilities)) return

                activeUnderlyingNetwork = network
                logRuntimeEvent(
                    "network_available",
                    mapOf("network" to network.toString(), "transport" to transportLabel(capabilities)),
                )
                runCatching { setUnderlyingNetworks(arrayOf(network)) }
                    .onFailure { error -> Log.w("SwimVpnService", "Unable to update VPN underlying network", error) }
                if (VpnManager.runtimeStatus.value == RuntimeStatus.DEGRADED) {
                    scheduleReconnect(DisconnectCause.NETWORK_LOST, "network_available_after_loss")
                }
            }

            override fun onLost(network: Network) {
                if (activeUnderlyingNetwork != network) return

                activeUnderlyingNetwork = null
                logRuntimeEvent("network_lost", mapOf("network" to network.toString()))
                updateRuntimeStatus(RuntimeStatus.DEGRADED, VpnManager.runtimeMode.value, cause = DisconnectCause.NETWORK_LOST)
                updateNotification("Network changed; waiting to reconnect")
                scheduleReconnect(DisconnectCause.NETWORK_LOST, "underlying_network_lost")
            }

            override fun onCapabilitiesChanged(network: Network, networkCapabilities: NetworkCapabilities) {
                if (!isUsableUnderlyingNetwork(networkCapabilities)) return

                val wasActive = activeUnderlyingNetwork == network
                activeUnderlyingNetwork = network
                if (!wasActive) {
                    logRuntimeEvent(
                        "network_available",
                        mapOf("network" to network.toString(), "transport" to transportLabel(networkCapabilities)),
                    )
                }
                runCatching { setUnderlyingNetworks(arrayOf(network)) }
                    .onFailure { error -> Log.w("SwimVpnService", "Unable to update VPN underlying capabilities", error) }
            }
        }
        networkCallback = callback
        val request = NetworkRequest.Builder()
            .addCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
            .addCapability(NetworkCapabilities.NET_CAPABILITY_NOT_VPN)
            .build()
        runCatching { connectivityManager.registerNetworkCallback(request, callback) }
            .onFailure { error -> Log.w("SwimVpnService", "Unable to register network callback", error) }
    }

    private fun unregisterNetworkCallback() {
        val callback = networkCallback ?: return
        val connectivityManager = getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        runCatching { connectivityManager.unregisterNetworkCallback(callback) }
        activeUnderlyingNetwork = null
        networkCallback = null
    }

    private fun isUsableUnderlyingNetwork(capabilities: NetworkCapabilities?): Boolean {
        if (capabilities == null) return false
        if (!capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)) return false
        if (!capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_NOT_VPN)) return false
        return capabilities.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) ||
            capabilities.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR) ||
            capabilities.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET)
    }

    private fun transportLabel(capabilities: NetworkCapabilities?): String {
        return when {
            capabilities == null -> "unknown"
            capabilities.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) -> "wifi"
            capabilities.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR) -> "cellular"
            capabilities.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET) -> "ethernet"
            else -> "other"
        }
    }

    private fun scheduleReconnect(cause: DisconnectCause, reason: String) {
        if (stoppedByUser || VpnManager.runtimeStatus.value == RuntimeStatus.STOPPED_BY_USER) {
            logRuntimeEvent("stopped_by_user", mapOf("skipReconnectReason" to reason))
            return
        }

        val session = activeSession
        if (session?.rawConfig.isNullOrBlank()) {
            logRuntimeEvent("reconnect_failed", mapOf("reason" to "missing_active_session", "cause" to cause.name))
            stopVpn(clearRuntimeState = false, reason = reason, cause = cause)
            return
        }
        val reconnectSession = session ?: return

        if (reconnectAttempt >= MAX_SERVICE_RECONNECT_ATTEMPTS) {
            logRuntimeEvent("reconnect_failed", mapOf("reason" to "max_attempts", "cause" to cause.name))
            stopVpn(clearRuntimeState = false, reason = reason, cause = cause)
            return
        }

        if (activeReconnectJob?.isActive == true) return

        val delayMs = SERVICE_RECONNECT_BACKOFF_MS[reconnectAttempt.coerceIn(0, SERVICE_RECONNECT_BACKOFF_MS.lastIndex)]
        logRuntimeEvent(
            "reconnect_scheduled",
            mapOf("attempt" to (reconnectAttempt + 1).toString(), "delayMs" to delayMs.toString(), "cause" to cause.name),
        )
        updateRuntimeStatus(RuntimeStatus.RECONNECTING, reconnectSession.requestedMode, cause = cause)
        activeReconnectJob = serviceScope.launch {
            delay(delayMs)
            reconnectAttempt += 1
            logRuntimeEvent("reconnect_started", mapOf("attempt" to reconnectAttempt.toString(), "cause" to cause.name))
            stopVpn(clearRuntimeState = false, reason = reason, cause = cause, stopService = false)
            startVpn(
                host = reconnectSession.host,
                port = reconnectSession.port,
                requestedMode = reconnectSession.requestedMode,
                rawConfig = reconnectSession.rawConfig,
            )
            activeReconnectJob = null
        }
    }

    private fun logRuntimeEvent(event: String, details: Map<String, String> = emptyMap()) {
        val redacted = details.mapValues { (_, value) -> redactForLog(value) }
        Log.i("SwimVpnService", "$event $redacted")
        AdaptiveEventLogger.log(event = event, details = redacted)
    }

    private fun logBatteryOptimizationState() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.M) return
        val powerManager = getSystemService(Context.POWER_SERVICE) as PowerManager
        if (!powerManager.isIgnoringBatteryOptimizations(packageName)) {
            logRuntimeEvent("battery_optimization_detected")
        }
    }

    private fun redactForLog(value: String): String {
        return value
            .replace(Regex("[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}")) {
                "${it.value.take(8)}..."
            }
            .replace(Regex("(?i)(password|token|uuid|id)=([^&\\s]+)"), "$1=***")
    }
}
