package com.countflow.feature.settings.about

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class AboutViewModelTest {

    @Test
    fun `state reads the version label from the provider and has no privacy policy url yet`() {
        val viewModel = AboutViewModel(appVersionProvider = { "0.4.9 (14)" })

        val state = viewModel.uiState.value

        assertThat(state.appVersionLabel).isEqualTo("0.4.9 (14)")
        // No final privacy-policy URL exists yet (TODO.md P0) — must never be silently
        // substituted with a placeholder that looks real.
        assertThat(state.privacyPolicyUrl).isNull()
    }
}
