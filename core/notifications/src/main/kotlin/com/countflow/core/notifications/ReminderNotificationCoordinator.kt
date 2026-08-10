package com.countflow.core.notifications

import com.countflow.core.domain.model.Event
import com.countflow.core.domain.model.Reminder
import com.countflow.core.domain.repository.ActiveReminder
import com.countflow.core.domain.repository.ReminderRepository
import kotlinx.coroutines.flow.first
import java.time.Clock
import java.time.Instant
import java.time.ZoneId
import javax.inject.Inject
import javax.inject.Singleton

/**
 * One full reminder cycle: find what is due, deliver it exactly once, schedule the next wakeup.
 *
 * Every real trigger — the alarm firing, boot, a timezone/time/date change, an event edited while
 * the app is open, the periodic safety net — funnels through [processDueAndReschedule]. That is
 * what makes idempotent delivery a property of *this one method*, not something every caller has
 * to get right independently: a reminder becomes ineligible for delivery the instant it is
 * resolved, and resolution is persisted before this method returns, so no caller can observe or
 * re-trigger a half-delivered state.
 */
@Singleton
class ReminderNotificationCoordinator @Inject constructor(
    private val reminderRepository: ReminderRepository,
    private val sender: NotificationSender,
    private val alarmScheduler: NotificationAlarmScheduler,
    private val clock: Clock,
) {

    /**
     * Delivers every currently-due reminder once, then schedules (or cancels) the single next
     * wakeup for whichever active reminder needs one soonest.
     *
     * Reads active reminders fresh via [ReminderRepository.observeActiveReminders] rather than a
     * value passed in — the same discipline `WidgetRefreshCoordinator.refreshAndReschedule`
     * (Session 12) follows, so every caller can just say "something might have changed, recompute"
     * without needing to know what changed or carry the new state itself.
     */
    suspend fun processDueAndReschedule(): ReminderCycleOutcome {
        val now = clock.instant()
        val zone = clock.zone
        val active = reminderRepository.observeActiveReminders().first()

        var delivered = 0
        val resolved = active.map { item ->
            if (item.reminder.isDueAt(item.event, now, zone)) {
                sender.send(item.reminder, item.event)
                val markedResolved = item.reminder.markResolved(item.event, zone)
                reminderRepository.upsertReminder(markedResolved)
                delivered++
                item.copy(reminder = markedResolved)
            } else {
                item
            }
        }

        val next = resolved.mapNotNull { it.reminder.pendingTriggerAt(it.event, now, zone) }.minOrNull()
        if (next != null) alarmScheduler.scheduleNextReminder(next) else alarmScheduler.cancelScheduledReminder()

        return ReminderCycleOutcome(remindersDelivered = delivered, nextReminderAt = next)
    }
}

/** Whether [ActiveReminder.reminder] should be delivered right now. */
internal fun Reminder.isDueAt(event: Event, now: Instant, deviceZone: ZoneId): Boolean =
    !isResolvedFor(event, deviceZone) && !scheduledTime(event, deviceZone).toInstant().isAfter(now)

/** The instant to arm an alarm for, or null if already resolved or already past. */
internal fun Reminder.pendingTriggerAt(event: Event, now: Instant, deviceZone: ZoneId): Instant? {
    if (isResolvedFor(event, deviceZone)) return null
    return scheduledTime(event, deviceZone).toInstant().takeIf { it.isAfter(now) }
}
