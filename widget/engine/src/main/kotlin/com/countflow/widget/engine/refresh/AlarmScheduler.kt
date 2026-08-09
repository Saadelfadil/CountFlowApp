package com.countflow.widget.engine.refresh

import java.time.Instant

/**
 * The Android-specific half of background refresh: setting or cancelling the one system wakeup
 * [WidgetRefreshCoordinator] has already decided is needed.
 *
 * This is the seam that keeps `AlarmManager` out of this module entirely (D-033) — [Instant] in,
 * a real `PendingIntent`/`AlarmManager` call out, implemented in `:widget:glance`. The interface
 * carries no policy of its own: it does not decide *when*, only *how* to ask the platform for a
 * wakeup at an instant someone else already computed.
 */
interface AlarmScheduler {

    /** Requests a wakeup at [at]. Replaces any previously scheduled wakeup — there is only ever one. */
    fun scheduleExactRefresh(at: Instant)

    /** Cancels the scheduled wakeup, if any. Safe to call when none is currently scheduled. */
    fun cancelScheduledRefresh()
}
