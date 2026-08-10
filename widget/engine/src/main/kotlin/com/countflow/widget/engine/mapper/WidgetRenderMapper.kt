package com.countflow.widget.engine.mapper

import com.countflow.core.domain.countdown.CountdownResult
import com.countflow.core.domain.countdown.CountdownStatus
import com.countflow.core.domain.countdown.showsMeaningfulDayCount
import com.countflow.core.domain.model.Event
import com.countflow.core.domain.model.ProgressStyle
import com.countflow.core.domain.model.WidgetBinding
import com.countflow.core.domain.model.WidgetStyle
import com.countflow.core.domain.model.defaultEmoji
import com.countflow.widget.engine.model.WidgetRenderModel
import com.countflow.widget.engine.progress.WidgetProgressEngine
import com.countflow.widget.engine.theme.WidgetThemeResolver
import java.time.ZoneId
import kotlin.math.absoluteValue

/**
 * Builds a [WidgetRenderModel] from a [Event], the [WidgetBinding] pointing at it, and an
 * already-computed [CountdownResult].
 *
 * The single place [WidgetBinding.resolveWidgetStyle], [WidgetBinding.resolveProgressStyle], and
 * [WidgetBinding.resolveAccentColor] are applied on the widget side — the "override, else the
 * event's default" precedence rule that lets two widgets on one event look different exists in
 * exactly one place on the domain side (D-013) and is exercised, not re-implemented, here.
 *
 * Takes [countdown] rather than computing it, so this stays a pure function with no clock, no
 * repository, and nothing to inject — [com.countflow.widget.engine.provider.WidgetRenderModelProvider]
 * owns the orchestration and calls this once it has everything in hand.
 */
internal object WidgetRenderMapper {

    /**
     * @param event the event the widget shows.
     * @param binding the placed widget's binding.
     * @param countdown the countdown computed for [event], in [zone].
     * @param zone the zone [countdown] was computed in, needed to resolve [Event.target] into
     *   the same calendar date [countdown]'s day count already reflects.
     */
    fun map(
        event: Event,
        binding: WidgetBinding,
        countdown: CountdownResult,
        zone: ZoneId,
    ): WidgetRenderModel {
        val rawStyle = binding.resolveWidgetStyle(event)
        val rawProgressStyle = binding.resolveProgressStyle(event)
        val isLegacyProgressStyle = rawStyle == WidgetStyle.PROGRESS

        // WidgetStyle.PROGRESS is no longer selectable (see its own KDoc) — Style and Progress
        // are now independent settings, so a dedicated "this style's whole identity is its ring"
        // style is redundant with just picking ProgressStyle.CIRCULAR. A binding written before
        // this change can still legitimately hold it, though (Room stores enums by name; nothing
        // rewrites old rows), so it is reinterpreted here, once, rather than rejected: every
        // ProgressLayout* composable always drew a ring whenever progress was visible at all,
        // regardless of Linear vs Circular, so CIRCULAR is what those widgets have always
        // actually looked like — this preserves that exact appearance rather than silently
        // switching to whatever the long-ignored progress field happens to say. NONE stays NONE,
        // since that check was the one part of the old rendering that already respected it.
        val effectiveStyle = if (isLegacyProgressStyle) WidgetStyle.Default else rawStyle
        val effectiveProgressStyle = if (isLegacyProgressStyle && rawProgressStyle != ProgressStyle.NONE) {
            ProgressStyle.CIRCULAR
        } else {
            rawProgressStyle
        }
        val progress = WidgetProgressEngine.calculate(countdown, effectiveProgressStyle)

        return WidgetRenderModel(
            eventId = event.id,
            appWidgetId = binding.appWidgetId,
            title = event.title,
            emoji = event.emoji ?: event.category.defaultEmoji,
            daysRemaining = countdown.calendarDaysRemaining.absoluteValue.toInt(),
            showDaysValue = countdown.showsMeaningfulDayCount,
            label = countdown.label,
            progress = progress,
            theme = WidgetThemeResolver.resolve(
                style = effectiveStyle,
                accentColor = binding.resolveAccentColor(event),
            ),
            target = event.target,
            targetZone = zone,
            showTitle = binding.showTitle,
            showEmoji = binding.showEmoji,
            showDate = binding.showTargetDate,
            // Independent of progress.isVisible on purpose: a user can ask to see "42%" as plain
            // text with no bar or ring drawn at all (ProgressStyle.NONE) — the percentage and the
            // progress graphic are two separate choices, not one conjoined toggle.
            showPercentageText = binding.showPercentage,
            isCompleted = event.isCompleted,
            isExpired = countdown.status == CountdownStatus.EXPIRED,
        )
    }
}
