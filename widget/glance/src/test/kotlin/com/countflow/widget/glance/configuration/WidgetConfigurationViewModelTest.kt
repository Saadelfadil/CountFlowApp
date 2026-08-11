package com.countflow.widget.glance.configuration

import android.app.Activity
import com.countflow.core.domain.countdown.CountdownConfig
import com.countflow.core.domain.countdown.CountdownEngine
import com.countflow.core.domain.model.AppWidgetId
import com.countflow.core.domain.model.Event
import com.countflow.core.domain.model.EventId
import com.countflow.core.domain.model.EventTarget
import com.countflow.core.domain.model.WidgetBinding
import com.countflow.core.domain.model.WidgetStyle
import com.countflow.core.domain.repository.BoundWidget
import com.countflow.core.domain.repository.EventFilter
import com.countflow.core.domain.repository.EventRepository
import com.countflow.core.domain.repository.EventSort
import com.countflow.core.domain.repository.WidgetBindingRepository
import com.countflow.core.domain.repository.WidgetStyleEntitlementRepository
import com.countflow.widget.engine.provider.WidgetRenderModelProvider
import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import java.time.Clock
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

/**
 * Style selection gated by rewarded-style entitlements, the reward flow that unlocks one, and the
 * LOADING/READY/SHOWING/FAILED readiness state the Unlock Style dialog reads.
 *
 * No AdMob SDK, ad ID, or network code anywhere in this file — [FakeRewardedStyleAdController]
 * stands in for the real, `:app`-only, AdMob-backed implementation, and every test drives it by
 * calling the exact same `onRewardEarned`/`onDismissed`/`onFailed` callbacks
 * [RewardedStyleAdController.show]'s real contract promises, or by setting [RewardedAdState]
 * transitions directly, never Google's SDK. This is "test our integration behavior," not "test
 * Google's SDK internals," taken literally: what is asserted is always CountFlow's own reaction to
 * a callback or state change, never whether an ad actually rendered.
 */
@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
class WidgetConfigurationViewModelTest {

    private val zone = ZoneId.of("UTC")
    private val now = Instant.parse("2026-06-15T08:00:00Z")
    private val clock = Clock.fixed(now, zone)
    private val event = Event.create(
        id = EventId("event-1"),
        title = "Trip to Kyoto",
        target = EventTarget.allDay(LocalDate.of(2026, 6, 27), zone),
        createdAt = Instant.EPOCH,
    )

    // A real (if inert) Activity instance — RewardedStyleAdController's real signature is
    // Activity-scoped, matching Google's own load/show APIs, so the fake honours that shape
    // rather than weakening it to a plain Context just to make testing easier.
    private val activity: Activity = Robolectric.buildActivity(Activity::class.java).create().get()

    private lateinit var events: FakeEventRepository
    private lateinit var bindings: FakeWidgetBindingRepository
    private lateinit var entitlements: FakeWidgetStyleEntitlementRepository
    private lateinit var adController: FakeRewardedStyleAdController
    private lateinit var viewModel: WidgetConfigurationViewModel

    @Before
    fun setUp() {
        Dispatchers.setMain(UnconfinedTestDispatcher())
        events = FakeEventRepository(listOf(event))
        bindings = FakeWidgetBindingRepository()
        entitlements = FakeWidgetStyleEntitlementRepository()
        adController = FakeRewardedStyleAdController()
        viewModel = newViewModel()
        viewModel.load(AppWidgetId(1))
        viewModel.onEventSelected(event.id)
    }

    @After
    fun tearDown() = Dispatchers.resetMain()

    private fun newViewModel() = WidgetConfigurationViewModel(
        eventRepository = events,
        widgetBindingRepository = bindings,
        widgetStyleEntitlementRepository = entitlements,
        adController = adController,
        renderModelProvider = WidgetRenderModelProvider(
            widgetBindingRepository = bindings,
            countdownEngine = CountdownEngine(clock, CountdownConfig.Default),
            clock = clock,
        ),
        clock = clock,
    )

    /** Opens the unlock dialog for [style] and drives the fake straight to READY — the common setup every show()-reaching test needs, since onWatchAdClicked now refuses to call show() before READY. */
    private fun openUnlockDialogReady(style: WidgetStyle, viewModel: WidgetConfigurationViewModel = this.viewModel) {
        viewModel.onWidgetStyleChange(style)
        adController.nextLoadOutcome = FakeRewardedStyleAdController.LoadOutcome.Ready
        viewModel.onUnlockDialogShown(activity)
    }

