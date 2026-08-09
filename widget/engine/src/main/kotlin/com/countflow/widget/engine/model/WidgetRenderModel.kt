package com.countflow.widget.engine.model

import com.countflow.core.domain.countdown.CountdownLabel
import com.countflow.core.domain.model.AppWidgetId
import com.countflow.core.domain.model.EventId
import com.countflow.core.domain.model.EventTarget
import java.time.ZoneId

/**
 * Everything a widget needs to draw itself. Pure Kotlin — no Android dependency, enforced by
 * this module being `countflow.jvm.library` rather than an Android library.
 *
 * This is the entire contract between the engine and the render layer. A Glance composable
 * should read a [WidgetRenderModel] and nothing else: not [com.countflow.core.domain.model.Event],
 * not [com.countflow.core.domain.countdown.CountdownResult], not a repository. That boundary is
 * what "widgets should only render" means in practice — a widget that reaches past this model for
 * something it wants has found a gap in the engine, not a shortcut worth taking.
 *
 * [label] carries a token rather than resolved text, for the same reason
 * [com.countflow.feature.events.model.EventCardUiModel] does: Glance has `Resources` available at
 * render time (through `LocalContext`), so
 * [com.countflow.core.designsystem.format.CountdownLabelFormatter.format] should be called there,
 * not baked in here. Doing it here would also mean a background render — one the system triggers
 * outside any UI recomposition — could not react to a language change without the model being
 * rebuilt for an unrelated reason.
 *
 * [target] is exposed rather than a pre-formatted date string for the same reason: formatting a
 * calendar date is a locale decision, made once, at render time, in the layer that has a
 * `Resources` object to make it with.
 *
 * @property eventId which event this widget shows. Carried through so a click action can open
 *   the app to the right place.
 * @property appWidgetId which placed widget this model was built for.
 * @property title the event's title.
 * @property emoji the glyph to show — the event's own, or its category's default. Resolved
 *   already, because unlike a label an emoji is fixed Unicode data with no locale variant to
 *   choose between at render time.
 * @property daysRemaining whole calendar days to or from the target, always non-negative —
 *   [label] carries the direction, so a widget never has to explain a negative number.
 * @property showDaysValue whether [daysRemaining] is worth drawing at all, given [label]. See
 *   [com.countflow.core.domain.countdown.showsMeaningfulDayCount].
 * @property label the countdown's display token.
 * @property progress the resolved progress state.
 * @property theme the resolved visual treatment.
 * @property target when the event happens, for a widget that opts to show the date.
 * @property targetZone the zone the render was computed in, needed to turn [target] into a
 *   calendar date consistently with how [daysRemaining] and [label] were computed.
 * @property showTitle whether this widget draws the title.
 * @property showEmoji whether this widget draws the emoji.
 * @property showDate whether this widget draws the target date.
 * @property showPercentageText whether [progress]'s [WidgetProgress.percentText] should be drawn
 *   alongside the progress indicator, already conjoined with [WidgetProgress.isVisible] — the
 *   renderer needs only this one boolean, not a repeat of the visibility check
 *   ([com.countflow.widget.engine.mapper.WidgetRenderMapper] does the conjoining).
 * @property isCompleted the user marked the event done.
 * @property isExpired the target has passed and the user has not marked it done.
 */
data class WidgetRenderModel(
    val eventId: EventId,
    val appWidgetId: AppWidgetId,
    val title: String,
    val emoji: String,
    val daysRemaining: Int,
    val showDaysValue: Boolean,
    val label: CountdownLabel,
    val progress: WidgetProgress,
    val theme: WidgetTheme,
    val target: EventTarget,
    val targetZone: ZoneId,
    val showTitle: Boolean,
    val showEmoji: Boolean,
    val showDate: Boolean,
    val showPercentageText: Boolean,
    val isCompleted: Boolean,
    val isExpired: Boolean,
)
