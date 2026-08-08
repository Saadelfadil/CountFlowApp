package com.countflow.core.domain.repository

import com.countflow.core.domain.model.AppWidgetId
import com.countflow.core.domain.model.Event
import com.countflow.core.domain.model.EventId
import com.countflow.core.domain.model.WidgetBinding
import kotlinx.coroutines.flow.Flow

/**
 * A widget binding together with the event it points at.
 *
 * Widgets almost always need both, and fetching them separately would mean two queries and a
 * window in which the event has been deleted but the binding has not.
 */
data class BoundWidget(
    val binding: WidgetBinding,
    val event: Event,
)

/**
 * Read and write access to the mapping between placed widgets and events.
 *
 * Bindings are device-local — [AppWidgetId] values are allocated by the launcher — so this data
 * is deliberately excluded from cloud backup and device transfer.
 */
interface WidgetBindingRepository {

    /** Observes a single widget's binding and event, emitting null if the widget is unbound. */
    fun observeBoundWidget(appWidgetId: AppWidgetId): Flow<BoundWidget?>

    /** Observes every bound widget. The update scheduler uses this to find what needs refreshing. */
    fun observeAllBoundWidgets(): Flow<List<BoundWidget>>

    /** Reads one widget's binding and event once. Null if unbound. */
    suspend fun getBoundWidget(appWidgetId: AppWidgetId): BoundWidget?

    /** Reads every bound widget once. */
    suspend fun getAllBoundWidgets(): List<BoundWidget>

    /** Reads the raw binding without resolving its event. */
    suspend fun getBinding(appWidgetId: AppWidgetId): WidgetBinding?

    /** Creates or replaces a binding. Called when widget configuration completes. */
    suspend fun upsertBinding(binding: WidgetBinding)

    /**
     * Removes bindings for widgets the user has deleted.
     *
     * Called from the widget receiver's `onDeleted`, which hands over a batch of ids.
     */
    suspend fun deleteBindings(appWidgetIds: List<AppWidgetId>)

    /** Removes every binding pointing at [eventId]. */
    suspend fun deleteBindingsForEvent(eventId: EventId)

    /**
     * Discards bindings whose widget no longer exists on any home screen.
     *
     * A widget can be removed without `onDeleted` arriving — during a launcher crash, or a
     * restore from backup that brings rows back for widgets that were never re-created. Left
     * alone these accumulate and cause pointless scheduling work, so the app reconciles against
     * the launcher's live id list at startup.
     *
     * @param liveAppWidgetIds the ids the launcher currently reports.
     */
    suspend fun pruneOrphanedBindings(liveAppWidgetIds: Set<AppWidgetId>)
}
