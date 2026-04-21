package com.swimvpn.app

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.content.Intent
import android.net.VpnService
import android.os.Build
import android.os.ParcelFileDescriptor
import android.util.Log
import androidx.core.app.NotificationCompat
import com.swimvpn.app.vpn.VpnManager
import com.swimvpn.app.vpn.VpnState
import kotlinx.coroutines.*

class SwimVpnService : VpnService() {

    private var vpnInterface: ParcelFileDescriptor? = null
    private val serviceJob = Job()
    private val serviceScope = CoroutineScope(Dispatchers.IO + serviceJob)

    companion object {
        const val ACTION_START = "com.swimvpn.app.START_VPN"
        const val ACTION_STOP = "com.swimvpn.app.STOP_VPN"

        const val EXTRA_SERVER_HOST = "SERVER_HOST"
        const val EXTRA_SERVER_PORT = "SERVER_PORT"
        const val EXTRA_PROTOCOL = "PROTOCOL"
        const val EXTRA_URL = "SUBSCRIPTION_URL"
        const val EXTRA_DATA_LIMIT = "DATA_LIMIT_BYTES"
        const val EXTRA_DATA_USED = "DATA_USED_BYTES"
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val action = intent?.action

        when (action) {
            ACTION_START -> {
                val host = intent.getStringExtra(EXTRA_SERVER_HOST) ?: "unknown"
                val port = intent.getIntExtra(EXTRA_SERVER_PORT, 443)
                val limit = intent.getLongExtra(EXTRA_DATA_LIMIT, -1L)
                val used = intent.getLongExtra(EXTRA_DATA_USED, 0L)
                startVpn(host, port, limit, used)
            }
            ACTION_STOP -> {
                stopVpn()
            }
        }

        return START_NOT_STICKY
    }

    private fun startVpn(host: String, port: Int, limit: Long, initialUsed: Long) {
        if (VpnManager.state.value == VpnState.CONNECTED || VpnManager.state.value == VpnState.CONNECTING) {
            return
        }

        VpnManager.updateState(VpnState.CONNECTING)

        serviceScope.launch {
            try {
                // 1. Initialiser l'interface TUN d'Android
                val builder = Builder()
                    .setSession("SWIMVPN+ ($host)")
                    .addAddress("10.0.0.2", 24)
                    .addRoute("0.0.0.0", 0) // Route tout le trafic IPv4
                    .addDnsServer("8.8.8.8")
                    .addDnsServer("1.1.1.1")
                    .setMtu(1500)

                // 2. Établir l'interface (Fait apparaître la clé VPN dans la barre d'état)
                vpnInterface = builder.establish()

                if (vpnInterface == null) {
                    VpnManager.setError("Failed to establish VPN interface. Permission missing?")
                    stopSelf()
                    return@launch
                }

                // 3. Simuler le temps de connexion au serveur distant
                delay(1500)

                // ICI, DANS LA VRAIE IMPLÉMENTATION (HORS SCOPE LLM) :
                // On passe le file descriptor (vpnInterface?.fd) au moteur natif (ex: tun2socks / xray-core JNI)
                // nativeStartEngine(vpnInterface!!.fd, host, port, configUrl)

                Log.i("SwimVpnService", "VPN Tunnel established to $host:$port")
                VpnManager.updateState(VpnState.CONNECTED)

                // Start simulating data consumption
                simulateTraffic(limit, initialUsed)

            } catch (e: Exception) {
                Log.e("SwimVpnService", "Error starting VPN", e)
                VpnManager.setError("Connection failed: ${e.localizedMessage}")
                stopVpn()
            }
        }
    }

    private fun stopVpn() {
        if (VpnManager.state.value == VpnState.DISCONNECTED || VpnManager.state.value == VpnState.DISCONNECTING) return
        
        VpnManager.updateState(VpnState.DISCONNECTING)
        
        try {
            vpnInterface?.close()
        } catch (e: Exception) {
            Log.e("SwimVpnService", "Error closing VPN interface", e)
        } finally {
            vpnInterface = null
            VpnManager.updateState(VpnState.DISCONNECTED)
            stopSelf()
        }
    }

    private fun simulateTraffic(limit: Long, initialUsed: Long) {
        serviceScope.launch {
            var currentSessionUsed = 0L
            while (VpnManager.state.value == VpnState.CONNECTED) {
                // Simulate 50KB - 500KB every 2 seconds
                val download = (50000..500000).random().toLong()
                val upload = (5000..50000).random().toLong()
                
                currentSessionUsed += (download + upload)
                VpnManager.updateUsage(download, upload)

                // Check limit
                if (limit > 0 && (initialUsed + currentSessionUsed) >= limit) {
                    Log.w("SwimVpnService", "Data limit reached! Stopping VPN.")
                    VpnManager.setError("Data limit reached. Please upgrade your plan.")
                    stopVpn()
                    break
                }

                delay(2000)
            }
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        serviceJob.cancel()
        stopVpn()
    }
}