    // ── Free styles always select immediately, regardless of entitlement state ──

    @Test
    fun `minimal selects immediately`() {
        viewModel.onWidgetStyleChange(WidgetStyle.MINIMAL)

        val state = viewModel.uiState.value
        assertThat(state.widgetStyle).isEqualTo(WidgetStyle.MINIMAL)
        assertThat(state.pendingRewardRequest).isNull()
    }

    @Test
    fun `material selects immediately`() {
        viewModel.onWidgetStyleChange(WidgetStyle.MATERIAL)

        val state = viewModel.uiState.value
        assertThat(state.widgetStyle).isEqualTo(WidgetStyle.MATERIAL)
        assertThat(state.pendingRewardRequest).isNull()
    }

    @Test
    fun `oled selects immediately`() {
        viewModel.onWidgetStyleChange(WidgetStyle.OLED)

        val state = viewModel.uiState.value
        assertThat(state.widgetStyle).isEqualTo(WidgetStyle.OLED)
        assertThat(state.pendingRewardRequest).isNull()
    }

    @Test
    fun `existing free styles never trigger an ad`() {
        listOf(WidgetStyle.MINIMAL, WidgetStyle.MATERIAL, WidgetStyle.OLED).forEach {
            viewModel.onWidgetStyleChange(it)
        }

        assertThat(adController.loadCallCount).isEqualTo(0)
        assertThat(adController.showCallCount).isEqualTo(0)
    }

    // ── A locked rewarded style requests a reward instead of selecting; the current selection
    // must not change yet ──

    @Test
    fun `locked glass emits a reward request and does not select`() {
        val selectionBefore = viewModel.uiState.value.widgetStyle

        viewModel.onWidgetStyleChange(WidgetStyle.GLASS)

        val state = viewModel.uiState.value
        assertThat(state.widgetStyle).isEqualTo(selectionBefore)
        assertThat(state.pendingRewardRequest).isEqualTo(RewardRequired(AppWidgetId(1), WidgetStyle.GLASS))
    }

    @Test
    fun `locked rounded emits a reward request and does not select`() {
        val selectionBefore = viewModel.uiState.value.widgetStyle

        viewModel.onWidgetStyleChange(WidgetStyle.ROUNDED)

        val state = viewModel.uiState.value
        assertThat(state.widgetStyle).isEqualTo(selectionBefore)
        assertThat(state.pendingRewardRequest).isEqualTo(RewardRequired(AppWidgetId(1), WidgetStyle.ROUNDED))
    }

    @Test
    fun `locked modern emits a reward request and does not select`() {
        val selectionBefore = viewModel.uiState.value.widgetStyle

        viewModel.onWidgetStyleChange(WidgetStyle.MODERN)

        val state = viewModel.uiState.value
        assertThat(state.widgetStyle).isEqualTo(selectionBefore)
        assertThat(state.pendingRewardRequest).isEqualTo(RewardRequired(AppWidgetId(1), WidgetStyle.MODERN))
    }

    // ── Cancelling the unlock dialog grants nothing ──

    @Test
    fun `cancelling the unlock dialog grants no entitlement`() = runTest {
        viewModel.onWidgetStyleChange(WidgetStyle.GLASS)

        viewModel.onRewardRequestHandled()

        assertThat(viewModel.uiState.value.pendingRewardRequest).isNull()
        assertThat(entitlements.isStyleUnlocked(AppWidgetId(1), WidgetStyle.GLASS)).isFalse()
        assertThat(adController.showCallCount).isEqualTo(0)
    }

    // ── Readiness state: task items 4-9 ──

    @Test
    fun `dialog opens while loading shows the LOADING state`() {
        viewModel.onWidgetStyleChange(WidgetStyle.GLASS)
        adController.nextLoadOutcome = FakeRewardedStyleAdController.LoadOutcome.StaysLoading

        viewModel.onUnlockDialogShown(activity)

        // The real dialog disables its primary button and shows "Preparing ad…" for exactly this
        // state — see WidgetConfigurationActivity's UnlockStyleDialog, not re-tested here since it
        // is a Compose rendering detail, not this ViewModel's own behavior.
        assertThat(viewModel.uiState.value.rewardedAdState).isEqualTo(RewardedAdState.LOADING)
    }

