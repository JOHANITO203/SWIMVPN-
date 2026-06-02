package com.swimvpn.app

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
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

                if (!autoConnectEnabled) {
                    return@launch
                }

                // Intentional no-op: we never start the VPN foreground service directly at boot.
                // Direct-boot has no trusted, freshly-revalidated access/entitlement state, so
                // restoring the tunnel here could connect a user whose access has been revoked.
                // Auto-connect is instead re-applied during normal app bootstrap, which revalidates
                // access against the backend (source of truth) before reconnecting.
                Log.i("AutoConnectBootReceiver", "Auto-connect enabled; deferring tunnel restore to app bootstrap revalidation (no boot-time service start)")
            } catch (error: Exception) {
                Log.e("AutoConnectBootReceiver", "Unable to restore auto-connect after boot", error)
            } finally {
                pendingResult.finish()
            }
        }
    }
}
