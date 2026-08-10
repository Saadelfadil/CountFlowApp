package com.countflow.core.database.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import com.countflow.core.domain.model.ProgressStyle
import com.countflow.core.domain.model.WidgetStyle
import java.time.Instant

/**
 * Storage row linking a placed home-screen widget to the event it shows.
 *
 * The foreign key cascades on delete, so removing an event cannot leave a binding pointing at a
 * row that no longer exists. Without the cascade, a widget would keep rendering from a stale
 * join and the app would have to defend against a missing event at every read.
 *
 * These rows are device-local — `app_widget_id` is allocated by the launcher — and are excluded
 * from cloud backup and device transfer.
 */
@Entity(
    tableName = "widget_bindings",
    foreignKeys = [
        ForeignKey(
            entity = EventEntity::class,
            parentColumns = ["id"],
            childColumns = ["event_id"],
            onDelete = ForeignKey.CASCADE,
            onUpdate = ForeignKey.CASCADE,
        ),
    ],
    indices = [Index(value = ["event_id"])],
)
data class WidgetBindingEntity(
    /** The launcher-assigned widget id, unique on this device. */
    @PrimaryKey
    @ColumnInfo(name = "app_widget_id")
    val appWidgetId: Int,

    @ColumnInfo(name = "event_id")
    val eventId: String,

    /** Null to inherit the event's default. */
    @ColumnInfo(name = "widget_style_override")
    val widgetStyleOverride: WidgetStyle?,

    /** Null to inherit the event's default. */
    @ColumnInfo(name = "progress_style_override")
    val progressStyleOverride: ProgressStyle?,

    /**
     * Whether this widget overrides the event's own accent at all — a third state
     * [accentArgbOverride] alone cannot carry, since that column's own null already means
     * "override is Dynamic" once [hasAccentOverride] is true. Existing rows migrate to `0`/`NULL`
     * (no override), matching every binding's real behavior before this column existed — every
     * widget inherited the event's accent unconditionally.
     */
    @ColumnInfo(name = "has_accent_override", defaultValue = "0")
    val hasAccentOverride: Boolean,

    /** Packed 0xAARRGGBB, meaningful only when [hasAccentOverride] is true; null there means the override itself is Dynamic. */
    @ColumnInfo(name = "accent_argb_override")
    val accentArgbOverride: Int?,

    @ColumnInfo(name = "show_title")
    val showTitle: Boolean,

    @ColumnInfo(name = "show_emoji")
    val showEmoji: Boolean,

    @ColumnInfo(name = "show_target_date")
    val showTargetDate: Boolean,

    @ColumnInfo(name = "show_percentage")
    val showPercentage: Boolean,

    @ColumnInfo(name = "created_at")
    val createdAt: Instant,
)
