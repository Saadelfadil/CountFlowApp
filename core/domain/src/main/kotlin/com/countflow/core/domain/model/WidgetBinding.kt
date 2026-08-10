package com.countflow.core.domain.model

import java.time.Instant

/**
 * The link between a placed home-screen widget and the event it displays.
 *
 * This is where per-widget appearance lives. The same event shown as two widgets can use two
 * different styles, which is the entire reason style is not a property of [Event]
 * (DECISIONS.md D-013). [accentColorOverride] follows the identical reasoning and pattern —
 * added when a Samsung Galaxy A55 physical-device test found the Customize Widget screen already
 * presented Accent as a per-widget setting (same picker, same step, same immediate-preview
 * behavior as Style and Progress) with no corresponding field here to actually hold that per-
 * widget choice, so a changed accent was accepted by the live preview and then silently dropped
 * on Save.
 *
 * Style/progress/accent resolution is all "override, else the event's default", expressed by
 * [resolveWidgetStyle], [resolveProgressStyle], and [resolveAccentColor] so the precedence rule
 * exists in exactly one place per field.
 *
 * Bindings are device-local: [appWidgetId] is allocated by the launcher, so these rows must be
 * excluded from cloud backup and device transfer.
 *
 * @property appWidgetId the launcher-assigned widget id. Unique per device.
 * @property eventId the event being displayed.
 * @property widgetStyleOverride style for this widget only, or null to inherit.
 * @property progressStyleOverride progress style for this widget only, or null to inherit.
 * @property accentColorOverride accent for this widget only, or null to inherit the event's own.
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
    val accentColorOverride: AccentColor?,
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

    /** The accent this widget should render with, given the [event] it is bound to. */
    fun resolveAccentColor(event: Event): AccentColor =
        accentColorOverride ?: event.accentColor

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
            accentColorOverride = null,
            showTitle = true,
            showEmoji = true,
            showTargetDate = false,
            showPercentage = false,
            createdAt = createdAt,
        )
    }
}
