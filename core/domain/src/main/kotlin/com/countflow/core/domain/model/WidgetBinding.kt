package com.countflow.core.domain.model

import java.time.Instant

/**
 * The link between a placed home-screen widget and the event it displays.
 *
 * This is where per-widget appearance lives. The same event shown as two widgets can use two
 * different styles, which is the entire reason style is not a property of [Event]
 * (DECISIONS.md D-013).
 *
 * Style resolution is "override, else the event's default", expressed by [resolveWidgetStyle]
 * and [resolveProgressStyle] so the precedence rule exists in exactly one place.
 *
 * Bindings are device-local: [appWidgetId] is allocated by the launcher, so these rows must be
 * excluded from cloud backup and device transfer.
 *
 * @property appWidgetId the launcher-assigned widget id. Unique per device.
 * @property eventId the event being displayed.
 * @property widgetStyleOverride style for this widget only, or null to inherit.
 * @property progressStyleOverride progress style for this widget only, or null to inherit.
 * @property showTitle whether the event title is drawn.
 * @property showEmoji whether the event emoji is drawn.
 * @property showTargetDate whether the target date is drawn.
 * @property showPercentage whether the completion percentage is drawn.
 * @property createdAt when the widget was placed.
 */
data class WidgetBinding(
    val appWidgetId: AppWidgetId,
    val eventId: EventId,
    val widgetStyleOverride: WidgetStyle?,
    val progressStyleOverride: ProgressStyle?,
    val showTitle: Boolean,
    val showEmoji: Boolean,
    val showTargetDate: Boolean,
    val showPercentage: Boolean,
    val createdAt: Instant,
) {

    /** The style this widget should render with, given the [event] it is bound to. */
    fun resolveWidgetStyle(event: Event): WidgetStyle =
        widgetStyleOverride ?: event.defaultWidgetStyle

    /** The progress style this widget should render with, given the [event] it is bound to. */
    fun resolveProgressStyle(event: Event): ProgressStyle =
        progressStyleOverride ?: event.defaultProgressStyle

    companion object {

        /**
         * Creates a binding that inherits all appearance from the event.
         *
         * This is what widget configuration produces on first placement; the user can then
         * override individual aspects.
         */
        fun inheriting(
            appWidgetId: AppWidgetId,
            eventId: EventId,
            createdAt: Instant,
        ): WidgetBinding = WidgetBinding(
            appWidgetId = appWidgetId,
            eventId = eventId,
            widgetStyleOverride = null,
            progressStyleOverride = null,
            showTitle = true,
            showEmoji = true,
            showTargetDate = false,
            showPercentage = false,
            createdAt = createdAt,
        )
    }
}
