package com.countflow.feature.events.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.countflow.core.domain.model.EventCategory
import com.countflow.core.domain.model.EventId
import com.countflow.core.domain.repository.EventFilter
import com.countflow.core.domain.repository.EventRepository
import com.countflow.core.domain.repository.EventSort
import com.countflow.feature.events.model.EventCardUiModel
import com.countflow.feature.events.model.EventUiMapper
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.Clock
import javax.inject.Inject
import kotlin.time.Duration.Companion.milliseconds

/**
 * State holder for the event list.
 *
 * Owns the search, sort, and filter inputs, combines them with the repository, and exposes one
 * immutable [HomeUiState]. The screen reads state and sends events; it computes nothing.
 *
 * The search text is held separately from the other inputs, and for a specific reason. The text
 * field must show what the user typed *immediately*, while the database should only be queried
 * once they pause — so the raw value feeds the state and a debounced copy feeds the query. A
 * single debounced input would make the field feel laggy; debouncing the whole input set would
 * also add a quarter-second to every sort tap, which come from deliberate presses and should
 * feel instant.
 */
@HiltViewModel
class EventsViewModel @Inject constructor(
    private val eventRepository: EventRepository,
    private val uiMapper: EventUiMapper,
    private val clock: Clock,
) : ViewModel() {

    private val searchQuery = MutableStateFlow("")
    private val listOptions = MutableStateFlow(ListOptions())

    @OptIn(FlowPreview::class, ExperimentalCoroutinesApi::class)
    private val events: Flow<List<EventCardUiModel>> = combine(
        searchQuery.debounce { text -> if (text.isBlank()) NO_DELAY else QUERY_DEBOUNCE },
        listOptions,
    ) { query, options -> query to options }
        .flatMapLatest { (query, options) ->
            eventRepository.observeEvents(
                filter = EventFilter(
                    query = query,
                    categories = options.categories,
                    includeArchived = options.showArchived,
                ),
                sort = options.sort,
            ).map { domainEvents ->
                // One instant for the whole list, so two identical events can never disagree
                // about what day it is.
                uiMapper.mapAll(domainEvents, clock.instant(), clock.zone)
            }
        }

    val uiState: StateFlow<HomeUiState> =
        combine(events, searchQuery, listOptions) { cards, query, options ->
            HomeUiState(
                events = cards,
                query = query,
                sort = options.sort,
                selectedCategories = options.categories,
                isLoading = false,
                showArchived = options.showArchived,
            )
        }.stateIn(
            scope = viewModelScope,
            // Survives a configuration change without re-querying, but releases the database
            // subscription once the screen is genuinely gone.
            started = SharingStarted.WhileSubscribed(SUBSCRIPTION_TIMEOUT_MS),
            initialValue = HomeUiState(),
        )

    /** Updates the search text. Reflected in the field at once, in the list after a pause. */
    fun onQueryChange(query: String) {
        searchQuery.value = query
    }

    /** Clears the search text. */
    fun onClearQuery() {
        searchQuery.value = ""
    }

    /** Changes the ordering. */
    fun onSortChange(sort: EventSort) {
        listOptions.value = listOptions.value.copy(sort = sort)
    }

    /** Adds or removes a category from the filter. */
    fun onCategoryToggle(category: EventCategory) {
        val current = listOptions.value.categories
        listOptions.value = listOptions.value.copy(
            categories = if (category in current) current - category else current + category,
        )
    }

    /** Clears every filter and the search text. */
    fun onClearFilters() {
        searchQuery.value = ""
        listOptions.value = listOptions.value.copy(categories = emptySet())
    }

    /** Shows or hides archived events. */
    fun onShowArchivedChange(showArchived: Boolean) {
        listOptions.value = listOptions.value.copy(showArchived = showArchived)
    }

    /** Marks an event complete, or reopens it. */
    fun onCompletedChange(id: String, isCompleted: Boolean) {
        viewModelScope.launch { eventRepository.setCompleted(EventId(id), isCompleted) }
    }

    /** Archives an event, or restores it. */
    fun onArchivedChange(id: String, isArchived: Boolean) {
        viewModelScope.launch { eventRepository.setArchived(EventId(id), isArchived) }
    }

    /**
     * Deletes an event permanently.
     *
     * Cascades to its reminders and widget bindings, so a widget showing it becomes unbound
     * rather than pointing at a row that no longer exists.
     */
    fun onDelete(id: String) {
        viewModelScope.launch { eventRepository.deleteEvent(EventId(id)) }
    }

    private data class ListOptions(
        val sort: EventSort = EventSort.Default,
        val categories: Set<EventCategory> = emptySet(),
        val showArchived: Boolean = false,
    )

    private companion object {
        val QUERY_DEBOUNCE = 250.milliseconds
        val NO_DELAY = 0.milliseconds
        const val SUBSCRIPTION_TIMEOUT_MS = 5_000L
    }
}
