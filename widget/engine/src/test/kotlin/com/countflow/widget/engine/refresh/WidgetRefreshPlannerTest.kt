package com.countflow.widget.engine.refresh

import com.countflow.core.domain.countdown.CountdownConfig
import com.countflow.core.domain.countdown.CountdownEngine
import com.countflow.core.domain.model.AppWidgetId
import com.countflow.core.domain.model.Event
import com.countflow.core.domain.model.EventId
import com.countflow.core.domain.model.EventTarget
import com.countflow.core.domain.model.WidgetBinding
import com.countflow.core.domain.repository.BoundWidget
import com.google.common.truth.Truth.assertThat
import org.junit.Test
import java.time.Clock
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

/**
 * Coalescing policy only — the individual per-event math is [CountdownEngine.nextTransitionAt]'s
 * own, exhaustively tested where it lives. What matters here is strictly "given several bound
 * widgets, which one instant do we actually wake up for."
 */
class WidgetRefreshPlannerTest {

    private val zone: ZoneId = ZoneId.of("UTC")
    private val now: Instant = Instant.parse("2026-06-15T08:00:00Z")
    private val planner = WidgetRefreshPlanner(CountdownEngine(Clock.fixed(now, zone), CountdownConfig.Default))

    private fun event(id: String, daysAhead: Long) = Event.create(
        id = EventId(id),
        title = "Event $id",
        target = EventTarget.allDay(LocalDate.of(2026, 6, 15).plusDays(daysAhead), zone),
        createdAt = Instant.parse("2026-01-01T00:00:00Z"),
    )

    private fun bound(event: Event, widgetId: Int) =
        BoundWidget(WidgetBinding.inheriting(AppWidgetId(widgetId), event.id, Instant.EPOCH), event)

    @Test
    fun `no bound widgets means no refresh is ever needed`() {
        assertThat(planner.nextGlobalRefresh(emptyList(), now, zone)).isNull()
    }

    @Test
    fun `a single widget's own next transition is the global answer`() {
        val subject = event("a", daysAhead = 10)

        val next = planner.nextGlobalRefresh(listOf(bound(subject, 1)), now, zone)

        assertThat(next).isEqualTo(
            CountdownEngine(Clock.fixed(now, zone), CountdownConfig.Default)
                .nextTransitionAt(subject, now, zone),
        )
    }

    @Test
    fun `the earlier of two events' transitions wins`() {
        val soon = event("soon", daysAhead = 1)
        val later = event("later", daysAhead = 100)

        val next = planner.nextGlobalRefresh(listOf(bound(soon, 1), bound(later, 2)), now, zone)

        assertThat(next).isEqualTo(
            CountdownEngine(Clock.fixed(now, zone), CountdownConfig.Default).nextTransitionAt(soon, now, zone),
        )
    }

    @Test
    fun `a later event never displaces an already-earlier global answer`() {
        // Same scenario, reversed insertion order — the result must not depend on list order.
        val soon = event("soon", daysAhead = 1)
        val later = event("later", daysAhead = 100)

        val next = planner.nextGlobalRefresh(listOf(bound(later, 2), bound(soon, 1)), now, zone)

        assertThat(next).isEqualTo(
            CountdownEngine(Clock.fixed(now, zone), CountdownConfig.Default).nextTransitionAt(soon, now, zone),
        )
    }

    @Test
    fun `two widgets on the same event only cost one wakeup, not two`() {
        val shared = event("shared", daysAhead = 3)

        val next = planner.nextGlobalRefresh(listOf(bound(shared, 1), bound(shared, 2)), now, zone)

        assertThat(next).isEqualTo(
            CountdownEngine(Clock.fixed(now, zone), CountdownConfig.Default).nextTransitionAt(shared, now, zone),
        )
    }

    @Test
    fun `a completed event contributes nothing, so only the remaining event decides the wakeup`() {
        val completed = event("done", daysAhead = 5).copy(isCompleted = true)
        val active = event("active", daysAhead = 50)

        val next = planner.nextGlobalRefresh(listOf(bound(completed, 1), bound(active, 2)), now, zone)

        assertThat(next).isEqualTo(
            CountdownEngine(Clock.fixed(now, zone), CountdownConfig.Default).nextTransitionAt(active, now, zone),
        )
    }

    @Test
    fun `every bound event being completed means no refresh is needed at all`() {
        val completed = event("done", daysAhead = 5).copy(isCompleted = true)

        assertThat(planner.nextGlobalRefresh(listOf(bound(completed, 1)), now, zone)).isNull()
    }
}
