package com.countflow.feature.settings.about

/** The installed package's own version, for display on the About screen. */
fun interface AppVersionProvider {

    /** e.g. "0.4.9 (14)". Never a hardcoded string — read from the installed package. */
    fun versionLabel(): String
}
