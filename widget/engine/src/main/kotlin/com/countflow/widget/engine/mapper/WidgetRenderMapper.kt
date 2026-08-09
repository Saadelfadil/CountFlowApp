package com.countflow.widget.engine.mapper

import com.countflow.core.domain.countdown.CountdownResult
import com.countflow.core.domain.countdown.CountdownStatus
import com.countflow.core.domain.countdown.showsMeaningfulDayCount
import com.countflow.core.domain.model.Event
import com.countflow.core.domain.model.WidgetBinding
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
 * The single place [WidgetBinding.resolveWidgetStyle] and
 * [WidgetBinding.resolveProgressStyle] are applied on the widget side — the "override, else the
 * event's default" precedence rule that lets two widgets on one event look different exists in
 * exactly one place on the domain side (D-013) and is exercised, not re-implemented, here.
 *
 * Takes [countdown] rather than computing it, so this stays a pure function with no clock, no
 * repository, and nothing to inject — [com.countflow.widget.engine.provider.WidgetRenderModelProvider]
 * owns the orchestration and calls this once it has everything in hand.
 */
object WidgetRenderMapper {

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
        val progressStyle = binding.resolveProgressStyle(event)
        val progress = WidgetProgressEngine.calculate(countdown, progressStyle)

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
                style = binding.resolveWidgetStyle(event),
                accentColor = event.accentColor,
            ),
            target = event.target,
            targetZone = zone,
            showTitle = binding.showTitle,
            showEmoji = binding.showEmoji,
            showDate = binding.showTargetDate,
            // Conjoined here, not left for the renderer: showing "40%" makes no sense next to a
            // bar that is not drawn at all, and the renderer should not have to re-derive that.
            showPercentageText = binding.showPercentage && progress.isVisible,
            isCompleted = event.isCompleted,
            isExpired = countdown.status == CountdownStatus.EXPIRED,
        )
    }
}
