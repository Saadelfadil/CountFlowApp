package com.countflow.widget.engine.testing

import com.countflow.core.domain.model.AppWidgetId
import com.countflow.core.domain.model.Event
import com.countflow.core.domain.model.EventId
import com.countflow.core.domain.model.WidgetBinding
import com.countflow.core.domain.repository.BoundWidget
import com.countflow.core.domain.repository.WidgetBindingRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map

/**
 * An in-memory [WidgetBindingRepository] for testing [com.countflow.widget.engine.provider.WidgetRenderModelProvider]
 * and [com.countflow.widget.engine.lifecycle.WidgetLifecycleCoordinator] without a database.
 *
 * Deliberately small and local to this module — a near-identical fake already exists in
 * `:feature:events`'s test sources, and duplicating a ~40-line fake once is cheaper than the
 * cross-module test-fixture module a shared version would need. Worth revisiting under the rule
 * of three if a third consumer appears; noted in KNOWN_ISSUES.md.
 */
internal class FakeWidgetBindingRepository {

    private val bound = MutableStateFlow<Map<AppWidgetId, BoundWidget>>(emptyMap())

    /** The ids passed to the most recent [WidgetBindingRepository.deleteBindings] call. */
    var deletedBatch: List<AppWidgetId>? = null
        private set

    /** The set passed to the most recent [WidgetBindingRepository.pruneOrphanedBindings] call. */
    var prunedAgainst: Set<AppWidgetId>? = null
        private set

    fun put(binding: WidgetBinding, event: Event) {
        bound.value = bound.value + (binding.appWidgetId to BoundWidget(binding, event))
    }

    val repository: WidgetBindingRepository = object : WidgetBindingRepository {
        override fun observeBoundWidget(appWidgetId: AppWidgetId): Flow<BoundWidget?> =
            bound.map { it[appWidgetId] }

        override fun observeAllBoundWidgets(): Flow<List<BoundWidget>> =
            bound.map { it.values.toList() }

        override suspend fun getBoundWidget(appWidgetId: AppWidgetId): BoundWidget? =
            bound.value[appWidgetId]

        override suspend fun getAllBoundWidgets(): List<BoundWidget> = bound.value.values.toList()

        override suspend fun getBinding(appWidgetId: AppWidgetId): WidgetBinding? =
            bound.value[appWidgetId]?.binding

        override suspend fun upsertBinding(binding: WidgetBinding) {
            val event = bound.value[binding.appWidgetId]?.event
                ?: error("FakeWidgetBindingRepository.upsertBinding needs put() first in tests")
            bound.value = bound.value + (binding.appWidgetId to BoundWidget(binding, event))
        }

        override suspend fun deleteBindings(appWidgetIds: List<AppWidgetId>) {
            deletedBatch = appWidgetIds
            bound.value = bound.value - appWidgetIds.toSet()
        }

        override suspend fun deleteBindingsForEvent(eventId: EventId) {
            bound.value = bound.value.filterValues { it.event.id != eventId }
        }

        override suspend fun pruneOrphanedBindings(liveAppWidgetIds: Set<AppWidgetId>) {
            prunedAgainst = liveAppWidgetIds
            bound.value = bound.value.filterKeys { it in liveAppWidgetIds }
        }
    }
}
