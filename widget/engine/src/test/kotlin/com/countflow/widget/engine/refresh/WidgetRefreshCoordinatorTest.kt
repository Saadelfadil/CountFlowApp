package com.countflow.widget.engine.refresh

import com.countflow.core.domain.countdown.CountdownConfig
import com.countflow.core.domain.countdown.CountdownEngine
import com.countflow.core.domain.model.AppWidgetId
import com.countflow.core.domain.model.Event
import com.countflow.core.domain.model.EventId
import com.countflow.core.domain.model.EventTarget
import com.countflow.core.domain.model.WidgetBinding
import com.countflow.widget.engine.testing.FakeWidgetBindingRepository
import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.test.runTest
import org.junit.Test
import java.time.Clock
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId

/**
 * The orchestration a real refresh cycle runs, entirely against fakes — no `AlarmManager`, no
 * Glance, no Robolectric. Every real Android-side caller (the alarm firing, boot, a timezone
 * change, the safety-net worker, the app-alive subscription) is "call [WidgetRefreshCoordinator.refreshAndReschedule]
 * again", so this is the one place that needs covering for all of them at once.
 */
class WidgetRefreshCoordinatorTest {

    private val zone: ZoneId = ZoneId.of("UTC")
    private val now: Instant = Instant.parse("2026-06-15T08:00:00Z")

    private val bindings = FakeWidgetBindingRepository()
    private val redrawer = FakeWidgetRedrawer()
    private val alarms = FakeAlarmScheduler()
    private val coordinator = WidgetRefreshCoordinator(
        widgetBindingRepository = bindings.repository,
        refreshPlanner = WidgetRefreshPlanner(CountdownEngine(Clock.fixed(now, zone), CountdownConfig.Default)),
        redrawer = redrawer,
        alarmScheduler = alarms,
        clock = Clock.fixed(now, zone),
    )

    private fun event(id: String, daysAhead: Long) = Event.create(
        id = EventId(id),
        title = "Event $id",
        target = EventTarget.allDay(LocalDate.of(2026, 6, 15).plusDays(daysAhead), zone),
        createdAt = Instant.parse("2026-01-01T00:00:00Z"),
    )

    /**
     * An event inside the imminent window (default one hour) — its own next transition is the
     * target instant itself, hours before the next local midnight every plain [event] above
     * shares. Needed wherever a test wants two events whose *next transitions* genuinely differ,
     * since two still-distant events (a week away, a year away) both transition at the same next
     * midnight and would not actually distinguish "earlier" from "later" (found the hard way —
     * see the git history of this file for the first, wrong version of these fixtures).
     */
    private fun imminentEvent(id: String, minutesAhead: Long) = Event.create(
        id = EventId(id),
        title = "Event $id",
        target = EventTarget.timed(LocalDateTime.of(2026, 6, 15, 8, 0).plusMinutes(minutesAhead), zone),
        createdAt = Instant.parse("2026-01-01T00:00:00Z"),
    )

    private fun place(event: Event, widgetId: Int) =
        bindings.put(WidgetBinding.inheriting(AppWidgetId(widgetId), event.id, Instant.EPOCH), event)

    @Test
    fun `no bound widgets redraws nothing and schedules no alarm`() = runTest {
        val outcome = coordinator.refreshAndReschedule()

        assertThat(outcome.widgetsRefreshed).isEqualTo(0)
        assertThat(outcome.nextRefreshAt).isNull()
        assertThat(alarms.scheduledAt).isNull()
        assertThat(alarms.cancelCount).isEqualTo(1)
    }

    @Test
    fun `one bound widget schedules exactly one alarm at its own next transition`() = runTest {
        place(event("a", daysAhead = 5), widgetId = 1)

        val outcome = coordinator.refreshAndReschedule()

        assertThat(outcome.widgetsRefreshed).isEqualTo(1)
        assertThat(outcome.nextRefreshAt).isNotNull()
        assertThat(alarms.scheduledAt).isEqualTo(outcome.nextRefreshAt)
        assertThat(redrawer.redrawCount).isEqualTo(1)
    }

