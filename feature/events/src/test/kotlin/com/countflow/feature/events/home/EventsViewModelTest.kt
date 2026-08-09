package com.countflow.feature.events.home

import app.cash.turbine.test
import com.countflow.core.domain.countdown.CountdownConfig
import com.countflow.core.domain.countdown.CountdownEngine
import com.countflow.core.domain.model.Event
import com.countflow.core.domain.model.EventCategory
import com.countflow.core.domain.model.EventId
import com.countflow.core.domain.model.EventTarget
import com.countflow.core.domain.repository.EventLifecycleFilter
import com.countflow.core.domain.repository.EventSort
import com.countflow.feature.events.model.EventUiMapper
import com.countflow.feature.events.testing.FakeEventRepository
import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Before
import org.junit.Test
import java.time.Clock
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

/**
 * The event list state holder.
 *
 * Tested against a fake repository rather than a real database: the behaviour under test is how
 * inputs combine into state and how they translate into a filter, not whether SQL works — which
 * the DAO tests already cover against real SQLite.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class EventsViewModelTest {

    private val zone: ZoneId = ZoneId.of("UTC")
    private val now: Instant = Instant.parse("2026-06-15T12:00:00Z")
    private lateinit var repository: FakeEventRepository
    private lateinit var viewModel: EventsViewModel

    @Before
    fun setUp() {
        // Unconfined for Main so anything launched in viewModelScope runs eagerly. A
        // StandardTestDispatcher created here would carry its own TestCoroutineScheduler,
        // separate from the one runTest installs, and virtual time would advance in one while
        // the code under test waited on the other.
        Dispatchers.setMain(UnconfinedTestDispatcher())
        repository = FakeEventRepository()
        viewModel = EventsViewModel(
            eventRepository = repository,
            uiMapper = EventUiMapper(
                CountdownEngine(Clock.fixed(now, zone), CountdownConfig.Default),
            ),
            clock = Clock.fixed(now, zone),
        )
    }

    @After
    fun tearDown() = Dispatchers.resetMain()

    private fun event(id: String, title: String = "Event $id") = Event.create(
        id = EventId(id),
        title = title,
        target = EventTarget.allDay(LocalDate.of(2026, 12, 25), zone),
        createdAt = Instant.parse("2026-01-01T00:00:00Z"),
    )

    @Test
    fun `starts in a loading state with no events`() {
        val initial = viewModel.uiState.value

        assertThat(initial.isLoading).isTrue()
        assertThat(initial.events).isEmpty()
        assertThat(initial.emptyState).isNull()
    }

    @Test
    fun `emits mapped events once the repository responds`() = runTest {
        repository.emit(listOf(event("a"), event("b")))

        viewModel.uiState.test {
            val loaded = awaitLoaded()

            assertThat(loaded.isLoading).isFalse()
            assertThat(loaded.events.map { it.id }).containsExactly("a", "b")
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `the search field updates immediately even though the query is debounced`() = runTest {
        // The whole reason the raw text and the debounced query are separate flows: a field
        // that lags a keystroke behind feels broken.
        repository.emit(listOf(event("a")))

        viewModel.uiState.test {
            awaitLoaded()
            viewModel.onQueryChange("holi")

            val typed = awaitItem()
            assertThat(typed.query).isEqualTo("holi")
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `the query reaches the repository only after the debounce elapses`() = runTest {
        // The other half of the split-flow design: the field updates at once, the database is
        // left alone until the user pauses. Asserting both halves is what proves the debounce
        // is actually doing something rather than being accidentally bypassed.
        repository.emit(listOf(event("a")))
        backgroundScope.launch { viewModel.uiState.collect { } }
        advanceTimeBy(1)

        viewModel.onQueryChange("kyoto")
        advanceTimeBy(100)
        assertThat(repository.lastFilter?.query).isEmpty()

        advanceTimeBy(300)
        assertThat(repository.lastFilter?.query).isEqualTo("kyoto")
    }

    @Test
    fun `changing the sort reaches the repository without waiting`() = runTest {
        // Sort comes from a deliberate tap and must not inherit the typing delay.
        repository.emit(listOf(event("a")))
        backgroundScope.launch { viewModel.uiState.collect { } }
        advanceTimeBy(1)

        viewModel.onSortChange(EventSort.TITLE)
        advanceTimeBy(1)

        assertThat(repository.lastSort).isEqualTo(EventSort.TITLE)
    }

    @Test
    fun `toggling a category adds then removes it`() = runTest {
        repository.emit(listOf(event("a")))

        viewModel.uiState.test {
            awaitLoaded()

            viewModel.onCategoryToggle(EventCategory.TRAVEL)
            assertThat(awaitItem().selectedCategories).containsExactly(EventCategory.TRAVEL)

            viewModel.onCategoryToggle(EventCategory.TRAVEL)
            assertThat(awaitItem().selectedCategories).isEmpty()

            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `clearing filters resets both the query and the categories`() = runTest {
        repository.emit(listOf(event("a")))

        viewModel.uiState.test {
            awaitLoaded()
            viewModel.onQueryChange("something")
            viewModel.onCategoryToggle(EventCategory.WORK)
            awaitState { it.query == "something" && it.selectedCategories.isNotEmpty() }

            viewModel.onClearFilters()

            val cleared = awaitState { it.query.isEmpty() && it.selectedCategories.isEmpty() }
            assertThat(cleared.hasActiveFilters).isFalse()
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `an empty upcoming list with no filters offers to create a countdown`() = runTest {
        repository.emit(emptyList())

        viewModel.uiState.test {
            val loaded = awaitLoaded()

            assertThat(loaded.emptyState).isEqualTo(HomeEmptyState.NO_UPCOMING)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `an empty list with an active filter says nothing matches, on every tab`() = runTest {
        // Showing the tab's own empty copy while a search is active reads as though the user's
        // data has disappeared, which is alarming and wrong.
        repository.emit(emptyList())

        viewModel.uiState.test {
            awaitLoaded()
            viewModel.onQueryChange("nothing like this")

            val filtered = awaitState { it.query == "nothing like this" }
            assertThat(filtered.emptyState).isEqualTo(HomeEmptyState.NO_MATCHES)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `defaults to the upcoming tab`() = runTest {
        repository.emit(emptyList())

        viewModel.uiState.test {
            val loaded = awaitLoaded()

            assertThat(loaded.tab).isEqualTo(EventLifecycleFilter.UPCOMING)
            assertThat(repository.lastFilter?.lifecycle).isEqualTo(EventLifecycleFilter.UPCOMING)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `switching tabs reaches the repository without waiting`() = runTest {
        // A tab tap is a deliberate press, like a sort tap, and must not inherit the search
        // field's typing delay.
        repository.emit(emptyList())
        backgroundScope.launch { viewModel.uiState.collect { } }
        advanceTimeBy(1)

        viewModel.onTabChange(EventLifecycleFilter.ARCHIVED)
        advanceTimeBy(1)

        assertThat(repository.lastFilter?.lifecycle).isEqualTo(EventLifecycleFilter.ARCHIVED)
    }

    @Test
    fun `an empty completed tab explains that completed events show up here`() = runTest {
        repository.emit(emptyList())

        viewModel.uiState.test {
            awaitLoaded()
            viewModel.onTabChange(EventLifecycleFilter.COMPLETED)

            val onCompleted = awaitState { it.tab == EventLifecycleFilter.COMPLETED }
            assertThat(onCompleted.emptyState).isEqualTo(HomeEmptyState.NO_COMPLETED)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `an empty archived tab explains that archived events show up here`() = runTest {
        repository.emit(emptyList())

        viewModel.uiState.test {
            awaitLoaded()
            viewModel.onTabChange(EventLifecycleFilter.ARCHIVED)

            val onArchived = awaitState { it.tab == EventLifecycleFilter.ARCHIVED }
            assertThat(onArchived.emptyState).isEqualTo(HomeEmptyState.NO_ARCHIVED)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `completing an event reaches the repository`() = runTest {
        viewModel.onCompletedChange("a", true)

        assertThat(repository.completed).containsExactly("a" to true)
    }

    @Test
    fun `archiving an event reaches the repository`() = runTest {
        viewModel.onArchivedChange("a", true)

        assertThat(repository.archived).containsExactly("a" to true)
    }

    @Test
    fun `deleting an event reaches the repository`() = runTest {
        viewModel.onDelete("a")

        assertThat(repository.deleted).containsExactly("a")
    }

    // ---------------------------------------------------------------- helpers

    /**
     * Consumes emissions until [predicate] holds.
     *
     * Necessary because several inputs are separate flows, so one user action can produce more
     * than one emission — clearing filters writes the query and the categories independently,
     * and there is a transient state in between. Compose coalesces those into a single frame,
     * so the intermediate state is not user-visible, but a test that grabs the first emission
     * would see it.
     */
    private suspend fun app.cash.turbine.TurbineTestContext<HomeUiState>.awaitState(
        predicate: (HomeUiState) -> Boolean,
    ): HomeUiState {
        var state = awaitItem()
        while (!predicate(state)) state = awaitItem()
        return state
    }

    private suspend fun app.cash.turbine.TurbineTestContext<HomeUiState>.awaitLoaded() =
        awaitState { !it.isLoading }
}
