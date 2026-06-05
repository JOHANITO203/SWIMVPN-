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
import com.swimvpn.app.config.RoutingOptions
import com.swimvpn.app.config.SourceType
import com.swimvpn.app.config.TunnelRuntimeAdapter
import com.swimvpn.app.config.XrayRoutingBuilder
import com.swimvpn.app.data.local.PreferencesManager
import com.swimvpn.app.runtime.Tun2SocksAssetCatalog
import com.swimvpn.app.runtime.Tun2SocksLaunchSpec
import com.swimvpn.app.runtime.Tun2SocksNativeBridge
import com.swimvpn.app.runtime.Tun2SocksNativeBridgeContract
import com.swimvpn.app.runtime.Tun2SocksRuntimeFilePreparer
import com.swimvpn.app.runtime.XrayProcessBridge
import com.swimvpn.app.vpn.DisconnectCause
import com.swimvpn.app.vpn.QuotaCutoffPolicy
import com.swimvpn.app.vpn.NetworkClassifier
import com.swimvpn.app.vpn.NetworkHandoffAction
import com.swimvpn.app.vpn.NetworkHandoffPolicy
import com.swimvpn.app.vpn.RuntimeMode
import com.swimvpn.app.vpn.RuntimeRecoveryPolicy
import com.swimvpn.app.vpn.RuntimeReconnectPolicy
import com.swimvpn.app.vpn.RuntimeServiceDestroyPolicy
import com.swimvpn.app.vpn.RuntimeStartupFailurePolicy
import com.swimvpn.app.vpn.RuntimeStartupHealthPolicy
import com.swimvpn.app.vpn.RuntimeStatus
import com.swimvpn.app.vpn.TunnelFallbackPolicy
import com.swimvpn.app.diagnostics.CrashReporter
import com.swimvpn.app.vpn.RuntimeStateStore
import com.swimvpn.app.vpn.StickyReconnectPolicy
import com.swimvpn.app.vpn.VpnManager
import com.swimvpn.app.vpn.VpnNotificationLanguage
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineExceptionHandler
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
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
    // OEM hardening: when a FULL_TUNNEL data-plane failure (establish()/tun2socks) occurs on a
    // device where the tunnel cannot run, we degrade once to LOCAL_PROXY so the user keeps working
    // connectivity. fellBackToProxy guards against repeated fallback within one connect chain;
    // pendingProxyFallback carries the retry payload so it is launched from finally (after the
    // failed startup job's handle is cleared), never overwriting activeStartupJob.
    private var fellBackToProxy = false
    private var pendingProxyFallback: Triple<String, Int, String?>? = null
    private var startedOnUnvalidatedNetwork = false
    private var networkCallback: ConnectivityManager.NetworkCallback? = null
    // One-shot callback that re-triggers the connect once a usable network appears
    // after a NO_NETWORK pre-flight refusal (R2). Self-unregisters after firing.
    private var pendingConnectNetworkCallback: ConnectivityManager.NetworkCallback? = null
    private val serviceJob = SupervisorJob()
    // Any uncaught coroutine exception must become a clean, visible FAILED state
    // (logged) rather than crashing the whole process. A crash must never be
    // silently swallowed nor surface as a fake "Connected".
    private val serviceExceptionHandler = CoroutineExceptionHandler { _, throwable ->
        if (throwable is CancellationException) return@CoroutineExceptionHandler
        Log.e("SwimVpnService", "Uncaught coroutine failure; forcing FAILED state", throwable)
        val cause = RuntimeStartupFailurePolicy.classify(throwable).cause
        runCatching {
            setRuntimeError(
                localizedContextFor(notificationLanguage).getString(
                    R.string.vpn_err_runtime_failed,
                    throwable.localizedMessage ?: throwable.javaClass.simpleName,
                ),
                cause,
            )
            stopVpn(clearRuntimeState = false, reason = "uncaught_coroutine_failure", cause = cause)
        }.onFailure { cleanupError ->
            Log.e("SwimVpnService", "Failed to surface uncaught coroutine failure", cleanupError)
        }
    }
    private val serviceScope = CoroutineScope(Dispatchers.IO + serviceJob + serviceExceptionHandler)
    private val xrayBridge by lazy { XrayProcessBridge(applicationContext) }
    private val tun2SocksFilePreparer by lazy { Tun2SocksRuntimeFilePreparer(applicationContext) }

    private val channelId = "swim_vpn_status"
    private val notificationId = 1

    companion object {
        private const val DEFAULT_VPN_MTU = 1280
        // Unique-local IPv6 address for the tun, so IPv6 is captured (not leaked) — see startTunnelInterface.
        private const val VPN_IPV6_ADDRESS = "fd00:2::2"
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
        const val EXTRA_CAMOUFLAGE_FP = "CAMOUFLAGE_FP"

        private val SERVICE_RECONNECT_BACKOFF_MS = longArrayOf(1_000L, 3_000L, 5_000L, 10_000L, 30_000L)
        private const val MAX_SERVICE_RECONNECT_ATTEMPTS = 5
        // A dead BYO residential proxy won't self-heal via retry → give up fast + tell the user.
        private const val MAX_BYO_PROXY_RECONNECT_ATTEMPTS = 2
        private const val STARTUP_HEALTH_PROOF_DELAY_MS = 1_000L
        private const val LIVENESS_POLL_INTERVAL_MS = 500L
        private const val TRAFFIC_PROBE_TIMEOUT_MS = 1_200
        private const val TRAFFIC_PROBE_RETRY_DELAY_MS = 300L
        // Passive watchdog: how long a confirmed-running session may show outbound
        // bytes with zero inbound bytes before being demoted to DEGRADED (NO_TRAFFIC).
        private const val TRAFFIC_STALL_THRESHOLD_MS = 15_000L
        // A BYO residential proxy that stops relaying is flagged faster than a managed server.
        private const val BYO_PROXY_STALL_THRESHOLD_MS = 8_000L
    }

    // Distinct startup failure that already carries an explicit DisconnectCause, so
    // the startup catch does not fall back to keyword classification for it.
    private class StartupHealthException(
        message: String,
        val disconnectCause: DisconnectCause,
    ) : IllegalStateException(message)

    private data class ActiveSession(
        val host: String,
        val port: Int,
        val requestedMode: RuntimeMode,
        val rawConfig: String?,
        // True when the active profile is a user-supplied (BYO) residential proxy (SOCKS5/HTTP).
        // Such proxies are flaky/ephemeral → give up reconnecting fast + surface a proxy-specific
        // message instead of a generic "connection error".
        val isByoProxy: Boolean = false,
        // Phase 3: camouflage uTLS fingerprint chosen by the VM (agent or manual). Carried so the
        // service-driven reconnects reuse it. Null = no override (today's behavior).
        val camouflageFingerprint: String? = null,
        // Sold-quota client cutoff: limit from the plan (-1 = unmetered), baseline = bytes already
        // used before this session started (so the meter continues across reconnects).
        val quotaLimitBytes: Long = -1L,
        val quotaBaselineBytes: Long = 0L,
    )

    private data class VpnNotificationContent(
        val title: String,
        val text: String,
    )

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        createNotificationChannel()

        when (intent?.action) {
            ACTION_START -> {
                val host = intent.getStringExtra(EXTRA_SERVER_HOST) ?: "unknown"
                val port = intent.getIntExtra(EXTRA_SERVER_PORT, 443)
                val requestedMode = RuntimeMode.fromPersisted(intent.getStringExtra(EXTRA_RUNTIME_MODE))

                // startAsForeground() first (anti-ANR): promote to foreground before
                // any heavy work so the system does not ANR/kill the service.
                startAsForeground()
                refreshNotificationLanguage()
                logBatteryOptimizationState()
                logRuntimeEvent("vpn_connect_requested", mapOf("mode" to requestedMode.name))
                // A manual connect must take precedence over any pending auto-reconnect:
                // cancel it so the explicit user request proceeds instead of being
                // silently ignored or racing the backoff job.
                cancelPendingAutoReconnect("manual_connect")
                startVpn(
                    host = host,
                    port = port,
                    requestedMode = requestedMode,
                    rawConfig = intent.getStringExtra(EXTRA_URL),
                    isByoProxy = isByoProxyProtocol(intent.getStringExtra(EXTRA_PROTOCOL)),
                    camouflageFingerprint = intent.getStringExtra(EXTRA_CAMOUFLAGE_FP),
                    quotaLimitBytes = intent.getLongExtra(EXTRA_DATA_LIMIT, -1L),
                    quotaBaselineBytes = intent.getLongExtra(EXTRA_DATA_USED, 0L),
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
                    isByoProxy = isByoProxyProtocol(intent.getStringExtra(EXTRA_PROTOCOL)) || (activeSession?.isByoProxy == true),
                    camouflageFingerprint = intent.getStringExtra(EXTRA_CAMOUFLAGE_FP) ?: activeSession?.camouflageFingerprint,
                    quotaLimitBytes = intent.getLongExtra(EXTRA_DATA_LIMIT, activeSession?.quotaLimitBytes ?: -1L),
                    quotaBaselineBytes = intent.getLongExtra(EXTRA_DATA_USED, activeSession?.quotaBaselineBytes ?: 0L),
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
                setRuntimeError(localizedContextFor(notificationLanguage).getString(R.string.vpn_err_restore_failed, error.localizedMessage), DisconnectCause.UNKNOWN)
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
        isByoProxy: Boolean = false,
        camouflageFingerprint: String? = null,
        quotaLimitBytes: Long = -1L,
        quotaBaselineBytes: Long = 0L,
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
                isByoProxy = isByoProxy,
                camouflageFingerprint = camouflageFingerprint,
                quotaLimitBytes = quotaLimitBytes,
                quotaBaselineBytes = quotaBaselineBytes,
            )
        }
    }

    private fun isByoProxyProtocol(protocol: String?): Boolean =
        protocol == "SOCKS5" || protocol == "HTTP"

    private fun startVpn(
        host: String,
        port: Int,
        requestedMode: RuntimeMode,
        rawConfig: String?,
        isByoProxy: Boolean = false,
        camouflageFingerprint: String? = null,
        quotaLimitBytes: Long = -1L,
        quotaBaselineBytes: Long = 0L,
    ) {
        if (VpnManager.runtimeStatus.value == RuntimeStatus.RUNNING ||
            VpnManager.runtimeStatus.value == RuntimeStatus.STARTING
        ) {
            return
        }

        // A fresh connect attempt supersedes any pending one-shot
        // network-availability retry (R2): cancel it so it cannot double-fire.
        cancelPendingConnectOnUsableNetwork("connect_started")
        VpnManager.setRuntimeMode(requestedMode)
        VpnManager.resetUsage()
        VpnManager.clearError()
        VpnManager.clearRuntimeDiagnostics()
        stoppedByUser = false
        // A fresh user-initiated full-tunnel attempt is allowed one proxy fallback again.
        if (requestedMode == RuntimeMode.FULL_TUNNEL) {
            fellBackToProxy = false
        }
        // Preserve the chosen camouflage fingerprint across internal reconnect paths that re-enter
        // startVpn without re-supplying it (they pass null): inherit the prior session's value, while
        // a fresh explicit value (user/VM connect) still takes precedence.
        val effectiveCamouflageFingerprint = camouflageFingerprint ?: activeSession?.camouflageFingerprint
        activeSession = ActiveSession(host, port, requestedMode, rawConfig, isByoProxy, effectiveCamouflageFingerprint, quotaLimitBytes, quotaBaselineBytes)
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
                // Pre-flight network gate: classify the active network and surface a
                // visible state WITHOUT starting the engine when it is unusable. This
                // prevents both the offline crash and the native SIGSEGV from launching
                // Xray/tun2socks against a dead underlying network, and refuses to start
                // on a captive/unvalidated network (which cannot carry real traffic).
                // R3: only NONE blocks the connect. NOT_VALIDATED no longer blocks —
                // the probe decides. We remember it was unvalidated so a probe failure
                // surfaces the captive (NETWORK_NOT_VALIDATED) message instead of
                // NO_TRAFFIC. A working-but-slow-to-validate network connects fine
                // (probe succeeds); a captive portal proceeds then fails with the
                // captive message.
                when (classifyActiveNetwork()) {
                    NetworkClassifier.NetworkClass.NONE -> {
                        startedOnUnvalidatedNetwork = false
                        logRuntimeEvent("startup_no_network", mapOf("mode" to requestedMode.name))
                        updateRuntimeStatus(RuntimeStatus.NO_NETWORK, requestedMode, cause = DisconnectCause.NETWORK_LOST)
                        // R2: re-trigger the connect automatically once a usable network appears.
                        registerPendingConnectOnUsableNetwork()
                        return@launch
                    }
                    NetworkClassifier.NetworkClass.NOT_VALIDATED -> {
                        startedOnUnvalidatedNetwork = true
                        logRuntimeEvent("startup_network_not_validated", mapOf("mode" to requestedMode.name))
                    }
                    NetworkClassifier.NetworkClass.USABLE -> {
                        startedOnUnvalidatedNetwork = false
                    }
                }

                // Geo bypass (OFF by default): when ON, LAN/private space + the user's direct list
                // (domains/IPs) are routed direct (outside the tunnel) via Xray routing rules.
                // Read at connect time.
                val bypassPrefs = PreferencesManager(applicationContext)
                val bypassGeo = bypassPrefs.bypassGeoEnabledFlow.first()
                val routingOptions = if (bypassGeo) {
                    val (directDomains, directIps) =
                        XrayRoutingBuilder.partitionDirectEntries(bypassPrefs.bypassGeoEntriesFlow.first())
                    RoutingOptions(
                        bypassGeo = true,
                        directDomains = directDomains,
                        directIps = RoutingOptions.DEFAULT_DIRECT_IPS + directIps,
                    )
                } else {
                    RoutingOptions()
                }
                val runtime = rawConfig?.takeIf { it.isNotBlank() }?.let {
                    TunnelRuntimeAdapter.prepareRuntimeFromRawConfig(
                        rawConfig = it,
                        sourceType = SourceType.BACKEND_API,
                        runtimeMode = requestedMode,
                        routingOptions = routingOptions,
                        camouflageFingerprint = activeSession?.camouflageFingerprint,
                    ).getOrElse { error ->
                        throw IllegalStateException(
                            "Invalid runtime config: ${error.localizedMessage}",
                            error,
                        )
                    }
                } ?: throw IllegalStateException("Missing runtime config for VPN session")

                when (requestedMode) {
                    // LOCAL_PROXY is retired (B1/B2): it never routed device traffic (no VpnService
                    // tun, no setHttpProxy), so it could report "connected" while leaking. Any
                    // LOCAL_PROXY request — including a stale persisted preference — now runs the
                    // real FULL_TUNNEL data plane. See docs/LOCAL_PROXY_ANALYSIS.md.
                    RuntimeMode.FULL_TUNNEL, RuntimeMode.LOCAL_PROXY ->
                        startTunnelInterface(runtime, host, port)
                    RuntimeMode.SPLIT_TUNNEL -> {
                        throw IllegalStateException("Split tunnel is not available yet")
                    }
                }
            } catch (e: CancellationException) {
                logRuntimeEvent("startup_cancelled", mapOf("reason" to (e.message ?: "cancelled")))
                throw e
            } catch (e: Exception) {
                Log.e("SwimVpnService", "Error starting VPN", e)
                // Every startup failure must become a visible state, never a naked
                // throw. The policy guarantees shouldReportFailure for any
                // non-cancellation error, so there is no re-throw path here.
                // A StartupHealthException already carries an explicit cause (e.g.
                // NO_TRAFFIC) so it bypasses keyword classification entirely.
                val cause = if (e is StartupHealthException) {
                    e.disconnectCause
                } else {
                    RuntimeStartupFailurePolicy.classify(e).cause
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
                CrashReporter.recordVpnFailure(stage = "startup", cause = cause.name, throwable = e)

                // OEM hardening: if the full-tunnel data plane failed for a tunnel-infrastructure
                // reason (establish()/tun2socks), degrade once to LOCAL_PROXY instead of leaving the
                // user with nothing. Network/server/config/user causes are excluded by the policy.
                // The retry is deferred to finally so it is launched after this job's handle is
                // cleared (never overwriting activeStartupJob).
                if (TunnelFallbackPolicy.shouldFallbackToProxy(
                        requestedMode = requestedMode,
                        cause = cause,
                        stoppedByUser = stoppedByUser,
                        alreadyFellBack = fellBackToProxy,
                    )
                ) {
                    fellBackToProxy = true
                    pendingProxyFallback = Triple(host, port, rawConfig)
                    logRuntimeEvent("tunnel_fallback_to_proxy", mapOf("from_cause" to cause.name))
                    AdaptiveEventLogger.log(
                        event = "tunnel_fallback_to_proxy",
                        details = mapOf("from_cause" to cause.name),
                    )
                } else {
                    setRuntimeError(localizedContextFor(notificationLanguage).getString(R.string.vpn_err_connection_failed, e.localizedMessage), cause)
                    stopVpn(clearRuntimeState = false, reason = "startup_failure", cause = cause)
                }
            } finally {
                activeStartupJob = null
                // Launch the proxy fallback (if armed) only after the failed startup job's handle
                // is cleared, so the fresh attempt owns activeStartupJob cleanly.
                pendingProxyFallback?.let { (fallbackHost, fallbackPort, fallbackConfig) ->
                    pendingProxyFallback = null
                    updateRuntimeStatus(RuntimeStatus.RECONNECTING, RuntimeMode.LOCAL_PROXY)
                    serviceScope.launch {
                        startVpn(
                            host = fallbackHost,
                            port = fallbackPort,
                            requestedMode = RuntimeMode.LOCAL_PROXY,
                            rawConfig = fallbackConfig,
                        )
                    }
                }
            }
        }
    }

    private suspend fun startTunnelInterface(
        runtime: TunnelRuntimeAdapter.RuntimePreparationResult,
        host: String,
        port: Int,
    ) {
        // Register the network callback BEFORE establishing the tunnel so that a
        // network loss during startup is observed (and not masked by the VPN
        // interface becoming the active network).
        registerNetworkCallback()
        val tun2SocksAvailability = Tun2SocksAssetCatalog.availability(applicationContext)
        startValidatedXrayRuntime(
            runtime = runtime,
            failurePrefix = "Xray tunnel runtime exited before tun2socks could be armed",
        )
        logRuntimeEvent("engine_started", mapOf("mode" to RuntimeMode.FULL_TUNNEL.name))

        val builder = Builder()
            .setSession("SWIMVPN+ (${runtime.profile.displayName})")
            .addAddress("10.0.0.2", 24)
            // IPv4-only tun. The IPv6 capture (an fd00:2::2 address + ::/0 route, added to close an
            // IPv6 leak) is reverted: advertising IPv6 on the tun makes the OS treat the device as
            // IPv6-capable, so dual-stack apps attempt IPv6 — which the supplier nodes can't egress →
            // the packets blackhole instead of failing fast → "connected but no internet" on named
            // (dual-stack) sites while IPv4 literals still work. The leak-closure must be redone so it
            // FAST-REJECTS IPv6 (RST, letting Happy Eyeballs fall back) rather than blackholing.
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
            // establish() failed: reap the already-started Xray process so it does
            // not leak as an orphan, then surface a clean failure. tun2socks has
            // not been started yet, so there is no native data plane to stop here.
            activeXraySessionId?.let { sessionId -> runCatching { xrayBridge.stop(sessionId) } }
            runCatching { xrayBridge.stopAll() }
            activeXraySessionId = null
            // H4: carry the cause explicitly. StartupHealthException bypasses the fragile keyword
            // classifier (the old message happened to match "permission" -> SERVICE_KILLED), so the
            // cause no longer depends on the message wording/localization. Cause kept as-is
            // (SERVICE_KILLED) to preserve recovery behavior; refine on-device if warranted.
            throw StartupHealthException(
                "Failed to establish the VPN tunnel interface",
                DisconnectCause.SERVICE_KILLED,
            )
        }

        // Guard the tun fd: never hand an invalid/closed descriptor to native
        // code (tun2socks) — that path risks a SIGSEGV. Transition to a clean
        // FAILED state instead. -1 indicates a closed/invalid descriptor.
        val tunFd = vpnInterface?.fd?.takeIf { it >= 0 }
            ?: throw IllegalStateException("VPN interface fd is unavailable or invalid")
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
                        CrashReporter.recordVpnFailure(stage = "tun2socks_bridge", cause = "ENGINE_CRASH", throwable = error)
                        if (VpnManager.runtimeStatus.value == RuntimeStatus.RUNNING) {
                            setRuntimeError(localizedContextFor(notificationLanguage).getString(R.string.vpn_err_tun2socks_failed, error.localizedMessage), DisconnectCause.ENGINE_CRASH)
                            scheduleReconnect(DisconnectCause.ENGINE_CRASH, "tun2socks_failure")
                        }
                        return@launch
                    }

                    preparedRuntime.exitStateFile.writeText(exitCode.toString())
                    if (VpnManager.runtimeStatus.value == RuntimeStatus.RUNNING) {
                        Log.w("SwimVpnService", "tun2socks exited unexpectedly with code $exitCode")
                        setRuntimeError(localizedContextFor(notificationLanguage).getString(R.string.vpn_err_tun2socks_exit, exitCode.toString()), DisconnectCause.ENGINE_CRASH)
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
            socksPort = runtime.ports.socksPort,
            serverHost = host,
            serverPort = port,
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
        // Network callback already registered before establish() (see above).
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
        // Register the network callback BEFORE arming the proxy runtime so that a
        // network loss during startup is observed.
        registerNetworkCallback()
        startValidatedXrayRuntime(
            runtime = runtime,
            failurePrefix = "Xray local proxy exited before becoming ready",
        )
        logRuntimeEvent("engine_started", mapOf("mode" to RuntimeMode.LOCAL_PROXY.name))

        awaitStartupHealthProof(
            mode = RuntimeMode.LOCAL_PROXY,
            requireTun2Socks = false,
            socksPort = runtime.ports.socksPort,
            serverHost = host,
            serverPort = port,
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
        // Network callback already registered before arming the proxy (see above).
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
        // A teardown cancels any armed proxy fallback so a stopped session never auto-restarts.
        pendingProxyFallback = null
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
            // R2: a teardown supersedes any pending one-shot network-availability retry.
            cancelPendingConnectOnUsableNetwork("stop_vpn")
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
            // TEARDOWN ORDER: native engines (Xray + tun2socks) MUST be stopped
            // BEFORE closing the tun fd. Closing the fd first would leave native
            // code reading/writing a dead descriptor (SIGSEGV/SIGPIPE).
            activeXraySessionId?.let { sessionId ->
                xrayBridge.stop(sessionId)
            }
            activeXraySessionId = null
            xrayBridge.stopAll()
        } catch (e: Exception) {
            Log.e("SwimVpnService", "Error stopping native runtime during teardown", e)
        } finally {
            // Close the tun fd only after native engines are stopped (above).
            runCatching { vpnInterface?.close() }
                .onFailure { error -> Log.e("SwimVpnService", "Error closing VPN interface", error) }
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
                getString(R.string.app_name),
                NotificationManager.IMPORTANCE_LOW,
            ).apply {
                description = getString(R.string.app_name)
                setShowBadge(false)
            }

            val notificationManager =
                getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            notificationManager.createNotificationChannel(channel)
        }
    }

    private fun createNotification(): Notification {
        val content = localizedNotificationContent()
        return NotificationCompat.Builder(this, channelId)
            .setContentTitle(content.title)
            .setContentText(content.text)
            .setStyle(NotificationCompat.BigTextStyle().bigText(content.text))
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

    private fun localizedNotificationContent(): VpnNotificationContent {
        val localizedContext = localizedContextFor(notificationLanguage)
        val status = VpnManager.runtimeStatus.value
        val mode = VpnManager.runtimeMode.value
        val modeLabel = localizedNotificationModeLabel(localizedContext, mode)
        val stateLabel = when (status) {
            RuntimeStatus.IDLE,
            RuntimeStatus.STOPPED_BY_USER -> localizedContext.getString(R.string.status_disconnected)
            RuntimeStatus.STARTING -> localizedContext.getString(R.string.status_connecting)
            RuntimeStatus.RUNNING -> localizedContext.getString(R.string.status_connected)
            RuntimeStatus.RECONNECTING -> localizedContext.getString(R.string.vpn_notification_reconnecting_title)
            RuntimeStatus.DEGRADED -> localizedContext.getString(R.string.vpn_notification_degraded_title)
            // NO_NETWORK is honestly shown as not-connected (no underlying network),
            // never as Connected. Reuses existing disconnected label (no new asset).
            RuntimeStatus.NO_NETWORK -> localizedContext.getString(R.string.status_no_network)
            RuntimeStatus.STOPPING -> localizedContext.getString(R.string.status_disconnecting)
            RuntimeStatus.FAILED -> localizedContext.getString(R.string.status_error)
        }

        val text = when (status) {
            RuntimeStatus.IDLE -> localizedContext.getString(R.string.vpn_notification_disconnected)
            RuntimeStatus.STARTING -> localizedContext.getString(R.string.vpn_notification_starting, modeLabel)
            RuntimeStatus.RUNNING -> localizedContext.getString(R.string.vpn_notification_connected, modeLabel)
            RuntimeStatus.RECONNECTING -> localizedContext.getString(R.string.vpn_notification_reconnecting)
            RuntimeStatus.DEGRADED -> localizedContext.getString(R.string.vpn_notification_degraded)
            // No active network: reuse the degraded body (network unavailable),
            // making the state visible without inventing a missing string asset.
            RuntimeStatus.NO_NETWORK -> localizedContext.getString(R.string.vpn_notification_no_network)
            RuntimeStatus.STOPPING -> localizedContext.getString(R.string.vpn_notification_stopping)
            RuntimeStatus.FAILED -> VpnManager.errorMessage.value
                ?.takeIf { it.isNotBlank() }
                ?: localizedContext.getString(R.string.vpn_notification_failed)
            RuntimeStatus.STOPPED_BY_USER -> localizedContext.getString(R.string.vpn_notification_stopped_by_user)
        }

        return VpnNotificationContent(
            title = localizedContext.getString(R.string.vpn_notification_title, stateLabel),
            text = text,
        )
    }

    private fun localizedNotificationModeLabel(context: Context, mode: RuntimeMode): String {
        return when (mode) {
            RuntimeMode.FULL_TUNNEL -> context.getString(R.string.vpn_notification_mode_tunnel)
            RuntimeMode.LOCAL_PROXY -> context.getString(R.string.vpn_notification_mode_proxy)
            RuntimeMode.SPLIT_TUNNEL -> context.getString(R.string.vpn_notification_mode_split_tunnel)
        }
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
                // R2: prevent the one-shot retry callback from leaking past destroy.
                cancelPendingConnectOnUsableNetwork("service_destroyed")
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
        // Revoked is a deliberate user/system action, NOT a crash: surface it as a
        // distinct PERMISSION_REVOKED cause, cancel any pending auto-reconnect so we
        // do not fight the revocation, then perform a terminal stop (no auto-retry).
        cancelPendingAutoReconnect("permission_revoked")
        setRuntimeError(localizedContextFor(notificationLanguage).getString(R.string.vpn_err_permission_revoked), DisconnectCause.PERMISSION_REVOKED)
        stopVpn(clearRuntimeState = false, reason = "vpn_revoked", cause = DisconnectCause.PERMISSION_REVOKED)
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

    private suspend fun awaitStartupHealthProof(
        mode: RuntimeMode,
        requireTun2Socks: Boolean,
        socksPort: Int,
        serverHost: String,
        serverPort: Int,
    ) {
        delay(STARTUP_HEALTH_PROOF_DELAY_MS)

        val xraySessionId = activeXraySessionId
        val xraySnapshot = xraySessionId?.let { xrayBridge.snapshot(it) }
        val xrayAlive = xraySessionId != null && xraySnapshot?.isAlive == true
        val tun2SocksAlive = activeTun2SocksContract != null && activeTun2SocksJob?.isActive == true

        // ACTIVE PROBE: only after the engine liveness check passes do we confirm the
        // tunnel actually carries traffic, by opening a short TCP connection THROUGH
        // the local SOCKS proxy to the VPN server itself. 127.0.0.1 is localhost (not
        // routed through the tun), so there is no recursion and no protect() needed.
        val engineLive = xrayAlive && (!requireTun2Socks || tun2SocksAlive)
        val trafficConfirmed = if (engineLive) {
            probeTrafficThroughProxy(socksPort, serverHost, serverPort)
        } else {
            false
        }

        if (!RuntimeStartupHealthPolicy.canMarkRunning(
                xrayAlive = xrayAlive,
                requireTun2Socks = requireTun2Socks,
                tun2SocksAlive = tun2SocksAlive,
                trafficConfirmed = trafficConfirmed,
            )
        ) {
            val xrayExit = xraySnapshot?.exitCode?.toString() ?: "unknown"
            val missingDataPlane = requireTun2Socks && !tun2SocksAlive
            // No traffic is a distinct failure: the engine is alive but the tunnel
            // cannot reach the server. Tag it explicitly with NO_TRAFFIC so it never
            // relies on keyword classification and never surfaces as a fake Connected.
            if (!engineLive) {
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

            // R3: when the connect started on an unvalidated (captive/no-internet)
            // network, a probe failure is almost certainly the captive portal — surface
            // the captive cause so the user gets the right message. Otherwise the engine
            // simply could not carry traffic: keep NO_TRAFFIC.
            val probeFailureCause = if (startedOnUnvalidatedNetwork) {
                DisconnectCause.NETWORK_NOT_VALIDATED
            } else {
                DisconnectCause.NO_TRAFFIC
            }
            val reason = "Tunnel established but no traffic reached the server through the proxy"
            logRuntimeEvent(
                "startup_health_failed",
                mapOf("mode" to mode.name, "reason" to reason, "cause" to probeFailureCause.name),
            )
            throw StartupHealthException(reason, probeFailureCause)
        }
    }

    /**
     * Performs a short SOCKS-tunneled TCP connect to the VPN server to prove the
     * data plane carries traffic. Allows one retry within a small total budget.
     * Returns true only on a successful connect.
     */
    private suspend fun probeTrafficThroughProxy(
        socksPort: Int,
        serverHost: String,
        serverPort: Int,
    ): Boolean {
        if (serverHost.isBlank() || serverHost == "unknown" || serverPort <= 0) {
            // R1: a blank/unknown host or non-positive port is a config problem, not a
            // proof of traffic. Never publish RUNNING without proof — fail startup with a
            // visible CONFIG_INVALID state instead of silently returning true.
            val reason = "Cannot prove traffic: server endpoint is missing or invalid ($serverHost:$serverPort)"
            logRuntimeEvent(
                "startup_health_failed",
                mapOf("reason" to reason, "cause" to DisconnectCause.CONFIG_INVALID.name),
            )
            throw StartupHealthException(reason, DisconnectCause.CONFIG_INVALID)
        }
        val proxy = java.net.Proxy(
            java.net.Proxy.Type.SOCKS,
            java.net.InetSocketAddress("127.0.0.1", socksPort),
        )
        repeat(2) { attempt ->
            val success = runCatching {
                java.net.Socket(proxy).use { socket ->
                    socket.connect(
                        java.net.InetSocketAddress(serverHost, serverPort),
                        TRAFFIC_PROBE_TIMEOUT_MS,
                    )
                }
            }.isSuccess
            if (success) return true
            if (attempt == 0) delay(TRAFFIC_PROBE_RETRY_DELAY_MS)
        }
        return false
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
            val monitorStartedAt = System.currentTimeMillis()
            var trafficStallReported = false
            while (VpnManager.runtimeStatus.value == RuntimeStatus.RUNNING) {
                val xraySessionId = activeXraySessionId
                val xraySnapshot = xraySessionId?.let { xrayBridge.snapshot(it) }
                if (xraySessionId == null || xraySnapshot == null || !xraySnapshot.isAlive) {
                    val exitCode = xraySnapshot?.exitCode?.toString() ?: "unknown"
                    Log.w("SwimVpnService", "Xray process is not alive for mode=$mode exitCode=$exitCode")
                    logRuntimeEvent("engine_crashed", mapOf("engine" to "xray", "exitCode" to exitCode))
                    setRuntimeError(localizedContextFor(notificationLanguage).getString(R.string.vpn_err_xray_stopped, exitCode), DisconnectCause.ENGINE_CRASH)
                    scheduleReconnect(DisconnectCause.ENGINE_CRASH, "xray_not_alive_$exitCode")
                    return@launch
                }

                if (requireTun2Socks) {
                    val tun2SocksJob = activeTun2SocksJob
                    val tun2SocksContract = activeTun2SocksContract
                    if (tun2SocksJob == null || tun2SocksContract == null || !tun2SocksJob.isActive) {
                        Log.w("SwimVpnService", "tun2socks monitor detected inactive data plane")
                        logRuntimeEvent("engine_crashed", mapOf("engine" to "tun2socks"))
                        setRuntimeError(localizedContextFor(notificationLanguage).getString(R.string.vpn_err_tun2socks_stopped), DisconnectCause.ENGINE_CRASH)
                        scheduleReconnect(DisconnectCause.ENGINE_CRASH, "tun2socks_not_alive")
                        return@launch
                    }
                }

                // QUOTA CUTOFF: stop immediately (no auto-reconnect) when the sold-quota
                // limit is reached. QuotaCutoffPolicy.isExhausted is fail-open: returns
                // false when limitBytes <= 0 (unmetered / not set).
                val qLimit = activeSession?.quotaLimitBytes ?: -1L
                if (QuotaCutoffPolicy.isExhausted(
                        limitBytes = qLimit,
                        baselineBytes = activeSession?.quotaBaselineBytes ?: 0L,
                        sessionBytes = VpnManager.bytesIn.value + VpnManager.bytesOut.value,
                    )
                ) {
                    logRuntimeEvent("quota_exhausted", mapOf("limit" to qLimit.toString()))
                    stoppedByUser = true // reuse existing anti-reconnect guard — scheduleReconnect checks this first
                    setRuntimeError(
                        localizedContextFor(notificationLanguage).getString(R.string.vpn_err_quota_exhausted),
                        DisconnectCause.QUOTA_EXHAUSTED,
                    )
                    stopVpn(clearRuntimeState = false, reason = "quota_exhausted", cause = DisconnectCause.QUOTA_EXHAUSTED)
                    return@launch
                }

                // PASSIVE WATCHDOG: a "zombie" tunnel keeps the engine alive and sends
                // outbound bytes but never receives any. The policy demotes only when
                // bytesOut > 0 && bytesIn == 0 past the threshold; a genuinely idle
                // session (0/0) is never demoted. Demote once to DEGRADED (NO_TRAFFIC)
                // rather than killing the session, so the UI shows UNSTABLE.
                if (!trafficStallReported &&
                    RuntimeStartupHealthPolicy.isTrafficStalled(
                        bytesIn = VpnManager.bytesIn.value,
                        bytesOut = VpnManager.bytesOut.value,
                        elapsedMs = System.currentTimeMillis() - monitorStartedAt,
                        thresholdMs = if (activeSession?.isByoProxy == true) BYO_PROXY_STALL_THRESHOLD_MS else TRAFFIC_STALL_THRESHOLD_MS,
                    )
                ) {
                    trafficStallReported = true
                    Log.w("SwimVpnService", "Traffic stalled (outbound only, no inbound) for mode=$mode")
                    logRuntimeEvent(
                        "traffic_stalled",
                        mapOf("mode" to mode.name, "cause" to DisconnectCause.NO_TRAFFIC.name),
                    )
                    // BYO residential proxy stalled = the user's proxy likely died. Surface a
                    // proxy-specific message (UNSTABLE shows errorMessage) instead of generic.
                    if (activeSession?.isByoProxy == true) {
                        VpnManager.setError(getString(R.string.proxy_session_down))
                    }
                    updateRuntimeStatus(RuntimeStatus.DEGRADED, mode, cause = DisconnectCause.NO_TRAFFIC)
                    // R5: DEGRADED(NO_TRAFFIC) must not be a dead-end. Engage the existing
                    // recovery path so the session either recovers or fails cleanly,
                    // instead of sitting in DEGRADED with this loop exited and nothing
                    // monitoring. The loop condition above requires RUNNING, so this
                    // monitor exits on the next iteration; scheduleReconnect takes over.
                    scheduleReconnect(DisconnectCause.NO_TRAFFIC, "traffic_stalled")
                    return@launch
                }

                // Tighter poll interval reduces dead-engine detection latency so a
                // crashed engine is reflected as a visible state quickly (instead
                // of lingering up to 2s as "Connected").
                delay(LIVENESS_POLL_INTERVAL_MS)
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
        updateNotification()
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
        updateNotification()
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

    // R2: after a NO_NETWORK pre-flight refusal, watch for the first usable network and
    // re-trigger the connect for the last requested session, then self-unregister. A
    // manual connect or stop cancels it first (see cancelPendingConnectOnUsableNetwork).
    private fun registerPendingConnectOnUsableNetwork() {
        if (pendingConnectNetworkCallback != null) return
        if (activeSession == null) return
        val connectivityManager = getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        val callback = object : ConnectivityManager.NetworkCallback() {
            override fun onAvailable(network: Network) {
                val capabilities = connectivityManager.getNetworkCapabilities(network)
                if (!isUsableUnderlyingNetwork(capabilities)) return
                fire(connectivityManager)
            }

            override fun onCapabilitiesChanged(network: Network, networkCapabilities: NetworkCapabilities) {
                if (!isUsableUnderlyingNetwork(networkCapabilities)) return
                fire(connectivityManager)
            }
        }
        pendingConnectNetworkCallback = callback
        val request = NetworkRequest.Builder()
            .addCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
            .addCapability(NetworkCapabilities.NET_CAPABILITY_NOT_VPN)
            .build()
        val registered = runCatching { connectivityManager.registerNetworkCallback(request, callback) }
            .onFailure { error -> Log.w("SwimVpnService", "Unable to register pending-connect network callback", error) }
            .isSuccess
        if (!registered) {
            pendingConnectNetworkCallback = null
            return
        }
        logRuntimeEvent("pending_connect_network_registered")
    }

    // Fires exactly once: unregisters itself, then re-triggers the connect for the last
    // requested session. Guards against re-entrancy via the null check on the field.
    private fun fire(connectivityManager: ConnectivityManager) {
        val callback = pendingConnectNetworkCallback ?: return
        runCatching { connectivityManager.unregisterNetworkCallback(callback) }
        pendingConnectNetworkCallback = null
        val session = activeSession ?: return
        if (stoppedByUser || VpnManager.runtimeStatus.value == RuntimeStatus.STOPPED_BY_USER) return
        logRuntimeEvent("pending_connect_network_available", mapOf("mode" to session.requestedMode.name))
        startVpn(
            host = session.host,
            port = session.port,
            requestedMode = session.requestedMode,
            rawConfig = session.rawConfig,
            isByoProxy = session.isByoProxy,
            quotaLimitBytes = session.quotaLimitBytes,
            quotaBaselineBytes = session.quotaBaselineBytes,
        )
    }

    private fun cancelPendingConnectOnUsableNetwork(reason: String) {
        val callback = pendingConnectNetworkCallback ?: return
        val connectivityManager = getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        runCatching { connectivityManager.unregisterNetworkCallback(callback) }
        pendingConnectNetworkCallback = null
        logRuntimeEvent("pending_connect_network_cancelled", mapOf("reason" to reason))
    }

    private fun hasActiveNetwork(): Boolean {
        return runCatching {
            val connectivityManager = getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
            val network = connectivityManager.activeNetwork ?: return false
            val capabilities = connectivityManager.getNetworkCapabilities(network)
            isUsableUnderlyingNetwork(capabilities)
        }.getOrDefault(false)
    }

    // Pre-flight classification of the current active network. Used only by the
    // startVpn gate; network-handoff continues to use isUsableUnderlyingNetwork().
    private fun classifyActiveNetwork(): NetworkClassifier.NetworkClass {
        return runCatching {
            val connectivityManager = getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
            val network = connectivityManager.activeNetwork
                ?: return NetworkClassifier.NetworkClass.NONE
            val capabilities = connectivityManager.getNetworkCapabilities(network)
                ?: return NetworkClassifier.NetworkClass.NONE
            NetworkClassifier.classify(
                hasInternet = capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET),
                notVpn = capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_NOT_VPN),
                hasUsableTransport = capabilities.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) ||
                    capabilities.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR) ||
                    capabilities.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET),
                validated = capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED),
            )
        }.getOrDefault(NetworkClassifier.NetworkClass.NONE)
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

    private fun cancelPendingAutoReconnect(reason: String) {
        val hadPending = activeReconnectJob != null || activeNetworkHandoffJob != null
        activeReconnectJob?.cancel()
        activeReconnectJob = null
        activeReconnectCause = null
        activeReconnectStarted = false
        activeNetworkHandoffJob?.cancel()
        activeNetworkHandoffJob = null
        reconnectAttempt = 0
        if (hadPending) {
            logRuntimeEvent("auto_reconnect_cancelled", mapOf("reason" to reason))
        }
    }

    private fun scheduleReconnect(cause: DisconnectCause, reason: String) {
        if (stoppedByUser || VpnManager.runtimeStatus.value == RuntimeStatus.STOPPED_BY_USER) {
            logRuntimeEvent("stopped_by_user", mapOf("skipReconnectReason" to reason))
            return
        }

        // A revoked permission is terminal: never auto-reconnect against it, otherwise
        // we would loop fighting the system's revocation.
        if (cause == DisconnectCause.PERMISSION_REVOKED) {
            logRuntimeEvent("reconnect_skipped", mapOf("reason" to reason, "cause" to cause.name))
            return
        }

        val session = activeSession
        if (session?.rawConfig.isNullOrBlank()) {
            logRuntimeEvent("reconnect_failed", mapOf("reason" to "missing_active_session", "cause" to cause.name))
            stopVpn(clearRuntimeState = false, reason = reason, cause = cause)
            return
        }
        val reconnectSession = session ?: return

        val maxAttempts = if (reconnectSession.isByoProxy) MAX_BYO_PROXY_RECONNECT_ATTEMPTS else MAX_SERVICE_RECONNECT_ATTEMPTS
        if (reconnectAttempt >= maxAttempts) {
            logRuntimeEvent(
                "reconnect_failed",
                mapOf("reason" to "max_attempts", "cause" to cause.name, "byoProxy" to reconnectSession.isByoProxy.toString()),
            )
            stopVpn(clearRuntimeState = false, reason = reason, cause = cause)
            // A dead BYO residential proxy: say so plainly (and where to fix it). Set AFTER stopVpn
            // so it is the final error surfaced to the UI.
            if (reconnectSession.isByoProxy) {
                VpnManager.setError(getString(R.string.proxy_session_down))
            }
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
                    isByoProxy = reconnectSession.isByoProxy,
                    quotaLimitBytes = reconnectSession.quotaLimitBytes,
                    quotaBaselineBytes = reconnectSession.quotaBaselineBytes,
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
