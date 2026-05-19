package com.swimvpn.app.vpn

import android.content.Context

data class RuntimeStateSnapshot(
    val status: RuntimeStatus,
    val mode: RuntimeMode,
    val updatedAt: Long,
    val error: String?,
    val lastDisconnectCause: DisconnectCause = DisconnectCause.UNKNOWN,
    val reconnectCount: Int = 0,
    val sessionStartedAt: Long? = null,
    val xrayLogPath: String? = null,
    val tun2SocksLogPath: String? = null,
) {
    fun isFresh(now: Long = System.currentTimeMillis(), maxAgeMs: Long = ACTIVE_STATE_MAX_AGE_MS): Boolean {
        return status == RuntimeStatus.IDLE ||
            status == RuntimeStatus.FAILED ||
            now - updatedAt <= maxAgeMs
    }

    companion object {
        const val ACTIVE_STATE_MAX_AGE_MS = 15_000L
    }
}

object RuntimeStateStore {
    private const val PREFS_NAME = "swimvpn_runtime_state"
    private const val KEY_STATUS = "status"
    private const val KEY_MODE = "mode"
    private const val KEY_UPDATED_AT = "updated_at"
    private const val KEY_ERROR = "error"
    private const val KEY_LAST_DISCONNECT_CAUSE = "last_disconnect_cause"
    private const val KEY_RECONNECT_COUNT = "reconnect_count"
    private const val KEY_SESSION_STARTED_AT = "session_started_at"
    private const val KEY_XRAY_LOG_PATH = "xray_log_path"
    private const val KEY_TUN2SOCKS_LOG_PATH = "tun2socks_log_path"

    fun write(
        context: Context,
        status: RuntimeStatus,
        mode: RuntimeMode,
        error: String? = null,
        lastDisconnectCause: DisconnectCause? = null,
        reconnectCount: Int? = null,
        sessionStartedAt: Long? = null,
        xrayLogPath: String? = null,
        tun2SocksLogPath: String? = null,
        updatedAt: Long = System.currentTimeMillis(),
    ) {
        val prefs = context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val effectiveCause = lastDisconnectCause
            ?: DisconnectCause.fromPersisted(prefs.getString(KEY_LAST_DISCONNECT_CAUSE, null))
        val effectiveReconnectCount = reconnectCount ?: prefs.getInt(KEY_RECONNECT_COUNT, 0)
        val effectiveSessionStartedAt = sessionStartedAt
            ?: prefs.getLong(KEY_SESSION_STARTED_AT, 0L).takeIf { it > 0L }
        val effectiveXrayLogPath = xrayLogPath ?: prefs.getString(KEY_XRAY_LOG_PATH, null)
        val effectiveTun2SocksLogPath = tun2SocksLogPath ?: prefs.getString(KEY_TUN2SOCKS_LOG_PATH, null)

        val editor = prefs
            .edit()
            .putString(KEY_STATUS, status.name)
            .putString(KEY_MODE, mode.name)
            .putLong(KEY_UPDATED_AT, updatedAt)
            .putString(KEY_ERROR, error)
            .putString(KEY_LAST_DISCONNECT_CAUSE, effectiveCause.name)
            .putInt(KEY_RECONNECT_COUNT, effectiveReconnectCount)

        if (effectiveSessionStartedAt != null) {
            editor.putLong(KEY_SESSION_STARTED_AT, effectiveSessionStartedAt)
        } else {
            editor.remove(KEY_SESSION_STARTED_AT)
        }

        if (!effectiveXrayLogPath.isNullOrBlank()) {
            editor.putString(KEY_XRAY_LOG_PATH, effectiveXrayLogPath)
        } else {
            editor.remove(KEY_XRAY_LOG_PATH)
        }

        if (!effectiveTun2SocksLogPath.isNullOrBlank()) {
            editor.putString(KEY_TUN2SOCKS_LOG_PATH, effectiveTun2SocksLogPath)
        } else {
            editor.remove(KEY_TUN2SOCKS_LOG_PATH)
        }

        editor.apply()
    }

    fun read(context: Context): RuntimeStateSnapshot {
        val prefs = context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val status = runCatching {
            RuntimeStatus.valueOf(prefs.getString(KEY_STATUS, RuntimeStatus.IDLE.name) ?: RuntimeStatus.IDLE.name)
        }.getOrDefault(RuntimeStatus.IDLE)
        val mode = RuntimeMode.fromPersisted(prefs.getString(KEY_MODE, RuntimeMode.FULL_TUNNEL.name))
        val updatedAt = prefs.getLong(KEY_UPDATED_AT, 0L)
        val error = prefs.getString(KEY_ERROR, null)
        val lastDisconnectCause = DisconnectCause.fromPersisted(prefs.getString(KEY_LAST_DISCONNECT_CAUSE, null))
        val reconnectCount = prefs.getInt(KEY_RECONNECT_COUNT, 0)
        val sessionStartedAt = prefs.getLong(KEY_SESSION_STARTED_AT, 0L).takeIf { it > 0L }
        val xrayLogPath = prefs.getString(KEY_XRAY_LOG_PATH, null)
        val tun2SocksLogPath = prefs.getString(KEY_TUN2SOCKS_LOG_PATH, null)

        return RuntimeStateSnapshot(
            status = status,
            mode = mode,
            updatedAt = updatedAt,
            error = error,
            lastDisconnectCause = lastDisconnectCause,
            reconnectCount = reconnectCount,
            sessionStartedAt = sessionStartedAt,
            xrayLogPath = xrayLogPath,
            tun2SocksLogPath = tun2SocksLogPath,
        )
    }

    fun clear(context: Context, mode: RuntimeMode = RuntimeMode.FULL_TUNNEL) {
        context.applicationContext
            .getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit()
            .clear()
            .putString(KEY_STATUS, RuntimeStatus.IDLE.name)
            .putString(KEY_MODE, mode.name)
            .putLong(KEY_UPDATED_AT, System.currentTimeMillis())
            .apply()
    }
}
