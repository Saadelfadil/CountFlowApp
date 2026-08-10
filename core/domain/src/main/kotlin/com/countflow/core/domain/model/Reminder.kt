package com.countflow.core.domain.model

import java.time.Instant
import java.time.LocalTime
import java.time.ZoneId
import java.time.ZonedDateTime

/**
 * How far ahead of an event a reminder fires.
 *
 * @property daysBefore whole days before the event's date. Zero means the day of the event.
 */
enum class ReminderType(val daysBefore: Int) {
    THIRTY_DAYS(daysBefore = 30),
    SEVEN_DAYS(daysBefore = 7),
    ONE_DAY(daysBefore = 1),
    DAY_OF(daysBefore = 0),
    ;

    companion object {
        /** Types offered when the user first enables reminders, nearest first. */
        val suggested: List<ReminderType> = listOf(DAY_OF, ONE_DAY, SEVEN_DAYS)
    }
}

/**
 * A scheduled notification for an event.
 *
 * Reminders fire at a wall-clock time on a calendar date, not at a fixed offset in
 * milliseconds. "Seven days before" means the same time of day a week earlier, which is not
 * `target - 604800000` when a DST boundary falls in between — that arithmetic would drift the
 * notification by an hour. [scheduledTime] computes it by calendar instead.
 *
 * @property id stable identifier.
 * @property eventId the event this reminder belongs to.
 * @property type how far ahead it fires.
 * @property timeOfDay the local wall-clock time to notify at, for reminders that are not on the
 *   event itself. Defaults to [DEFAULT_TIME_OF_DAY].
 * @property isEnabled whether this individual reminder is active. The event's
 *   [Event.remindersEnabled] must also be true for it to fire.
 * @property deliveredForScheduledTime the exact [scheduledTime] this reminder was last resolved
 *   for — either because a notification was actually sent for it, or because it was silently
 *   skipped for having already passed the moment it was activated (Session 13, D-065). Compared
 *   against a freshly computed [scheduledTime] rather than stored as a plain boolean: editing the
 *   event to a new date changes [scheduledTime], which makes an old delivery automatically stop
 *   matching and the reminder live again for the new date, with no separate "reset on edit" code
 *   path required.
 */
data class Reminder(
    val id: ReminderId,
    val eventId: EventId,
    val type: ReminderType,
    val timeOfDay: LocalTime,
    val isEnabled: Boolean,
    val deliveredForScheduledTime: Instant? = null,
) {

    /**
     * When this reminder should fire for [event], for a device in [deviceZone].
     *
     * Derived by stepping back whole calendar days from the event's date and then applying
     * [timeOfDay], so DST transitions between now and the event do not shift the notification.
     *
     * The zone the calendar subtraction runs in depends on the event, not unconditionally on
     * [deviceZone]: an all-day target follows [deviceZone] (it re-resolves for a traveller the
     * same way the event itself does, D-014), but a timed target uses its own authored zone
     * ([EventTarget.zone]) so "seven days before my Tokyo flight" means the same thing on the day
     * it is set as on the day it fires, regardless of where the device happens to be by then —
     * the same zone-pinning [EventTarget] itself already applies to the event's own instant.
     * [deviceZone] is still the parameter name because it remains what all-day targets use, and
     * because passing anything else for a timed target would be redundant.
     *
     * For [ReminderType.DAY_OF] on a timed event the event's own instant is used rather than
     * [timeOfDay] — notifying at 09:00 about a 07:00 flight would be useless. This is zone-
     * invariant: it is the same instant no matter which zone re-expresses it.
     */
    fun scheduledTime(event: Event, deviceZone: ZoneId): ZonedDateTime {
        val zone = if (event.target.isAllDay) deviceZone else event.target.zone
        val start = event.target.startAt(zone)
        if (type == ReminderType.DAY_OF && !event.target.isAllDay) {
            return start
        }
        return start.toLocalDate()
            .minusDays(type.daysBefore.toLong())
            .atTime(timeOfDay)
            .atZone(zone)
    }

    /** Whether this reminder has already been resolved — sent or skipped — for its current [scheduledTime]. */
    fun isResolvedFor(event: Event, deviceZone: ZoneId): Boolean =
        deliveredForScheduledTime == scheduledTime(event, deviceZone).toInstant()

    /** This reminder, marked resolved for its currently-computed [scheduledTime]. */
    fun markResolved(event: Event, deviceZone: ZoneId): Reminder =
        copy(deliveredForScheduledTime = scheduledTime(event, deviceZone).toInstant())

    /**
     * This reminder, ready to persist as newly active.
     *
     * If [scheduledTime] has already passed at [now], marks it resolved immediately without ever
     * notifying — the brief's own rule ("never fire a reminder whose trigger has already
     * passed," "do not immediately fire old reminders just because they were selected"). Applying
     * this once, at the moment a reminder is written, means every later read sees exactly the
     * same shape of state a reminder that fired and was marked delivered has — the scheduler
     * needs no separate "is this a stale new reminder or a caught-up old one" case.
     */
    fun withPastTriggerResolved(event: Event, now: Instant, deviceZone: ZoneId): Reminder =
        if (!scheduledTime(event, deviceZone).toInstant().isAfter(now)) markResolved(event, deviceZone) else this

    companion object {

        /** Default notification time: mid-morning, late enough not to wake anyone. */
        val DEFAULT_TIME_OF_DAY: LocalTime = LocalTime.of(9, 0)

        /** Creates an enabled reminder with the default time of day. */
        fun of(
            eventId: EventId,
            type: ReminderType,
            id: ReminderId = ReminderId.random(),
            timeOfDay: LocalTime = DEFAULT_TIME_OF_DAY,
        ): Reminder = Reminder(
            id = id,
            eventId = eventId,
            type = type,
            timeOfDay = timeOfDay,
            isEnabled = true,
        )
    }
}
