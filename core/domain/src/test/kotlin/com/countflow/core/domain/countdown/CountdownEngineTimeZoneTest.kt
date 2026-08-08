package com.countflow.core.domain.countdown

import com.countflow.core.domain.testing.Fixtures
import com.countflow.core.domain.testing.Fixtures.LONDON
import com.countflow.core.domain.testing.Fixtures.NEW_YORK
import com.countflow.core.domain.testing.Fixtures.SYDNEY
import com.countflow.core.domain.testing.Fixtures.TOKYO
import com.countflow.core.domain.testing.Fixtures.UTC
import com.google.common.truth.Truth.assertThat
import org.junit.Test
import java.time.Duration

/**
 * Daylight saving, travel, and the all-day versus timed distinction.
 *
 * The DST cases each guard themselves with [Fixtures.assertCrossesDstTransition], so if a tz
 * database update moves a transition the test fails loudly rather than quietly degrading into
 * an ordinary-week test that proves nothing.
 */
class CountdownEngineTimeZoneTest {

    private val engine = Fixtures.engine()

    // ------------------------------------------------------------------ DST

    @Test
    fun `spring forward does not lose a day from the count`() {
        // Europe/London moves to BST on 2026-03-29, making that day 23 hours long.
        val now = Fixtures.dateTime("2026-03-27T12:00").atZone(LONDON).toInstant()
        val event = Fixtures.allDayEvent(on = Fixtures.date("2026-03-30"), zone = LONDON)
        val target = event.target.startAt(LONDON).toInstant()
        Fixtures.assertCrossesDstTransition(LONDON, now, target)

        val result = engine.countdownAt(event, now, LONDON)

        // Three midnights away, even though fewer than 72 hours elapse.
        assertThat(result.calendarDaysRemaining).isEqualTo(3)
        assertThat(result.totals.totalHours).isEqualTo(59)
        assertThat(result.totals.totalDays).isEqualTo(2)
    }

    @Test
    fun `fall back does not add a day to the count`() {
        // Europe/London returns to GMT on 2026-10-25, making that day 25 hours long.
        val now = Fixtures.dateTime("2026-10-24T23:00").atZone(LONDON).toInstant()
        val event = Fixtures.allDayEvent(on = Fixtures.date("2026-10-26"), zone = LONDON)
        val target = event.target.startAt(LONDON).toInstant()
        Fixtures.assertCrossesDstTransition(LONDON, now, target)

        val result = engine.countdownAt(event, now, LONDON)

        assertThat(result.calendarDaysRemaining).isEqualTo(2)
        assertThat(result.totals.totalHours).isEqualTo(26)
        assertThat(result.totals.totalDays).isEqualTo(1)
    }

    @Test
    fun `a timed event keeps its wall clock across a spring forward`() {
        // 09:00 stays 09:00, so only 71 hours elapse — the clocks ate one.
        //
        // This is the clearest demonstration of why the two measurements are separate types:
        // on the local timeline the gap is exactly three days with no remainder, while the
        // absolute duration is 71 hours. A countdown that displayed 71/24 would show "2 days"
        // on the Friday of a spring-forward weekend, which is wrong.
        val now = Fixtures.dateTime("2026-03-27T09:00").atZone(LONDON).toInstant()
        val event = Fixtures.timedEvent(at = Fixtures.dateTime("2026-03-30T09:00"), zone = LONDON)
        Fixtures.assertCrossesDstTransition(LONDON, now, event.target.instant)

        val result = engine.countdownAt(event, now, LONDON)

        assertThat(result.calendarDaysRemaining).isEqualTo(3)
        assertThat(result.breakdown.days).isEqualTo(3)
        assertThat(result.breakdown.hours).isEqualTo(0)
        assertThat(result.totals.totalHours).isEqualTo(71)
        assertThat(result.totals.totalDays).isEqualTo(2)
    }

