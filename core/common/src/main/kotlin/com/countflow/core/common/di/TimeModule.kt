package com.countflow.core.common.di

import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import java.time.Clock
import javax.inject.Singleton

/**
 * Provides the application's source of time.
 *
 * `java.time.Clock` *is* the clock abstraction — it is an injectable type with a ready-made
 * fixed implementation for tests (`Clock.fixed`). Wrapping it in a bespoke interface would add
 * a layer that does nothing except require its own fake, so CountFlow injects it directly.
 *
 * Nothing outside this module should call `Instant.now()` or `LocalDate.now()`. Those read the
 * system clock straight through and make the calling code untestable at time boundaries — which
 * is exactly where a countdown app's bugs live.
 *
 * `systemDefaultZone` rather than `systemUTC`: the zone this clock reports is the device's
 * current zone, and the countdown engine relies on that to resolve all-day events for a
 * traveller.
 */
@Module
@InstallIn(SingletonComponent::class)
internal object TimeModule {

    @Provides
    @Singleton
    fun providesClock(): Clock = Clock.systemDefaultZone()
}
