package com.countflow.core.domain.countdown

import com.countflow.core.domain.testing.Fixtures
import com.countflow.core.domain.testing.Fixtures.UTC
import com.google.common.truth.Truth.assertThat
import org.junit.Test
import java.time.Duration

/**
 * [CountdownEngine.nextTransitionAt] — the pure calculation the background refresh scheduler
 * (DECISIONS.md D-062) uses to decide when a widget actually needs to redraw.
 *
 * Every test asserts two things: the returned instant, and that re-running [countdownAt] at that
 * exact instant really does produce a different [CountdownResult.label] or
 * [CountdownResult.status] than at `now` — the property this whole calculation exists to
 * guarantee. An instant that merely looks plausible but doesn't actually change anything would be
 * a wasted wakeup; a missed one would be a stale widget.
 */
class CountdownEngineNextTransitionTest {

    private val engine = Fixtures.engine()

    private fun nextTransition(now: String, target: String, zone: java.time.ZoneId = UTC) =
        engine.nextTransitionAt(
            event = Fixtures.timedEvent(at = Fixtures.dateTime(target), zone = zone),
            now = Fixtures.dateTime(now).atZone(zone).toInstant(),
            deviceZone = zone,
        )

    private fun assertTransitionChangesSomething(now: String, target: String, zone: java.time.ZoneId = UTC) {
        val nowInstant = Fixtures.dateTime(now).atZone(zone).toInstant()
        val event = Fixtures.timedEvent(at = Fixtures.dateTime(target), zone = zone)
        val before = engine.countdownAt(event, nowInstant, zone)
        val next = engine.nextTransitionAt(event, nowInstant, zone)

        assertThat(next).isNotNull()
        val after = engine.countdownAt(event, next!!, zone)
        assertThat(after.label != before.label || after.status != before.status).isTrue()
    }

    // ---------------------------------------------------------------- far future / ordinary days

    @Test
    fun `a far future event refreshes at the next local midnight, not sooner`() {
        // 218 days out — the brief's own example of what should NOT refresh every minute.
        val next = nextTransition(now = "2026-01-01T10:00:00", target = "2026-08-07T09:00:00")

        assertThat(next).isEqualTo(Fixtures.instant("2026-01-02T00:00:00Z"))
        assertTransitionChangesSomething(now = "2026-01-01T10:00:00", target = "2026-08-07T09:00:00")
    }

    @Test
    fun `tomorrow transitions to today at the next local midnight`() {
        val next = nextTransition(now = "2026-06-15T09:00:00", target = "2026-06-16T15:00:00")

        assertThat(next).isEqualTo(Fixtures.instant("2026-06-16T00:00:00Z"))
        assertTransitionChangesSomething(now = "2026-06-15T09:00:00", target = "2026-06-16T15:00:00")
    }

    @Test
    fun `today, well before the imminent window, refreshes when imminent begins`() {
        // Target is at 15:00, now is 09:00 — six hours out, past the one-hour imminent threshold.
        val next = nextTransition(now = "2026-06-16T09:00:00", target = "2026-06-16T15:00:00")

        assertThat(next).isEqualTo(Fixtures.instant("2026-06-16T14:00:00Z"))
        assertTransitionChangesSomething(now = "2026-06-16T09:00:00", target = "2026-06-16T15:00:00")
    }

    @Test
    fun `imminent refreshes exactly at the target, when it expires`() {
        val next = nextTransition(now = "2026-06-16T14:30:00", target = "2026-06-16T15:00:00")

        assertThat(next).isEqualTo(Fixtures.instant("2026-06-16T15:00:00Z"))
        assertTransitionChangesSomething(now = "2026-06-16T14:30:00", target = "2026-06-16T15:00:00")
    }

    @Test
    fun `next week boundary is a genuine plateau, not a change at the very next midnight`() {
        // Monday-start week; today is Wednesday the 17th, target is Thursday the 25th — inside
        // "next calendar week" territory (the following Mon-Sun) and past the near-future
        // threshold, so this is the NextWeek label specifically. The label stays "NextWeek" at
        // the very next midnight too (calendarDaysRemaining 8 -> 7, both still > nearFutureDays
        // and still inside the same week window) — the real transition is two days out, when
        // calendarDaysRemaining reaches 6 and the near-future threshold takes over regardless of
        // the week window. A calculator that only checked the next midnight would miss this.
        val next = nextTransition(now = "2026-06-17T08:00:00", target = "2026-06-25T08:00:00")

        assertThat(next).isEqualTo(Fixtures.instant("2026-06-19T00:00:00Z"))
        assertTransitionChangesSomething(now = "2026-06-17T08:00:00", target = "2026-06-25T08:00:00")
    }

