package com.countflow.widget.engine.refresh

import java.time.Instant

/**
 * What one [WidgetRefreshCoordinator.refreshAndReschedule] call actually did.
 *
 * Exists so the Android-side callers — the alarm receiver, the boot/timezone/time/date receiver,
 * the periodic safety-net worker, and the app-alive reactive subscription — can log something
 * concrete without this module reaching for a `Logger` it deliberately does not depend on
 * (D-033's own tradeoff: `:widget:engine` cannot see `:core:common`'s logging facade). The
 * information leaves this module as plain data; what to do with it is entirely `:widget:glance`'s
 * decision.
 *
 * @property widgetsRefreshed how many distinct bound widgets were redrawn.
 * @property nextRefreshAt when the next wakeup was scheduled for, or `null` if none was needed
 *   (nothing bound, or every bound event is completed or terminally expired) — in which case any
 *   previously scheduled alarm was cancelled instead.
 */
data class RefreshOutcome(
    val widgetsRefreshed: Int,
    val nextRefreshAt: Instant?,
)
