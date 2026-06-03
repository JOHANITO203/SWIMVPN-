package com.swimvpn.app.diagnostics

import android.util.Log

/**
 * Central crash / diagnostic reporting seam.
 *
 * Today it writes to logcat only, so there is ZERO extra runtime dependency and zero build risk.
 * It is intentionally shaped to be backed by Firebase Crashlytics (including NDK native crashes)
 * WITHOUT changing any call site: when a Firebase project is wired (see
 * docs/CRASHLYTICS_SETUP.md), uncomment the `// CRASHLYTICS:` lines in this file and add the
 * Gradle bits described in the doc. Every method stays a safe no-op until then.
 *
 * Rationale: the OEM-killer crashes reported on some Xiaomi/Redmi and mid-range Samsung devices
 * are most likely NATIVE signals (SIGSEGV/SIGABRT) in the in-process tun2socks JNI, which a
 * try/catch cannot capture. Crashlytics-NDK is the only practical way to get the native stack and
 * the exact device model + ABI without physical access to the testers' phones.
 */
object CrashReporter {
    private const val TAG = "CrashReporter"

    /** Breadcrumb log; in Crashlytics this becomes part of the next crash report. */
    fun log(message: String) {
        Log.i(TAG, message)
        // CRASHLYTICS: FirebaseCrashlytics.getInstance().log(message)
    }

    /** Attaches a custom key/value to subsequent reports (e.g. device, abi, vpn mode). */
    fun setKey(key: String, value: String) {
        Log.d(TAG, "key $key=$value")
        // CRASHLYTICS: FirebaseCrashlytics.getInstance().setCustomKey(key, value)
    }

    /** Records a non-fatal exception for triage. */
    fun recordException(throwable: Throwable, context: String? = null) {
        Log.e(TAG, "non-fatal" + (context?.let { " [$it]" } ?: ""), throwable)
        // CRASHLYTICS: context?.let { FirebaseCrashlytics.getInstance().log(it) }
        // CRASHLYTICS: FirebaseCrashlytics.getInstance().recordException(throwable)
    }

    /**
     * Convenience for VPN runtime failures: tags the failing stage + cause and records a non-fatal
     * so we can triage tunnel/data-plane failures (the OEM-killer surface) by device once
     * Crashlytics is live.
     */
    fun recordVpnFailure(stage: String, cause: String, throwable: Throwable? = null) {
        Log.e(TAG, "vpn_failure stage=$stage cause=$cause", throwable)
        // CRASHLYTICS: setKey("vpn_stage", stage); setKey("vpn_cause", cause)
        // CRASHLYTICS: throwable?.let { recordException(it, "vpn:$stage") }
    }
}