    @Test
    fun `a wall clock time inside the spring forward gap is moved forward`() {
        // 01:30 on 2026-03-29 never happens in London. java.time shifts it to 02:30 rather than
        // throwing, and the engine must simply cope.
        val event = Fixtures.timedEvent(at = Fixtures.dateTime("2026-03-29T01:30"), zone = LONDON)

        val resolved = event.target.startAt(LONDON)

        assertThat(resolved.hour).isEqualTo(2)
        assertThat(resolved.minute).isEqualTo(30)
    }

    @Test
    fun `an all-day event on a day with no midnight still resolves`() {
        // America/Sao_Paulo historically skipped midnight on DST days. Lagos never has, so use
        // a zone whose rules are stable and assert the general guarantee instead: start-of-day
        // is always a real instant.
        val event = Fixtures.allDayEvent(on = Fixtures.date("2026-03-29"), zone = LONDON)

        val start = event.target.startAt(LONDON)

        assertThat(start.toLocalDate()).isEqualTo(Fixtures.date("2026-03-29"))
        assertThat(start.toInstant()).isNotNull()
    }

    @Test
    fun `a countdown spanning both transitions in a year stays calendar-accurate`() {
        val now = Fixtures.dateTime("2026-01-15T12:00").atZone(NEW_YORK).toInstant()
        val event = Fixtures.allDayEvent(on = Fixtures.date("2026-12-25"), zone = NEW_YORK)

        val result = engine.countdownAt(event, now, NEW_YORK)

        // 2026 is not a leap year: 15 Jan to 25 Dec is 344 days.
        assertThat(result.calendarDaysRemaining).isEqualTo(344)
    }

    // ------------------------------------------------------------------ travel

    @Test
    fun `an all-day event follows the traveller`() {
        // New Year's Eve is midnight wherever you are, not midnight back home.
        val event = Fixtures.allDayEvent(on = Fixtures.date("2026-12-31"), zone = LONDON)

        val atHome = event.target.startAt(LONDON)
        val abroad = event.target.startAt(TOKYO)

        assertThat(atHome.toLocalDate()).isEqualTo(Fixtures.date("2026-12-31"))
        assertThat(atHome.hour).isEqualTo(0)
        assertThat(abroad.toLocalDate()).isEqualTo(Fixtures.date("2026-12-31"))
        assertThat(abroad.hour).isEqualTo(0)
        // Same wall clock, different instants — nine hours apart.
        assertThat(Duration.between(abroad.toInstant(), atHome.toInstant()).toHours()).isEqualTo(9)
    }

    @Test
    fun `a timed event stays pinned to the zone it was authored in`() {
        // A Tokyo departure at 14:05 is the same instant seen from London, shown as 05:05.
        val event = Fixtures.timedEvent(at = Fixtures.dateTime("2026-12-31T14:05"), zone = TOKYO)

        val fromTokyo = event.target.startAt(TOKYO)
        val fromLondon = event.target.startAt(LONDON)

        assertThat(fromTokyo.toInstant()).isEqualTo(fromLondon.toInstant())
        assertThat(fromTokyo.hour).isEqualTo(14)
        assertThat(fromLondon.hour).isEqualTo(5)
    }

    @Test
    fun `travel can change how many days away an all-day event is`() {
        // Sitting in Sydney on the 16th while London is still on the 15th: a London-authored
        // all-day event on the 16th is today here and tomorrow there. Both answers are correct
        // for where the device is, which is the point of resolving in the device zone.
        val event = Fixtures.allDayEvent(on = Fixtures.date("2026-06-16"), zone = LONDON)
        val now = Fixtures.dateTime("2026-06-15T20:00").atZone(LONDON).toInstant()

        val inLondon = engine.countdownAt(event, now, LONDON)
        val inSydney = engine.countdownAt(event, now, SYDNEY)

        assertThat(inLondon.calendarDaysRemaining).isEqualTo(1)
        assertThat(inLondon.label).isEqualTo(CountdownLabel.Tomorrow)
        assertThat(inSydney.calendarDaysRemaining).isEqualTo(0)
        assertThat(inSydney.label).isEqualTo(CountdownLabel.Today)
    }

