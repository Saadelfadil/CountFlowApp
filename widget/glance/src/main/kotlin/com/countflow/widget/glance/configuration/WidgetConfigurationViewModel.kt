package com.countflow.widget.glance.configuration

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.countflow.core.domain.model.AccentColor
import com.countflow.core.domain.model.AppWidgetId
import com.countflow.core.domain.model.Event
import com.countflow.core.domain.model.EventId
import com.countflow.core.domain.model.ProgressStyle
import com.countflow.core.domain.model.WidgetBinding
import com.countflow.core.domain.model.WidgetStyle
import com.countflow.core.domain.repository.EventRepository
import com.countflow.core.domain.repository.WidgetBindingRepository
import com.countflow.widget.engine.provider.WidgetRenderModelProvider
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
 * Never writes a binding except in direct response to [onConfirm]. That single rule is what
 * guarantees "no orphan bindings": every intermediate step — picking an event, changing style,
 * flipping a toggle — only updates in-memory state and recomputes [WidgetConfigurationUiState.previewModel]
 * through [WidgetRenderModelProvider.preview], which touches no repository. If the user backs out
 * at any point, nothing was ever written, so there is nothing to clean up — the activity's default
 * `RESULT_CANCELED`, set before any UI is shown, tells the launcher to remove the placed widget on
 * its own. Session 9 added an entire customization step to this screen specifically *because* that
 * guarantee made it safe to: a naive "write on every change, so the preview matches the database"
 * design would have reopened exactly the risk this class was built to close in Milestone 4.
 */
