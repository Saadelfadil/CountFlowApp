package com.countflow.feature.events.home

import androidx.compose.runtime.Immutable
import com.countflow.core.domain.model.EventCategory
import com.countflow.core.domain.repository.EventLifecycleFilter
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
 * @property tab which of the three lifecycle buckets is showing.
 */
@Immutable
data class HomeUiState(
    val events: List<EventCardUiModel> = emptyList(),
    val query: String = "",
    val sort: EventSort = EventSort.Default,
    val selectedCategories: Set<EventCategory> = emptySet(),
    val isLoading: Boolean = true,
    val tab: EventLifecycleFilter = EventLifecycleFilter.Default,
) {
    /** Whether any filter is narrowing the list. Drives which empty state is shown. */
    val hasActiveFilters: Boolean
        get() = query.isNotBlank() || selectedCategories.isNotEmpty()

    /**
     * Which empty state applies, or null when there are rows to show.
     *
     * A search/category filter takes priority over the tab's own empty copy regardless of which
     * tab is active — "nothing matches" tells the user to widen their search, where the tab's own
     * copy would read as though their data had vanished. Below that, each tab gets its own
     * message rather than one generic "no events" — what "add one" means is different on
     * Completed and Archived than it is on Upcoming.
     *
     * Deliberately not distinguishing genuine first-launch ("never created anything") from
     * "cleared out the Upcoming tab" (everything is completed or archived) — the Upcoming tab's
     * copy reads correctly either way, and a separate signal just to tell those two cases apart
     * would be more machinery than this distinction is worth.
     */
    val emptyState: HomeEmptyState?
        get() = when {
            isLoading || events.isNotEmpty() -> null
            hasActiveFilters -> HomeEmptyState.NO_MATCHES
            tab == EventLifecycleFilter.COMPLETED -> HomeEmptyState.NO_COMPLETED
            tab == EventLifecycleFilter.ARCHIVED -> HomeEmptyState.NO_ARCHIVED
            else -> HomeEmptyState.NO_UPCOMING
        }
}

/** Why the list is empty. */
enum class HomeEmptyState {
    /** The Upcoming tab has nothing in it — no events at all, or everything is done/archived. */
    NO_UPCOMING,

    /** The Completed tab has nothing in it. */
    NO_COMPLETED,

    /** The Archived tab has nothing in it. */
    NO_ARCHIVED,

    /** Events exist in this tab, but the current filters exclude all of them. */
    NO_MATCHES,
}
