package com.countflow.core.data.repository

import com.countflow.core.common.di.CountFlowDispatcher
import com.countflow.core.common.di.Dispatcher
import com.countflow.core.data.mapper.toDomain
import com.countflow.core.data.mapper.toEntity
import com.countflow.core.database.dao.ReminderDao
import com.countflow.core.domain.model.EventId
import com.countflow.core.domain.model.Reminder
import com.countflow.core.domain.model.ReminderId
import com.countflow.core.domain.repository.ReminderRepository
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

/** Room-backed [ReminderRepository]. */
@Singleton
internal class ReminderRepositoryImpl @Inject constructor(
    private val reminderDao: ReminderDao,
    @Dispatcher(CountFlowDispatcher.IO) private val ioDispatcher: CoroutineDispatcher,
) : ReminderRepository {

    override fun observeRemindersForEvent(eventId: EventId): Flow<List<Reminder>> =
        reminderDao.observeRemindersForEvent(eventId.value)
            .map { entities -> entities.map { it.toDomain() } }
            .flowOn(ioDispatcher)

    override suspend fun getRemindersForEvent(eventId: EventId): List<Reminder> =
        withContext(ioDispatcher) {
            reminderDao.getRemindersForEvent(eventId.value).map { it.toDomain() }
        }

    override suspend fun getActiveReminders(): List<Reminder> = withContext(ioDispatcher) {
        reminderDao.getActiveReminders().map { it.toDomain() }
    }

    override suspend fun upsertReminder(reminder: Reminder) = withContext(ioDispatcher) {
        reminderDao.upsertReminder(reminder.toEntity())
    }

    override suspend fun replaceRemindersForEvent(eventId: EventId, reminders: List<Reminder>) =
        withContext(ioDispatcher) {
            reminderDao.replaceRemindersForEvent(
                eventId = eventId.value,
                reminders = reminders.map { it.toEntity() },
            )
        }

    override suspend fun deleteReminder(id: ReminderId) = withContext(ioDispatcher) {
        reminderDao.deleteReminder(id.value)
    }
}
