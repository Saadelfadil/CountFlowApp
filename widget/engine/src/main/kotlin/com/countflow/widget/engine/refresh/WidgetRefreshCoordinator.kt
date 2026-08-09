package com.countflow.widget.engine.refresh

import com.countflow.core.domain.repository.WidgetBindingRepository
import java.time.Clock
import javax.inject.Inject
import javax.inject.Singleton

/**
 * One full background-refresh cycle: read what's placed, redraw it, decide the next wakeup.
 *
 * This is the orchestration ARCHITECTURE.md's D-008 describes — "read the current state, update,
 * calculate the next wakeup, schedule it" (DECISIONS.md D-062 implements it) — with every
 * Android-specific piece pushed behind [WidgetRedrawer] and [AlarmScheduler] so the sequencing
 * itself stays plain, synchronous-looking, and testable with fakes rather than Robolectric.
 * Every real caller (the alarm firing, `BOOT_COMPLETED`, a timezone change, an event edited while
 * the app is open, the periodic safety net) funnels through this one method, which is exactly
 * what keeps "recalculate the schedule when relevant state changes" (the brief's own list of
 * triggers) from needing a bespoke code path per trigger — they all just mean "call this again."
 */
@Singleton
class WidgetRefreshCoordinator @Inject constructor(
    private val widgetBindingRepository: WidgetBindingRepository,
    private val refreshPlanner: WidgetRefreshPlanner,
    private val redrawer: WidgetRedrawer,
    private val alarmScheduler: AlarmScheduler,
    private val clock: Clock,
) {

    /**
     * Redraws every placed widget against the current database state, then schedules (or
     * cancels) the single next wakeup for whichever bound event needs one soonest.
     *
     * Redraw happens unconditionally and before rescheduling — Room is always the source of
     * truth (D-002), so there is never a reason to compute the next wakeup against data staler
     * than what was just drawn.
     */
    suspend fun refreshAndReschedule(): RefreshOutcome {
        val boundWidgets = widgetBindingRepository.getAllBoundWidgets()
        redrawer.redrawAll()

        val next = refreshPlanner.nextGlobalRefresh(boundWidgets, clock.instant(), clock.zone)
        if (next != null) {
            alarmScheduler.scheduleExactRefresh(next)
        } else {
            alarmScheduler.cancelScheduledRefresh()
        }

        return RefreshOutcome(widgetsRefreshed = boundWidgets.size, nextRefreshAt = next)
    }
}
