package com.countflow.core.data.mapper

import com.countflow.core.database.entity.WidgetBindingEntity
import com.countflow.core.database.entity.WidgetBindingWithEvent
import com.countflow.core.domain.model.AccentColor
import com.countflow.core.domain.model.AppWidgetId
import com.countflow.core.domain.model.EventId
import com.countflow.core.domain.model.WidgetBinding
import com.countflow.core.domain.repository.BoundWidget

internal fun WidgetBindingEntity.toDomain(): WidgetBinding = WidgetBinding(
    appWidgetId = AppWidgetId(appWidgetId),
    eventId = EventId(eventId),
    widgetStyleOverride = widgetStyleOverride,
    progressStyleOverride = progressStyleOverride,
    // hasAccentOverride is the "override, or inherit" state accentArgbOverride alone cannot carry
    // (its own null means the override itself is Dynamic, once an override exists at all) — see
    // WidgetBindingEntity's own KDoc.
    accentColorOverride = if (hasAccentOverride) {
        accentArgbOverride?.let(AccentColor::Fixed) ?: AccentColor.Dynamic
    } else {
        null
    },
    showTitle = showTitle,
    showEmoji = showEmoji,
    showTargetDate = showTargetDate,
    showPercentage = showPercentage,
    createdAt = createdAt,
)

internal fun WidgetBinding.toEntity(): WidgetBindingEntity = WidgetBindingEntity(
    appWidgetId = appWidgetId.value,
    eventId = eventId.value,
    widgetStyleOverride = widgetStyleOverride,
    progressStyleOverride = progressStyleOverride,
    hasAccentOverride = accentColorOverride != null,
    accentArgbOverride = (accentColorOverride as? AccentColor.Fixed)?.argb,
    showTitle = showTitle,
    showEmoji = showEmoji,
    showTargetDate = showTargetDate,
    showPercentage = showPercentage,
    createdAt = createdAt,
)

/**
 * Combines a binding with its event, or returns null when the event is missing.
 *
 * The foreign-key cascade should make a missing event impossible. "Should" is doing real work
 * there — a restore that replays rows in the wrong order, or a future schema change that drops
 * the constraint, could both produce one. Returning null lets the caller skip that widget
 * instead of throwing inside a render path, where an exception means a permanently blank widget
 * rather than a caught error.
 */
internal fun WidgetBindingWithEvent.toDomainOrNull(): BoundWidget? {
    val event = event ?: return null
    return BoundWidget(binding = binding.toDomain(), event = event.toDomain())
}