    @Test
    fun `onAdLoaded moves the state to READY`() {
        viewModel.onWidgetStyleChange(WidgetStyle.GLASS)
        adController.nextLoadOutcome = FakeRewardedStyleAdController.LoadOutcome.Ready

        viewModel.onUnlockDialogShown(activity)

        assertThat(viewModel.uiState.value.rewardedAdState).isEqualTo(RewardedAdState.READY)
    }

    @Test
    fun `tapping watch ad before READY does not call show and grants nothing`() = runTest {
        viewModel.onWidgetStyleChange(WidgetStyle.GLASS)
        adController.nextLoadOutcome = FakeRewardedStyleAdController.LoadOutcome.StaysLoading
        viewModel.onUnlockDialogShown(activity)

        viewModel.onWatchAdClicked(activity)

        assertThat(adController.showCallCount).isEqualTo(0)
        assertThat(entitlements.isStyleUnlocked(AppWidgetId(1), WidgetStyle.GLASS)).isFalse()
    }

    @Test
    fun `repeated taps while SHOWING cannot show the ad twice`() {
        openUnlockDialogReady(WidgetStyle.GLASS)
        adController.autoResolveShow = false

        viewModel.onWatchAdClicked(activity) // -> SHOWING, showCallCount = 1
        assertThat(viewModel.uiState.value.rewardedAdState).isEqualTo(RewardedAdState.SHOWING)

        viewModel.onWatchAdClicked(activity) // state is no longer READY -> must no-op

        assertThat(adController.showCallCount).isEqualTo(1)
    }

    @Test
    fun `a genuine load failure moves the state to FAILED`() {
        viewModel.onWidgetStyleChange(WidgetStyle.GLASS)
        adController.nextLoadOutcome = FakeRewardedStyleAdController.LoadOutcome.Failed

        viewModel.onUnlockDialogShown(activity)

        assertThat(viewModel.uiState.value.rewardedAdState).isEqualTo(RewardedAdState.FAILED)
    }

    @Test
    fun `retry moves FAILED back to LOADING`() {
        viewModel.onWidgetStyleChange(WidgetStyle.GLASS)
        adController.nextLoadOutcome = FakeRewardedStyleAdController.LoadOutcome.Failed
        viewModel.onUnlockDialogShown(activity)
        assertThat(viewModel.uiState.value.rewardedAdState).isEqualTo(RewardedAdState.FAILED)

        adController.nextLoadOutcome = FakeRewardedStyleAdController.LoadOutcome.StaysLoading
        viewModel.onRetryClicked(activity)

        assertThat(viewModel.uiState.value.rewardedAdState).isEqualTo(RewardedAdState.LOADING)
        assertThat(adController.loadCallCount).isEqualTo(2) // the initial load + this retry
    }

    @Test
    fun `a successful retry reaches READY`() {
        viewModel.onWidgetStyleChange(WidgetStyle.GLASS)
        adController.nextLoadOutcome = FakeRewardedStyleAdController.LoadOutcome.Failed
        viewModel.onUnlockDialogShown(activity)
        assertThat(viewModel.uiState.value.rewardedAdState).isEqualTo(RewardedAdState.FAILED)

        adController.nextLoadOutcome = FakeRewardedStyleAdController.LoadOutcome.Ready
        viewModel.onRetryClicked(activity)

        assertThat(viewModel.uiState.value.rewardedAdState).isEqualTo(RewardedAdState.READY)
    }

    @Test
    fun `retry while not FAILED is a no-op`() {
        viewModel.onWidgetStyleChange(WidgetStyle.GLASS)
        adController.nextLoadOutcome = FakeRewardedStyleAdController.LoadOutcome.Ready
        viewModel.onUnlockDialogShown(activity)
        assertThat(viewModel.uiState.value.rewardedAdState).isEqualTo(RewardedAdState.READY)

        viewModel.onRetryClicked(activity)

        assertThat(adController.loadCallCount).isEqualTo(1) // only the original load — retry did nothing
    }

    // ── Ad/show failure grants nothing and leaves the Style locked ──

    @Test
    fun `an ad that fails to show grants no entitlement and moves to FAILED`() = runTest {
        openUnlockDialogReady(WidgetStyle.GLASS)
        adController.nextShowOutcome =
            FakeRewardedStyleAdController.ShowOutcome.Failed("Ad unavailable right now. Try again later.")

        viewModel.onWatchAdClicked(activity)

        assertThat(entitlements.isStyleUnlocked(AppWidgetId(1), WidgetStyle.GLASS)).isFalse()
        assertThat(viewModel.uiState.value.pendingRewardRequest).isNotNull() // dialog stays open to retry
        assertThat(viewModel.uiState.value.adFeedback).isEqualTo("Ad unavailable right now. Try again later.")
        assertThat(viewModel.uiState.value.rewardedAdState).isEqualTo(RewardedAdState.FAILED)
    }

