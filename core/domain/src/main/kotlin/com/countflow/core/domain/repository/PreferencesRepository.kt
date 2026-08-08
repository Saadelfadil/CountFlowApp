package com.countflow.core.domain.repository

import com.countflow.core.domain.model.ProgressStyle
import com.countflow.core.domain.model.WidgetStyle
import kotlinx.coroutines.flow.Flow

/** How the app chooses between light and dark. */
enum class ThemeMode {
    /** Follow the system setting. */
    SYSTEM,
    LIGHT,
    DARK,
    ;

    companion object {
        val Default: ThemeMode = SYSTEM
    }
}

/**
 * User settings.
 *
 * One object rather than a preference-per-flow so a consumer gets a consistent snapshot: reading
 * theme mode and dynamic-colour separately can briefly combine an old value with a new one and
 * produce a visible flash of the wrong theme.
 *
 * @property themeMode light, dark, or follow the system.
 * @property useDynamicColor whether to derive colours from the wallpaper.
 * @property defaultWidgetStyle pre-selected when creating an event.
 * @property defaultProgressStyle pre-selected when creating an event.
 * @property defaultEventSort how the event list is ordered.
 * @property isPremium whether the user has a premium entitlement. Always false until billing
 *   is implemented in Milestone 9; stored here so gating can be written and tested now.
 * @property lastWidgetUpdateEpochMillis when widgets were last refreshed, or null if never.
 *   Used by the Milestone 8 scheduler as a self-healing check.
 */
data class UserPreferences(
    val themeMode: ThemeMode,
    val useDynamicColor: Boolean,
    val defaultWidgetStyle: WidgetStyle,
    val defaultProgressStyle: ProgressStyle,
    val defaultEventSort: EventSort,
    val isPremium: Boolean,
    val lastWidgetUpdateEpochMillis: Long?,
) {
    companion object {
        /** The settings a fresh install starts with. */
        val Default: UserPreferences = UserPreferences(
            themeMode = ThemeMode.Default,
            useDynamicColor = true,
            defaultWidgetStyle = WidgetStyle.Default,
            defaultProgressStyle = ProgressStyle.Default,
            defaultEventSort = EventSort.Default,
            isPremium = false,
            lastWidgetUpdateEpochMillis = null,
        )
    }
}

/**
 * Access to user settings.
 *
 * Preferences only. Events never live here — they belong in the database, which supports
 * querying, sorting, and relational integrity that a key-value store does not.
 */
interface PreferencesRepository {

    /** Observes the full settings snapshot. Emits [UserPreferences.Default] on a fresh install. */
    val preferences: Flow<UserPreferences>

    /** Reads settings once. */
    suspend fun getPreferences(): UserPreferences

    suspend fun setThemeMode(mode: ThemeMode)

    suspend fun setUseDynamicColor(enabled: Boolean)

    suspend fun setDefaultWidgetStyle(style: WidgetStyle)

    suspend fun setDefaultProgressStyle(style: ProgressStyle)

    suspend fun setDefaultEventSort(sort: EventSort)

    /**
     * Records the premium entitlement.
     *
     * Written only by the billing layer once it exists. Treated as a cache of the Play Store's
     * answer, never as the authority — an entitlement check must be able to fall back to
     * re-querying Play.
     */
    suspend fun setPremium(isPremium: Boolean)

    /** Records when widgets were last refreshed. */
    suspend fun setLastWidgetUpdate(epochMillis: Long)
}
