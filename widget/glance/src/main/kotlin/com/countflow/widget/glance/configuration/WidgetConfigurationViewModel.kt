package com.countflow.widget.glance.configuration

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.countflow.core.domain.model.AppWidgetId
import com.countflow.core.domain.model.EventId
import com.countflow.core.domain.model.WidgetBinding
import com.countflow.core.domain.repository.EventRepository
import com.countflow.core.domain.repository.WidgetBindingRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.time.Clock
import javax.inject.Inject

/**
 * State holder for widget configuration.
 *
 * Takes the widget id through [load] rather than a `SavedStateHandle`. The value comes from the
 * launching `Intent`'s extras, which a Hilt-injected `SavedStateHandle` does not populate for a
 * plain `ComponentActivity` without extra wiring — passing it explicitly is one indirection
 * fewer and easier to verify than getting that wiring right.
 *
 * Never writes a binding except in direct response to [onEventSelected]. That single rule is
 * what guarantees "no orphan bindings": if the user backs out without picking anything, nothing
 * was ever written, so there is nothing to clean up — the activity's default `RESULT_CANCELED`,
 * set before any UI is shown, tells the launcher to remove the placed widget on its own.
 */
@HiltViewModel
class WidgetConfigurationViewModel @Inject constructor(
    private val eventRepository: EventRepository,
    private val widgetBindingRepository: WidgetBindingRepository,
    private val clock: Clock,
) : ViewModel() {

    private val _uiState = MutableStateFlow(WidgetConfigurationUiState())
    val uiState: StateFlow<WidgetConfigurationUiState> = _uiState.asStateFlow()

    private var appWidgetId: AppWidgetId? = null

    /** Loads the picker for [appWidgetId]. Safe to call more than once; only the first counts. */
    fun load(appWidgetId: AppWidgetId) {
        if (this.appWidgetId != null) return
        this.appWidgetId = appWidgetId

        viewModelScope.launch {
            val existing = widgetBindingRepository.getBinding(appWidgetId)
            val events = eventRepository.getAllEvents().sortedBy { it.target.epochMillis }
            _uiState.update {
                it.copy(events = events, currentEventId = existing?.eventId, isLoading = false)
            }
        }
    }

    /** Binds [eventId] to this widget, inheriting the event's default appearance. */
    fun onEventSelected(eventId: EventId) {
        val id = appWidgetId ?: return
        if (_uiState.value.isSaving) return
        _uiState.update { it.copy(isSaving = true) }

        viewModelScope.launch {
            val existing = widgetBindingRepository.getBinding(id)
            val binding = existing?.takeIf { it.eventId == eventId }
                ?: WidgetBinding.inheriting(id, eventId, clock.instant())

            widgetBindingRepository.upsertBinding(binding)
            _uiState.update { it.copy(isSaving = false, isSaved = true) }
        }
    }
}
