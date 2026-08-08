package com.countflow.core.database.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import com.countflow.core.domain.model.EventCategory
import com.countflow.core.domain.model.ProgressStyle
import com.countflow.core.domain.model.WidgetStyle
import java.time.Instant

/**
 * Storage row for a countdown event.
 *
 * This is a storage shape, not the domain model. The differences are deliberate:
 *
 * - The target is three columns rather than one value object, so the instant is indexable and
 *   the list can be ordered by it in SQL instead of in memory.
 * - The accent colour is a nullable integer where the domain has a sealed type. Null means
 *   "follow the wallpaper"; the mapper in `:core:data` is the only place that knows.
 * - Identifiers are plain strings rather than value classes, because Room stores columns.
 *
 * Indexed on what the list actually filters and sorts by. Without the `target_epoch_millis`
 * index the default "soonest first" ordering is a full scan on every keystroke of the search
 * field.
 */
@Entity(
    tableName = "events",
    indices = [
        Index(value = ["target_epoch_millis"]),
        Index(value = ["category"]),
        Index(value = ["is_archived", "is_completed"]),
    ],
)
data class EventEntity(
    @PrimaryKey
    @ColumnInfo(name = "id")
    val id: String,

    @ColumnInfo(name = "title")
    val title: String,

    @ColumnInfo(name = "emoji")
    val emoji: String?,

    @ColumnInfo(name = "icon_key")
    val iconKey: String?,

    @ColumnInfo(name = "category")
    val category: EventCategory,

    /**
     * The target instant. For an all-day event this is midnight in [targetZoneId], which is what
     * lets the authored calendar date be recovered exactly.
     */
    @ColumnInfo(name = "target_epoch_millis")
    val targetEpochMillis: Long,

    /** IANA zone identifier, for example `Europe/London`. Never a fixed UTC offset. */
    @ColumnInfo(name = "target_zone_id")
    val targetZoneId: String,

    @ColumnInfo(name = "is_all_day")
    val isAllDay: Boolean,

    @ColumnInfo(name = "created_at")
    val createdAt: Instant,

    /** Packed 0xAARRGGBB, or null to follow the Material You wallpaper palette. */
    @ColumnInfo(name = "accent_argb")
    val accentArgb: Int?,

    @ColumnInfo(name = "default_widget_style")
    val defaultWidgetStyle: WidgetStyle,

    @ColumnInfo(name = "default_progress_style")
    val defaultProgressStyle: ProgressStyle,

    @ColumnInfo(name = "reminders_enabled")
    val remindersEnabled: Boolean,

    @ColumnInfo(name = "is_archived")
    val isArchived: Boolean,

    @ColumnInfo(name = "is_completed")
    val isCompleted: Boolean,
)
