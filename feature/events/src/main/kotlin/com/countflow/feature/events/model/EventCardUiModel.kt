package com.countflow.feature.events.model

import androidx.compose.runtime.Immutable
import com.countflow.core.domain.countdown.CountdownLabel
import com.countflow.core.domain.model.EventCategory

/**
 * Everything one row of the event list needs to draw itself.
 *
 * Compose never sees `Event`, `CountdownResult`, or `EventTarget`. Those carry behaviour and
 * time semantics that a composable has no business reasoning about — the moment a screen can
 * reach `event.target`, someone will compute a day count in a composable and get it wrong in a
 * way no test catches.
 *
 * `@Immutable` is a promise to the compiler, not decoration: it lets Compose skip recomposing a
 * row whose model is unchanged, which is what keeps a list of a hundred countdowns smooth when
 * one of them ticks over.
 *
 * Two fields are deliberately *tokens* rather than finished strings. [label] and [category] are
 * resolved to text at composition, so switching language re-renders them; baking strings in here
 * would freeze the list in whatever locale was active when the flow last emitted.
 *
 * @property id the event's identifier as a plain string, used as the list key.
 * @property title what the user is counting down to.
 * @property emoji the event's emoji, falling back to a per-category default so a row is never
 *   visually empty.
 * @property category presentation token for the category chip.
 * @property label presentation token for the countdown text.
 * @property daysValue the headline number — whole days, unsigned.
 * @property showDaysValue false when [label] already says everything, as with "Tomorrow";
 *   showing "1" beside "Tomorrow" is noise.
 * @property progress completion from creation to target, `0f..1f`.
 * @property progressPercent the same value as a whole number, for the caption and as a cache key.
 * @property showProgress whether this event's style asks for a progress indicator.
 * @property accentArgb the event's colour, or null to follow the Material You palette.
 * @property isPast the target has gone by.
 * @property isCompleted the user marked it done.
 * @property isArchived hidden from the default list.
 */
@Immutable
data class EventCardUiModel(
    val id: String,
    val title: String,
    val emoji: String,
    val category: EventCategory,
    val label: CountdownLabel,
    val daysValue: Int,
    val showDaysValue: Boolean,
    val progress: Float,
    val progressPercent: Int,
    val showProgress: Boolean,
    val accentArgb: Int?,
    val isPast: Boolean,
    val isCompleted: Boolean,
    val isArchived: Boolean,
)