    @Test
    fun `an earlier event added later replaces the previously scheduled alarm`() = runTest {
        place(event("later", daysAhead = 100), widgetId = 1)
        val first = coordinator.refreshAndReschedule()

        place(imminentEvent("soon", minutesAhead = 30), widgetId = 2)
        val second = coordinator.refreshAndReschedule()

        assertThat(second.nextRefreshAt).isLessThan(first.nextRefreshAt)
        assertThat(alarms.scheduledAt).isEqualTo(second.nextRefreshAt)
        assertThat(alarms.scheduleCount).isEqualTo(2)
    }

    @Test
    fun `a later event added after an earlier one does not move the alarm sooner`() = runTest {
        place(imminentEvent("soon", minutesAhead = 30), widgetId = 1)
        val first = coordinator.refreshAndReschedule()

        place(event("later", daysAhead = 100), widgetId = 2)
        val second = coordinator.refreshAndReschedule()

        assertThat(second.nextRefreshAt).isEqualTo(first.nextRefreshAt)
    }

    @Test
    fun `deleting the earliest event recalculates the schedule around what remains`() = runTest {
        place(imminentEvent("soon", minutesAhead = 30), widgetId = 1)
        place(event("later", daysAhead = 100), widgetId = 2)
        val withBoth = coordinator.refreshAndReschedule()

        bindings.repository.deleteBindingsForEvent(EventId("soon"))
        val afterDelete = coordinator.refreshAndReschedule()

        assertThat(afterDelete.nextRefreshAt).isGreaterThan(withBoth.nextRefreshAt)
        assertThat(alarms.scheduledAt).isEqualTo(afterDelete.nextRefreshAt)
    }

    @Test
    fun `removing the earliest widget recalculates the schedule`() = runTest {
        place(imminentEvent("soon", minutesAhead = 30), widgetId = 1)
        place(event("later", daysAhead = 100), widgetId = 2)
        val withBoth = coordinator.refreshAndReschedule()

        bindings.repository.deleteBindings(listOf(AppWidgetId(1)))
        val afterRemoval = coordinator.refreshAndReschedule()

        assertThat(afterRemoval.nextRefreshAt).isGreaterThan(withBoth.nextRefreshAt)
    }

    @Test
    fun `editing an event to a nearer date pulls the schedule in`() = runTest {
        place(event("a", daysAhead = 100), widgetId = 1)
        val before = coordinator.refreshAndReschedule()

        // Simulates what a real edit looks like once it reaches the repository: the same
        // widget id now points at an event whose target changed.
        place(imminentEvent("a", minutesAhead = 30), widgetId = 1)
        val after = coordinator.refreshAndReschedule()

        assertThat(after.nextRefreshAt).isLessThan(before.nextRefreshAt)
    }

    @Test
    fun `two widgets on the same event coalesce into one alarm and one redraw pass`() = runTest {
        val shared = event("shared", daysAhead = 5)
        place(shared, widgetId = 1)
        place(shared, widgetId = 2)

        val outcome = coordinator.refreshAndReschedule()

        assertThat(outcome.widgetsRefreshed).isEqualTo(2)
        assertThat(redrawer.redrawCount).isEqualTo(1)
        assertThat(alarms.scheduleCount).isEqualTo(1)
    }

    @Test
    fun `a completed event's widget is redrawn once but schedules no alarm`() = runTest {
        place(event("done", daysAhead = 5).copy(isCompleted = true), widgetId = 1)

        val outcome = coordinator.refreshAndReschedule()

        assertThat(outcome.widgetsRefreshed).isEqualTo(1)
        assertThat(outcome.nextRefreshAt).isNull()
        assertThat(alarms.cancelCount).isEqualTo(1)
    }

    // ---------------------------------------------------------------- fakes

    private class FakeWidgetRedrawer : WidgetRedrawer {
        var redrawCount = 0
            private set

        override suspend fun redrawAll() {
            redrawCount++
        }
    }

    private class FakeAlarmScheduler : AlarmScheduler {
        var scheduledAt: Instant? = null
            private set
        var scheduleCount = 0
            private set
        var cancelCount = 0
            private set

        override fun scheduleExactRefresh(at: Instant) {
            scheduledAt = at
            scheduleCount++
        }

        override fun cancelScheduledRefresh() {
            scheduledAt = null
            cancelCount++
        }
    }
}
