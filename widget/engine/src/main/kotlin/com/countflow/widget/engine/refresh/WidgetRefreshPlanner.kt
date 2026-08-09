package com.countflow.widget.engine.refresh

import com.countflow.core.domain.countdown.CountdownEngine
import com.countflow.core.domain.repository.BoundWidget
import java.time.Instant
import java.time.ZoneId
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Coalesces every placed widget's own next-transition instant into one global wakeup.
 *
 * This is the "scheduling... but nothing about countdowns" half of the split D-004 already drew
 * between this module and `:core:domain`: [CountdownEngine.nextTransitionAt] decides *when a
 * single event's* displayed label would next change — that decision is inseparable from the
 * exact rules [CountdownEngine] already owns, so it lives there, not here. This class only asks
 * that question once per distinct bound event and takes the earliest answer, which is a pure
 * policy about *widgets*, not about countdowns.
 *
 * Deduplicated by event, not by widget: two placed widgets showing the same event only need one
 * wakeup to redraw both, since [CountdownEngine.nextTransitionAt] would return the identical
 * instant for both, and computing it twice buys nothing.
 */
@Singleton
class WidgetRefreshPlanner @Inject constructor(
    private val countdownEngine: CountdownEngine,
) {

    /**
     * The earliest instant any placed widget's displayed information would change, or `null`
     * when nothing bound needs a future redraw at all — no widgets are placed, or every bound
     * event is completed or terminally expired.
     */
    fun nextGlobalRefresh(
        boundWidgets: List<BoundWidget>,
        now: Instant,
        deviceZone: ZoneId,
    ): Instant? = boundWidgets
        .distinctBy { it.event.id }
        .mapNotNull { countdownEngine.nextTransitionAt(it.event, now, deviceZone) }
        .minOrNull()
}
