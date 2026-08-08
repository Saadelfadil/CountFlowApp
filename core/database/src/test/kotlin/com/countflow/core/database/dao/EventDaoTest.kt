package com.countflow.core.database.dao

import app.cash.turbine.test
import com.countflow.core.database.DatabaseTestCase
import com.countflow.core.domain.model.EventCategory
import com.countflow.core.domain.repository.EventSort
import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Test
import java.time.Instant

/**
 * The event query, exercised against real SQLite.
 *
 * Everything here was previously assumed rather than verified: that the `CASE WHEN` sort arms
 * order correctly, that an empty category set means "no filter" rather than "match nothing",
 * and that `COLLATE NOCASE` does what the search box needs.
 */
internal class EventDaoTest : DatabaseTestCase() {

    private val dao get() = database.eventDao()

    /** Mirrors the defaults `EventRepositoryImpl` passes, so tests read like real calls. */
    private suspend fun query(
        text: String = "",
        categories: Set<EventCategory> = emptySet(),
        includeArchived: Boolean = false,
        includeCompleted: Boolean = true,
        sort: EventSort = EventSort.TARGET_DATE,
    ) = dao.observeEvents(
        query = text,
        categories = categories,
        filterByCategory = categories.isNotEmpty(),
        includeArchived = includeArchived,
        includeCompleted = includeCompleted,
        sort = sort.name,
    ).first().map { it.id }

    // ---------------------------------------------------------------- sorting

    @Test
    fun `sorts by target date soonest first`() = runTest {
        dao.upsertEvents(
            listOf(
                event("c", targetEpochMillis = 3_000),
                event("a", targetEpochMillis = 1_000),
                event("b", targetEpochMillis = 2_000),
            ),
        )

        assertThat(query(sort = EventSort.TARGET_DATE)).containsExactly("a", "b", "c").inOrder()
    }

    @Test
    fun `sorts by title case-insensitively`() = runTest {
        // Without COLLATE NOCASE, SQLite orders uppercase before lowercase and "apple" would
        // sort after "Banana".
        dao.upsertEvents(
            listOf(
                event("b", title = "Banana"),
                event("a", title = "apple"),
                event("c", title = "Cherry"),
            ),
        )

        assertThat(query(sort = EventSort.TITLE)).containsExactly("a", "b", "c").inOrder()
    }

    @Test
    fun `sorts by created date newest first`() = runTest {
        dao.upsertEvents(
            listOf(
                event("old", createdAt = Instant.ofEpochMilli(1_000)),
                event("new", createdAt = Instant.ofEpochMilli(3_000)),
                event("mid", createdAt = Instant.ofEpochMilli(2_000)),
            ),
        )

        assertThat(query(sort = EventSort.CREATED_DATE))
            .containsExactly("new", "mid", "old").inOrder()
    }

    @Test
    fun `sorts by category then falls back to target date`() = runTest {
        // The trailing `target_epoch_millis ASC` in the query is what makes ties deterministic;
        // without it the order within a category would be whatever SQLite happened to return.
        dao.upsertEvents(
            listOf(
                event("work-later", category = EventCategory.WORK, targetEpochMillis = 9_000),
                event("birthday", category = EventCategory.BIRTHDAY, targetEpochMillis = 5_000),
                event("work-sooner", category = EventCategory.WORK, targetEpochMillis = 1_000),
            ),
        )

        assertThat(query(sort = EventSort.CATEGORY))
            .containsExactly("birthday", "work-sooner", "work-later").inOrder()
    }

    // ---------------------------------------------------------------- filtering

    @Test
    fun `an empty category set means no filter rather than no results`() = runTest {
        // SQL `IN ()` matches nothing, so the query takes a separate flag. If that flag were
        // ever dropped, the event list would silently go blank for every user.
        dao.upsertEvents(listOf(event("a"), event("b")))

        assertThat(query(categories = emptySet())).hasSize(2)
    }

    @Test
    fun `filters to the requested categories`() = runTest {
        dao.upsertEvents(
            listOf(
                event("work", category = EventCategory.WORK),
                event("birthday", category = EventCategory.BIRTHDAY),
                event("travel", category = EventCategory.TRAVEL),
            ),
        )

        val result = query(categories = setOf(EventCategory.WORK, EventCategory.TRAVEL))

        assertThat(result).containsExactly("work", "travel")
    }

