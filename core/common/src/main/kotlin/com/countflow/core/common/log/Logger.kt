package com.countflow.core.common.log

/**
 * Logging facade for CountFlow.
 *
 * Modules depend on this rather than `android.util.Log` for two reasons: pure-JVM modules can
 * use it without an Android dependency, and it gives Crashlytics a single place to hook into
 * when crash reporting is wired up in Milestone 9 — no call sites change at that point.
 */
interface Logger {

    /** Logs a developer-facing debug message. Stripped from release builds. */
    fun debug(tag: String, message: String)

    /** Logs a recoverable problem that did not prevent the operation from completing. */
    fun warn(tag: String, message: String, throwable: Throwable? = null)

    /** Logs a failure. In release builds these are the candidates for crash reporting. */
    fun error(tag: String, message: String, throwable: Throwable? = null)
}
