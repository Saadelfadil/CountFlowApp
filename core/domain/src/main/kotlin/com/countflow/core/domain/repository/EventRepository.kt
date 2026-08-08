package com.countflow.core.domain.repository

import com.countflow.core.domain.model.Event
import com.countflow.core.domain.model.EventCategory
import com.countflow.core.domain.model.EventId
import kotlinx.coroutines.flow.Flow

/**
 * How events are sorted in the list.
 *
 * Sorting is a domain concept rather than a UI one because it is applied in the database query,
 * not in memory — a thousand events should not be loaded to show ten.
 */
enum class EventSort {
    /** Soonest target first. The default: a countdown list is about what is coming next. */
    TARGET_DATE,
    TITLE,
    CREATED_DATE,
    CATEGORY,
    ;

    companion object {
        val Default: EventSort = TARGET_DATE
    }
}

/**
 * Which events to include.
 *
 * @property query case-insensitive substring match on the title. Blank means no filtering.
 * @property categories restrict to these categories. Empty means all.
 * @property includeArchived whether archived events are returned.
 * @property includeCompleted whether events the user marked done are returned.
 */
data class EventFilter(
    val query: String = "",
    val categories: Set<EventCategory> = emptySet(),
    val includeArchived: Boolean = false,
    val includeCompleted: Boolean = true,
) {
    companion object {
        /** Everything the main list shows by default. */
        val Default: EventFilter = EventFilter()
    }
}

/**
 * Read and write access to countdown events.
 *
 * Reads return [Flow] so the UI and the widget layer observe a single source of truth and update
 * without polling. Writes are `suspend` and return nothing — the corresponding `Flow` emits the
 * new state, so a caller never has to reconcile a returned value against what it is observing.
 *
 * Implementations live in `:core:data`. This interface is in the domain so use cases and
 * ViewModels depend on the contract rather than on Room.
 */
interface EventRepository {

    /** Observes events matching [filter], ordered by [sort]. */
    fun observeEvents(
        filter: EventFilter = EventFilter.Default,
        sort: EventSort = EventSort.Default,
    ): Flow<List<Event>>

    /** Observes a single event, emitting null if it does not exist or is deleted. */
    fun observeEvent(id: EventId): Flow<Event?>

    /**
     * Observes every event that has at least one widget bound to it.
     *
     * The widget update scheduler needs exactly this set: computing the next moment any widget's
     * text changes requires the bound events and nothing else.
     */
    fun observeEventsWithWidgets(): Flow<List<Event>>

    /** Reads a single event once. Returns null if it does not exist. */
    suspend fun getEvent(id: EventId): Event?

    /** Reads every event once, unfiltered. Used by backup. */
    suspend fun getAllEvents(): List<Event>

    /** Inserts a new event, or replaces the existing one with the same id. */
    suspend fun upsertEvent(event: Event)

    /** Inserts or replaces several events. Used by restore. */
    suspend fun upsertEvents(events: List<Event>)

    /**
     * Deletes an event and everything that depends on it.
     *
     * Reminders and widget bindings for this event are removed by the database's cascade, so a
     * widget left on the home screen becomes unbound rather than pointing at a missing row.
     */
    suspend fun deleteEvent(id: EventId)

    /** Sets the archived flag. Archived events stay out of the list but keep their widgets. */
    suspend fun setArchived(id: EventId, isArchived: Boolean)

    /** Sets the completed flag. */
    suspend fun setCompleted(id: EventId, isCompleted: Boolean)
}
