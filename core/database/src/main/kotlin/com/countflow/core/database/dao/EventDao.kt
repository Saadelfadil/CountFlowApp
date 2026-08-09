package com.countflow.core.database.dao

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Upsert
import com.countflow.core.database.entity.EventEntity
import com.countflow.core.domain.model.EventCategory
import kotlinx.coroutines.flow.Flow

/**
 * Queries over the events table.
 *
 * Filtering and sorting happen in SQL rather than in memory. That matters more than it looks:
 * the search field re-queries on every keystroke, and loading every event to filter it in Kotlin
 * would allocate the whole table each time.
 *
 * The sort is expressed as `CASE WHEN` arms rather than string-concatenated SQL so it stays
 * compile-time verified by Room. A `@RawQuery` would be shorter and would give up that checking.
 */
@Dao
interface EventDao {

    /**
     * Observes events matching the given filters, ordered by [sort].
     *
     * @param query case-insensitive substring of the title; empty matches everything.
     * @param categories restrict to these categories; ignored when [filterByCategory] is false.
     * @param filterByCategory whether [categories] applies. Needed because SQL `IN ()` with an
     *   empty list matches nothing, which is the opposite of what "no filter" should mean.
     * @param lifecycle the name of an `EventLifecycleFilter` constant — exactly one of the three
     *   `CASE WHEN` arms below is true for any given row, since the buckets are mutually
     *   exclusive (an archived-and-completed event counts as archived).
     * @param sort the name of an `EventSort` constant.
     */
    @Query(
        """
        SELECT * FROM events
        WHERE (:query = '' OR title LIKE '%' || :query || '%' COLLATE NOCASE)
          AND (:filterByCategory = 0 OR category IN (:categories))
          AND (
            (:lifecycle = 'UPCOMING' AND is_completed = 0 AND is_archived = 0) OR
            (:lifecycle = 'COMPLETED' AND is_completed = 1 AND is_archived = 0) OR
            (:lifecycle = 'ARCHIVED' AND is_archived = 1)
          )
        ORDER BY
          CASE WHEN :sort = 'TARGET_DATE' THEN target_epoch_millis END ASC,
          CASE WHEN :sort = 'CREATED_DATE' THEN created_at END DESC,
          CASE WHEN :sort = 'TITLE' THEN title END COLLATE NOCASE ASC,
          CASE WHEN :sort = 'CATEGORY' THEN category END ASC,
          target_epoch_millis ASC
        """,
    )
    fun observeEvents(
        query: String,
        categories: Set<EventCategory>,
        filterByCategory: Boolean,
        lifecycle: String,
        sort: String,
    ): Flow<List<EventEntity>>

    @Query("SELECT * FROM events WHERE id = :id")
    fun observeEvent(id: String): Flow<EventEntity?>

    /**
     * Observes every event that at least one widget is bound to.
     *
     * The update scheduler needs exactly this set and nothing more: computing the next moment
     * any widget's text changes only requires the events actually on a home screen.
     */
    @Query(
        """
        SELECT * FROM events
        WHERE id IN (SELECT DISTINCT event_id FROM widget_bindings)
        ORDER BY target_epoch_millis ASC
        """,
    )
    fun observeEventsWithWidgets(): Flow<List<EventEntity>>

    @Query("SELECT * FROM events WHERE id = :id")
    suspend fun getEvent(id: String): EventEntity?

    @Query("SELECT * FROM events ORDER BY created_at ASC")
    suspend fun getAllEvents(): List<EventEntity>

    @Upsert
    suspend fun upsertEvent(event: EventEntity)

    @Upsert
    suspend fun upsertEvents(events: List<EventEntity>)

    /** Deletes the event. Reminders and widget bindings follow via the foreign-key cascade. */
    @Query("DELETE FROM events WHERE id = :id")
    suspend fun deleteEvent(id: String)

    @Query("UPDATE events SET is_archived = :isArchived WHERE id = :id")
    suspend fun setArchived(id: String, isArchived: Boolean)

    @Query("UPDATE events SET is_completed = :isCompleted WHERE id = :id")
    suspend fun setCompleted(id: String, isCompleted: Boolean)
}
