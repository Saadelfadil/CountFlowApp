package com.countflow.core.domain.repository

import com.countflow.core.domain.model.EventId
import com.countflow.core.domain.model.Reminder
import com.countflow.core.domain.model.ReminderId
import kotlinx.coroutines.flow.Flow

/**
 * Read and write access to event reminders.
 *
 * Reminders are stored but not yet scheduled — notification delivery arrives in Milestone 7.
 * The contract exists now so the data model is settled and the event edit screen can persist
 * reminder choices in Milestone 3 without a migration later.
 */
interface ReminderRepository {

    /** Observes the reminders belonging to an event. */
    fun observeRemindersForEvent(eventId: EventId): Flow<List<Reminder>>

    /** Reads the reminders belonging to an event once. */
    suspend fun getRemindersForEvent(eventId: EventId): List<Reminder>

    /**
     * Reads every enabled reminder whose event also has reminders enabled.
     *
     * Both switches must be on for a reminder to fire, so the scheduler asks for the
     * already-intersected set rather than re-deriving it.
     */
    suspend fun getActiveReminders(): List<Reminder>

    /** Creates or replaces a reminder. */
    suspend fun upsertReminder(reminder: Reminder)

    /** Replaces the full set of reminders for an event in one transaction. */
    suspend fun replaceRemindersForEvent(eventId: EventId, reminders: List<Reminder>)

    /** Deletes a single reminder. */
    suspend fun deleteReminder(id: ReminderId)
}
