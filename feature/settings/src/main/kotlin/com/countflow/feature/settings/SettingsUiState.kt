package com.countflow.feature.settings

import com.countflow.core.domain.repository.ThemeMode

/**
 * Settings screen state.
 *
 * @property themeMode the user's chosen light/dark/system preference.
 * @property useDynamicColor whether the app derives colors from the wallpaper (Material You).
 * @property notificationsAllowed whether a CountFlow notification will actually reach the user
 *   right now — [com.countflow.feature.settings.notification.NotificationStatusProvider], not the
 *   per-event reminder selections themselves, which remain the source of truth for *what* the
 *   user wants (this screen only reports whether Android will deliver it).
 * @property appVersionLabel the installed package's version, e.g. "0.4.9 (14)".
 */
data class SettingsUiState(
    val themeMode: ThemeMode = ThemeMode.Default,
    val useDynamicColor: Boolean = true,
    val notificationsAllowed: Boolean = true,
    val appVersionLabel: String = "",
)
