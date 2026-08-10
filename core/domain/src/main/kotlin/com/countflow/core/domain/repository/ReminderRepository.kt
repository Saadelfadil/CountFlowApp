package com.countflow.core.domain.repository

import com.countflow.core.domain.model.Event
import com.countflow.core.domain.model.EventId
import com.countflow.core.domain.model.Reminder
import com.countflow.core.domain.model.ReminderId
import kotlinx.coroutines.flow.Flow

/**
 * Read and write access to event reminders.
 */
interface ReminderRepository {

    /** Observes the reminders belonging to an event. */
    fun observeRemindersForEvent(eventId: EventId): Flow<List<Reminder>>

    /** Reads the reminders belonging to an event once. */
    suspend fun getRemindersForEvent(eventId: EventId): List<Reminder>

    /**
     * Reads every enabled reminder whose event also has reminders enabled, once.
     *
     * Both switches must be on for a reminder to fire, so callers ask for the already-
     * intersected set rather than re-deriving it. An archived or completed event's reminders are
     * excluded here, at the query level — not by the notification scheduler filtering a larger
     * list — so completing or archiving an event needs no explicit reminder-cancellation code of
     * its own (Session 13, D-066).
     */
    suspend fun getActiveReminders(): List<Reminder>

    /**
     * The reactive form of [getActiveReminders], joined with each reminder's owning event.
     *
     * Re-emits on any write to either the `reminders` or `events` table — a reminder toggled, an
     * event edited, completed, archived, restored, or deleted — which is exactly the trigger set
     * [com.countflow.core.domain.repository.EventRepository.observeEventsWithWidgets] already
     * gives the widget refresh scheduler for the same reason (Session 12, D-063). The notification
     * scheduler subscribes to this instead of polling.
     */
    fun observeActiveReminders(): Flow<List<ActiveReminder>>

    /** Creates or replaces a reminder. */
    suspend fun upsertReminder(reminder: Reminder)

    /** Replaces the full set of reminders for an event in one transaction. */
    suspend fun replaceRemindersForEvent(eventId: EventId, reminders: List<Reminder>)

    /** Deletes a single reminder. */
    suspend fun deleteReminder(id: ReminderId)
}

/**
 * A reminder joined to the event it belongs to.
 *
 * [Reminder.scheduledTime] needs the event to compute anything, the same reason
 * [com.countflow.core.domain.repository.BoundWidget] pairs a widget binding with its event.
 */
data class ActiveReminder(
    val reminder: Reminder,
    val event: Event,
)
