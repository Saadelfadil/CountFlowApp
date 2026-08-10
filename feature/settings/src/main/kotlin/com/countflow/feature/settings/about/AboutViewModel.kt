package com.countflow.feature.settings.about

import androidx.lifecycle.ViewModel
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import javax.inject.Inject

/** State holder for the About screen. Nothing here changes after load, so a single read suffices. */
@HiltViewModel
class AboutViewModel @Inject constructor(
    appVersionProvider: AppVersionProvider,
) : ViewModel() {

    private val _uiState = MutableStateFlow(
        AboutUiState(
            appVersionLabel = appVersionProvider.versionLabel(),
            // No final privacy-policy URL exists yet — see AboutUiState's KDoc and TODO.md P0.
            privacyPolicyUrl = null,
        ),
    )
    val uiState: StateFlow<AboutUiState> = _uiState.asStateFlow()
}
