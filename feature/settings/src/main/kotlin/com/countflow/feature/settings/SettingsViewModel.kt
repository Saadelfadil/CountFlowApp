package com.countflow.feature.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.countflow.core.domain.repository.PreferencesRepository
import com.countflow.core.domain.repository.ThemeMode
import com.countflow.feature.settings.about.AppVersionProvider
import com.countflow.feature.settings.notification.NotificationStatusProvider
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * State holder for the Settings screen.
 *
 * Notification status is not itself a [Flow][kotlinx.coroutines.flow.Flow] — Android exposes it
 * only as a point-in-time read ([NotificationStatusProvider.areNotificationsEnabled]) — so it is
 * held in its own [MutableStateFlow] and combined with the reactive preferences stream. The user
 * can change it entirely outside CountFlow (Settings → Android's notification settings → toggle →
 * back), so [refreshNotificationStatus] exists to be called on every screen resume, not just once
 * at construction.
 */
@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val preferencesRepository: PreferencesRepository,
    private val notificationStatusProvider: NotificationStatusProvider,
    appVersionProvider: AppVersionProvider,
) : ViewModel() {

    private val notificationsAllowed =
        MutableStateFlow(notificationStatusProvider.areNotificationsEnabled())

    private val appVersionLabel = appVersionProvider.versionLabel()

    val uiState: StateFlow<SettingsUiState> = combine(
        preferencesRepository.preferences,
        notificationsAllowed,
    ) { preferences, allowed ->
        SettingsUiState(
            themeMode = preferences.themeMode,
            useDynamicColor = preferences.useDynamicColor,
            notificationsAllowed = allowed,
            appVersionLabel = appVersionLabel,
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS),
        initialValue = SettingsUiState(appVersionLabel = appVersionLabel),
    )

    fun onThemeModeChange(mode: ThemeMode) {
        viewModelScope.launch { preferencesRepository.setThemeMode(mode) }
    }

    fun onDynamicColorChange(enabled: Boolean) {
        viewModelScope.launch { preferencesRepository.setUseDynamicColor(enabled) }
    }

    /** Re-reads notification status. Call on every screen resume — see class KDoc. */
    fun refreshNotificationStatus() {
        notificationsAllowed.value = notificationStatusProvider.areNotificationsEnabled()
    }

    private companion object {
        const val STOP_TIMEOUT_MILLIS = 5_000L
    }
}
