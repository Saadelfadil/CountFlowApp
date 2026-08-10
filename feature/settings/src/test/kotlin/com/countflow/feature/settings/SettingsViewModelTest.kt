package com.countflow.feature.settings

import app.cash.turbine.test
import com.countflow.core.domain.repository.ThemeMode
import com.countflow.core.domain.repository.UserPreferences
import com.countflow.feature.settings.testing.FakePreferencesRepository
import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class SettingsViewModelTest {

    private lateinit var preferencesRepository: FakePreferencesRepository
    private var notificationsEnabled = true

    @Before
    fun setUp() {
        Dispatchers.setMain(UnconfinedTestDispatcher())
        preferencesRepository = FakePreferencesRepository()
        notificationsEnabled = true
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private fun viewModel() = SettingsViewModel(
        preferencesRepository = preferencesRepository,
        notificationStatusProvider = { notificationsEnabled },
        appVersionProvider = { "0.4.9 (14)" },
    )

    @Test
    fun `initial state reflects stored preferences and notification status`() = runTest {
        preferencesRepository = FakePreferencesRepository(
            UserPreferences.Default.copy(themeMode = ThemeMode.DARK, useDynamicColor = false),
        )
        notificationsEnabled = false

        viewModel().uiState.test {
            val state = awaitItem()
            assertThat(state.themeMode).isEqualTo(ThemeMode.DARK)
            assertThat(state.useDynamicColor).isFalse()
            assertThat(state.notificationsAllowed).isFalse()
            assertThat(state.appVersionLabel).isEqualTo("0.4.9 (14)")
        }
    }

    @Test
    fun `onThemeModeChange persists and updates state`() = runTest {
        val viewModel = viewModel()
        viewModel.uiState.test {
            assertThat(awaitItem().themeMode).isEqualTo(ThemeMode.Default)

            viewModel.onThemeModeChange(ThemeMode.LIGHT)

            assertThat(awaitItem().themeMode).isEqualTo(ThemeMode.LIGHT)
            assertThat(preferencesRepository.getPreferences().themeMode).isEqualTo(ThemeMode.LIGHT)
        }
    }

    @Test
    fun `onDynamicColorChange persists and updates state`() = runTest {
        val viewModel = viewModel()
        viewModel.uiState.test {
            assertThat(awaitItem().useDynamicColor).isTrue()

            viewModel.onDynamicColorChange(false)

            assertThat(awaitItem().useDynamicColor).isFalse()
            assertThat(preferencesRepository.getPreferences().useDynamicColor).isFalse()
        }
    }

    @Test
    fun `refreshNotificationStatus re-reads the provider`() = runTest {
        val viewModel = viewModel()
        viewModel.uiState.test {
            assertThat(awaitItem().notificationsAllowed).isTrue()

            notificationsEnabled = false
            viewModel.refreshNotificationStatus()

            assertThat(awaitItem().notificationsAllowed).isFalse()
        }
    }

    @Test
    fun `refreshNotificationStatus with no change does not emit a duplicate state`() = runTest {
        val viewModel = viewModel()
        viewModel.uiState.test {
            val initial = awaitItem()

            viewModel.refreshNotificationStatus()

            expectNoEvents()
            assertThat(initial.notificationsAllowed).isTrue()
        }
    }
}
