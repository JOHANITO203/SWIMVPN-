package com.swimvpn.app.adaptive

import android.util.Log

object AdaptiveEventLogger {
    private const val TAG = "SwimDecisionAgent"

    fun log(event: String, details: Map<String, Any?> = emptyMap()) {
        val payload = buildString {
            append("event=")
            append(event)
            details.forEach { (key, value) ->
                if (value != null) {
                    append(' ')
                    append(key)
                    append('=')
                    append(value.toString().replace('\n', ' '))
                }
            }
        }
        Log.i(TAG, payload)
    }
}
