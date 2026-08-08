package com.countflow.core.common.log

import android.util.Log
import javax.inject.Inject
import javax.inject.Singleton

/**
 * [Logger] backed by Logcat.
 *
 * Debug messages are gated on [Log.isLoggable] so they cost nothing in release builds without
 * needing a `BuildConfig.DEBUG` reference, which would couple this module to the app's
 * build configuration.
 */
@Singleton
internal class AndroidLogger @Inject constructor() : Logger {

    override fun debug(tag: String, message: String) {
        if (Log.isLoggable(tag, Log.DEBUG)) {
            Log.d(tag, message)
        }
    }

    override fun warn(tag: String, message: String, throwable: Throwable?) {
        Log.w(tag, message, throwable)
    }

    override fun error(tag: String, message: String, throwable: Throwable?) {
        Log.e(tag, message, throwable)
    }
}
