package com.countflow.core.data.di

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.core.handlers.ReplaceFileCorruptionHandler
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.preferencesDataStoreFile
import com.countflow.core.common.di.ApplicationScope
import com.countflow.core.common.di.CountFlowDispatcher
import com.countflow.core.common.di.Dispatcher
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.plus
import javax.inject.Singleton

/** Provides the preferences DataStore. */
@Module
@InstallIn(SingletonComponent::class)
internal object DataStoreModule {

    @Provides
    @Singleton
    fun providesPreferencesDataStore(
        @ApplicationContext context: Context,
        @Dispatcher(CountFlowDispatcher.IO) ioDispatcher: CoroutineDispatcher,
        @ApplicationScope scope: CoroutineScope,
    ): DataStore<Preferences> = PreferenceDataStoreFactory.create(
        // A corrupted preferences file is recoverable — the worst case is that the user's theme
        // choice resets. Throwing instead would crash on every launch with no way out but a
        // reinstall, which would also take their events with it.
        corruptionHandler = ReplaceFileCorruptionHandler { emptyPreferences() },
        scope = scope + ioDispatcher,
        produceFile = { context.preferencesDataStoreFile(PREFERENCES_NAME) },
    )

    private const val PREFERENCES_NAME = "countflow_preferences"
}
