package com.countflow.feature.settings.about

/**
 * About screen state.
 *
 * @property appVersionLabel the installed package's version, e.g. "0.4.9 (14)".
 * @property privacyPolicyUrl `null` until a final URL exists. No placeholder or fake URL is ever
 *   substituted here — publishing one would look shipped when it is not. The row this backs
 *   renders disabled while `null`; see `TODO.md`'s P0 section, which tracks the real URL as a
 *   release blocker.
 */
data class AboutUiState(
    val appVersionLabel: String = "",
    val privacyPolicyUrl: String? = null,
)
