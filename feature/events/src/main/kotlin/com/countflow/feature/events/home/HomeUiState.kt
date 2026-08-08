package com.countflow.feature.events.home

import androidx.compose.runtime.Immutable
import com.countflow.core.domain.model.EventCategory
import com.countflow.core.domain.repository.EventSort
import com.countflow.feature.events.model.EventCardUiModel

/**
 * Everything the home screen renders, in one immutable value.
 *
 * A single state object rather than several independent flows, so the screen can never draw a
 * half-updated combination — an empty-state message beside a stale list, say, because the list
 * flow emitted before the loading flag did.
 *
 * @property events the rows to draw, already mapped for display.
 * @property query the current search text.
 * @property sort the active ordering.
 * @property selectedCategories the active category filter; empty means no filter.
 * @property isLoading true until the first emission from the database.
 * @property showArchived whether archived events are included.
 */
@Immutable
data class HomeUiState(
    val events: List<EventCardUiModel> = emptyList(),
    val query: String = "",
    val sort: EventSort = EventSort.Default,
    val selectedCategories: Set<EventCategory> = emptySet(),
    val isLoading: Boolean = true,
    val showArchived: Boolean = false,
) {
    /** Whether any filter is narrowing the list. Drives which empty state is shown. */
    val hasActiveFilters: Boolean
        get() = query.isNotBlank() || selectedCategories.isNotEmpty()

    /**
     * Which empty state applies, or null when there are rows to show.
     *
     * Distinguishing the two matters: "no events yet" invites the user to create one, while
     * "nothing matches" tells them to widen the search. Showing the first when a filter is
     * active reads as though their data has vanished.
     */
    val emptyState: HomeEmptyState?
        get() = when {
            isLoading || events.isNotEmpty() -> null
            hasActiveFilters -> HomeEmptyState.NO_MATCHES
            else -> HomeEmptyState.NO_EVENTS
        }
}

/** Why the list is empty. */
enum class HomeEmptyState {
    /** The user has not created anything yet. */
    NO_EVENTS,

    /** Events exist, but the current filters exclude all of them. */
    NO_MATCHES,
}
