package com.countflow.core.domain.countdown

import com.countflow.core.domain.testing.Fixtures
import com.countflow.core.domain.testing.Fixtures.UTC
import com.google.common.truth.Truth.assertThat
import org.junit.Test
import java.time.DayOfWeek
import java.time.Duration

/**
 * Label and status resolution.
 *
 * Table-driven: each case states a "now", a target, and the label expected, so adding a boundary
 * is one line and a failure names the exact scenario rather than a line number.
 */
class CountdownEngineLabelTest {

    private val engine = Fixtures.engine()

    private data class LabelCase(
        val description: String,
        val now: String,
        val target: String,
        val expectedLabel: CountdownLabel,
        val expectedStatus: CountdownStatus,
    )

    @Test
    fun `resolves labels and statuses for timed events`() {
        // 2026-06-15 is a Monday, which keeps the week-boundary cases easy to reason about.
        val cases = listOf(
            LabelCase(
                description = "same day, later",
                now = "2026-06-15T08:00",
                target = "2026-06-15T20:00",
                expectedLabel = CountdownLabel.Today,
                expectedStatus = CountdownStatus.TODAY,
            ),
            LabelCase(
                description = "within the imminent threshold",
                now = "2026-06-15T19:30",
                target = "2026-06-15T20:00",
                expectedLabel = CountdownLabel.StartingSoon,
                expectedStatus = CountdownStatus.IMMINENT,
            ),
            LabelCase(
                description = "exactly at the imminent threshold is still imminent",
                now = "2026-06-15T19:00",
                target = "2026-06-15T20:00",
                expectedLabel = CountdownLabel.StartingSoon,
                expectedStatus = CountdownStatus.IMMINENT,
            ),
            LabelCase(
                description = "one second beyond the threshold is merely today",
                now = "2026-06-15T18:59:59",
                target = "2026-06-15T20:00",
                expectedLabel = CountdownLabel.Today,
                expectedStatus = CountdownStatus.TODAY,
            ),
            LabelCase(
                description = "next calendar day",
                now = "2026-06-15T08:00",
                target = "2026-06-16T08:00",
                expectedLabel = CountdownLabel.Tomorrow,
                expectedStatus = CountdownStatus.UPCOMING,
            ),
            LabelCase(
                description = "two days out",
                now = "2026-06-15T08:00",
                target = "2026-06-17T08:00",
                expectedLabel = CountdownLabel.InDays(2),
                expectedStatus = CountdownStatus.UPCOMING,
            ),
            LabelCase(
                description = "six days out is still a plain day count",
                now = "2026-06-15T08:00",
                target = "2026-06-21T08:00",
                expectedLabel = CountdownLabel.InDays(6),
                expectedStatus = CountdownStatus.UPCOMING,
            ),
            LabelCase(
                description = "seven days out lands in the next calendar week",
                now = "2026-06-15T08:00",
                target = "2026-06-22T08:00",
                expectedLabel = CountdownLabel.NextWeek,
                expectedStatus = CountdownStatus.UPCOMING,
            ),
            LabelCase(
                description = "the week after next is a plain day count again",
                now = "2026-06-15T08:00",
                target = "2026-06-30T08:00",
                expectedLabel = CountdownLabel.InDays(15),
                expectedStatus = CountdownStatus.UPCOMING,
            ),
            LabelCase(
                description = "passed earlier today",
                now = "2026-06-15T20:00",
                target = "2026-06-15T08:00",
                expectedLabel = CountdownLabel.Expired,
                expectedStatus = CountdownStatus.EXPIRED,
            ),
            LabelCase(
                description = "passed yesterday",
                now = "2026-06-15T08:00",
                target = "2026-06-14T08:00",
                expectedLabel = CountdownLabel.Yesterday,
                expectedStatus = CountdownStatus.EXPIRED,
            ),
            LabelCase(
                description = "passed three days ago",
                now = "2026-06-15T08:00",
                target = "2026-06-12T08:00",
                expectedLabel = CountdownLabel.DaysAgo(3),
                expectedStatus = CountdownStatus.EXPIRED,
            ),
            LabelCase(
                description = "passed seven days ago is the last counted day",
                now = "2026-06-15T08:00",
                target = "2026-06-08T08:00",
                expectedLabel = CountdownLabel.DaysAgo(7),
                expectedStatus = CountdownStatus.EXPIRED,
            ),
            LabelCase(
                description = "passed eight days ago stops counting",
                now = "2026-06-15T08:00",
                target = "2026-06-07T08:00",
                expectedLabel = CountdownLabel.Expired,
                expectedStatus = CountdownStatus.EXPIRED,
            ),
        )

        cases.forEach { case ->
            val event = Fixtures.timedEvent(at = Fixtures.dateTime(case.target), zone = UTC)
            val result = engine.countdownAt(
                event = event,
                now = Fixtures.dateTime(case.now).atZone(UTC).toInstant(),
                deviceZone = UTC,
            )

            assertThat(result.label).isEqualTo(case.expectedLabel)
            assertThat(result.status).isEqualTo(case.expectedStatus)
        }
    }

