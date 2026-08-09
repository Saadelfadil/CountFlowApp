package com.countflow.core.common.di

import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import java.time.Clock
import java.time.Instant
import java.time.ZoneId
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
 * The device's current zone, not `systemUTC`: the countdown engine relies on that to resolve
 * all-day events for a traveller.
 *
 * This is [LiveDefaultZoneClock], not `Clock.systemDefaultZone()` — found by Session 12 real-device
 * timezone testing (D-064): `Clock.systemDefaultZone()` snapshots `ZoneId.systemDefault()` once,
 * at construction, into an immutable `Clock`. Bound `@Singleton`, that snapshot is taken once per
 * process and never changes again, so every long-lived consumer — the widget refresh scheduler,
 * every ViewModel — kept computing against the zone the process happened to start in, even after
 * a real `ACTION_TIMEZONE_CHANGED` broadcast was correctly received and handled. A traveller
 * landing in a new zone would see stale countdowns until the app process happened to restart.
 */
@Module
@InstallIn(SingletonComponent::class)
internal object TimeModule {

    @Provides
    @Singleton
    fun providesClock(): Clock = LiveDefaultZoneClock()
}

/**
 * A [Clock] whose [getZone] re-reads [ZoneId.systemDefault] on every call instead of caching it,
 * so a single long-lived (`@Singleton`) instance stays correct across a real device timezone
 * change. See [TimeModule.providesClock].
 */
internal class LiveDefaultZoneClock : Clock() {
    override fun getZone(): ZoneId = ZoneId.systemDefault()
    override fun withZone(zone: ZoneId): Clock = system(zone)
    override fun instant(): Instant = systemUTC().instant()
}
