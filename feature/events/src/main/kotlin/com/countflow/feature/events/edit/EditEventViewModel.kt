package com.countflow.feature.events.edit

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.navigation.toRoute
import com.countflow.core.domain.model.AccentColor
import com.countflow.core.domain.model.AppWidgetId
import com.countflow.core.domain.model.Event
import com.countflow.core.domain.model.EventCategory
import com.countflow.core.domain.model.EventId
import com.countflow.core.domain.model.EventTarget
import com.countflow.core.domain.model.Reminder
import com.countflow.core.domain.model.ReminderType
import com.countflow.core.domain.model.WidgetBinding
import com.countflow.core.domain.repository.EventRepository
import com.countflow.core.domain.repository.ReminderRepository
import com.countflow.core.domain.validation.EventValidationResult
import com.countflow.core.domain.validation.errors
import com.countflow.feature.events.navigation.EditEventRoute
import com.countflow.core.domain.validation.EventValidator
import com.countflow.widget.engine.provider.WidgetRenderModelProvider
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
 *
 * Also owns [EditEventUiState.previewModel], recomputed after every field change through
 * [WidgetRenderModelProvider.preview] — the exact pure, no-I/O render path the widget
 * configuration screen's own live preview already uses (D-048), reused here rather than
 * re-derived so this form's preview can never quietly drift from what a real widget would show.
 * Nothing about this preview is ever written: [refreshPreview] constructs an [Event] and
 * [WidgetBinding] that exist only in memory, the same discipline [onSave] itself already follows
 * for the real write.
 */
@HiltViewModel
class EditEventViewModel @Inject constructor(
    private val eventRepository: EventRepository,
    private val reminderRepository: ReminderRepository,
    private val validator: EventValidator,
    private val renderModelProvider: WidgetRenderModelProvider,
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

    /**
     * This event's existing reminders, keyed by type. Reused by [buildReminders] so a save that
     * leaves a reminder's selection unchanged preserves its identity and
     * [Reminder.deliveredForScheduledTime] — a reminder that already fired must not be handed a
     * fresh id and re-armed by an unrelated field edit.
     */
    private var loadedReminders: Map<ReminderType, Reminder> = emptyMap()

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
            val reminders = reminderRepository.getRemindersForEvent(id)
            loadedReminders = reminders.associateBy { it.type }
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
                    selectedReminderTypes = reminders.filter { it.isEnabled }.map { it.type }.toSet(),
                )
            }
            refreshPreview()
        }
    }

    /** Turns one reminder offset on or off. There is no separate master switch to keep in sync. */
    fun onReminderTypeToggle(type: ReminderType, enabled: Boolean) {
        _uiState.update {
            it.copy(
                selectedReminderTypes = if (enabled) {
                    it.selectedReminderTypes + type
                } else {
                    it.selectedReminderTypes - type
                },
            )
        }
    }

    fun onTitleChange(title: String) {
        _uiState.update { it.copy(title = title) }
        refreshPreview()
    }

    fun onEmojiChange(emoji: String) {
        _uiState.update { it.copy(emoji = emoji) }
        refreshPreview()
    }

    fun onCategoryChange(category: EventCategory) {
        _uiState.update { it.copy(category = category) }
        refreshPreview()
    }

    fun onDateChange(date: LocalDate) {
        _uiState.update { it.copy(date = date) }
        refreshPreview()
    }

    fun onTimeChange(time: LocalTime) {
        _uiState.update { it.copy(time = time) }
        refreshPreview()
    }

    fun onAllDayChange(isAllDay: Boolean) {
        _uiState.update { it.copy(isAllDay = isAllDay) }
        refreshPreview()
    }

    fun onAccentColorChange(accentColor: AccentColor) {
        _uiState.update { it.copy(accentColor = accentColor) }
        refreshPreview()
    }

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
            val event = buildEvent(state, target, state.title.trim(), emoji)
            eventRepository.upsertEvent(event)
            reminderRepository.replaceRemindersForEvent(
                eventId = event.id,
                reminders = buildReminders(state, event),
            )
            _uiState.update { it.copy(isSaving = false, isSaved = true) }
        }
    }

    /**
     * Recomputes [EditEventUiState.previewModel] from the form's current field values.
     *
     * Runs synchronously — no repository read, no I/O — through the exact same
     * [WidgetRenderModelProvider.preview] the widget configuration screen already uses for its
     * own live preview (D-048), so this form's preview can never show something a real widget
     * wouldn't. Nothing it builds is written: [buildEvent] and the [WidgetBinding] below exist
     * only for this one call.
     *
     * Null when there is nothing meaningful to preview yet — a blank title, or (not reachable
     * today, since the date picker always has a default) no date.
     */
    private fun refreshPreview() {
        val state = _uiState.value
        val title = state.title.trim()
        val target = buildTarget(state)
        if (title.isBlank() || target == null) {
            _uiState.update { it.copy(previewModel = null) }
            return
        }

        val emoji = state.emoji.trim().ifBlank { null }
        val event = buildEvent(state, target, title, emoji)
        val binding = WidgetBinding.inheriting(
            appWidgetId = AppWidgetId(AppWidgetId.INVALID),
            eventId = event.id,
            createdAt = clock.instant(),
        )
        val model = renderModelProvider.preview(event, binding)
        _uiState.update { it.copy(previewModel = model) }
    }

    /**
     * Applies the form's fields onto [loadedEvent], or builds a fresh [Event] when creating.
     *
     * Shared between [onSave] (the real write) and [refreshPreview] (never written) so the two
     * can never quietly diverge on what a save would actually produce.
     */
    private fun buildEvent(
        state: EditEventUiState,
        target: EventTarget,
        title: String,
        emoji: String?,
    ): Event = loadedEvent?.copy(
        title = title,
        emoji = emoji,
        category = state.category,
        target = target,
        accentColor = state.accentColor,
        remindersEnabled = state.selectedReminderTypes.isNotEmpty(),
    ) ?: Event.create(
        title = title,
        target = target,
        createdAt = clock.instant(),
        emoji = emoji,
        category = state.category,
        accentColor = state.accentColor,
        remindersEnabled = state.selectedReminderTypes.isNotEmpty(),
    )

    /**
     * Builds the reminder set a save should persist.
     *
     * A still-selected type reuses its existing [Reminder] — same id, same
     * [Reminder.deliveredForScheduledTime] — so a save that does not touch reminders (editing only
     * the title, say) cannot silently un-resolve one that already fired. Every reminder, reused or
     * freshly created, is then run through [Reminder.withPastTriggerResolved] against *this* save's
     * event and instant: a still-future reminder is untouched, but one whose trigger this very
     * edit just moved into the past — pulling the date closer, say — is silently resolved rather
     * than firing the moment the save completes, the same "never fire an already-past trigger"
     * rule applied uniformly, not just for brand-new selections.
     */
    private fun buildReminders(state: EditEventUiState, event: Event): List<Reminder> =
        state.selectedReminderTypes.map { type ->
            val reminder = loadedReminders[type] ?: Reminder.of(eventId = event.id, type = type)
            reminder.withPastTriggerResolved(event, clock.instant(), clock.zone)
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
