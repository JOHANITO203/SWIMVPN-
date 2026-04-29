package com.swimvpn.app.data.local

import android.annotation.SuppressLint
import android.content.Context
import android.provider.Settings

object DeviceIdentityProvider {
    private val invalidDeviceIds = setOf("unknown_device_id", "unknown", "null")

    @SuppressLint("HardwareIds")
    fun getDeviceId(context: Context): String? {
        val rawDeviceId = Settings.Secure.getString(
            context.applicationContext.contentResolver,
            Settings.Secure.ANDROID_ID,
        )
        val deviceId = rawDeviceId?.trim().orEmpty()
        if (deviceId.isBlank() || deviceId.lowercase() in invalidDeviceIds) {
            return null
        }
        return deviceId
    }
}
