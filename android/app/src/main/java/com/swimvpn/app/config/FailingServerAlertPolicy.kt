package com.swimvpn.app.config

/**
 * Pure decision policy for the failing-imported-server alert.
 *
 * The alert nudges the user to refresh their subscription when an IMPORTED server
 * keeps failing. It is intentionally narrow and non-spammy:
 *  - only for imported servers (backend servers are managed by SWIMVPN, not the user),
 *  - only once a server has failed at least [MIN_CONSECUTIVE_FAILURES] times in a row,
 *  - only once per server (the caller tracks the already-alerted set).
 *
 * Holds NO state and performs NO I/O. The caller owns the already-alerted set and
 * emits the existing toast/side-effect when this returns true.
 */
object FailingServerAlertPolicy {

    const val MIN_CONSECUTIVE_FAILURES = 3

    /**
     * @param source the failing server's source tag (e.g. "imported", "backend").
     * @param consecutiveFailures current consecutive-failure streak for that server.
     * @param alreadyAlertedForServer whether an alert was already emitted for this server.
     * @return true only when the server is imported, has failed [MIN_CONSECUTIVE_FAILURES]+
     *         times in a row, and has not been alerted yet.
     */
    fun shouldAlert(source: String?, consecutiveFailures: Int, alreadyAlertedForServer: Boolean): Boolean {
        if (source != "imported") return false
        if (consecutiveFailures < MIN_CONSECUTIVE_FAILURES) return false
        if (alreadyAlertedForServer) return false
        return true
    }
}
