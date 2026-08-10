package com.countflow.widget.glance.configuration

import androidx.compose.runtime.Immutable
import com.countflow.core.domain.model.AccentColor
import com.countflow.core.domain.model.Event
import com.countflow.core.domain.model.EventId
import com.countflow.core.domain.model.ProgressStyle
import com.countflow.core.domain.model.WidgetStyle
import com.countflow.widget.engine.model.WidgetRenderModel

/**
 * The configuration screen, as one immutable value.
 *
 * Two steps, not two screens: [selectedEventId] being null is "choose an event"; non-null is
 * "customize it," with [previewModel] reflecting exactly what a save right now would produce.
 * Nothing here writes anything — see [com.countflow.widget.glance.configuration.WidgetConfigurationViewModel]'s
 * class doc for why that guarantee matters.
 *
 * @property events the user's events to choose from, soonest first.
 * @property currentEventId the event already bound to this widget, if this is a reconfigure
 *   rather than a first-time setup. Used only to highlight the existing choice in step one.
 * @property selectedEventId the event chosen for step two — the "which event" this widget will
 *   show, distinct from [currentEventId] once the user picks something.
 * @property widgetStyle the style being customized.
 * @property progressStyle the progress presentation being customized.
 * @property showTitle whether the event title will be drawn.
 * @property showEmoji whether the event emoji will be drawn.
 * @property showTargetDate whether the target date will be drawn.
 * @property showPercentage whether the progress percentage will be drawn alongside the bar.
 * @property accentColor the accent being customized — always a resolved value (this widget's own
 *   override if it has one, otherwise the event's), the same shape [widgetStyle]/[progressStyle]
 *   already use, so [onConfirm][com.countflow.widget.glance.configuration.WidgetConfigurationViewModel.onConfirm]
 *   derives the same "only write an override when it actually differs" precedence
 *   [com.countflow.core.domain.model.WidgetBinding] applies to style and progress style (D-013).
 * @property previewModel the render model step two's live preview draws — computed by
 *   [com.countflow.widget.engine.provider.WidgetRenderModelProvider.preview] from the selections
 *   above, the same pipeline a real widget render uses, never faked.
 * @property isLoading true until the event list has loaded once.
 * @property isSaving true once the user has confirmed and the binding is being written.
 * @property isSaved true once the binding write has completed and the activity should finish
 *   with `RESULT_OK`.
 */
@Immutable
data class WidgetConfigurationUiState(
    val events: List<Event> = emptyList(),
    val currentEventId: EventId? = null,
    val selectedEventId: EventId? = null,
    val widgetStyle: WidgetStyle = WidgetStyle.Default,
    val progressStyle: ProgressStyle = ProgressStyle.Default,
    val showTitle: Boolean = true,
    val showEmoji: Boolean = true,
    val showTargetDate: Boolean = false,
    val showPercentage: Boolean = false,
    val accentColor: AccentColor = AccentColor.Default,
    val previewModel: WidgetRenderModel? = null,
    val isLoading: Boolean = true,
    val isSaving: Boolean = false,
    val isSaved: Boolean = false,
) {
    /** Whether step two (customize) should show — an event has been chosen for this widget. */
    val isCustomizing: Boolean get() = selectedEventId != null
}
