package com.countflow.core.database.dao

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Upsert
import com.countflow.core.database.entity.WidgetBindingEntity
import com.countflow.core.database.entity.WidgetBindingWithEvent
import kotlinx.coroutines.flow.Flow

/**
 * Queries over the widget bindings table.
 *
 * Reads that a widget will render from return [WidgetBindingWithEvent], so the binding and its
 * event arrive together. Fetching them in two queries would open a window in which the event has
 * been deleted but the binding has not, and the widget would have to handle a state that the
 * database's cascade already makes impossible.
 */
@Dao
interface WidgetBindingDao {

    @Transaction
    @Query("SELECT * FROM widget_bindings WHERE app_widget_id = :appWidgetId")
    fun observeBoundWidget(appWidgetId: Int): Flow<WidgetBindingWithEvent?>

    @Transaction
    @Query("SELECT * FROM widget_bindings")
    fun observeAllBoundWidgets(): Flow<List<WidgetBindingWithEvent>>

    @Transaction
    @Query("SELECT * FROM widget_bindings WHERE app_widget_id = :appWidgetId")
    suspend fun getBoundWidget(appWidgetId: Int): WidgetBindingWithEvent?

    @Transaction
    @Query("SELECT * FROM widget_bindings")
    suspend fun getAllBoundWidgets(): List<WidgetBindingWithEvent>

    @Query("SELECT * FROM widget_bindings WHERE app_widget_id = :appWidgetId")
    suspend fun getBinding(appWidgetId: Int): WidgetBindingEntity?

    @Upsert
    suspend fun upsertBinding(binding: WidgetBindingEntity)

    @Query("DELETE FROM widget_bindings WHERE app_widget_id IN (:appWidgetIds)")
    suspend fun deleteBindings(appWidgetIds: List<Int>)

    @Query("DELETE FROM widget_bindings WHERE event_id = :eventId")
    suspend fun deleteBindingsForEvent(eventId: String)

    /**
     * Removes bindings for widgets the launcher no longer knows about.
     *
     * Widgets can disappear without `onDeleted` ever arriving — a launcher crash, or a restore
     * that brings rows back for widgets that were never re-created. Left alone they accumulate
     * and cause pointless scheduling work.
     */
    @Query("DELETE FROM widget_bindings WHERE app_widget_id NOT IN (:liveAppWidgetIds)")
    suspend fun pruneOrphanedBindings(liveAppWidgetIds: Set<Int>)

    /** Removes every binding. Used when the launcher reports no live widgets at all. */
    @Query("DELETE FROM widget_bindings")
    suspend fun deleteAllBindings()
}
