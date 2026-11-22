package sh.measure.android.logger

import android.util.Log

/**
 * Prints every monitor (event or span) collected by the SDK directly to logcat so developers
 * can see them in real time, without needing a backend server.
 *
 * Unlike [Logger], this printer is always enabled and is independent of the internal SDK
 * logging flag. It writes under a dedicated [TAG] so monitors are easy to filter in logcat:
 *
 * ```
 * adb logcat -s MeasureMonitor
 * ```
 *
 * The data itself is still persisted in the local SQLite database. This printer only gives
 * developers an immediate, human readable view of what is being stored.
 */
internal object MonitorPrinter {
    const val TAG = "MeasureMonitor"

    /**
     * Prints a single event as it is stored in the database.
     */
    fun event(
        type: String,
        sessionId: String,
        timestamp: String,
        userTriggered: Boolean,
        data: String?,
    ) {
        Log.i(
            TAG,
            buildString {
                append("EVENT  ").append(type)
                if (userTriggered) append("  (user-triggered)")
                append('\n')
                append("   session : ").append(sessionId).append('\n')
                append("   time    : ").append(timestamp)
                if (!data.isNullOrBlank()) {
                    append('\n').append("   data    : ").append(data)
                }
            },
        )
    }

    /**
     * Prints a single span (trace) as it is stored in the database.
     */
    fun span(
        name: String,
        durationMs: Long,
        status: String,
        sessionId: String,
    ) {
        Log.i(
            TAG,
            buildString {
                append("SPAN   ").append(name)
                append("  ").append(durationMs).append("ms  [").append(status).append(']')
                append('\n')
                append("   session : ").append(sessionId)
            },
        )
    }

    /**
     * Prints an arbitrary informational line, used for summaries like how many monitors are
     * currently retained in the database.
     */
    fun line(message: String) {
        Log.i(TAG, message)
    }
}
