package com.countflow.core.database.converter

import androidx.room.TypeConverter
import com.countflow.core.domain.model.EventCategory
import com.countflow.core.domain.model.ProgressStyle
import com.countflow.core.domain.model.ReminderType
import com.countflow.core.domain.model.WidgetStyle
import java.time.Instant
import java.time.LocalTime

/**
 * How non-primitive column types are stored.
 *
 * ### Enums are stored by name, never by ordinal
 *
 * Room would happily store an ordinal, and it would be smaller. It is also a live grenade:
 * inserting a constant in the middle of an enum silently reinterprets every existing row, so
 * everyone's "Birthday" events quietly become "Holiday" on the next release. Names cost a few
 * bytes and make reordering harmless. Renaming a constant is still a breaking change, and needs
 * a migration — that is a deliberate, visible act rather than an accident.
 *
 * ### Unknown names fall back rather than crash
 *
 * A row written by a newer version of the app, restored onto an older one, can contain an enum
 * name this build has never heard of. Throwing there would make the event list uncrashable only
 * by uninstalling. Falling back to the default loses a little fidelity and keeps the app usable,
 * which is the right trade for a countdown app.
 */
internal object Converters {

    // ------------------------------------------------------------------ instants

    @TypeConverter
    fun instantToEpochMillis(value: Instant?): Long? = value?.toEpochMilli()

    @TypeConverter
    fun epochMillisToInstant(value: Long?): Instant? = value?.let(Instant::ofEpochMilli)

    // ------------------------------------------------------------------ times of day

    /** Stored as seconds from midnight, so it sorts and compares in SQL. */
    @TypeConverter
    fun localTimeToSeconds(value: LocalTime?): Int? = value?.toSecondOfDay()

    /** Clamped rather than trusted: a corrupt row must not throw inside a query. */
    @TypeConverter
    fun secondsToLocalTime(value: Int?): LocalTime? =
        value?.coerceIn(0, SECONDS_PER_DAY - 1)?.let { LocalTime.ofSecondOfDay(it.toLong()) }

    // ------------------------------------------------------------------ enums

    @TypeConverter
    fun eventCategoryToName(value: EventCategory?): String? = value?.name

    @TypeConverter
    fun nameToEventCategory(value: String?): EventCategory? = value?.let { name ->
        EventCategory.entries.firstOrNull { it.name == name } ?: EventCategory.Default
    }

    @TypeConverter
    fun widgetStyleToName(value: WidgetStyle?): String? = value?.name

    @TypeConverter
    fun nameToWidgetStyle(value: String?): WidgetStyle? = value?.let { name ->
        WidgetStyle.entries.firstOrNull { it.name == name } ?: WidgetStyle.Default
    }

    @TypeConverter
    fun progressStyleToName(value: ProgressStyle?): String? = value?.name

    @TypeConverter
    fun nameToProgressStyle(value: String?): ProgressStyle? = value?.let { name ->
        ProgressStyle.entries.firstOrNull { it.name == name } ?: ProgressStyle.Default
    }

    @TypeConverter
    fun reminderTypeToName(value: ReminderType?): String? = value?.name

    @TypeConverter
    fun nameToReminderType(value: String?): ReminderType? = value?.let { name ->
        ReminderType.entries.firstOrNull { it.name == name } ?: ReminderType.DAY_OF
    }

    private const val SECONDS_PER_DAY = 24 * 60 * 60
}
