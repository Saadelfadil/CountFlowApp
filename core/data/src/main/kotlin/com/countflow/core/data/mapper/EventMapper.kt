package com.countflow.core.data.mapper

import com.countflow.core.database.entity.EventEntity
import com.countflow.core.domain.model.AccentColor
import com.countflow.core.domain.model.Event
import com.countflow.core.domain.model.EventId
import com.countflow.core.domain.model.EventTarget

/**
 * Translation between the storage row and the domain model.
 *
 * Two shape differences are handled here, and nowhere else:
 *
 * - **Accent colour.** The column is a nullable integer where null means "follow the wallpaper";
 *   the domain has a sealed [AccentColor] so that intent cannot be confused with "unset". This
 *   file is the single place that knows null means [AccentColor.Dynamic].
 * - **Target.** Three columns collapse into one [EventTarget] value object.
 *
 * Round-tripping is covered by tests: any field that maps asymmetrically is a data-loss bug that
 * only shows up after a user restarts the app.
 */
internal fun EventEntity.toDomain(): Event = Event(
    id = EventId(id),
    title = title,
    emoji = emoji,
    iconKey = iconKey,
    category = category,
    target = EventTarget(
        epochMillis = targetEpochMillis,
        zoneId = targetZoneId,
        isAllDay = isAllDay,
    ),
    createdAt = createdAt,
    accentColor = accentArgb?.let(AccentColor::Fixed) ?: AccentColor.Dynamic,
    defaultWidgetStyle = defaultWidgetStyle,
    defaultProgressStyle = defaultProgressStyle,
    remindersEnabled = remindersEnabled,
    isArchived = isArchived,
    isCompleted = isCompleted,
)

internal fun Event.toEntity(): EventEntity = EventEntity(
    id = id.value,
    title = title,
    emoji = emoji,
    iconKey = iconKey,
    category = category,
    targetEpochMillis = target.epochMillis,
    targetZoneId = target.zoneId,
    isAllDay = target.isAllDay,
    createdAt = createdAt,
    accentArgb = when (val accent = accentColor) {
        is AccentColor.Fixed -> accent.argb
        AccentColor.Dynamic -> null
    },
    defaultWidgetStyle = defaultWidgetStyle,
    defaultProgressStyle = defaultProgressStyle,
    remindersEnabled = remindersEnabled,
    isArchived = isArchived,
    isCompleted = isCompleted,
)
