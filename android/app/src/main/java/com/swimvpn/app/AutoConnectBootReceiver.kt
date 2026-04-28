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

                Log.i("AutoConnectBootReceiver", "Skipping direct boot restore; app bootstrap must revalidate access first")
            } catch (error: Exception) {
                Log.e("AutoConnectBootReceiver", "Unable to restore auto-connect after boot", error)
            } finally {
                pendingResult.finish()
            }
        }
    }
}
