package com.countflow.core.domain.model

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
 */
data class Reminder(
    val id: ReminderId,
    val eventId: EventId,
    val type: ReminderType,
    val timeOfDay: LocalTime,
    val isEnabled: Boolean,
) {

    /**
     * When this reminder should fire for [event], for a device in [deviceZone].
     *
     * Derived by stepping back whole calendar days from the event's date and then applying
     * [timeOfDay], so DST transitions between now and the event do not shift the notification.
     *
     * For [ReminderType.DAY_OF] on a timed event the event's own time is used rather than
     * [timeOfDay] — notifying at 09:00 about a 07:00 flight would be useless.
     */
    fun scheduledTime(event: Event, deviceZone: ZoneId): ZonedDateTime {
        val start = event.target.startAt(deviceZone)
        if (type == ReminderType.DAY_OF && !event.target.isAllDay) {
            return start
        }
        return start.toLocalDate()
            .minusDays(type.daysBefore.toLong())
            .atTime(timeOfDay)
            .atZone(deviceZone)
    }

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
