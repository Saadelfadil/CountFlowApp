package com.countflow.core.database.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import com.countflow.core.domain.model.ReminderType
import java.time.Instant
import java.time.LocalTime

/**
 * Storage row for a scheduled reminder.
 *
 * The `(event_id, type)` index is unique on purpose: an event cannot have two "seven days
 * before" reminders. Enforcing that in the schema rather than in application code means a
 * double-tap on the reminder toggle, or a restore that replays the same row twice, cannot
 * produce duplicate notifications.
 */
@Entity(
    tableName = "reminders",
    foreignKeys = [
        ForeignKey(
            entity = EventEntity::class,
            parentColumns = ["id"],
            childColumns = ["event_id"],
            onDelete = ForeignKey.CASCADE,
            onUpdate = ForeignKey.CASCADE,
        ),
    ],
    indices = [
        Index(value = ["event_id"]),
        Index(value = ["event_id", "type"], unique = true),
    ],
)
data class ReminderEntity(
    @PrimaryKey
    @ColumnInfo(name = "id")
    val id: String,

    @ColumnInfo(name = "event_id")
    val eventId: String,

    @ColumnInfo(name = "type")
    val type: ReminderType,

    /**
     * Wall-clock time to notify at.
     *
     * Persisted as seconds from midnight by the converter, so it sorts and compares in SQL and
     * cannot acquire a locale-dependent format.
     */
    @ColumnInfo(name = "time_of_day")
    val timeOfDay: LocalTime,

    @ColumnInfo(name = "is_enabled")
    val isEnabled: Boolean,

    /**
     * Epoch millis of the [com.countflow.core.domain.model.Reminder.scheduledTime] this row was
     * last resolved for — sent, or silently skipped for already being in the past when
     * activated. Null means never resolved. Compared against a freshly computed scheduled time,
     * not read as a plain flag, so editing the owning event's date makes an old resolution stop
     * matching automatically. Added in schema v2 (Session 13, D-065).
     */
    @ColumnInfo(name = "delivered_for_scheduled_time", defaultValue = "NULL")
    val deliveredForScheduledTime: Instant? = null,
)
