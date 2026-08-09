package com.countflow.core.common.di

import com.google.common.truth.Truth.assertThat
import org.junit.After
import org.junit.Before
import org.junit.Test
import java.time.Clock
import java.time.ZoneId
import java.util.TimeZone

/**
 * Regression coverage for D-064: a `@Singleton` clock built from `Clock.systemDefaultZone()`
 * freezes [ZoneId.systemDefault] at construction, so it silently keeps reporting the zone the
 * process started in even after the device's real timezone changes. [LiveDefaultZoneClock] must
 * not have that problem.
 */
class LiveDefaultZoneClockTest {

    private val originalDefault: TimeZone = TimeZone.getDefault()

    @After
    fun restoreDefaultTimeZone() {
        TimeZone.setDefault(originalDefault)
    }

    @Test
    fun `zone reflects the current system default, not a value snapshotted at construction`() {
        TimeZone.setDefault(TimeZone.getTimeZone("Africa/Casablanca"))
        val clock = LiveDefaultZoneClock()
        assertThat(clock.zone).isEqualTo(ZoneId.of("Africa/Casablanca"))

        TimeZone.setDefault(TimeZone.getTimeZone("America/New_York"))
        assertThat(clock.zone).isEqualTo(ZoneId.of("America/New_York"))
    }

    @Test
    fun `unlike systemDefaultZone, an existing instance keeps up with a live timezone change`() {
        TimeZone.setDefault(TimeZone.getTimeZone("Africa/Casablanca"))
        val staleClock = Clock.systemDefaultZone()
        val liveClock = LiveDefaultZoneClock()

        TimeZone.setDefault(TimeZone.getTimeZone("America/New_York"))

        assertThat(staleClock.zone).isEqualTo(ZoneId.of("Africa/Casablanca"))
        assertThat(liveClock.zone).isEqualTo(ZoneId.of("America/New_York"))
    }

    @Test
    fun `withZone pins to the requested zone rather than tracking the system default`() {
        TimeZone.setDefault(TimeZone.getTimeZone("Africa/Casablanca"))
        val pinned = LiveDefaultZoneClock().withZone(ZoneId.of("Asia/Tokyo"))

        TimeZone.setDefault(TimeZone.getTimeZone("America/New_York"))

        assertThat(pinned.zone).isEqualTo(ZoneId.of("Asia/Tokyo"))
    }

    @Test
    fun `instant tracks real time`() {
        val clock = LiveDefaultZoneClock()
        val before = Clock.systemUTC().instant()
        val reading = clock.instant()
        val after = Clock.systemUTC().instant()

        assertThat(reading).isAtLeast(before)
        assertThat(reading).isAtMost(after)
    }
}
