package com.countflow.widget.engine.provider

import com.countflow.core.domain.countdown.CountdownEngine
import com.countflow.core.domain.model.AppWidgetId
import com.countflow.core.domain.model.Event
import com.countflow.core.domain.model.WidgetBinding
import com.countflow.core.domain.repository.BoundWidget
import com.countflow.core.domain.repository.WidgetBindingRepository
import com.countflow.widget.engine.mapper.WidgetRenderMapper
import com.countflow.widget.engine.model.WidgetRenderModel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import java.time.Clock
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Loads a widget's data and turns it into a [WidgetRenderModel] — the "Room → repository →
 * countdown engine → widget engine" pipeline this milestone exists to build, collapsed into one
 * call each for the two shapes a renderer needs: [observe] for a live composition, [get] for a
 * one-shot render.
 *
 * Only [WidgetBindingRepository] is injected, not [com.countflow.core.domain.repository.EventRepository]
 * separately — [WidgetBindingRepository.observeBoundWidget] already joins the two (see
 * [BoundWidget]'s own documentation, written in Milestone 2 with this exact consumer in mind), so
 * "load event" and "load widget binding" are one query, not two.
 *
 * Uses [CountdownEngine.countdownAt] with an explicit instant and zone, rather than the
 * clock-reading [CountdownEngine.countdown] convenience, so [WidgetRenderMapper] can resolve the
 * event's target into the *same* calendar date the day count already reflects — the same reason
 * [com.countflow.feature.events.model.EventUiMapper] takes `now`/`zone` explicitly (D-030),
 * applied here for consistency-within-one-model rather than consistency-across-a-list.
 */
@Singleton
class WidgetRenderModelProvider @Inject constructor(
    private val widgetBindingRepository: WidgetBindingRepository,
    private val countdownEngine: CountdownEngine,
    private val clock: Clock,
) {

    /**
     * Observes the render model for [appWidgetId], emitting null whenever the widget is
     * unbound — which a fresh placement is, until configuration completes, and which a widget
     * whose event was deleted becomes, via the database's cascade.
     */
    fun observe(appWidgetId: AppWidgetId): Flow<WidgetRenderModel?> =
        widgetBindingRepository.observeBoundWidget(appWidgetId).map(::toRenderModelOrNull)

    /** Reads the render model for [appWidgetId] once. */
    suspend fun get(appWidgetId: AppWidgetId): WidgetRenderModel? =
        toRenderModelOrNull(widgetBindingRepository.getBoundWidget(appWidgetId))

    /**
     * Computes the render model for an [event] and [binding] the caller already has in hand —
     * no repository read, no I/O, safe to call on every keystroke. The configuration screen's
     * live preview is the reason this exists: it needs the exact same "what would this widget
     * show" logic a real render uses, for a binding that has not been (and may never be) written
     * to the database yet, since writing one speculatively would risk the very orphan-binding
     * guarantee `WidgetConfigurationActivity`'s own class doc explains.
     */
    fun preview(event: Event, binding: WidgetBinding): WidgetRenderModel {
        val countdown = countdownEngine.countdownAt(event, clock.instant(), clock.zone)
        return WidgetRenderMapper.map(event, binding, countdown, clock.zone)
    }

    private fun toRenderModelOrNull(bound: BoundWidget?): WidgetRenderModel? {
        bound ?: return null
        val countdown = countdownEngine.countdownAt(bound.event, clock.instant(), clock.zone)
        return WidgetRenderMapper.map(bound.event, bound.binding, countdown, clock.zone)
    }
}
