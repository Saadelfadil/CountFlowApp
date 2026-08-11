package com.countflow.core.database.di

import android.content.Context
import androidx.room.Room
import com.countflow.core.database.CountFlowDatabase
import com.countflow.core.database.CountFlowMigrations
import com.countflow.core.database.dao.EventDao
import com.countflow.core.database.dao.ReminderDao
import com.countflow.core.database.dao.WidgetBindingDao
import com.countflow.core.database.dao.WidgetStyleEntitlementDao
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

/** Provides the database and its DAOs. */
@Module
@InstallIn(SingletonComponent::class)
internal object DatabaseModule {

    @Provides
    @Singleton
    fun providesDatabase(@ApplicationContext context: Context): CountFlowDatabase =
        Room.databaseBuilder(
            context = context,
            klass = CountFlowDatabase::class.java,
            name = CountFlowDatabase.NAME,
        )
            .addMigrations(*CountFlowMigrations)
            // Note what is deliberately absent: fallbackToDestructiveMigration. Room will throw
            // on a missing migration rather than silently recreating the database, which is the
            // behaviour we want — losing every countdown and blanking every home-screen widget
            // is not an acceptable way to handle a developer's oversight.
            .build()

    @Provides
    fun providesEventDao(database: CountFlowDatabase): EventDao = database.eventDao()

    @Provides
    fun providesWidgetBindingDao(database: CountFlowDatabase): WidgetBindingDao =
        database.widgetBindingDao()

    @Provides
    fun providesReminderDao(database: CountFlowDatabase): ReminderDao = database.reminderDao()

    @Provides
    fun providesWidgetStyleEntitlementDao(database: CountFlowDatabase): WidgetStyleEntitlementDao =
        database.widgetStyleEntitlementDao()
}