    @Test
    fun `completed takes precedence over every other state`() {
        // A finished event must never read as "expired" — the user already dealt with it.
        val past = Fixtures.timedEvent(
            at = Fixtures.dateTime("2026-06-01T08:00"),
            isCompleted = true,
        )
        val future = Fixtures.timedEvent(
            at = Fixtures.dateTime("2026-12-01T08:00"),
            isCompleted = true,
        )
        val now = Fixtures.dateTime("2026-06-15T08:00").atZone(UTC).toInstant()

        assertThat(engine.countdownAt(past, now, UTC).status).isEqualTo(CountdownStatus.COMPLETED)
        assertThat(engine.countdownAt(past, now, UTC).label).isEqualTo(CountdownLabel.Completed)
        assertThat(engine.countdownAt(future, now, UTC).status).isEqualTo(CountdownStatus.COMPLETED)
    }

    @Test
    fun `next week respects the configured first day of the week`() {
        // 2026-06-21 is a Sunday. Whether it counts as "next week" depends entirely on where the
        // week boundary sits, which is a locale property — hence the config knob.
        val event = Fixtures.timedEvent(at = Fixtures.dateTime("2026-06-21T08:00"))
        val now = Fixtures.dateTime("2026-06-15T08:00").atZone(UTC).toInstant() // Monday

        val mondayStart = Fixtures.engine(
            CountdownConfig.Default.copy(weekStartsOn = DayOfWeek.MONDAY, nearFutureDays = 3),
        )
        val sundayStart = Fixtures.engine(
            CountdownConfig.Default.copy(weekStartsOn = DayOfWeek.SUNDAY, nearFutureDays = 3),
        )

        // Week starting Monday: 15th–21st is this week, so the 21st is not next week.
        assertThat(mondayStart.countdownAt(event, now, UTC).label).isEqualTo(CountdownLabel.InDays(6))
        // Week starting Sunday: this week is the 14th–20th, so the 21st begins next week.
        assertThat(sundayStart.countdownAt(event, now, UTC).label).isEqualTo(CountdownLabel.NextWeek)
    }

    @Test
    fun `next week comparison survives the turn of the year`() {
        // Comparing ISO week numbers would break here: week 53 is followed by week 1, and a
        // numeric "is it this week plus one" test silently fails. The engine compares dates.
        val event = Fixtures.timedEvent(at = Fixtures.dateTime("2027-01-05T08:00")) // Tuesday
        val now = Fixtures.dateTime("2026-12-30T08:00").atZone(UTC).toInstant() // Wednesday

        val config = CountdownConfig.Default.copy(nearFutureDays = 3)
        val result = Fixtures.engine(config).countdownAt(event, now, UTC)

        assertThat(result.label).isEqualTo(CountdownLabel.NextWeek)
    }

    @Test
    fun `imminent threshold is configurable`() {
        val event = Fixtures.timedEvent(at = Fixtures.dateTime("2026-06-15T12:00"))
        val now = Fixtures.dateTime("2026-06-15T09:00").atZone(UTC).toInstant()

        val oneHour = Fixtures.engine(CountdownConfig.Default)
        val sixHours = Fixtures.engine(
            CountdownConfig.Default.copy(imminentThreshold = Duration.ofHours(6)),
        )

        assertThat(oneHour.countdownAt(event, now, UTC).status).isEqualTo(CountdownStatus.TODAY)
        assertThat(sixHours.countdownAt(event, now, UTC).status).isEqualTo(CountdownStatus.IMMINENT)
    }
}