    // ── Dismissing the ad without earning a reward grants nothing (task item 11) ──

    @Test
    fun `an ad dismissed without a reward grants no entitlement`() = runTest {
        openUnlockDialogReady(WidgetStyle.GLASS)
        adController.nextShowOutcome = FakeRewardedStyleAdController.ShowOutcome.DismissedWithoutReward

        viewModel.onWatchAdClicked(activity)

        assertThat(entitlements.isStyleUnlocked(AppWidgetId(1), WidgetStyle.GLASS)).isFalse()
        assertThat(viewModel.uiState.value.widgetStyle).isNotEqualTo(WidgetStyle.GLASS)
        // A voluntary close is not a failure — the dialog stays open with no alarming message, and
        // the existing preload-after-dismiss lifecycle is preserved: the fake models this exactly
        // like the real AdMobRewardedStyleAdController does, moving back to LOADING on its own.
        assertThat(viewModel.uiState.value.pendingRewardRequest).isNotNull()
        assertThat(viewModel.uiState.value.adFeedback).isNull()
        assertThat(viewModel.uiState.value.rewardedAdState).isEqualTo(RewardedAdState.LOADING)
    }

    // ── Only the genuine earned-reward callback may ever grant an entitlement (task item 10) ──

    @Test
    fun `a genuine earned-reward callback grants the entitlement`() = runTest {
        openUnlockDialogReady(WidgetStyle.GLASS)
        adController.nextShowOutcome = FakeRewardedStyleAdController.ShowOutcome.RewardEarned

        viewModel.onWatchAdClicked(activity)

        assertThat(entitlements.isStyleUnlocked(AppWidgetId(1), WidgetStyle.GLASS)).isTrue()
    }

    @Test
    fun `after a successful reward the lock disappears`() = runTest {
        openUnlockDialogReady(WidgetStyle.GLASS)
        adController.nextShowOutcome = FakeRewardedStyleAdController.ShowOutcome.RewardEarned

        viewModel.onWatchAdClicked(activity)

        assertThat(viewModel.uiState.value.isStyleLocked(WidgetStyle.GLASS)).isFalse()
        assertThat(viewModel.uiState.value.unlockedRewardedStyles).contains(WidgetStyle.GLASS)
    }

    @Test
    fun `after a successful reward glass is selected and the pending request clears`() = runTest {
        openUnlockDialogReady(WidgetStyle.GLASS)
        adController.nextShowOutcome = FakeRewardedStyleAdController.ShowOutcome.RewardEarned

        viewModel.onWatchAdClicked(activity)

        val state = viewModel.uiState.value
        assertThat(state.widgetStyle).isEqualTo(WidgetStyle.GLASS)
        assertThat(state.pendingRewardRequest).isNull()
        // Selecting refreshes the Main Preview through the same path every other style change
        // already uses (WidgetConfigurationViewModel.updateAndRefresh).
        assertThat(state.previewModel?.theme?.style).isEqualTo(WidgetStyle.GLASS)
    }

    // ── An unlocked rewarded style (already granted, e.g. from a prior session) selects exactly
    // like a free style ──

    @Test
    fun `unlocked glass selects normally`() = runTest {
        entitlements.grantRewardedStyle(AppWidgetId(1), WidgetStyle.GLASS)
        viewModel.onEventSelected(event.id)

        viewModel.onWidgetStyleChange(WidgetStyle.GLASS)

        val state = viewModel.uiState.value
        assertThat(state.widgetStyle).isEqualTo(WidgetStyle.GLASS)
        assertThat(state.pendingRewardRequest).isNull()
    }

    @Test
    fun `unlocked rounded selects normally`() = runTest {
        entitlements.grantRewardedStyle(AppWidgetId(1), WidgetStyle.ROUNDED)
        viewModel.onEventSelected(event.id)

        viewModel.onWidgetStyleChange(WidgetStyle.ROUNDED)

        val state = viewModel.uiState.value
        assertThat(state.widgetStyle).isEqualTo(WidgetStyle.ROUNDED)
        assertThat(state.pendingRewardRequest).isNull()
    }

