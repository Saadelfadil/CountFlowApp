package com.countflow.feature.events.edit

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.navigation.toRoute
import com.countflow.core.domain.model.AccentColor
import com.countflow.core.domain.model.Event
import com.countflow.core.domain.model.EventCategory
import com.countflow.core.domain.model.EventId
import com.countflow.core.domain.model.EventTarget
import com.countflow.core.domain.repository.EventRepository
import com.countflow.core.domain.validation.EventValidationResult
import com.countflow.core.domain.validation.errors
import com.countflow.feature.events.navigation.EditEventRoute
import com.countflow.core.domain.validation.EventValidator
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.time.Clock
import java.time.LocalDate
import java.time.LocalTime
import javax.inject.Inject

/**
 * State holder for creating and editing an event.
 *
 * One ViewModel serves both, because the form is identical and the only difference is whether it
 * starts empty or pre-filled. Splitting them would duplicate every field handler.
 *
 * Nothing reaches the repository without passing [EventValidator] first. That check lives in the
 * domain rather than here precisely so this is not the only place enforcing it — restore and,
 * later, widget configuration write events too.
 */
@HiltViewModel
class EditEventViewModel @Inject constructor(
    private val eventRepository: EventRepository,
    private val validator: EventValidator,
    private val clock: Clock,
    savedStateHandle: SavedStateHandle,
) : ViewModel() {

    /** Null when creating, set when editing. */
    private val editingId: EventId? = runCatching {
        savedStateHandle.toRoute<EditEventRoute>().eventId
    }.getOrNull()?.let(::EventId)

    private val _uiState = MutableStateFlow(
        EditEventUiState(
            isEditing = editingId != null,
            isLoading = editingId != null,
            date = LocalDate.now(clock).plusDays(DEFAULT_DAYS_AHEAD),
        ),
    )
    val uiState: StateFlow<EditEventUiState> = _uiState.asStateFlow()

    /** The event being edited, kept so unedited fields survive a save unchanged. */
    private var loadedEvent: Event? = null

    init {
        editingId?.let(::load)
    }

    private fun load(id: EventId) {
        viewModelScope.launch {
            val event = eventRepository.getEvent(id)
            if (event == null) {
                _uiState.update { it.copy(isLoading = false, notFound = true) }
                return@launch
            }

            loadedEvent = event
            val start = event.target.startAt(clock.zone)
            _uiState.update {
                it.copy(
                    isLoading = false,
                    title = event.title,
                    emoji = event.emoji.orEmpty(),
                    category = event.category,
                    date = start.toLocalDate(),
                    time = start.toLocalTime(),
                    isAllDay = event.target.isAllDay,
                    accentColor = event.accentColor,
                )
            }
        }
    }

    fun onTitleChange(title: String) = _uiState.update { it.copy(title = title) }

    fun onEmojiChange(emoji: String) = _uiState.update { it.copy(emoji = emoji) }

    fun onCategoryChange(category: EventCategory) =
        _uiState.update { it.copy(category = category) }

    fun onDateChange(date: LocalDate) = _uiState.update { it.copy(date = date) }

    fun onTimeChange(time: LocalTime) = _uiState.update { it.copy(time = time) }

    fun onAllDayChange(isAllDay: Boolean) = _uiState.update { it.copy(isAllDay = isAllDay) }

    fun onAccentColorChange(accentColor: AccentColor) =
        _uiState.update { it.copy(accentColor = accentColor) }

    /**
     * Validates and persists.
     *
     * Sets `hasAttemptedSave` before validating so any resulting errors become visible — this is
     * the moment the form earns the right to complain.
     */
    fun onSave() {
        val state = _uiState.value
        if (!state.canSave) return

        val target = buildTarget(state) ?: run {
            // No date chosen. The picker defaults to one, so this is only reachable if a future
            // change makes the field clearable.
            _uiState.update { it.copy(hasAttemptedSave = true) }
            return
        }

        val emoji = state.emoji.trim().ifBlank { null }
        val result = validator.validate(title = state.title.trim(), emoji = emoji, target = target)

        if (result is EventValidationResult.Invalid) {
            _uiState.update {
                it.copy(hasAttemptedSave = true, errors = result.errors, isSaving = false)
            }
            return
        }

        _uiState.update { it.copy(hasAttemptedSave = true, errors = emptyList(), isSaving = true) }

        viewModelScope.launch {
            val existing = loadedEvent
            val event = existing?.copy(
                title = state.title.trim(),
                emoji = emoji,
                category = state.category,
                target = target,
                accentColor = state.accentColor,
            ) ?: Event.create(
                title = state.title.trim(),
                target = target,
                createdAt = clock.instant(),
                emoji = emoji,
                category = state.category,
                accentColor = state.accentColor,
            )

            eventRepository.upsertEvent(event)
            _uiState.update { it.copy(isSaving = false, isSaved = true) }
        }
    }

    /**
     * Builds the target from the form.
     *
     * The all-day toggle is not cosmetic — it selects between two genuinely different kinds of
     * target. An all-day event follows the device as it travels; a timed one stays pinned to the
     * zone it was authored in.
     */
    private fun buildTarget(state: EditEventUiState): EventTarget? {
        val date = state.date ?: return null
        return if (state.isAllDay) {
            EventTarget.allDay(date, clock.zone)
        } else {
            EventTarget.timed(date.atTime(state.time), clock.zone)
        }
    }

    private companion object {
        /** A new event defaults to a week out rather than today, which is rarely what is meant. */
        const val DEFAULT_DAYS_AHEAD = 7L
    }
}
