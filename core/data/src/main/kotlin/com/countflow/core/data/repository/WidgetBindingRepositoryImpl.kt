package com.countflow.core.data.repository

import com.countflow.core.common.di.CountFlowDispatcher
import com.countflow.core.common.di.Dispatcher
import com.countflow.core.data.mapper.toDomain
import com.countflow.core.data.mapper.toDomainOrNull
import com.countflow.core.data.mapper.toEntity
import com.countflow.core.database.dao.WidgetBindingDao
import com.countflow.core.domain.model.AppWidgetId
import com.countflow.core.domain.model.EventId
import com.countflow.core.domain.model.WidgetBinding
import com.countflow.core.domain.repository.BoundWidget
import com.countflow.core.domain.repository.WidgetBindingRepository
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

/** Room-backed [WidgetBindingRepository]. */
@Singleton
internal class WidgetBindingRepositoryImpl @Inject constructor(
    private val widgetBindingDao: WidgetBindingDao,
    @Dispatcher(CountFlowDispatcher.IO) private val ioDispatcher: CoroutineDispatcher,
) : WidgetBindingRepository {

    override fun observeBoundWidget(appWidgetId: AppWidgetId): Flow<BoundWidget?> =
        widgetBindingDao.observeBoundWidget(appWidgetId.value)
            .map { it?.toDomainOrNull() }
            .flowOn(ioDispatcher)

    override fun observeAllBoundWidgets(): Flow<List<BoundWidget>> =
        widgetBindingDao.observeAllBoundWidgets()
            .map { rows -> rows.mapNotNull { it.toDomainOrNull() } }
            .flowOn(ioDispatcher)

    override suspend fun getBoundWidget(appWidgetId: AppWidgetId): BoundWidget? =
        withContext(ioDispatcher) {
            widgetBindingDao.getBoundWidget(appWidgetId.value)?.toDomainOrNull()
        }

    override suspend fun getAllBoundWidgets(): List<BoundWidget> = withContext(ioDispatcher) {
        widgetBindingDao.getAllBoundWidgets().mapNotNull { it.toDomainOrNull() }
    }

    override suspend fun getBinding(appWidgetId: AppWidgetId): WidgetBinding? =
        withContext(ioDispatcher) {
            widgetBindingDao.getBinding(appWidgetId.value)?.toDomain()
        }

    override suspend fun upsertBinding(binding: WidgetBinding) = withContext(ioDispatcher) {
        widgetBindingDao.upsertBinding(binding.toEntity())
    }

    override suspend fun deleteBindings(appWidgetIds: List<AppWidgetId>) =
        withContext(ioDispatcher) {
            if (appWidgetIds.isNotEmpty()) {
                widgetBindingDao.deleteBindings(appWidgetIds.map { it.value })
            }
        }

    override suspend fun deleteBindingsForEvent(eventId: EventId) = withContext(ioDispatcher) {
        widgetBindingDao.deleteBindingsForEvent(eventId.value)
    }

    override suspend fun pruneOrphanedBindings(liveAppWidgetIds: Set<AppWidgetId>) =
        withContext(ioDispatcher) {
            // `NOT IN ()` over an empty set is never true in SQLite, so the generated query
            // would delete nothing at exactly the moment everything should go. Handled here
            // rather than relying on a SQL edge case behaving as intended.
            if (liveAppWidgetIds.isEmpty()) {
                widgetBindingDao.deleteAllBindings()
            } else {
                widgetBindingDao.pruneOrphanedBindings(liveAppWidgetIds.map { it.value }.toSet())
            }
        }
}