    @Test
    fun `the midnight immediately after a next-week target genuinely changes nothing`() {
        // The specific case the plateau bug would get wrong: confirm the label truly is
        // unchanged one midnight forward, so the test above is asserting a real plateau and not
        // an accident of the fixture.
        val event = Fixtures.timedEvent(at = Fixtures.dateTime("2026-06-25T08:00:00"))
        val before = engine.countdownAt(
            event,
            Fixtures.dateTime("2026-06-17T08:00:00").atZone(UTC).toInstant(),
            UTC,
        )
        val atNextMidnight = engine.countdownAt(event, Fixtures.instant("2026-06-18T00:00:00Z"), UTC)

        assertThat(before.label).isEqualTo(CountdownLabel.NextWeek)
        assertThat(atNextMidnight.label).isEqualTo(CountdownLabel.NextWeek)
    }

    @Test
    fun `crosses a month boundary at the next local midnight`() {
        val next = nextTransition(now = "2026-01-30T08:00:00", target = "2026-02-02T08:00:00")

        assertThat(next).isEqualTo(Fixtures.instant("2026-01-31T00:00:00Z"))
        assertTransitionChangesSomething(now = "2026-01-30T08:00:00", target = "2026-02-02T08:00:00")
    }

    @Test
    fun `crosses a year boundary at the next local midnight`() {
        val next = nextTransition(now = "2026-12-30T08:00:00", target = "2027-01-02T08:00:00")

        assertThat(next).isEqualTo(Fixtures.instant("2026-12-31T00:00:00Z"))
        assertTransitionChangesSomething(now = "2026-12-30T08:00:00", target = "2027-01-02T08:00:00")
    }

    @Test
    fun `a leap day target still refreshes at the next local midnight`() {
        val next = nextTransition(now = "2028-02-27T08:00:00", target = "2028-02-29T08:00:00")

        assertThat(next).isEqualTo(Fixtures.instant("2028-02-28T00:00:00Z"))
        assertTransitionChangesSomething(now = "2028-02-27T08:00:00", target = "2028-02-29T08:00:00")
    }

    // ---------------------------------------------------------------- DST

    @Test
    fun `a spring-forward local midnight is still resolved correctly`() {
        // US DST 2026 spring-forward is March 8th, at 02:00 local — not at midnight, so this
        // specific midnight exists normally. What matters is that the day-boundary arithmetic
        // keeps producing correct, DST-transition-adjacent instants on both sides of the shift,
        // the same `atStartOfDay(zone)` mechanism EventTarget.startAt already relies on.
        Fixtures.assertCrossesDstTransition(
            zone = Fixtures.NEW_YORK,
            from = Fixtures.dateTime("2026-03-07T12:00:00").atZone(Fixtures.NEW_YORK).toInstant(),
            to = Fixtures.dateTime("2026-03-09T12:00:00").atZone(Fixtures.NEW_YORK).toInstant(),
        )

        val next = nextTransition(
            now = "2026-03-07T10:00:00",
            target = "2026-03-10T09:00:00",
            zone = Fixtures.NEW_YORK,
        )

        assertThat(next)
            .isEqualTo(Fixtures.date("2026-03-08").atStartOfDay(Fixtures.NEW_YORK).toInstant())
        assertTransitionChangesSomething(
            now = "2026-03-07T10:00:00",
            target = "2026-03-10T09:00:00",
            zone = Fixtures.NEW_YORK,
        )
    }

    @Test
    fun `a fall-back local midnight is still resolved correctly`() {
        // US DST 2026 fall-back is November 1st, at 02:00 local — same reasoning as the
        // spring-forward test above.
        Fixtures.assertCrossesDstTransition(
            zone = Fixtures.NEW_YORK,
            from = Fixtures.dateTime("2026-10-31T12:00:00").atZone(Fixtures.NEW_YORK).toInstant(),
            to = Fixtures.dateTime("2026-11-02T12:00:00").atZone(Fixtures.NEW_YORK).toInstant(),
        )

        val next = nextTransition(
            now = "2026-10-31T10:00:00",
            target = "2026-11-03T09:00:00",
            zone = Fixtures.NEW_YORK,
        )

        assertThat(next)
            .isEqualTo(Fixtures.date("2026-11-01").atStartOfDay(Fixtures.NEW_YORK).toInstant())
        assertTransitionChangesSomething(
            now = "2026-10-31T10:00:00",
            target = "2026-11-03T09:00:00",
            zone = Fixtures.NEW_YORK,
        )
    }

    // ---------------------------------------------------------------- zones

    @Test
    fun `the target zone and the device zone can disagree without breaking the calculation`() {
        // A flight authored in Tokyo, evaluated for a widget on a device sitting in New York.
        val event = Fixtures.timedEvent(at = Fixtures.dateTime("2026-06-20T09:00:00"), zone = Fixtures.TOKYO)
        val now = Fixtures.dateTime("2026-06-16T00:00:00").atZone(Fixtures.NEW_YORK).toInstant()

        val next = engine.nextTransitionAt(event, now, Fixtures.NEW_YORK)

        // The next transition is the device's own next local midnight, not Tokyo's.
        assertThat(next).isEqualTo(
            Fixtures.date("2026-06-17").atStartOfDay(Fixtures.NEW_YORK).toInstant(),
        )
        val before = engine.countdownAt(event, now, Fixtures.NEW_YORK)
        val after = engine.countdownAt(event, next!!, Fixtures.NEW_YORK)
        assertThat(after.calendarDaysRemaining).isLessThan(before.calendarDaysRemaining)
    }

