package com.countflow.core.database.dao

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Upsert
import com.countflow.core.database.entity.ReminderEntity
import kotlinx.coroutines.flow.Flow

/** Queries over the reminders table. */
@Dao
interface ReminderDao {

    @Query("SELECT * FROM reminders WHERE event_id = :eventId ORDER BY type ASC")
    fun observeRemindersForEvent(eventId: String): Flow<List<ReminderEntity>>

    @Query("SELECT * FROM reminders WHERE event_id = :eventId ORDER BY type ASC")
    suspend fun getRemindersForEvent(eventId: String): List<ReminderEntity>

    /**
     * Reminders that are eligible to fire.
     *
     * Both switches have to be on — the reminder's own and its event's master toggle — so the
     * join is done here rather than leaving the scheduler to intersect two lists.
     */
    @Query(
        """
        SELECT r.* FROM reminders AS r
        INNER JOIN events AS e ON e.id = r.event_id
        WHERE r.is_enabled = 1
          AND e.reminders_enabled = 1
          AND e.is_archived = 0
          AND e.is_completed = 0
        """,
    )
    suspend fun getActiveReminders(): List<ReminderEntity>

    @Upsert
    suspend fun upsertReminder(reminder: ReminderEntity)

    @Query("DELETE FROM reminders WHERE id = :id")
    suspend fun deleteReminder(id: String)

    @Query("DELETE FROM reminders WHERE event_id = :eventId")
    suspend fun deleteRemindersForEvent(eventId: String)

    /**
     * Replaces an event's reminders atomically.
     *
     * A delete followed by an insert from the caller would leave a window where the event has no
     * reminders; if the process died there, the user's settings would be silently lost.
     */
    @Transaction
    suspend fun replaceRemindersForEvent(eventId: String, reminders: List<ReminderEntity>) {
        deleteRemindersForEvent(eventId)
        reminders.forEach { upsertReminder(it) }
    }
}
