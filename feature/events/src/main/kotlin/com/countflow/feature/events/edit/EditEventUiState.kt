package com.countflow.feature.events.edit

import androidx.compose.runtime.Immutable
import com.countflow.core.domain.model.AccentColor
import com.countflow.core.domain.model.EventCategory
import com.countflow.core.domain.validation.EventValidationError
import com.countflow.widget.engine.model.WidgetRenderModel
import java.time.LocalDate
import java.time.LocalTime

/**
 * The create/edit form, as one immutable value.
 *
 * Validation errors live here as domain tokens rather than as strings, for the same reason the
 * countdown label does: the message must be resolved in the user's language at composition.
 *
 * Errors are only surfaced once the user has tried to save. Validating as they type would flag
 * an empty title the instant the form opens, which reads as the app being annoyed at them for
 * not having typed yet.
 *
 * @property isEditing true when amending an existing event rather than creating one.
 * @property title the title field.
 * @property emoji the emoji field; blank means "use the category default".
 * @property category the selected category.
 * @property date the target date.
 * @property time the target time, ignored when [isAllDay].
 * @property isAllDay whether this is a whole-day event.
 * @property accentColor the accent applied to this event and every widget showing it.
 * @property errors problems found by the last save attempt.
 * @property hasAttemptedSave whether to show [errors] at all.
 * @property isSaving a save is in flight.
 * @property isSaved the save completed and the screen should close.
 * @property isLoading true while an existing event is being fetched.
 * @property notFound the event being edited no longer exists.
 * @property previewModel what a widget showing this event would render right now, recomputed
 *   after every field change via `WidgetRenderModelProvider.preview()` — the same pure, no-I/O
 *   render path the widget configuration screen's own live preview uses (D-048). Null only when
 *   there is no date yet to preview against; every other field has a usable default from the
 *   moment the form opens, so this is null only very briefly on a fresh create.
 */
@Immutable
data class EditEventUiState(
    val isEditing: Boolean = false,
    val title: String = "",
    val emoji: String = "",
    val category: EventCategory = EventCategory.Default,
    val date: LocalDate? = null,
    val time: LocalTime = DEFAULT_TIME,
    val isAllDay: Boolean = true,
    val accentColor: AccentColor = AccentColor.Default,
    val errors: List<EventValidationError> = emptyList(),
    val hasAttemptedSave: Boolean = false,
    val isSaving: Boolean = false,
    val isSaved: Boolean = false,
    val isLoading: Boolean = false,
    val notFound: Boolean = false,
    val previewModel: WidgetRenderModel? = null,
) {
    /** Errors worth showing — none until the user has actually tried to save. */
    val visibleErrors: List<EventValidationError>
        get() = if (hasAttemptedSave) errors else emptyList()

    /**
     * Whether the save control is enabled.
     *
     * Deliberately permissive: it stays enabled while the form is invalid so that pressing it
     * reveals what is wrong. A disabled button with no explanation leaves the user guessing
     * which field the app dislikes.
     */
    val canSave: Boolean
        get() = !isSaving && !isLoading

    companion object {
        /** The time a new timed event starts at, if the user does not change it. */
        val DEFAULT_TIME: LocalTime = LocalTime.of(9, 0)
    }
}
