package com.swimvpn.app

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager.NameNotFoundException
import android.content.pm.PackageManager
import android.content.pm.ServiceInfo
import android.content.res.Configuration
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.net.NetworkRequest
import android.net.VpnService
import android.os.Build
import android.os.LocaleList
import android.os.ParcelFileDescriptor
import android.os.PowerManager
import android.util.Log
import androidx.core.app.NotificationCompat
import com.swimvpn.app.BuildConfig
import com.swimvpn.app.adaptive.AdaptiveEventLogger
import com.swimvpn.app.config.SourceType
import com.swimvpn.app.config.TunnelRuntimeAdapter
import com.swimvpn.app.data.local.PreferencesManager
import com.swimvpn.app.runtime.Tun2SocksAssetCatalog
import com.swimvpn.app.runtime.Tun2SocksLaunchSpec
import com.swimvpn.app.runtime.Tun2SocksNativeBridge
import com.swimvpn.app.runtime.Tun2SocksNativeBridgeContract
import com.swimvpn.app.runtime.Tun2SocksRuntimeFilePreparer
import com.swimvpn.app.runtime.XrayProcessBridge
import com.swimvpn.app.vpn.DisconnectCause
import com.swimvpn.app.vpn.NetworkHandoffAction
import com.swimvpn.app.vpn.NetworkHandoffPolicy
import com.swimvpn.app.vpn.RuntimeMode
import com.swimvpn.app.vpn.RuntimeRecoveryPolicy
import com.swimvpn.app.vpn.RuntimeReconnectPolicy
import com.swimvpn.app.vpn.RuntimeServiceDestroyPolicy
import com.swimvpn.app.vpn.RuntimeStartupFailurePolicy
import com.swimvpn.app.vpn.RuntimeStartupHealthPolicy
import com.swimvpn.app.vpn.RuntimeStatus
import com.swimvpn.app.vpn.RuntimeStateStore
import com.swimvpn.app.vpn.StickyReconnectPolicy
import com.swimvpn.app.vpn.VpnManager
import com.swimvpn.app.vpn.VpnNotificationLanguage
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import java.util.Locale

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
    private var activeReconnectCause: DisconnectCause? = null
    private var activeReconnectStarted = false
    private var activeNetworkHandoffJob: Job? = null
    private var activeSession: ActiveSession? = null
    private var activeUnderlyingNetwork: Network? = null
    private var notificationLanguage = VpnNotificationLanguage.DEFAULT_LANGUAGE
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
        const val ACTION_RESTART = "com.swimvpn.app.RESTART_VPN"
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
        private const val STARTUP_HEALTH_PROOF_DELAY_MS = 1_000L
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

                startAsForeground()
                refreshNotificationLanguage()
                logBatteryOptimizationState()
                logRuntimeEvent("vpn_connect_requested", mapOf("mode" to requestedMode.name))
                startVpn(
                    host = host,
                    port = port,
                    requestedMode = requestedMode,
                    rawConfig = intent.getStringExtra(EXTRA_URL),
                )
            }

            ACTION_RESTART -> {
                val host = intent.getStringExtra(EXTRA_SERVER_HOST) ?: activeSession?.host ?: "unknown"
                val port = intent.getIntExtra(EXTRA_SERVER_PORT, activeSession?.port ?: 443)
                val requestedMode = RuntimeMode.fromPersisted(intent.getStringExtra(EXTRA_RUNTIME_MODE))
                val rawConfig = intent.getStringExtra(EXTRA_URL) ?: activeSession?.rawConfig

                startAsForeground()
                refreshNotificationLanguage()
                logRuntimeEvent("vpn_restart_requested", mapOf("mode" to requestedMode.name))
                restartVpn(
                    host = host,
                    port = port,
                    requestedMode = requestedMode,
                    rawConfig = rawConfig,
                )
            }

            ACTION_STOP -> {
                stoppedByUser = true
                logRuntimeEvent("stopped_by_user")
                stopVpn(reason = "manual_stop", cause = DisconnectCause.USER_STOPPED, finalStatus = RuntimeStatus.STOPPED_BY_USER)
            }

            null -> {
                restoreStickySessionIfAllowed()
            }
        }

        return START_STICKY
    }

    private fun restoreStickySessionIfAllowed() {
        val snapshot = RuntimeStateStore.read(applicationContext)
        if (!StickyReconnectPolicy.shouldRestoreStickySession(snapshot)) {
            logRuntimeEvent(
                "sticky_restore_skipped",
                mapOf(
                    "status" to snapshot.status.name,
                    "cause" to snapshot.lastDisconnectCause.name,
                ),
            )
            stopSelf()
            return
        }

        startAsForeground()
        refreshNotificationLanguage()
        serviceScope.launch {
            val prefs = PreferencesManager(applicationContext)
            runCatching {
                val payload = prefs.getAutoConnectPayload()
                val vpnPermissionAvailable = snapshot.mode != RuntimeMode.FULL_TUNNEL ||
                    prepare(applicationContext) == null
                if (!RuntimeRecoveryPolicy.shouldRecoverKilledSession(
                        snapshot = snapshot,
                        payloadAvailable = payload != null,
                        vpnPermissionAvailable = vpnPermissionAvailable,
                    )
                ) {
                    logRuntimeEvent(
                        "sticky_restore_skipped",
                        mapOf("reason" to stickyRestoreSkipReason(snapshot, payload != null, vpnPermissionAvailable)),
                    )
                    stopForeground(STOP_FOREGROUND_REMOVE)
                    stopSelf()
                    return@launch
                }

                val recoveryPayload = payload ?: return@launch
                logBatteryOptimizationState()
                logRuntimeEvent(
                    "sticky_restore_started",
                    mapOf("mode" to recoveryPayload.runtimeMode.name),
                )
                startVpn(
                    host = recoveryPayload.host,
                    port = recoveryPayload.port,
                    requestedMode = recoveryPayload.runtimeMode,
                    rawConfig = recoveryPayload.runtimeConfig,
                )
            }.onFailure { error ->
                Log.e("SwimVpnService", "Unable to restore sticky VPN session", error)
                setRuntimeError("VPN restore failed: ${error.localizedMessage}", DisconnectCause.UNKNOWN)
                stopVpn(clearRuntimeState = false, reason = "sticky_restore_failed", cause = DisconnectCause.UNKNOWN)
            }
        }
    }

    private fun stickyRestoreSkipReason(
        snapshot: com.swimvpn.app.vpn.RuntimeStateSnapshot,
        payloadAvailable: Boolean,
        vpnPermissionAvailable: Boolean,
    ): String {
        if (!StickyReconnectPolicy.shouldRestoreStickySession(snapshot)) {
            return "snapshot_not_recoverable"
        }

        if (!payloadAvailable) {
            return "missing_payload"
        }

        if (snapshot.mode == RuntimeMode.FULL_TUNNEL && !vpnPermissionAvailable) {
            return "vpn_permission_missing"
        }

        return "policy_denied"
    }

    private fun startAsForeground() {
        val notification = createNotification()
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

    private fun refreshNotificationLanguage() {
        serviceScope.launch {
            val language = runCatching {
                PreferencesManager(applicationContext).languageFlow.first()
            }.getOrDefault(VpnNotificationLanguage.DEFAULT_LANGUAGE)
            val normalized = VpnNotificationLanguage.normalize(language)
            if (notificationLanguage != normalized) {
                notificationLanguage = normalized
                updateNotification()
            }
        }
    }

    private fun restartVpn(
        host: String,
        port: Int,
        requestedMode: RuntimeMode,
        rawConfig: String?,
    ) {
        if (rawConfig.isNullOrBlank()) {
            logRuntimeEvent("reconnect_failed", mapOf("reason" to "missing_restart_config", "mode" to requestedMode.name))
            stopVpn(clearRuntimeState = false, reason = "missing_restart_config", cause = DisconnectCause.CONFIG_INVALID)
            return
        }

        activeStartupJob?.cancel()
        activeReconnectJob?.cancel()
        activeReconnectJob = null
        activeReconnectCause = null
        activeReconnectStarted = false
        reconnectAttempt = 0
        stoppedByUser = false

        serviceScope.launch {
            logRuntimeEvent("vpn_restart_started", mapOf("mode" to requestedMode.name))
            stopVpn(
                clearRuntimeState = true,
                reason = "runtime_mode_change",
                cause = DisconnectCause.UNKNOWN,
                finalStatus = RuntimeStatus.STOPPING,
                stopService = false,
            )
            startVpn(
                host = host,
                port = port,
                requestedMode = requestedMode,
                rawConfig = rawConfig,
            )
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
            } catch (e: CancellationException) {
                logRuntimeEvent("startup_cancelled", mapOf("reason" to (e.message ?: "cancelled")))
                throw e
            } catch (e: Exception) {
                Log.e("SwimVpnService", "Error starting VPN", e)
                val failureDecision = RuntimeStartupFailurePolicy.classify(e)
                if (!failureDecision.shouldReportFailure) {
                    throw e
                }
                val cause = failureDecision.cause
                AdaptiveEventLogger.log(
                    event = "runtime_failed",
                    details = mapOf(
                        "reason" to "startup_failure",
                        "mode" to requestedMode,
                        "error" to e.localizedMessage,
                    ),
                )
                logRuntimeEvent("reconnect_failed", mapOf("error" to (e.localizedMessage ?: "unknown")))
                setRuntimeError("Connection failed: ${e.localizedMessage}", cause)
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
                            setRuntimeError("tun2socks failed: ${error.localizedMessage}", DisconnectCause.ENGINE_CRASH)
                            scheduleReconnect(DisconnectCause.ENGINE_CRASH, "tun2socks_failure")
                        }
                        return@launch
                    }

                    preparedRuntime.exitStateFile.writeText(exitCode.toString())
                    if (VpnManager.runtimeStatus.value == RuntimeStatus.RUNNING) {
                        Log.w("SwimVpnService", "tun2socks exited unexpectedly with code $exitCode")
                        setRuntimeError("tun2socks exited with code $exitCode", DisconnectCause.ENGINE_CRASH)
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
        awaitStartupHealthProof(
            mode = RuntimeMode.FULL_TUNNEL,
            requireTun2Socks = true,
        )
        VpnManager.markHandshake()
        updateRuntimeStatus(RuntimeStatus.RUNNING, RuntimeMode.FULL_TUNNEL)
        reconnectAttempt = 0
        VpnManager.markHealthyRuntimeSession(
            reconnectCount = reconnectAttempt,
            sessionStartedAt = sessionStartedAt,
        )
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

        updateNotification()

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

        awaitStartupHealthProof(
            mode = RuntimeMode.LOCAL_PROXY,
            requireTun2Socks = false,
        )
        VpnManager.markHandshake()
        updateRuntimeStatus(RuntimeStatus.RUNNING, RuntimeMode.LOCAL_PROXY)
        reconnectAttempt = 0
        VpnManager.markHealthyRuntimeSession(
            reconnectCount = reconnectAttempt,
            sessionStartedAt = sessionStartedAt,
        )
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
        updateNotification()

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
            activeNetworkHandoffJob?.cancel()
            activeNetworkHandoffJob = null
            if (stopService) {
                activeReconnectJob?.cancel()
                activeReconnectJob = null
                activeReconnectCause = null
                activeReconnectStarted = false
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
                    activeReconnectCause = null
                    activeReconnectStarted = false
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
                localizedNotificationText(),
                NotificationManager.IMPORTANCE_LOW,
            ).apply {
                description = localizedNotificationText()
                setShowBadge(false)
            }

            val notificationManager =
                getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            notificationManager.createNotificationChannel(channel)
        }
    }

    private fun createNotification(): Notification {
        return NotificationCompat.Builder(this, channelId)
            .setContentTitle("SWIMVPN+")
            .setContentText(localizedNotificationText())
            .setSmallIcon(R.drawable.ic_stat_swimvpn)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setCategory(NotificationCompat.CATEGORY_SERVICE)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setSilent(true)
            .setLocalOnly(true)
            .setContentIntent(mainActivityPendingIntent())
            .setForegroundServiceBehavior(NotificationCompat.FOREGROUND_SERVICE_IMMEDIATE)
            .build()
    }

    private fun updateNotification() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            checkSelfPermission(android.Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) {
            Log.w("SwimVpnService", "Notification permission not granted, skipping notification update")
            return
        }

        val notificationManager =
            getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        notificationManager.notify(notificationId, createNotification())
    }

    private fun localizedNotificationText(): String {
        val localizedContext = localizedContextFor(notificationLanguage)
        return localizedContext.getString(R.string.vpn_notification_running)
    }

    private fun localizedContextFor(language: String): Context {
        val locale = Locale.forLanguageTag(VpnNotificationLanguage.normalize(language))
        val configuration = Configuration(resources.configuration)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            configuration.setLocales(LocaleList(locale))
        } else {
            @Suppress("DEPRECATION")
            configuration.setLocale(locale)
        }
        return createConfigurationContext(configuration)
    }

    private fun mainActivityPendingIntent(): PendingIntent {
        val flags = PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        val intent = Intent(this, MainActivity::class.java).apply {
            action = Intent.ACTION_MAIN
            addCategory(Intent.CATEGORY_LAUNCHER)
            this.flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        return PendingIntent.getActivity(this, 0, intent, flags)
    }

    override fun onDestroy() {
        super.onDestroy()
        logRuntimeEvent("service_destroyed")
        val currentStatus = VpnManager.runtimeStatus.value
        val destroyDecision = RuntimeServiceDestroyPolicy.recoveryDecision(
            currentStatus = currentStatus,
            stoppedByUser = stoppedByUser,
        )
        if (destroyDecision != null) {
            RuntimeStateStore.write(
                context = applicationContext,
                status = destroyDecision.status,
                mode = VpnManager.runtimeMode.value,
                error = VpnManager.errorMessage.value,
                lastDisconnectCause = destroyDecision.cause,
                reconnectCount = reconnectAttempt,
                sessionStartedAt = sessionStartedAt,
                xrayLogPath = VpnManager.metrics.value.xrayLogPath,
                tun2SocksLogPath = VpnManager.metrics.value.tun2SocksLogPath,
            )
            VpnManager.setRuntimeDiagnostics(
                lastDisconnectCause = destroyDecision.cause,
                reconnectCount = reconnectAttempt,
                sessionStartedAt = sessionStartedAt,
            )
            serviceScope.cancel()
            stopVpn(
                clearRuntimeState = false,
                reason = "service_destroyed",
                cause = destroyDecision.cause,
                finalStatus = destroyDecision.status,
                stopService = false,
            )
            return
        }

        serviceScope.cancel()
        if (currentStatus != RuntimeStatus.FAILED) {
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
        setRuntimeError("VPN permission was revoked by the system", DisconnectCause.SERVICE_KILLED)
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

    private suspend fun awaitStartupHealthProof(mode: RuntimeMode, requireTun2Socks: Boolean) {
        delay(STARTUP_HEALTH_PROOF_DELAY_MS)

        val xraySessionId = activeXraySessionId
        val xraySnapshot = xraySessionId?.let { xrayBridge.snapshot(it) }
        val xrayAlive = xraySessionId != null && xraySnapshot?.isAlive == true
        val tun2SocksAlive = activeTun2SocksContract != null && activeTun2SocksJob?.isActive == true

        if (!RuntimeStartupHealthPolicy.canMarkRunning(
                xrayAlive = xrayAlive,
                requireTun2Socks = requireTun2Socks,
                tun2SocksAlive = tun2SocksAlive,
            )
        ) {
            val xrayExit = xraySnapshot?.exitCode?.toString() ?: "unknown"
            val missingDataPlane = requireTun2Socks && !tun2SocksAlive
            val reason = if (missingDataPlane) {
                "tun2socks data plane stopped before startup proof completed"
            } else {
                "Xray runtime stopped before startup proof completed (exit=$xrayExit)"
            }
            logRuntimeEvent(
                "startup_health_failed",
                mapOf("mode" to mode.name, "reason" to reason),
            )
            throw IllegalStateException(reason)
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
                    lastDisconnectCause = VpnManager.metrics.value.lastDisconnectCause,
                    reconnectCount = VpnManager.metrics.value.reconnectCount,
                    sessionStartedAt = VpnManager.metrics.value.sessionStartedAt,
                    xrayLogPath = VpnManager.metrics.value.xrayLogPath,
                    tun2SocksLogPath = VpnManager.metrics.value.tun2SocksLogPath,
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
                    setRuntimeError("Xray runtime stopped unexpectedly (exit=$exitCode)", DisconnectCause.ENGINE_CRASH)
                    scheduleReconnect(DisconnectCause.ENGINE_CRASH, "xray_not_alive_$exitCode")
                    return@launch
                }

                if (requireTun2Socks) {
                    val tun2SocksJob = activeTun2SocksJob
                    val tun2SocksContract = activeTun2SocksContract
                    if (tun2SocksJob == null || tun2SocksContract == null || !tun2SocksJob.isActive) {
                        Log.w("SwimVpnService", "tun2socks monitor detected inactive data plane")
                        logRuntimeEvent("engine_crashed", mapOf("engine" to "tun2socks"))
                        setRuntimeError("tun2socks data plane stopped unexpectedly", DisconnectCause.ENGINE_CRASH)
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
            lastDisconnectCause = runtimeStatusCauseForStore(status, cause),
            reconnectCount = reconnectAttempt,
            sessionStartedAt = sessionStartedAt,
            xrayLogPath = VpnManager.metrics.value.xrayLogPath,
            tun2SocksLogPath = VpnManager.metrics.value.tun2SocksLogPath,
        )
        VpnManager.updateRuntimeStatus(status)
    }

    private fun setRuntimeError(message: String, cause: DisconnectCause = DisconnectCause.UNKNOWN) {
        RuntimeStateStore.write(
            context = applicationContext,
            status = RuntimeStatus.FAILED,
            mode = VpnManager.runtimeMode.value,
            error = message,
            lastDisconnectCause = cause,
            reconnectCount = reconnectAttempt,
            sessionStartedAt = sessionStartedAt,
            xrayLogPath = VpnManager.metrics.value.xrayLogPath,
            tun2SocksLogPath = VpnManager.metrics.value.tun2SocksLogPath,
        )
        VpnManager.setRuntimeDiagnostics(
            lastDisconnectCause = cause,
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
                cancelNetworkHandoffReconnect("network_available")
            }

            override fun onLost(network: Network) {
                val decision = NetworkHandoffPolicy.onLost(
                    isActiveUnderlyingNetwork = activeUnderlyingNetwork == network,
                    stoppedByUser = stoppedByUser,
                    currentStatus = VpnManager.runtimeStatus.value,
                )
                if (decision.action != NetworkHandoffAction.DEBOUNCE_RECONNECT) return

                activeUnderlyingNetwork = null
                logRuntimeEvent("network_lost", mapOf("network" to network.toString()))
                updateRuntimeStatus(RuntimeStatus.DEGRADED, VpnManager.runtimeMode.value, cause = DisconnectCause.NETWORK_LOST)
                updateNotification()
                scheduleNetworkHandoffReconnect(decision)
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
                cancelNetworkHandoffReconnect("network_capabilities_changed")
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
        activeNetworkHandoffJob?.cancel()
        activeNetworkHandoffJob = null
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
        activeReconnectCause = cause
        activeReconnectStarted = false
        activeReconnectJob = serviceScope.launch {
            try {
                delay(delayMs)
                activeReconnectStarted = true
                reconnectAttempt += 1
                logRuntimeEvent("reconnect_started", mapOf("attempt" to reconnectAttempt.toString(), "cause" to cause.name))
                stopVpn(clearRuntimeState = false, reason = reason, cause = cause, stopService = false)
                startVpn(
                    host = reconnectSession.host,
                    port = reconnectSession.port,
                    requestedMode = reconnectSession.requestedMode,
                    rawConfig = reconnectSession.rawConfig,
                )
            } finally {
                activeReconnectJob = null
                activeReconnectCause = null
                activeReconnectStarted = false
            }
        }
    }

    private fun scheduleNetworkHandoffReconnect(decision: com.swimvpn.app.vpn.NetworkHandoffDecision) {
        if (activeNetworkHandoffJob?.isActive == true) return

        logRuntimeEvent(
            "network_handoff_reconnect_debounced",
            mapOf("delayMs" to decision.delayMs.toString()),
        )
        activeNetworkHandoffJob = serviceScope.launch {
            delay(decision.delayMs)
            val expiredDecision = NetworkHandoffPolicy.onGraceExpired(
                hasUsableUnderlyingNetwork = activeUnderlyingNetwork != null,
                stoppedByUser = stoppedByUser,
            )
            activeNetworkHandoffJob = null
            if (expiredDecision.action == NetworkHandoffAction.RECONNECT_NOW) {
                logRuntimeEvent("network_handoff_reconnect_due")
                scheduleReconnect(DisconnectCause.NETWORK_LOST, "underlying_network_lost")
            }
        }
    }

    private fun cancelNetworkHandoffReconnect(reason: String) {
        val cancelledPendingReconnect = if (RuntimeReconnectPolicy.shouldCancelPendingReconnectForRecoveredNetwork(
                cause = activeReconnectCause,
                started = activeReconnectStarted,
            )
        ) {
            activeReconnectJob?.cancel()
            activeReconnectJob = null
            activeReconnectCause = null
            activeReconnectStarted = false
            logRuntimeEvent("network_reconnect_cancelled", mapOf("reason" to reason))
            true
        } else {
            false
        }
        val decision = NetworkHandoffPolicy.onAvailable(
            hasPendingHandoffReconnect = activeNetworkHandoffJob?.isActive == true,
        )
        if (decision.action != NetworkHandoffAction.CANCEL_DEBOUNCE && !cancelledPendingReconnect) return

        activeNetworkHandoffJob?.cancel()
        activeNetworkHandoffJob = null
        logRuntimeEvent("network_handoff_recovered", mapOf("reason" to reason))
        if (VpnManager.runtimeStatus.value == RuntimeStatus.DEGRADED ||
            VpnManager.runtimeStatus.value == RuntimeStatus.RECONNECTING
        ) {
            updateRuntimeStatus(RuntimeStatus.RUNNING, VpnManager.runtimeMode.value)
            updateNotification()
        }
    }

    private fun runtimeStatusCauseForStore(status: RuntimeStatus, cause: DisconnectCause): DisconnectCause? {
        if (status == RuntimeStatus.RUNNING && cause == DisconnectCause.UNKNOWN) {
            return DisconnectCause.UNKNOWN
        }

        return if (cause == DisconnectCause.UNKNOWN) {
            VpnManager.metrics.value.lastDisconnectCause
        } else {
            cause
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
