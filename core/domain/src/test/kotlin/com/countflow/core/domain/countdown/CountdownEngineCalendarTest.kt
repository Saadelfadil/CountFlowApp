package com.countflow.core.domain.countdown

import com.countflow.core.domain.testing.Fixtures
import com.countflow.core.domain.testing.Fixtures.UTC
import com.google.common.truth.Truth.assertThat
import org.junit.Test

/**
 * Day counts are calendar comparisons, not duration division (DECISIONS.md D-015).
 *
 * These are the cases that catch the classic countdown bug. Dividing elapsed milliseconds by
 * 86,400,000 gives a different answer from counting midnights roughly half the time, and the
 * midnight count is the one users mean when they say "five days away".
 */
class CountdownEngineCalendarTest {

    private val engine = Fixtures.engine()

    private fun countdown(now: String, target: String) = engine.countdownAt(
        event = Fixtures.timedEvent(at = Fixtures.dateTime(target), zone = UTC),
        now = Fixtures.dateTime(now).atZone(UTC).toInstant(),
        deviceZone = UTC,
    )

    @Test
    fun `twenty three hours across midnight is tomorrow, not today`() {
        // Duration says zero whole days. The calendar says one. The calendar is right.
        val result = countdown(now = "2026-06-15T23:00", target = "2026-06-16T22:00")

        assertThat(result.totals.totalDays).isEqualTo(0)
        assertThat(result.calendarDaysRemaining).isEqualTo(1)
        assertThat(result.label).isEqualTo(CountdownLabel.Tomorrow)
    }

    @Test
    fun `twenty five hours inside two calendar days is still tomorrow`() {
        // The mirror case: duration rounds up to a day, and the calendar agrees for once.
        val result = countdown(now = "2026-06-15T01:00", target = "2026-06-16T02:00")

        assertThat(result.totals.totalDays).isEqualTo(1)
        assertThat(result.calendarDaysRemaining).isEqualTo(1)
        assertThat(result.label).isEqualTo(CountdownLabel.Tomorrow)
    }

    @Test
    fun `twenty six hours spanning three calendar days counts two`() {
        val result = countdown(now = "2026-06-15T23:00", target = "2026-06-17T01:00")

        assertThat(result.totals.totalDays).isEqualTo(1)
        assertThat(result.calendarDaysRemaining).isEqualTo(2)
        assertThat(result.label).isEqualTo(CountdownLabel.InDays(2))
    }

    @Test
    fun `one minute across midnight is tomorrow`() {
        val result = countdown(now = "2026-06-15T23:59", target = "2026-06-16T00:00")

        assertThat(result.calendarDaysRemaining).isEqualTo(1)
        // Still within the imminent window, so the label reflects urgency over the day count.
        assertThat(result.status).isEqualTo(CountdownStatus.IMMINENT)
    }

    @Test
    fun `leap day is counted as a real day`() {
        // 2028 is a leap year, so the 28th of February to the 1st of March is two days.
        val leap = countdown(now = "2028-02-28T08:00", target = "2028-03-01T08:00")
        assertThat(leap.calendarDaysRemaining).isEqualTo(2)

        // 2027 is not, so the same dates are one day apart.
        val common = countdown(now = "2027-02-28T08:00", target = "2027-03-01T08:00")
        assertThat(common.calendarDaysRemaining).isEqualTo(1)
    }

    @Test
    fun `a target on the leap day itself resolves correctly`() {
        val result = countdown(now = "2028-02-27T08:00", target = "2028-02-29T08:00")

        assertThat(result.calendarDaysRemaining).isEqualTo(2)
        assertThat(result.label).isEqualTo(CountdownLabel.InDays(2))
    }

    @Test
    fun `counts across a month boundary`() {
        val result = countdown(now = "2026-01-30T08:00", target = "2026-02-02T08:00")

        assertThat(result.calendarDaysRemaining).isEqualTo(3)
        assertThat(result.label).isEqualTo(CountdownLabel.InDays(3))
    }

    @Test
    fun `counts across a year boundary`() {
        val result = countdown(now = "2026-12-30T08:00", target = "2027-01-02T08:00")

        assertThat(result.calendarDaysRemaining).isEqualTo(3)
        assertThat(result.breakdown.years).isEqualTo(0)
        assertThat(result.breakdown.months).isEqualTo(0)
        assertThat(result.breakdown.days).isEqualTo(3)
    }

    @Test
    fun `counts a multi-year span in whole calendar units`() {
        val result = countdown(now = "2026-01-01T00:00:00", target = "2027-03-02T04:05:06")

        assertThat(result.breakdown.years).isEqualTo(1)
        assertThat(result.breakdown.months).isEqualTo(2)
        assertThat(result.breakdown.days).isEqualTo(1)
        assertThat(result.breakdown.hours).isEqualTo(4)
        assertThat(result.breakdown.minutes).isEqualTo(5)
        assertThat(result.breakdown.seconds).isEqualTo(6)
    }

    @Test
    fun `february to march keeps month length honest`() {
        // One calendar month from the 31st of January lands on the 28th, not the 31st. Duration
        // arithmetic with a nominal 30-day month would report a day count here instead.
        val result = countdown(now = "2026-01-31T00:00", target = "2026-02-28T00:00")

        assertThat(result.breakdown.years).isEqualTo(0)
        assertThat(result.breakdown.months).isEqualTo(0)
        assertThat(result.breakdown.days).isEqualTo(28)
        assertThat(result.calendarDaysRemaining).isEqualTo(28)
    }

    @Test
    fun `totals report the same span in every unit`() {
        val result = countdown(now = "2026-06-15T00:00", target = "2026-06-25T00:00")

        assertThat(result.totals.totalDays).isEqualTo(10)
        assertThat(result.totals.totalWeeks).isEqualTo(1)
        assertThat(result.totals.totalHours).isEqualTo(240)
        assertThat(result.totals.totalMinutes).isEqualTo(14_400)
        assertThat(result.totals.totalSeconds).isEqualTo(864_000)
    }

    @Test
    fun `totals are unsigned for past events`() {
        val result = countdown(now = "2026-06-25T00:00", target = "2026-06-15T00:00")

        assertThat(result.isPast).isTrue()
        assertThat(result.totals.totalDays).isEqualTo(10)
        assertThat(result.calendarDaysRemaining).isEqualTo(-10)
        assertThat(result.breakdown.days).isEqualTo(10)
    }

    @Test
    fun `remaining is zero once the target has passed`() {
        val result = countdown(now = "2026-06-25T00:00", target = "2026-06-15T00:00")

        assertThat(result.remaining).isEqualTo(java.time.Duration.ZERO)
    }
}
