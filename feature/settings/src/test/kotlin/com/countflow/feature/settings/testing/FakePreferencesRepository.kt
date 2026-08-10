package com.countflow.feature.settings.testing

import com.countflow.core.domain.model.ProgressStyle
import com.countflow.core.domain.model.WidgetStyle
import com.countflow.core.domain.repository.EventSort
import com.countflow.core.domain.repository.PreferencesRepository
import com.countflow.core.domain.repository.ThemeMode
import com.countflow.core.domain.repository.UserPreferences
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow

/** An in-memory [PreferencesRepository] for ViewModel tests. */
internal class FakePreferencesRepository(
    initial: UserPreferences = UserPreferences.Default,
) : PreferencesRepository {

    private val state = MutableStateFlow(initial)

    override val preferences: Flow<UserPreferences> = state

    override suspend fun getPreferences(): UserPreferences = state.value

    override suspend fun setThemeMode(mode: ThemeMode) {
        state.value = state.value.copy(themeMode = mode)
    }

    override suspend fun setUseDynamicColor(enabled: Boolean) {
        state.value = state.value.copy(useDynamicColor = enabled)
    }

    override suspend fun setDefaultWidgetStyle(style: WidgetStyle) {
        state.value = state.value.copy(defaultWidgetStyle = style)
    }

    override suspend fun setDefaultProgressStyle(style: ProgressStyle) {
        state.value = state.value.copy(defaultProgressStyle = style)
    }

    override suspend fun setDefaultEventSort(sort: EventSort) {
        state.value = state.value.copy(defaultEventSort = sort)
    }

    override suspend fun setPremium(isPremium: Boolean) {
        state.value = state.value.copy(isPremium = isPremium)
    }

    override suspend fun setLastWidgetUpdate(epochMillis: Long) {
        state.value = state.value.copy(lastWidgetUpdateEpochMillis = epochMillis)
    }
}
