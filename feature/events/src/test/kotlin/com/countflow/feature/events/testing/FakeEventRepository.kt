package com.countflow.feature.events.testing

import com.countflow.core.domain.model.Event
import com.countflow.core.domain.model.EventId
import com.countflow.core.domain.repository.EventFilter
import com.countflow.core.domain.repository.EventRepository
import com.countflow.core.domain.repository.EventSort
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map

/**
 * An in-memory [EventRepository] for ViewModel tests.
 *
 * Deliberately does **not** re-implement filtering or sorting. Doing so would mean the ViewModel
 * tests pass against a hand-written approximation of SQL while the real query behaves
 * differently — the classic way a fake turns green tests into false confidence. Query behaviour
 * is covered where it belongs, in the DAO tests against real SQLite.
 *
 * What this fake does record is the [lastFilter] and [lastSort] it was asked for, which is the
 * ViewModel's actual responsibility: translating UI inputs into a repository call.
 */
internal class FakeEventRepository : EventRepository {

    private val events = MutableStateFlow<List<Event>>(emptyList())

    /** The filter passed to the most recent [observeEvents] call. */
    var lastFilter: EventFilter? = null
        private set

    /** The sort passed to the most recent [observeEvents] call. */
    var lastSort: EventSort? = null
        private set

    /** Ids passed to [setCompleted], with the value requested. */
    val completed = mutableListOf<Pair<String, Boolean>>()

    /** Ids passed to [setArchived], with the value requested. */
    val archived = mutableListOf<Pair<String, Boolean>>()

    /** Ids passed to [deleteEvent]. */
    val deleted = mutableListOf<String>()

    /** Publishes [values] to every active observer. */
    fun emit(values: List<Event>) {
        events.value = values
    }

    override fun observeEvents(filter: EventFilter, sort: EventSort): Flow<List<Event>> {
        lastFilter = filter
        lastSort = sort
        return events
    }

    override fun observeEvent(id: EventId): Flow<Event?> =
        events.map { list -> list.firstOrNull { it.id == id } }

    override fun observeEventsWithWidgets(): Flow<List<Event>> = events

    override suspend fun getEvent(id: EventId): Event? = events.value.firstOrNull { it.id == id }

    override suspend fun getAllEvents(): List<Event> = events.value

    override suspend fun upsertEvent(event: Event) {
        events.value = events.value.filterNot { it.id == event.id } + event
    }

    override suspend fun upsertEvents(events: List<Event>) {
        events.forEach { upsertEvent(it) }
    }

    override suspend fun deleteEvent(id: EventId) {
        deleted += id.value
        events.value = events.value.filterNot { it.id == id }
    }

    override suspend fun setArchived(id: EventId, isArchived: Boolean) {
        archived += id.value to isArchived
    }

    override suspend fun setCompleted(id: EventId, isCompleted: Boolean) {
        completed += id.value to isCompleted
    }
}
