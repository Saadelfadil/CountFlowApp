package com.countflow.feature.settings.about

import android.content.Context
import android.content.pm.PackageManager
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * [AppVersionProvider] backed by [PackageManager].
 *
 * Reads the *installed* package's version rather than referencing `BuildConfig` directly, so
 * this module stays decoupled from `:app`'s build configuration — the same reasoning
 * `AndroidLogger` already applies to `BuildConfig.DEBUG`.
 *
 * [PackageInfo.longVersionCode][android.content.pm.PackageInfo.getLongVersionCode] has existed
 * since API 28; at this project's minSdk 31 the pre-28 `versionCode` fallback would be dead code
 * (confirmed by lint's `ObsoleteSdkInt`), so there is no branch here — same reasoning
 * `CountFlowTheme` already applies to its absent `Build.VERSION` guard.
 */
@Singleton
internal class AndroidAppVersionProvider @Inject constructor(
    @ApplicationContext private val context: Context,
) : AppVersionProvider {

    override fun versionLabel(): String {
        val info = runCatching {
            context.packageManager.getPackageInfo(context.packageName, 0)
        }.getOrNull() ?: return UNKNOWN_VERSION

        return "${info.versionName} (${info.longVersionCode})"
    }

    private companion object {
        const val UNKNOWN_VERSION = "—"
    }
}