    @Test
    fun `unlocked modern selects normally`() = runTest {
        entitlements.grantRewardedStyle(AppWidgetId(1), WidgetStyle.MODERN)
        viewModel.onEventSelected(event.id)

        viewModel.onWidgetStyleChange(WidgetStyle.MODERN)

        val state = viewModel.uiState.value
        assertThat(state.widgetStyle).isEqualTo(WidgetStyle.MODERN)
        assertThat(state.pendingRewardRequest).isNull()
    }

    // ── Per-widget isolation: granting Glass for widget 1 must not unlock it for widget 2, via
    // either the direct-grant path or the full reward-earned path ──

    @Test
    fun `entitlement is scoped to the current widget id`() = runTest {
        entitlements.grantRewardedStyle(AppWidgetId(1), WidgetStyle.GLASS)

        val widgetTwo = newViewModel()
        widgetTwo.load(AppWidgetId(2))
        widgetTwo.onEventSelected(event.id)

        widgetTwo.onWidgetStyleChange(WidgetStyle.GLASS)

        val state = widgetTwo.uiState.value
        assertThat(state.widgetStyle).isNotEqualTo(WidgetStyle.GLASS)
        assertThat(state.pendingRewardRequest).isEqualTo(RewardRequired(AppWidgetId(2), WidgetStyle.GLASS))
        // Widget 1's own grant is untouched by widget 2 ever having been asked about it.
        assertThat(entitlements.isStyleUnlocked(AppWidgetId(1), WidgetStyle.GLASS)).isTrue()
    }

    @Test
    fun `a real reward earned for widget A does not unlock glass for widget B`() = runTest {
        openUnlockDialogReady(WidgetStyle.GLASS)
        adController.nextShowOutcome = FakeRewardedStyleAdController.ShowOutcome.RewardEarned
        viewModel.onWatchAdClicked(activity) // widget 1 (this class's own `viewModel`) earns and unlocks Glass

        val widgetB = newViewModel()
        widgetB.load(AppWidgetId(2))
        widgetB.onEventSelected(event.id)
        widgetB.onWidgetStyleChange(WidgetStyle.GLASS)

        assertThat(widgetB.uiState.value.pendingRewardRequest)
            .isEqualTo(RewardRequired(AppWidgetId(2), WidgetStyle.GLASS))
        assertThat(widgetB.uiState.value.widgetStyle).isNotEqualTo(WidgetStyle.GLASS)
    }

    // ---------------------------------------------------------------- fakes

    /** An in-memory [EventRepository]. Filtering/sorting is not re-implemented — this ViewModel never calls [observeEvents]. */
    private class FakeEventRepository(initial: List<Event>) : EventRepository {
        private val store = initial.associateBy { it.id }.toMutableMap()
        override fun observeEvents(filter: EventFilter, sort: EventSort): Flow<List<Event>> = flowOf(store.values.toList())
        override fun observeEvent(id: EventId): Flow<Event?> = flowOf(store[id])
        override fun observeEventsWithWidgets(): Flow<List<Event>> = flowOf(store.values.toList())
        override suspend fun getEvent(id: EventId): Event? = store[id]
        override suspend fun getAllEvents(): List<Event> = store.values.toList()
        override suspend fun upsertEvent(event: Event) { store[event.id] = event }
        override suspend fun upsertEvents(events: List<Event>) { events.forEach { store[it.id] = it } }
        override suspend fun deleteEvent(id: EventId) { store.remove(id) }
        override suspend fun setArchived(id: EventId, isArchived: Boolean) = Unit
        override suspend fun setCompleted(id: EventId, isCompleted: Boolean) = Unit
    }

    /**
     * An in-memory [WidgetBindingRepository]. Only [getBinding]/[upsertBinding] are exercised by
     * [WidgetConfigurationViewModel] — the rest satisfy the interface (and let
     * [WidgetRenderModelProvider] be constructed) without pretending to join data no test here
     * needs, the same restraint `EditEventViewModelTest`'s own `NoOpWidgetBindingRepository` uses.
     */
    private class FakeWidgetBindingRepository : WidgetBindingRepository {
        private val store = mutableMapOf<AppWidgetId, WidgetBinding>()
        override fun observeBoundWidget(appWidgetId: AppWidgetId): Flow<BoundWidget?> = flowOf(null)
        override fun observeAllBoundWidgets(): Flow<List<BoundWidget>> = flowOf(emptyList())
        override suspend fun getBoundWidget(appWidgetId: AppWidgetId): BoundWidget? = null
        override suspend fun getAllBoundWidgets(): List<BoundWidget> = emptyList()
        override suspend fun getBinding(appWidgetId: AppWidgetId): WidgetBinding? = store[appWidgetId]
        override suspend fun upsertBinding(binding: WidgetBinding) { store[binding.appWidgetId] = binding }
        override suspend fun deleteBindings(appWidgetIds: List<AppWidgetId>) { appWidgetIds.forEach(store::remove) }
        override suspend fun deleteBindingsForEvent(eventId: EventId) = Unit
        override suspend fun pruneOrphanedBindings(liveAppWidgetIds: Set<AppWidgetId>) = Unit
    }

