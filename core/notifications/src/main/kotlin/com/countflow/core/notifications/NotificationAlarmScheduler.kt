package com.countflow.core.notifications

import java.time.Instant

/**
 * Schedules the single next system wakeup for reminder delivery.
 *
 * A distinct interface from `:widget:engine`'s `AlarmScheduler` (Session 12) rather than a
 * shared one — same shape, same reason for existing (isolate `AlarmManager` behind a fake-able
 * seam), but reminder delivery and widget redraw are different outcomes with independent
 * lifecycles, and a shared interface would only invite the two coordinators to start reaching
 * into each other's concerns (DECISIONS.md D-067).
 */
interface NotificationAlarmScheduler {
    fun scheduleNextReminder(at: Instant)
    fun cancelScheduledReminder()
}