@HiltViewModel
class WidgetConfigurationViewModel @Inject constructor(
    private val eventRepository: EventRepository,
    private val widgetBindingRepository: WidgetBindingRepository,
    private val renderModelProvider: WidgetRenderModelProvider,
    private val clock: Clock,
) : ViewModel() {

    private val _uiState = MutableStateFlow(WidgetConfigurationUiState())
    val uiState: StateFlow<WidgetConfigurationUiState> = _uiState.asStateFlow()

    private var appWidgetId: AppWidgetId? = null
    private var existingBinding: WidgetBinding? = null

    /** Loads step one's event list for [appWidgetId]. Safe to call more than once; only the first counts. */
    fun load(appWidgetId: AppWidgetId) {
        if (this.appWidgetId != null) return
        this.appWidgetId = appWidgetId

        viewModelScope.launch {
            val existing = widgetBindingRepository.getBinding(appWidgetId)
            existingBinding = existing
            val events = eventRepository.getAllEvents().sortedBy { it.target.epochMillis }
            _uiState.update {
                it.copy(events = events, currentEventId = existing?.eventId, isLoading = false)
            }
        }
    }

    /**
     * Moves to step two for [eventId] — customize, not save. A reconfigure of the same event
     * that already has a binding starts from its existing overrides; anything else (a fresh
     * placement, or re-pointing at a different event) starts from that event's own defaults,
     * the same `WidgetBinding.inheriting` semantics a plain event-only pick has always used.
     */
    fun onEventSelected(eventId: EventId) {
        val event = _uiState.value.events.firstOrNull { it.id == eventId } ?: return
        val existing = existingBinding?.takeIf { it.eventId == eventId }

        _uiState.update {
            it.copy(
                selectedEventId = eventId,
                widgetStyle = existing?.widgetStyleOverride ?: event.defaultWidgetStyle,
                progressStyle = existing?.progressStyleOverride ?: event.defaultProgressStyle,
                accentColor = existing?.accentColorOverride ?: event.accentColor,
                showTitle = existing?.showTitle ?: true,
                showEmoji = existing?.showEmoji ?: true,
                showTargetDate = existing?.showTargetDate ?: false,
                showPercentage = existing?.showPercentage ?: false,
            )
        }
        refreshPreview(event)
    }

    /** Back to step one, discarding step two's in-memory selections — nothing was ever written. */
    fun onChangeEvent() = _uiState.update {
        it.copy(selectedEventId = null, previewModel = null)
    }

    fun onWidgetStyleChange(style: WidgetStyle) =
        updateAndRefresh { it.copy(widgetStyle = style) }

    fun onProgressStyleChange(style: ProgressStyle) =
        updateAndRefresh { it.copy(progressStyle = style) }

    fun onShowTitleChange(show: Boolean) = updateAndRefresh { it.copy(showTitle = show) }

    fun onShowEmojiChange(show: Boolean) = updateAndRefresh { it.copy(showEmoji = show) }

    fun onShowTargetDateChange(show: Boolean) = updateAndRefresh { it.copy(showTargetDate = show) }

    fun onShowPercentageChange(show: Boolean) = updateAndRefresh { it.copy(showPercentage = show) }

    fun onAccentColorChange(accentColor: AccentColor) =
        updateAndRefresh { it.copy(accentColor = accentColor) }

    /** Writes the binding step two describes, then finishes — the one place anything is saved. */
    fun onConfirm() {
        val id = appWidgetId ?: return
        val state = _uiState.value
        val eventId = state.selectedEventId ?: return
        if (state.isSaving) return
        _uiState.update { it.copy(isSaving = true) }

        viewModelScope.launch {
            val createdAt = existingBinding?.takeIf { it.eventId == eventId }?.createdAt ?: clock.instant()
            val event = state.events.first { it.id == eventId }
            val binding = WidgetBinding(
                appWidgetId = id,
                eventId = eventId,
                widgetStyleOverride = state.widgetStyle.takeIf { it != event.defaultWidgetStyle },
                progressStyleOverride = state.progressStyle.takeIf { it != event.defaultProgressStyle },
                accentColorOverride = state.accentColor.takeIf { it != event.accentColor },
                showTitle = state.showTitle,
                showEmoji = state.showEmoji,
                showTargetDate = state.showTargetDate,
                showPercentage = state.showPercentage,
                createdAt = createdAt,
            )
            widgetBindingRepository.upsertBinding(binding)
            _uiState.update { it.copy(isSaving = false, isSaved = true) }
        }
    }

    private inline fun updateAndRefresh(transform: (WidgetConfigurationUiState) -> WidgetConfigurationUiState) {
        val next = transform(_uiState.value)
        _uiState.value = next
        val event = next.events.firstOrNull { it.id == next.selectedEventId } ?: return
        refreshPreview(event)
    }

    /**
     * Recomputes [WidgetConfigurationUiState.previewModel] from the current in-memory selections.
     *
     * Threads every selection through [previewBinding], never through a faked copy of [event] —
     * this is the exact pipeline [onConfirm] writes for real, down to
     * [WidgetRenderModelProvider.preview] calling the same
     * [com.countflow.widget.engine.mapper.WidgetRenderMapper] a real render uses, so there is no
     * separate "what the preview shows" logic that could drift from "what Save persists." An
     * earlier version faked the accent specifically by copying it onto [event]
     * (`event.copy(accentColor = it)`) rather than the binding — accurate for the preview alone,
     * but the reason Save had nothing to actually write: no field carried the choice anywhere
     * [onConfirm] could reach it.
     */
    private fun refreshPreview(event: Event) {
        val state = _uiState.value
        val previewBinding = WidgetBinding(
            appWidgetId = appWidgetId ?: AppWidgetId(AppWidgetId.INVALID),
            eventId = event.id,
            widgetStyleOverride = state.widgetStyle.takeIf { it != event.defaultWidgetStyle },
            progressStyleOverride = state.progressStyle.takeIf { it != event.defaultProgressStyle },
            accentColorOverride = state.accentColor.takeIf { it != event.accentColor },
            showTitle = state.showTitle,
            showEmoji = state.showEmoji,
            showTargetDate = state.showTargetDate,
            showPercentage = state.showPercentage,
            createdAt = clock.instant(),
        )
        val model = renderModelProvider.preview(event, previewBinding)
        _uiState.update { it.copy(previewModel = model) }
    }
}
