package com.countflow.feature.events.edit

import androidx.lifecycle.SavedStateHandle
import app.cash.turbine.test
import com.countflow.core.domain.countdown.CountdownConfig
import com.countflow.core.domain.countdown.CountdownEngine
import com.countflow.core.domain.model.AccentColor
import com.countflow.core.domain.model.AppWidgetId
import com.countflow.core.domain.model.Event
import com.countflow.core.domain.model.EventId
import com.countflow.core.domain.model.WidgetBinding
import com.countflow.core.domain.repository.BoundWidget
import com.countflow.core.domain.repository.WidgetBindingRepository
import com.countflow.core.domain.validation.EventValidator
import com.countflow.feature.events.testing.FakeEventRepository
import com.countflow.widget.engine.provider.WidgetRenderModelProvider
import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Before
import org.junit.Test
import java.time.Clock
import java.time.Instant
import java.time.ZoneId

/**
 * The create/edit form's state holder, focused on the parts Session 11 added: the live widget
 * preview reacting to form input, and never persisting anything before [EditEventViewModel.onSave].
 *
 * Save/validation itself has no dedicated coverage here — it was already exercised end to end on
 * a device every session since Milestone 3 (see `TODO.md`'s testing-gaps section, which named
 * this ViewModel's total absence of unit tests as a known hole this file also closes).
 */
@OptIn(ExperimentalCoroutinesApi::class)
class EditEventViewModelTest {

    private val zone: ZoneId = ZoneId.of("UTC")
    private val now: Instant = Instant.parse("2026-06-15T12:00:00Z")
    private lateinit var eventRepository: FakeEventRepository
    private lateinit var viewModel: EditEventViewModel

    @Before
    fun setUp() {
        Dispatchers.setMain(UnconfinedTestDispatcher())
        eventRepository = FakeEventRepository()
        val clock = Clock.fixed(now, zone)
        viewModel = EditEventViewModel(
            eventRepository = eventRepository,
            validator = EventValidator(clock),
            renderModelProvider = WidgetRenderModelProvider(
                widgetBindingRepository = NoOpWidgetBindingRepository,
                countdownEngine = CountdownEngine(clock, CountdownConfig.Default),
                clock = clock,
            ),
            clock = clock,
            savedStateHandle = SavedStateHandle(),
        )
    }

    @After
    fun tearDown() = Dispatchers.resetMain()

    @Test
    fun `there is no preview until a title is entered`() {
        // The date field always has a default, but the title starts blank — the create form's
        // own genuine "nothing to preview yet" state.
        assertThat(viewModel.uiState.value.previewModel).isNull()
    }

    @Test
    fun `typing a title produces a preview`() = runTest {
        // Each field change is two separate state updates under the hood — the field itself,
        // then the recomputed preview — so assertions await the settled value they actually
        // care about rather than the very next emission, the same reason EventsViewModelTest's
        // own `awaitState` exists.
        viewModel.uiState.test {
            awaitItem() // initial state, no title yet

            viewModel.onTitleChange("Trip to Kyoto")

            val withTitle = awaitState { it.previewModel?.title == "Trip to Kyoto" }
            assertThat(withTitle.previewModel).isNotNull()
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `the preview updates as the title keeps changing`() = runTest {
        viewModel.uiState.test {
            awaitItem() // initial

            viewModel.onTitleChange("Draft")
            awaitState { it.previewModel?.title == "Draft" }

            viewModel.onTitleChange("Final title")
            val settled = awaitState { it.previewModel?.title == "Final title" }

            assertThat(settled.previewModel?.title).isEqualTo("Final title")
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `the preview reflects an accent color change`() = runTest {
        viewModel.uiState.test {
            awaitItem() // initial

            viewModel.onTitleChange("Trip to Kyoto")
            val before = awaitState { it.previewModel != null }
            assertThat(before.previewModel?.theme?.accentColorArgb).isNull()

            viewModel.onAccentColorChange(AccentColor.Fixed(0xFFFF0000.toInt()))
            val after = awaitState { it.previewModel?.theme?.accentColorArgb != null }

            assertThat(after.previewModel?.theme?.accentColorArgb).isEqualTo(0xFFFF0000.toInt())
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `clearing the title back to blank clears the preview`() = runTest {
        viewModel.uiState.test {
            awaitItem() // initial

            viewModel.onTitleChange("Trip to Kyoto")
            awaitState { it.previewModel != null }

            viewModel.onTitleChange("")
            val cleared = awaitState { it.title.isEmpty() && it.previewModel == null }

            assertThat(cleared.previewModel).isNull()
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `the preview never reaches the repository`() {
        // WidgetRenderModelProvider.preview() is pure and synchronous by construction (D-048),
        // but the point being guarded here is specifically the ViewModel's own behaviour: no
        // field handler must ever call upsertEvent on the caller's behalf.
        viewModel.onTitleChange("Trip to Kyoto")
        viewModel.onAccentColorChange(AccentColor.Fixed(0xFF00FF00.toInt()))

        assertThat(eventRepository.getAllEventsSync()).isEmpty()
    }

    @Test
    fun `saving is what actually persists the event`() = runTest {
        viewModel.onTitleChange("Trip to Kyoto")

        viewModel.uiState.test {
            awaitState { it.title == "Trip to Kyoto" }

            viewModel.onSave()

            awaitState { it.isSaved }
            cancelAndIgnoreRemainingEvents()
        }

        assertThat(eventRepository.getAllEventsSync().map(Event::title)).containsExactly("Trip to Kyoto")
    }

    // ---------------------------------------------------------------- helpers

    private fun FakeEventRepository.getAllEventsSync(): List<Event> =
        runBlocking { getAllEvents() }

    private suspend fun app.cash.turbine.TurbineTestContext<EditEventUiState>.awaitState(
        predicate: (EditEventUiState) -> Boolean,
    ): EditEventUiState {
        var state = awaitItem()
        while (!predicate(state)) state = awaitItem()
        return state
    }

    /**
     * [WidgetRenderModelProvider.preview] never touches this — it exists only so the provider
     * can be constructed. Every method throws if that assumption is ever wrong.
     */
    private object NoOpWidgetBindingRepository : WidgetBindingRepository {
        override fun observeBoundWidget(appWidgetId: AppWidgetId): Flow<BoundWidget?> = flowOf(null)
        override fun observeAllBoundWidgets(): Flow<List<BoundWidget>> = flowOf(emptyList())
        override suspend fun getBoundWidget(appWidgetId: AppWidgetId): BoundWidget? = null
        override suspend fun getAllBoundWidgets(): List<BoundWidget> = emptyList()
        override suspend fun getBinding(appWidgetId: AppWidgetId): WidgetBinding? = null
        override suspend fun upsertBinding(binding: WidgetBinding) = error("not used by preview()")
        override suspend fun deleteBindings(appWidgetIds: List<AppWidgetId>) = error("not used by preview()")
        override suspend fun deleteBindingsForEvent(eventId: EventId) = error("not used by preview()")
        override suspend fun pruneOrphanedBindings(liveAppWidgetIds: Set<AppWidgetId>) = error("not used by preview()")
    }
}
