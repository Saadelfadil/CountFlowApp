package com.countflow.core.data.repository

import com.countflow.core.common.di.CountFlowDispatcher
import com.countflow.core.common.di.Dispatcher
import com.countflow.core.data.mapper.toDomain
import com.countflow.core.data.mapper.toEntity
import com.countflow.core.database.dao.EventDao
import com.countflow.core.domain.model.Event
import com.countflow.core.domain.model.EventId
import com.countflow.core.domain.repository.EventFilter
import com.countflow.core.domain.repository.EventRepository
import com.countflow.core.domain.repository.EventSort
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Room-backed [EventRepository].
 *
 * Mapping runs on the IO dispatcher via [flowOn] rather than on whatever collects the flow. Room
 * already emits off the main thread, but the entity-to-domain mapping would otherwise run on the
 * collector — which for a widget is a broadcast receiver's main thread.
 */
@Singleton
internal class EventRepositoryImpl @Inject constructor(
    private val eventDao: EventDao,
    @Dispatcher(CountFlowDispatcher.IO) private val ioDispatcher: CoroutineDispatcher,
) : EventRepository {

    override fun observeEvents(filter: EventFilter, sort: EventSort): Flow<List<Event>> =
        eventDao.observeEvents(
            query = filter.query.trim(),
            categories = filter.categories,
            // SQL `IN ()` over an empty set matches nothing, which is the opposite of what an
            // empty filter should mean, so the query needs telling whether to apply it at all.
            filterByCategory = filter.categories.isNotEmpty(),
            lifecycle = filter.lifecycle.name,
            sort = sort.name,
        )
            .map { entities -> entities.map { it.toDomain() } }
            .flowOn(ioDispatcher)

    override fun observeEvent(id: EventId): Flow<Event?> =
        eventDao.observeEvent(id.value)
            .map { it?.toDomain() }
            .flowOn(ioDispatcher)

    override fun observeEventsWithWidgets(): Flow<List<Event>> =
        eventDao.observeEventsWithWidgets()
            .map { entities -> entities.map { it.toDomain() } }
            .flowOn(ioDispatcher)

    override suspend fun getEvent(id: EventId): Event? = withContext(ioDispatcher) {
        eventDao.getEvent(id.value)?.toDomain()
    }

    override suspend fun getAllEvents(): List<Event> = withContext(ioDispatcher) {
        eventDao.getAllEvents().map { it.toDomain() }
    }

    override suspend fun upsertEvent(event: Event) = withContext(ioDispatcher) {
        eventDao.upsertEvent(event.toEntity())
    }

    override suspend fun upsertEvents(events: List<Event>) = withContext(ioDispatcher) {
        eventDao.upsertEvents(events.map { it.toEntity() })
    }

    override suspend fun deleteEvent(id: EventId) = withContext(ioDispatcher) {
        eventDao.deleteEvent(id.value)
    }

    override suspend fun setArchived(id: EventId, isArchived: Boolean) =
        withContext(ioDispatcher) { eventDao.setArchived(id.value, isArchived) }

    override suspend fun setCompleted(id: EventId, isCompleted: Boolean) =
        withContext(ioDispatcher) { eventDao.setCompleted(id.value, isCompleted) }
}
