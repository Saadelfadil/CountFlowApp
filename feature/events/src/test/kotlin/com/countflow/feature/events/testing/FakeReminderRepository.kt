package com.countflow.feature.events.testing

import com.countflow.core.domain.model.EventId
import com.countflow.core.domain.model.Reminder
import com.countflow.core.domain.model.ReminderId
import com.countflow.core.domain.repository.ActiveReminder
import com.countflow.core.domain.repository.ReminderRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map

/** An in-memory [ReminderRepository] for ViewModel tests. */
internal class FakeReminderRepository : ReminderRepository {

    private val reminders = MutableStateFlow<List<Reminder>>(emptyList())

    /** The reminder set passed to the most recent [replaceRemindersForEvent] call, per event. */
    val lastReplaced = mutableMapOf<String, List<Reminder>>()

    override fun observeRemindersForEvent(eventId: EventId): Flow<List<Reminder>> =
        reminders.map { list -> list.filter { it.eventId == eventId } }

    override suspend fun getRemindersForEvent(eventId: EventId): List<Reminder> =
        reminders.value.filter { it.eventId == eventId }

    override suspend fun getActiveReminders(): List<Reminder> =
        reminders.value.filter { it.isEnabled }

    override fun observeActiveReminders(): Flow<List<ActiveReminder>> =
        throw UnsupportedOperationException("not used by EditEventViewModel")

    override suspend fun upsertReminder(reminder: Reminder) {
        reminders.value = reminders.value.filterNot { it.id == reminder.id } + reminder
    }

    override suspend fun replaceRemindersForEvent(eventId: EventId, reminders: List<Reminder>) {
        lastReplaced[eventId.value] = reminders
        this.reminders.value = this.reminders.value.filterNot { it.eventId == eventId } + reminders
    }

    override suspend fun deleteReminder(id: ReminderId) {
        reminders.value = reminders.value.filterNot { it.id == id }
    }
}