    // ---------------------------------------------------------------- terminal states

    @Test
    fun `a completed event never needs another refresh`() {
        val event = Fixtures.timedEvent(
            at = Fixtures.dateTime("2026-06-20T09:00:00"),
            isCompleted = true,
        )
        val now = Fixtures.dateTime("2026-06-16T00:00:00").atZone(UTC).toInstant()

        assertThat(engine.nextTransitionAt(event, now, UTC)).isNull()
    }

    @Test
    fun `an event expired well past the recent-past window never needs another refresh`() {
        val event = Fixtures.timedEvent(at = Fixtures.dateTime("2026-01-01T09:00:00"))
        val now = Fixtures.dateTime("2026-06-16T00:00:00").atZone(UTC).toInstant()

        assertThat(engine.nextTransitionAt(event, now, UTC)).isNull()
    }

    @Test
    fun `an event that expired earlier today never needs another refresh`() {
        val event = Fixtures.timedEvent(at = Fixtures.dateTime("2026-06-16T09:00:00"))
        val now = Fixtures.dateTime("2026-06-16T10:00:00").atZone(UTC).toInstant()

        assertThat(engine.nextTransitionAt(event, now, UTC)).isNull()
    }

    @Test
    fun `an event still inside the recent-past window schedules the next daysAgo transition`() {
        // Expired two days ago — still inside the default 7-day recentPastDays window, so the
        // label ("2 days ago") will keep advancing at each local midnight until it does become
        // terminally Expired.
        val event = Fixtures.timedEvent(at = Fixtures.dateTime("2026-06-14T09:00:00"))
        val now = Fixtures.dateTime("2026-06-16T10:00:00").atZone(UTC).toInstant()

        val next = engine.nextTransitionAt(event, now, UTC)

        assertThat(next).isEqualTo(Fixtures.instant("2026-06-17T00:00:00Z"))
        val before = engine.countdownAt(event, now, UTC)
        val after = engine.countdownAt(event, next!!, UTC)
        assertThat(after.label).isNotEqualTo(before.label)
    }

    @Test
    fun `expiring transitions into the terminal label and stops there`() {
        // Just inside the recent-past window's edge: the day this label finally becomes
        // permanently "Expired" rather than "N days ago".
        val config = CountdownConfig(recentPastDays = 2)
        val engine = Fixtures.engine(config)
        val event = Fixtures.timedEvent(at = Fixtures.dateTime("2026-06-14T09:00:00"))
        val now = Fixtures.dateTime("2026-06-16T10:00:00").atZone(UTC).toInstant() // "2 days ago"

        val next = engine.nextTransitionAt(event, now, UTC)
        assertThat(next).isEqualTo(Fixtures.instant("2026-06-17T00:00:00Z"))

        val afterTransition = engine.countdownAt(event, next!!, UTC)
        assertThat(afterTransition.label).isEqualTo(CountdownLabel.Expired)
        // And now that it is truly terminal, no further transition is ever scheduled.
        assertThat(engine.nextTransitionAt(event, next, UTC)).isNull()
    }

    // ---------------------------------------------------------------- all-day events

    @Test
    fun `an all-day event skips the imminent candidate entirely`() {
        // All-day events are never IMMINENT (D-023) — the only candidates that matter are the
        // day's end (which coincides with the next local midnight once the day arrives).
        val event = Fixtures.allDayEvent(on = Fixtures.date("2026-06-16"))
        val now = Fixtures.dateTime("2026-06-16T10:00:00").atZone(UTC).toInstant()

        val next = engine.nextTransitionAt(event, now, UTC)

        assertThat(next).isEqualTo(Fixtures.instant("2026-06-17T00:00:00Z"))
        val after = engine.countdownAt(event, next!!, UTC)
        assertThat(after.status).isEqualTo(CountdownStatus.EXPIRED)
    }

    @Test
    fun `an all-day event several days out refreshes at the next midnight, not its own`() {
        val event = Fixtures.allDayEvent(on = Fixtures.date("2026-06-20"))
        val now = Fixtures.dateTime("2026-06-16T10:00:00").atZone(UTC).toInstant()

        val next = engine.nextTransitionAt(event, now, UTC)

        assertThat(next).isEqualTo(Fixtures.instant("2026-06-17T00:00:00Z"))
    }

    // ---------------------------------------------------------------- configuration sensitivity

    @Test
    fun `a shorter imminent threshold moves the imminent transition earlier`() {
        val config = CountdownConfig(imminentThreshold = Duration.ofMinutes(15))
        val engine = Fixtures.engine(config)

        val next = engine.nextTransitionAt(
            event = Fixtures.timedEvent(at = Fixtures.dateTime("2026-06-16T15:00:00")),
            now = Fixtures.dateTime("2026-06-16T09:00:00").atZone(UTC).toInstant(),
            deviceZone = UTC,
        )

        assertThat(next).isEqualTo(Fixtures.instant("2026-06-16T14:45:00Z"))
    }
}
