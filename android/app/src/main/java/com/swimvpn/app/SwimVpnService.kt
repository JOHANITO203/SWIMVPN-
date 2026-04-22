package com.swimvpn.app

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.content.Intent
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
import com.swimvpn.app.runtime.XrayProcessBridge
import com.swimvpn.app.vpn.RuntimeMode
import com.swimvpn.app.vpn.RuntimeStatus
import com.swimvpn.app.vpn.VpnManager
import java.io.File
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

class SwimVpnService : VpnService() {

    private enum class Tun2SocksAvailability {
        AVAILABLE,
        MISSING,
    }

    private data class Tun2SocksContract(
        val availability: Tun2SocksAvailability,
        val executableFile: File?,
        val probePaths: List<String>,
        val localSocksHost: String,
        val localSocksPort: Int,
        val tunMtu: Int,
    ) {
        val isAvailable: Boolean
            get() = availability == Tun2SocksAvailability.AVAILABLE && executableFile != null
    }

    private var vpnInterface: ParcelFileDescriptor? = null
    private var activeXraySessionId: String? = null
    private val serviceJob = Job()
    private val serviceScope = CoroutineScope(Dispatchers.IO + serviceJob)
    private val xrayBridge by lazy { XrayProcessBridge(applicationContext) }

    private val channelId = "swim_vpn_status"
    private val notificationId = 1

    companion object {
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
        VpnManager.updateRuntimeStatus(RuntimeStatus.STARTING)

        serviceScope.launch {
            try {
                val runtime = rawConfig?.takeIf { it.isNotBlank() }?.let {
                    TunnelRuntimeAdapter.prepareRuntimeFromRawConfig(
                        rawConfig = it,
                        sourceType = SourceType.BACKEND_API,
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
                VpnManager.setError("Connection failed: ${e.localizedMessage}")
                stopVpn(clearRuntimeState = false)
            }
        }
    }

    private suspend fun startTunnelInterface(
        runtime: TunnelRuntimeAdapter.RuntimePreparationResult,
        host: String,
        port: Int,
    ) {
        val tun2SocksContract = resolveTun2SocksContract(runtime)

        vpnInterface = Builder()
            .setSession("SWIMVPN+ (${runtime.profile.displayName})")
            .addAddress("10.0.0.2", 24)
            .addRoute("0.0.0.0", 0)
            .addDnsServer("8.8.8.8")
            .addDnsServer("1.1.1.1")
            .setMtu(1500)
            .establish()

        if (vpnInterface == null) {
            throw IllegalStateException("Failed to establish VPN interface. Permission missing?")
        }

        VpnManager.markStarted()
        delay(350)
        VpnManager.markHandshake()
        VpnManager.updateRuntimeStatus(RuntimeStatus.RUNNING)

        val runtimeMessage = if (tun2SocksContract.isAvailable) {
            "Tunnel interface ready; tun2socks detected, native data plane wiring pending"
        } else {
            "Tunnel interface ready; tun2socks is not packaged yet, running transitional mode"
        }
        updateNotification(runtimeMessage)

        Log.i(
            "SwimVpnService",
            buildString {
                append("Prepared tunnel interface for ${runtime.profile.protocol} ${runtime.summary} via $host:$port")
                append(" | tun2socks=")
                append(tun2SocksContract.availability.name)
                append(" | socks=")
                append("${tun2SocksContract.localSocksHost}:${tun2SocksContract.localSocksPort}")
                append(" | mtu=")
                append(tun2SocksContract.tunMtu)
                if (!tun2SocksContract.isAvailable) {
                    append(" | probes=")
                    append(tun2SocksContract.probePaths.joinToString())
                }
            },
        )
    }

    private suspend fun startLocalProxy(
        runtime: TunnelRuntimeAdapter.RuntimePreparationResult,
        host: String,
        port: Int,
    ) {
        val preparedRuntime = xrayBridge.prepare(runtime.runtimeConfig)
        val running = xrayBridge.start(preparedRuntime)
        activeXraySessionId = running.sessionId()

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
                    append("Xray local proxy exited before becoming ready")
                    if (!stderrTail.isNullOrBlank()) {
                        append(": ")
                        append(stderrTail)
                    }
                }
            )
        }

        VpnManager.markHandshake()
        VpnManager.updateRuntimeStatus(RuntimeStatus.RUNNING)
        updateNotification("Local proxy ready: 127.0.0.1:${runtime.ports.socksPort}")

        Log.i(
            "SwimVpnService",
            "Started local proxy for ${runtime.profile.protocol} ${runtime.summary} via $host:$port",
        )
    }

    private fun stopVpn(clearRuntimeState: Boolean = true) {
        if (clearRuntimeState &&
            (VpnManager.runtimeStatus.value == RuntimeStatus.IDLE ||
                VpnManager.runtimeStatus.value == RuntimeStatus.STOPPING)
        ) {
            return
        }

        if (clearRuntimeState) {
            VpnManager.updateRuntimeStatus(RuntimeStatus.STOPPING)
        }

        try {
            activeXraySessionId?.let { sessionId ->
                xrayBridge.stop(sessionId)
            }
            activeXraySessionId = null
            vpnInterface?.close()
        } catch (e: Exception) {
            Log.e("SwimVpnService", "Error closing VPN interface", e)
        } finally {
            vpnInterface = null
            if (clearRuntimeState) {
                VpnManager.updateRuntimeStatus(RuntimeStatus.IDLE)
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
                activeXraySessionId?.let { sessionId ->
                    xrayBridge.stop(sessionId)
                }
                activeXraySessionId = null
                vpnInterface?.close()
            } catch (e: Exception) {
                Log.e("SwimVpnService", "Error closing VPN interface during failure cleanup", e)
            } finally {
                vpnInterface = null
            }
        }
    }

    private fun resolveTun2SocksContract(
        runtime: TunnelRuntimeAdapter.RuntimePreparationResult,
    ): Tun2SocksContract {
        val candidateFiles = buildList {
            val nativeLibDir = applicationInfo.nativeLibraryDir
            add(File(nativeLibDir, "libtun2socks.so"))
            add(File(nativeLibDir, "libhevsocks5.so"))
            add(File(nativeLibDir, "libhev-socks5-tunnel.so"))
            add(File(nativeLibDir, "tun2socks"))
            add(File(nativeLibDir, "hev-socks5-tunnel"))
            val assetRoot = File(applicationInfo.dataDir, "files/${BuildConfig.XRAY_ASSET_ROOT.removeSuffix("/xray")}/tun2socks")
            add(File(assetRoot, "tun2socks"))
            add(File(assetRoot, "hev-socks5-tunnel"))
        }

        val executable = candidateFiles.firstOrNull { it.exists() && it.isFile }
        return Tun2SocksContract(
            availability = if (executable != null) {
                Tun2SocksAvailability.AVAILABLE
            } else {
                Tun2SocksAvailability.MISSING
            },
            executableFile = executable,
            probePaths = candidateFiles.map { it.absolutePath },
            localSocksHost = "127.0.0.1",
            localSocksPort = runtime.ports.socksPort,
            tunMtu = 1500,
        )
    }
}
