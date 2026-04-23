package com.swimvpn.app

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.net.VpnService
import android.os.Build
import android.util.Log
import com.swimvpn.app.data.local.PreferencesManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

class AutoConnectBootReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent?) {
        val action = intent?.action ?: return
        if (action != Intent.ACTION_BOOT_COMPLETED && action != Intent.ACTION_MY_PACKAGE_REPLACED) {
            return
        }

        val pendingResult = goAsync()
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val appContext = context.applicationContext
                val prefs = PreferencesManager(appContext)
                val autoConnectEnabled = prefs.autoConnectFlow.first()
                val payload = prefs.getAutoConnectPayload()

                if (!autoConnectEnabled || payload == null) {
                    return@launch
                }

                if (payload.runtimeMode == com.swimvpn.app.vpn.RuntimeMode.FULL_TUNNEL &&
                    VpnService.prepare(appContext) != null
                ) {
                    Log.i("AutoConnectBootReceiver", "Skipping boot restore because VPN permission must be granted again")
                    return@launch
                }

                val serviceIntent = Intent(appContext, SwimVpnService::class.java).apply {
                    setAction(SwimVpnService.ACTION_START)
                    putExtra(SwimVpnService.EXTRA_SERVER_HOST, payload.host)
                    putExtra(SwimVpnService.EXTRA_SERVER_PORT, payload.port)
                    putExtra(SwimVpnService.EXTRA_PROTOCOL, payload.protocol)
                    putExtra(SwimVpnService.EXTRA_URL, payload.runtimeConfig)
                    putExtra(SwimVpnService.EXTRA_RUNTIME_MODE, payload.runtimeMode.name)
                }

                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    appContext.startForegroundService(serviceIntent)
                } else {
                    appContext.startService(serviceIntent)
                }
                Log.i("AutoConnectBootReceiver", "Restored auto-connect after boot/package replace for ${payload.host}:${payload.port}")
            } catch (error: Exception) {
                Log.e("AutoConnectBootReceiver", "Unable to restore auto-connect after boot", error)
            } finally {
                pendingResult.finish()
            }
        }
    }
}
