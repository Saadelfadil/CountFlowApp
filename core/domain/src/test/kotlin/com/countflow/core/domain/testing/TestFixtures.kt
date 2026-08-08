package com.countflow.core.domain.testing

import com.countflow.core.domain.countdown.CountdownConfig
import com.countflow.core.domain.countdown.CountdownEngine
import com.countflow.core.domain.model.Event
import com.countflow.core.domain.model.EventId
import com.countflow.core.domain.model.EventTarget
import java.time.Clock
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId

/**
 * Shared fixtures for the countdown tests.
 *
 * Every helper takes explicit times. Nothing here reads the system clock, so the suite is
 * deterministic and can be run on a machine in any zone at any time of year.
 */
internal object Fixtures {

    val UTC: ZoneId = ZoneId.of("UTC")
    val LONDON: ZoneId = ZoneId.of("Europe/London")
    val NEW_YORK: ZoneId = ZoneId.of("America/New_York")
    val TOKYO: ZoneId = ZoneId.of("Asia/Tokyo")
    val SYDNEY: ZoneId = ZoneId.of("Australia/Sydney")

    /** A creation instant far enough in the past that it never interferes with a test. */
    val LONG_AGO: Instant = Instant.parse("2020-01-01T00:00:00Z")

    /** Builds an engine whose clock is irrelevant; tests call `countdownAt` with explicit times. */
    fun engine(config: CountdownConfig = CountdownConfig.Default): CountdownEngine =
        CountdownEngine(clock = Clock.systemUTC(), config = config)

    /** Builds an engine pinned to [now] in [zone], for exercising the `countdown` entry point. */
    fun engineAt(
        now: Instant,
        zone: ZoneId,
        config: CountdownConfig = CountdownConfig.Default,
    ): CountdownEngine = CountdownEngine(Clock.fixed(now, zone), config)

    /** An event at a specific wall-clock time in [zone]. */
    fun timedEvent(
        at: LocalDateTime,
        zone: ZoneId = UTC,
        createdAt: Instant = LONG_AGO,
        isCompleted: Boolean = false,
    ): Event = Event.create(
        id = EventId("test-timed"),
        title = "Timed event",
        target = EventTarget.timed(at, zone),
        createdAt = createdAt,
    ).copy(isCompleted = isCompleted)

    /** An all-day event on [on], authored in [zone]. */
    fun allDayEvent(
        on: LocalDate,
        zone: ZoneId = UTC,
        createdAt: Instant = LONG_AGO,
        isCompleted: Boolean = false,
    ): Event = Event.create(
        id = EventId("test-all-day"),
        title = "All-day event",
        target = EventTarget.allDay(on, zone),
        createdAt = createdAt,
    ).copy(isCompleted = isCompleted)

    /** Parses an ISO-8601 instant, for readable test tables. */
    fun instant(text: String): Instant = Instant.parse(text)

    /** Parses an ISO-8601 local date-time, for readable test tables. */
    fun dateTime(text: String): LocalDateTime = LocalDateTime.parse(text)

    /** Parses an ISO-8601 date, for readable test tables. */
    fun date(text: String): LocalDate = LocalDate.parse(text)

    /**
     * Asserts that a DST transition really happens between two instants.
     *
     * Used to guard the daylight-saving tests. Without it, a change to the tz database — or a
     * mistake picking a date — would leave those tests passing while silently exercising an
     * ordinary week, which is worse than not having them.
     */
    fun assertCrossesDstTransition(zone: ZoneId, from: Instant, to: Instant) {
        val before = zone.rules.getOffset(from)
        val after = zone.rules.getOffset(to)
        check(before != after) {
            "Expected a DST transition in $zone between $from and $to, but the UTC offset " +
                "was $before at both ends. Fix the test dates."
        }
    }
}