    /** An in-memory [WidgetStyleEntitlementRepository], the same free-style short-circuit and rejection the real one enforces. */
    private class FakeWidgetStyleEntitlementRepository : WidgetStyleEntitlementRepository {
        private val granted = mutableSetOf<Pair<AppWidgetId, WidgetStyle>>()
        override suspend fun isStyleUnlocked(appWidgetId: AppWidgetId, style: WidgetStyle): Boolean =
            !style.isRewarded || (appWidgetId to style) in granted
        override suspend fun grantRewardedStyle(appWidgetId: AppWidgetId, style: WidgetStyle) {
            require(style.isRewarded) { "Cannot grant a rewarded-style entitlement for $style — it is not a rewarded style." }
            granted += appWidgetId to style
        }
    }

    /**
     * A [RewardedStyleAdController] that never makes a real ad request — [load] and [show] instead
     * synchronously drive [state] and invoke callbacks per [nextLoadOutcome]/[nextShowOutcome],
     * exactly the same shape the real `AdMobRewardedStyleAdController` produces from Google's own
     * callbacks, so [WidgetConfigurationViewModel]'s own reaction is what gets tested here, not
     * Google's SDK.
     */
    private class FakeRewardedStyleAdController : RewardedStyleAdController {
        private val _state = MutableStateFlow(RewardedAdState.LOADING)
        override val state: StateFlow<RewardedAdState> = _state.asStateFlow()

        var loadCallCount = 0
            private set
        var showCallCount = 0
            private set

        /** What the next [load] call should transition [state] to. */
        var nextLoadOutcome: LoadOutcome = LoadOutcome.Ready

        sealed interface LoadOutcome {
            /** The ad finishes loading successfully. */
            data object Ready : LoadOutcome

            /** A genuine consent/load failure. */
            data object Failed : LoadOutcome

            /** Simulates a load still in flight — [state] stays LOADING, nothing resolves yet. */
            data object StaysLoading : LoadOutcome
        }

        /**
         * Whether [show] immediately resolves via [nextShowOutcome] (the default, used by every
         * test that cares about the outcome) or only transitions to SHOWING and waits — set false
         * to test duplicate-tap-while-SHOWING behavior without needing to resolve the ad at all.
         */
        var autoResolveShow: Boolean = true
        var nextShowOutcome: ShowOutcome = ShowOutcome.RewardEarned

        sealed interface ShowOutcome {
            data object RewardEarned : ShowOutcome
            data object DismissedWithoutReward : ShowOutcome
            data class Failed(val reason: String) : ShowOutcome
        }

        override fun load(activity: Activity) {
            loadCallCount++
            _state.value = when (nextLoadOutcome) {
                LoadOutcome.Ready -> RewardedAdState.READY
                LoadOutcome.Failed -> RewardedAdState.FAILED
                LoadOutcome.StaysLoading -> RewardedAdState.LOADING
            }
        }

        override fun show(
            activity: Activity,
            onRewardEarned: () -> Unit,
            onDismissed: () -> Unit,
            onFailed: (reason: String) -> Unit,
        ) {
            showCallCount++
            _state.value = RewardedAdState.SHOWING
            if (!autoResolveShow) return
            when (val outcome = nextShowOutcome) {
                ShowOutcome.RewardEarned -> onRewardEarned()
                ShowOutcome.DismissedWithoutReward -> {
                    onDismissed()
                    // Mirrors AdMobRewardedStyleAdController's own "not a failure, prepare the next
                    // ad" lifecycle after a plain dismiss.
                    _state.value = RewardedAdState.LOADING
                }
                is ShowOutcome.Failed -> {
                    _state.value = RewardedAdState.FAILED
                    onFailed(outcome.reason)
                }
            }
        }
    }
}
