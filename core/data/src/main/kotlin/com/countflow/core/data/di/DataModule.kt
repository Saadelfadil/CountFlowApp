package com.countflow.core.data.di

import com.countflow.core.data.preferences.PreferencesRepositoryImpl
import com.countflow.core.data.repository.EventRepositoryImpl
import com.countflow.core.data.repository.ReminderRepositoryImpl
import com.countflow.core.data.repository.WidgetBindingRepositoryImpl
import com.countflow.core.data.repository.WidgetStyleEntitlementRepositoryImpl
import com.countflow.core.domain.countdown.CountdownConfig
import com.countflow.core.domain.repository.EventRepository
import com.countflow.core.domain.repository.PreferencesRepository
import com.countflow.core.domain.repository.ReminderRepository
import com.countflow.core.domain.repository.WidgetBindingRepository
import com.countflow.core.domain.repository.WidgetStyleEntitlementRepository
import dagger.Binds
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import java.time.temporal.WeekFields
import java.util.Locale
import javax.inject.Singleton

/** Binds the domain's repository interfaces to their Room and DataStore implementations. */
@Module
@InstallIn(SingletonComponent::class)
internal abstract class DataModule {

    @Binds
    @Singleton
    abstract fun bindsEventRepository(impl: EventRepositoryImpl): EventRepository

    @Binds
    @Singleton
    abstract fun bindsWidgetBindingRepository(
        impl: WidgetBindingRepositoryImpl,
    ): WidgetBindingRepository

    @Binds
    @Singleton
    abstract fun bindsReminderRepository(impl: ReminderRepositoryImpl): ReminderRepository

    @Binds
    @Singleton
    abstract fun bindsPreferencesRepository(
        impl: PreferencesRepositoryImpl,
    ): PreferencesRepository

    @Binds
    @Singleton
    abstract fun bindsWidgetStyleEntitlementRepository(
        impl: WidgetStyleEntitlementRepositoryImpl,
    ): WidgetStyleEntitlementRepository
}

/** Supplies domain policy that depends on the device's configuration. */
@Module
@InstallIn(SingletonComponent::class)
internal object DomainPolicyModule {

    /**
     * The countdown label thresholds, with the week boundary taken from the device locale.
     *
     * Whether a date counts as "next week" depends on which day a week starts on, and that
     * differs by locale — Monday across most of Europe, Sunday in the United States. The engine
     * stays locale-agnostic by taking it as configuration; resolving it is this layer's job.
     */
    @Provides
    @Singleton
    fun providesCountdownConfig(): CountdownConfig = CountdownConfig.Default.copy(
        weekStartsOn = WeekFields.of(Locale.getDefault()).firstDayOfWeek,
    )
}