    @Test
    fun `the authored date survives a round trip through storage`() {
        val event = Fixtures.allDayEvent(on = Fixtures.date("2028-02-29"), zone = NEW_YORK)

        assertThat(event.target.authoredDate()).isEqualTo(Fixtures.date("2028-02-29"))
    }

    // ------------------------------------------------------------------ all-day lifetime

    @Test
    fun `an all-day event stays today for its whole day`() {
        val event = Fixtures.allDayEvent(on = Fixtures.date("2026-06-15"), zone = UTC)

        listOf("2026-06-15T00:00", "2026-06-15T12:00", "2026-06-15T23:59:59").forEach { at ->
            val result = engine.countdownAt(
                event = event,
                now = Fixtures.dateTime(at).atZone(UTC).toInstant(),
                deviceZone = UTC,
            )

            assertThat(result.isPast).isFalse()
            assertThat(result.status).isEqualTo(CountdownStatus.TODAY)
            assertThat(result.label).isEqualTo(CountdownLabel.Today)
        }
    }

    @Test
    fun `an all-day event in progress reports no time remaining`() {
        // Rather than "twelve hours", which would read as time still to wait.
        val event = Fixtures.allDayEvent(on = Fixtures.date("2026-06-15"), zone = UTC)
        val now = Fixtures.dateTime("2026-06-15T12:00").atZone(UTC).toInstant()

        val result = engine.countdownAt(event, now, UTC)

        assertThat(result.remaining).isEqualTo(Duration.ZERO)
        assertThat(result.status).isEqualTo(CountdownStatus.TODAY)
    }

    @Test
    fun `an all-day event is never imminent`() {
        // IMMINENT drives second-level ticking, which is meaningless for a whole-day event.
        // The awkward case is the minute either side of midnight, where a naive threshold check
        // would flip an all-day event to "starting soon" for the whole of its day.
        val event = Fixtures.allDayEvent(on = Fixtures.date("2026-06-15"), zone = UTC)

        val statuses = listOf(
            "2026-06-14T23:59",
            "2026-06-15T00:00",
            "2026-06-15T00:30",
            "2026-06-15T18:00",
        ).map { at ->
            engine.countdownAt(
                event = event,
                now = Fixtures.dateTime(at).atZone(UTC).toInstant(),
                deviceZone = UTC,
            ).status
        }

        assertThat(statuses).containsNoneOf(CountdownStatus.IMMINENT, CountdownStatus.EXPIRED)
    }

    @Test
    fun `a timed event at the same moment is imminent`() {
        // The contrast with the previous test: for a timed event the threshold does apply.
        val event = Fixtures.timedEvent(at = Fixtures.dateTime("2026-06-15T00:00"), zone = UTC)
        val now = Fixtures.dateTime("2026-06-14T23:59").atZone(UTC).toInstant()

        assertThat(engine.countdownAt(event, now, UTC).status).isEqualTo(CountdownStatus.IMMINENT)
    }

    @Test
    fun `an all-day event expires at the following midnight`() {
        val event = Fixtures.allDayEvent(on = Fixtures.date("2026-06-15"), zone = UTC)
        val now = Fixtures.dateTime("2026-06-16T00:00").atZone(UTC).toInstant()

        val result = engine.countdownAt(event, now, UTC)

        assertThat(result.isPast).isTrue()
        assertThat(result.status).isEqualTo(CountdownStatus.EXPIRED)
        assertThat(result.label).isEqualTo(CountdownLabel.Yesterday)
    }

    @Test
    fun `a timed event expires the moment it starts`() {
        val event = Fixtures.timedEvent(at = Fixtures.dateTime("2026-06-15T12:00"), zone = UTC)
        val now = Fixtures.dateTime("2026-06-15T12:00").atZone(UTC).toInstant()

        val result = engine.countdownAt(event, now, UTC)

        assertThat(result.isPast).isTrue()
        assertThat(result.status).isEqualTo(CountdownStatus.EXPIRED)
    }
}
