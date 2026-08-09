package com.countflow.core.data.repository

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import app.cash.turbine.test
import com.countflow.core.database.CountFlowDatabase
import com.countflow.core.domain.model.Event
import com.countflow.core.domain.model.EventCategory
import com.countflow.core.domain.model.EventId
import com.countflow.core.domain.model.EventTarget
import com.countflow.core.domain.model.WidgetBinding
import com.countflow.core.domain.model.AppWidgetId
import com.countflow.core.domain.repository.EventFilter
import com.countflow.core.domain.repository.EventLifecycleFilter
import com.countflow.core.domain.repository.EventSort
import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

/**
 * The event repository against a real database.
 *
 * Tested end to end — domain model in, SQLite, domain model out — rather than against a mocked
 * DAO. A mock would confirm only that the repository called the method the test author expected;
 * this confirms the round trip actually preserves the data and that the filter translation is
 * right.
 */
@RunWith(RobolectricTestRunner::class)
internal class EventRepositoryImplTest {

    private lateinit var database: CountFlowDatabase
    private lateinit var repository: EventRepositoryImpl
    private lateinit var bindings: WidgetBindingRepositoryImpl

    private val zone: ZoneId = ZoneId.of("UTC")

    @Before
    fun setUp() {
        database = Room.inMemoryDatabaseBuilder(
            ApplicationProvider.getApplicationContext(),
            CountFlowDatabase::class.java,
        ).allowMainThreadQueries().build()

        // Dispatchers.Unconfined, not a TestDispatcher. A TestDispatcher created here would
        // carry its own TestCoroutineScheduler, which then collides with the one `runTest`
        // installs — "Detected use of different schedulers". These tests exercise SQL, not
        // virtual time, so running the repository's withContext inline is both correct and
        // simpler than threading one scheduler through @Before.
        repository = EventRepositoryImpl(database.eventDao(), Dispatchers.Unconfined)
        bindings = WidgetBindingRepositoryImpl(database.widgetBindingDao(), Dispatchers.Unconfined)
    }

    @After
    fun tearDown() = database.close()

    private fun event(
        id: String,
        title: String = "Event $id",
        date: LocalDate = LocalDate.of(2026, 6, 15),
        category: EventCategory = EventCategory.GENERAL,
    ) = Event.create(
        id = EventId(id),
        title = title,
        target = EventTarget.allDay(date, zone),
        createdAt = Instant.EPOCH,
        category = category,
    )

    @Test
    fun `an event round trips through the database unchanged`() = runTest {
        val original = event("a", title = "Kyoto").copy(
            emoji = "🌸",
            iconKey = "plane",
            remindersEnabled = true,
        )

        repository.upsertEvent(original)

        assertThat(repository.getEvent(EventId("a"))).isEqualTo(original)
    }

    @Test
    fun `observing events emits again when one is added`() = runTest {
        repository.upsertEvent(event("a"))

        repository.observeEvents().test {
            assertThat(awaitItem().map { it.id.value }).containsExactly("a")

            repository.upsertEvent(event("b"))

            assertThat(awaitItem().map { it.id.value }).containsExactly("a", "b")
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `the default filter hides archived events`() = runTest {
        repository.upsertEvents(listOf(event("live"), event("filed")))
        repository.setArchived(EventId("filed"), true)

        val visible = repository.observeEvents().first().map { it.id.value }

        assertThat(visible).containsExactly("live")
    }

    @Test
    fun `the lifecycle filter selects the archived bucket`() = runTest {
        repository.upsertEvents(listOf(event("live"), event("filed")))
        repository.setArchived(EventId("filed"), true)

        val archived = repository.observeEvents(
            filter = EventFilter(lifecycle = EventLifecycleFilter.ARCHIVED),
        ).first().map { it.id.value }

        assertThat(archived).containsExactly("filed")
    }

    @Test
    fun `an empty category filter returns everything`() = runTest {
        // Guards the flag that stops SQL `IN ()` from matching nothing. If this regressed the
        // event list would go blank for every user with no error anywhere.
        repository.upsertEvents(listOf(event("a"), event("b")))

        val all = repository.observeEvents(EventFilter(categories = emptySet())).first()

        assertThat(all).hasSize(2)
    }

    @Test
    fun `filters and sorts translate to the query`() = runTest {
        repository.upsertEvents(
            listOf(
                event("c", title = "Cherry", category = EventCategory.TRAVEL),
                event("a", title = "apple", category = EventCategory.TRAVEL),
                event("w", title = "Work thing", category = EventCategory.WORK),
            ),
        )

        val travelByTitle = repository.observeEvents(
            filter = EventFilter(categories = setOf(EventCategory.TRAVEL)),
            sort = EventSort.TITLE,
        ).first().map { it.id.value }

        assertThat(travelByTitle).containsExactly("a", "c").inOrder()
    }

    @Test
    fun `search matches titles case-insensitively`() = runTest {
        repository.upsertEvents(
            listOf(event("a", title = "Summer Holiday"), event("b", title = "Dentist")),
        )

        val found = repository.observeEvents(EventFilter(query = "HOLIDAY")).first()

        assertThat(found.map { it.id.value }).containsExactly("a")
    }

    @Test
    fun `a blank search is treated as no search`() = runTest {
        // The repository trims before querying, so stray whitespace from the search field does
        // not silently filter everything out.
        repository.upsertEvents(listOf(event("a"), event("b")))

        assertThat(repository.observeEvents(EventFilter(query = "   ")).first()).hasSize(2)
    }

    @Test
    fun `deleting an event cascades to its widget bindings`() = runTest {
        repository.upsertEvent(event("a"))
        bindings.upsertBinding(
            WidgetBinding.inheriting(AppWidgetId(1), EventId("a"), Instant.EPOCH),
        )

        repository.deleteEvent(EventId("a"))

        assertThat(repository.getEvent(EventId("a"))).isNull()
        assertThat(bindings.getAllBoundWidgets()).isEmpty()
    }

    @Test
    fun `only events with widgets reach the scheduler`() = runTest {
        repository.upsertEvents(listOf(event("bound"), event("unbound")))
        bindings.upsertBinding(
            WidgetBinding.inheriting(AppWidgetId(1), EventId("bound"), Instant.EPOCH),
        )

        val scheduled = repository.observeEventsWithWidgets().first().map { it.id.value }

        assertThat(scheduled).containsExactly("bound")
    }

    @Test
    fun `archive and complete flags persist`() = runTest {
        repository.upsertEvent(event("a"))

        repository.setCompleted(EventId("a"), true)

        assertThat(repository.getEvent(EventId("a"))?.isCompleted).isTrue()
    }

    @Test
    fun `observing a missing event emits null`() = runTest {
        assertThat(repository.observeEvent(EventId("ghost")).first()).isNull()
    }
}