    @Test
    fun `hides archived events by default and shows them on request`() = runTest {
        dao.upsertEvents(listOf(event("live"), event("filed", isArchived = true)))

        assertThat(query(includeArchived = false)).containsExactly("live")
        assertThat(query(includeArchived = true)).containsExactly("live", "filed")
    }

    @Test
    fun `hides completed events on request`() = runTest {
        dao.upsertEvents(listOf(event("open"), event("done", isCompleted = true)))

        assertThat(query(includeCompleted = true)).containsExactly("open", "done")
        assertThat(query(includeCompleted = false)).containsExactly("open")
    }

    @Test
    fun `search matches a substring regardless of case`() = runTest {
        dao.upsertEvents(
            listOf(
                event("a", title = "Summer Holiday"),
                event("b", title = "winter holiday"),
                event("c", title = "Dentist"),
            ),
        )

        assertThat(query(text = "holiday")).containsExactly("a", "b")
        assertThat(query(text = "HOLIDAY")).containsExactly("a", "b")
        assertThat(query(text = "dent")).containsExactly("c")
    }

    @Test
    fun `an empty search matches everything`() = runTest {
        dao.upsertEvents(listOf(event("a"), event("b")))

        assertThat(query(text = "")).hasSize(2)
    }

    @Test
    fun `filters combine rather than override one another`() = runTest {
        dao.upsertEvents(
            listOf(
                event("keep", title = "Trip to Rome", category = EventCategory.TRAVEL),
                event("wrong-category", title = "Trip report", category = EventCategory.WORK),
                event("archived", title = "Trip to Paris", category = EventCategory.TRAVEL, isArchived = true),
                event("no-match", title = "Dentist", category = EventCategory.TRAVEL),
            ),
        )

        val result = query(text = "trip", categories = setOf(EventCategory.TRAVEL))

        assertThat(result).containsExactly("keep")
    }

    // ---------------------------------------------------------------- observation

    @Test
    fun `the flow re-emits when an event changes`() = runTest {
        dao.upsertEvent(event("a", title = "Original"))

        dao.observeEvent("a").test {
            assertThat(awaitItem()?.title).isEqualTo("Original")

            dao.upsertEvent(event("a", title = "Renamed"))

            assertThat(awaitItem()?.title).isEqualTo("Renamed")
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `observing a missing event emits null rather than failing`() = runTest {
        assertThat(dao.observeEvent("nope").first()).isNull()
    }

    @Test
    fun `only events with widgets are returned to the scheduler`() = runTest {
        dao.upsertEvents(listOf(event("bound"), event("unbound")))
        database.widgetBindingDao().upsertBinding(binding(appWidgetId = 1, eventId = "bound"))

        val result = dao.observeEventsWithWidgets().first().map { it.id }

        assertThat(result).containsExactly("bound")
    }

    @Test
    fun `an event bound to several widgets is returned once`() = runTest {
        // The query uses DISTINCT; without it the scheduler would process the event twice.
        dao.upsertEvent(event("bound"))
        database.widgetBindingDao().upsertBinding(binding(appWidgetId = 1, eventId = "bound"))
        database.widgetBindingDao().upsertBinding(binding(appWidgetId = 2, eventId = "bound"))

        assertThat(dao.observeEventsWithWidgets().first()).hasSize(1)
    }

    // ---------------------------------------------------------------- writes

    @Test
    fun `upsert replaces an existing event rather than duplicating it`() = runTest {
        dao.upsertEvent(event("a", title = "First"))
        dao.upsertEvent(event("a", title = "Second"))

        assertThat(dao.getAllEvents()).hasSize(1)
        assertThat(dao.getEvent("a")?.title).isEqualTo("Second")
    }

    @Test
    fun `archive and complete flags update in place`() = runTest {
        dao.upsertEvent(event("a"))

        dao.setArchived("a", true)
        dao.setCompleted("a", true)

        val stored = dao.getEvent("a")
        assertThat(stored?.isArchived).isTrue()
        assertThat(stored?.isCompleted).isTrue()
    }
}
