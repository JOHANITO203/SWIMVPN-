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

    fun write(
        context: Context,
        status: RuntimeStatus,
        mode: RuntimeMode,
        error: String? = null,
        lastDisconnectCause: DisconnectCause = DisconnectCause.UNKNOWN,
        reconnectCount: Int = 0,
        sessionStartedAt: Long? = null,
        updatedAt: Long = System.currentTimeMillis(),
    ) {
        val editor = context.applicationContext
            .getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit()
            .putString(KEY_STATUS, status.name)
            .putString(KEY_MODE, mode.name)
            .putLong(KEY_UPDATED_AT, updatedAt)
            .putString(KEY_ERROR, error)
            .putString(KEY_LAST_DISCONNECT_CAUSE, lastDisconnectCause.name)
            .putInt(KEY_RECONNECT_COUNT, reconnectCount)

        if (sessionStartedAt != null) {
            editor.putLong(KEY_SESSION_STARTED_AT, sessionStartedAt)
        } else {
            editor.remove(KEY_SESSION_STARTED_AT)
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

        return RuntimeStateSnapshot(
            status = status,
            mode = mode,
            updatedAt = updatedAt,
            error = error,
            lastDisconnectCause = lastDisconnectCause,
            reconnectCount = reconnectCount,
            sessionStartedAt = sessionStartedAt,
        )
    }

    fun clear(context: Context, mode: RuntimeMode = RuntimeMode.FULL_TUNNEL) {
        write(
            context = context,
            status = RuntimeStatus.IDLE,
            mode = mode,
            error = null,
        )
    }
}
