package com.countflow.core.domain.countdown

import com.countflow.core.domain.testing.Fixtures
import com.countflow.core.domain.testing.Fixtures.UTC
import com.google.common.truth.Truth.assertThat
import org.junit.Test

/**
 * [showsMeaningfulDayCount], shared by the event list and the widget renderer.
 *
 * A regression here would be silent in both consumers — each would just start showing "1" next
 * to "Tomorrow" — which is exactly why the rule has one definition and one test, not two.
 */
class CountdownLabelPresentationTest {

    private val engine = Fixtures.engine()

    private fun countdownFor(daysAhead: Long) = engine.countdownAt(
        event = Fixtures.timedEvent(at = Fixtures.dateTime("2026-06-15T08:00").plusDays(daysAhead)),
        now = Fixtures.dateTime("2026-06-15T08:00").atZone(UTC).toInstant(),
        deviceZone = UTC,
    )

    @Test
    fun `near-term labels never show a day count`() {
        listOf(0L, 1L, -1L).forEach { daysAhead ->
            assertThat(countdownFor(daysAhead).showsMeaningfulDayCount).isFalse()
        }
    }

    @Test
    fun `every label beyond the near-term set shows a day count`() {
        listOf(2L, 6L, 7L, 30L, -3L, -7L).forEach { daysAhead ->
            assertThat(countdownFor(daysAhead).showsMeaningfulDayCount).isTrue()
        }
    }

    @Test
    fun `completed and expired never show a day count`() {
        val completed = engine.countdownAt(
            event = Fixtures.timedEvent(at = Fixtures.dateTime("2026-01-01T00:00"), isCompleted = true),
            now = Fixtures.dateTime("2026-06-15T08:00").atZone(UTC).toInstant(),
            deviceZone = UTC,
        )
        val expired = countdownFor(-30L)

        assertThat(completed.showsMeaningfulDayCount).isFalse()
        assertThat(expired.showsMeaningfulDayCount).isFalse()
    }
}
