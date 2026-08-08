package com.countflow.core.data.preferences

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import com.countflow.core.common.log.Logger
import com.countflow.core.domain.model.ProgressStyle
import com.countflow.core.domain.model.WidgetStyle
import com.countflow.core.domain.repository.EventSort
import com.countflow.core.domain.repository.PreferencesRepository
import com.countflow.core.domain.repository.ThemeMode
import com.countflow.core.domain.repository.UserPreferences
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import java.io.IOException
import javax.inject.Inject
import javax.inject.Singleton

/**
 * DataStore-backed [PreferencesRepository].
 *
 * Preferences only. Events live in the database, which supports the querying, sorting, and
 * relational integrity a key-value store does not.
 *
 * Enums are stored by name and read back defensively: an unrecognised value falls back to the
 * default rather than throwing. A preferences file written by a newer build and read by an older
 * one is a real scenario after a Play Store rollback, and it must not make Settings uncrashable.
 */
@Singleton
internal class PreferencesRepositoryImpl @Inject constructor(
    private val dataStore: DataStore<Preferences>,
    private val logger: Logger,
) : PreferencesRepository {

    override val preferences: Flow<UserPreferences> = dataStore.data
        .catch { throwable ->
            // A corrupt or unreadable file must degrade to defaults, not crash the app on
            // launch. Anything other than IO is a programming error and is left to propagate.
            if (throwable is IOException) {
                logger.error(TAG, "Failed to read preferences; falling back to defaults", throwable)
                emit(emptyPreferences())
            } else {
                throw throwable
            }
        }
        .map(::toUserPreferences)

    override suspend fun getPreferences(): UserPreferences = preferences.first()

    override suspend fun setThemeMode(mode: ThemeMode) = edit { it[Keys.THEME_MODE] = mode.name }

    override suspend fun setUseDynamicColor(enabled: Boolean) =
        edit { it[Keys.USE_DYNAMIC_COLOR] = enabled }

    override suspend fun setDefaultWidgetStyle(style: WidgetStyle) =
        edit { it[Keys.DEFAULT_WIDGET_STYLE] = style.name }

    override suspend fun setDefaultProgressStyle(style: ProgressStyle) =
        edit { it[Keys.DEFAULT_PROGRESS_STYLE] = style.name }

    override suspend fun setDefaultEventSort(sort: EventSort) =
        edit { it[Keys.DEFAULT_EVENT_SORT] = sort.name }

    override suspend fun setPremium(isPremium: Boolean) =
        edit { it[Keys.IS_PREMIUM] = isPremium }

    override suspend fun setLastWidgetUpdate(epochMillis: Long) =
        edit { it[Keys.LAST_WIDGET_UPDATE] = epochMillis }

    private suspend fun edit(block: (androidx.datastore.preferences.core.MutablePreferences) -> Unit) {
        runCatching { dataStore.edit(block) }
            .onFailure { logger.error(TAG, "Failed to write preferences", it) }
    }

    private fun toUserPreferences(stored: Preferences) = UserPreferences(
        themeMode = stored[Keys.THEME_MODE].toEnumOr(ThemeMode.Default),
        useDynamicColor = stored[Keys.USE_DYNAMIC_COLOR] ?: true,
        defaultWidgetStyle = stored[Keys.DEFAULT_WIDGET_STYLE].toEnumOr(WidgetStyle.Default),
        defaultProgressStyle = stored[Keys.DEFAULT_PROGRESS_STYLE].toEnumOr(ProgressStyle.Default),
        defaultEventSort = stored[Keys.DEFAULT_EVENT_SORT].toEnumOr(EventSort.Default),
        isPremium = stored[Keys.IS_PREMIUM] ?: false,
        lastWidgetUpdateEpochMillis = stored[Keys.LAST_WIDGET_UPDATE],
    )

    /** Resolves a stored name to an enum constant, falling back rather than throwing. */
    private inline fun <reified T : Enum<T>> String?.toEnumOr(fallback: T): T =
        this?.let { name -> enumValues<T>().firstOrNull { it.name == name } } ?: fallback

    private object Keys {
        val THEME_MODE = stringPreferencesKey("theme_mode")
        val USE_DYNAMIC_COLOR = booleanPreferencesKey("use_dynamic_color")
        val DEFAULT_WIDGET_STYLE = stringPreferencesKey("default_widget_style")
        val DEFAULT_PROGRESS_STYLE = stringPreferencesKey("default_progress_style")
        val DEFAULT_EVENT_SORT = stringPreferencesKey("default_event_sort")
        val IS_PREMIUM = booleanPreferencesKey("is_premium")
        val LAST_WIDGET_UPDATE = longPreferencesKey("last_widget_update")
    }

    private companion object {
        const val TAG = "PreferencesRepository"
    }
}
