package com.countflow.widget.glance.configuration

import androidx.compose.runtime.Immutable
import com.countflow.core.domain.model.Event
import com.countflow.core.domain.model.EventId

/**
 * The configuration screen, as one immutable value.
 *
 * @property events the user's events to choose from, soonest first.
 * @property currentEventId the event already bound to this widget, if this is a reconfigure
 *   rather than a first-time setup. Used only to highlight the existing choice.
 * @property isLoading true until the event list has loaded once.
 * @property isSaving true once the user has picked an event and the binding is being written.
 * @property isSaved true once the binding write has completed and the activity should finish
 *   with `RESULT_OK`.
 */
@Immutable
data class WidgetConfigurationUiState(
    val events: List<Event> = emptyList(),
    val currentEventId: EventId? = null,
    val isLoading: Boolean = true,
    val isSaving: Boolean = false,
    val isSaved: Boolean = false,
)
