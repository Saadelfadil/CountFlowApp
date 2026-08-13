package com.countflow.widget.glance.configuration

import android.app.Activity
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.countflow.core.domain.model.AccentColor
import com.countflow.core.domain.model.AppWidgetId
import com.countflow.core.domain.model.Event
import com.countflow.core.domain.model.EventId
import com.countflow.core.domain.model.ProgressStyle
import com.countflow.core.domain.model.WidgetBinding
import com.countflow.core.domain.model.WidgetStyle
import com.countflow.core.domain.repository.EventRepository
import com.countflow.core.domain.repository.WidgetBindingRepository
import com.countflow.core.domain.repository.WidgetStyleEntitlementRepository
import com.countflow.widget.engine.provider.WidgetRenderModelProvider
import com.countflow.widget.glance.WidgetSizeClass
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.time.Clock
import javax.inject.Inject

/**
 * State holder for widget configuration.
 *
 * Takes the widget id through [load] rather than a `SavedStateHandle`. The value comes from the
 * launching `Intent`'s extras, which a Hilt-injected `SavedStateHandle` does not populate for a
 * plain `ComponentActivity` without extra wiring — passing it explicitly is one indirection
 * fewer and easier to verify than getting that wiring right.
 *
 * Never writes a binding except in direct response to [onConfirm]. That single rule is what
 * guarantees "no orphan bindings": every intermediate step — picking an event, changing style,
 * flipping a toggle — only updates in-memory state and recomputes [WidgetConfigurationUiState.previewModel]
 * through [WidgetRenderModelProvider.preview], which touches no repository. If the user backs out
 * at any point, nothing was ever written, so there is nothing to clean up — the activity's default
 * `RESULT_CANCELED`, set before any UI is shown, tells the launcher to remove the placed widget on
 * its own. Session 9 added an entire customization step to this screen specifically *because* that
 * guarantee made it safe to: a naive "write on every change, so the preview matches the database"
 * design would have reopened exactly the risk this class was built to close in Milestone 4.
 */
@HiltViewModel
internal class WidgetConfigurationViewModel @Inject constructor(
    private val eventRepository: EventRepository,
    private val widgetBindingRepository: WidgetBindingRepository,
    private val widgetStyleEntitlementRepository: WidgetStyleEntitlementRepository,
    private val adController: RewardedStyleAdController,
    private val renderModelProvider: WidgetRenderModelProvider,
    private val clock: Clock,
) : ViewModel() {

    private val _uiState = MutableStateFlow(WidgetConfigurationUiState())
    val uiState: StateFlow<WidgetConfigurationUiState> = _uiState.asStateFlow()

    private var appWidgetId: AppWidgetId? = null
    private var existingBinding: WidgetBinding? = null

    init {
        // Mirrors adController's own readiness into uiState.rewardedAdState, the one field the
        // unlock dialog actually reads — collecting here rather than in onUnlockDialogShown means
        // this never has to be started/stopped per dialog visibility, and a fresh READY/FAILED
        // value from a preload that finished while the dialog was closed is never missed.
        viewModelScope.launch {
            adController.state.collect { adState ->
                _uiState.update { it.copy(rewardedAdState = adState) }
            }
        }
    }

    /**
     * Loads step one's event list for [appWidgetId]. Safe to call more than once; only the first
     * counts.
     *
     * @param actualSizeClass the real footprint this placed widget currently occupies —
     *   `WidgetConfigurationActivity` reads it straight off `AppWidgetManager`, outside any
     *   composition, the same way it always has. Copied verbatim into
     *   [WidgetConfigurationUiState.sizeClass], which the live preview renders at and the
     *   "Preview · {size}" label reads — display-only, with no UI anywhere on this screen able to
     *   change it. Physical widget size is entirely launcher-controlled: the user resizes the real
     *   widget from the home screen, not from here.
     */
    fun load(appWidgetId: AppWidgetId, actualSizeClass: WidgetSizeClass = WidgetSizeClass.STANDARD) {
        if (this.appWidgetId != null) return
        this.appWidgetId = appWidgetId

        viewModelScope.launch {
            val existing = widgetBindingRepository.getBinding(appWidgetId)
            existingBinding = existing
            val events = eventRepository.getAllEvents().sortedBy { it.target.epochMillis }
            _uiState.update {
                it.copy(
                    events = events,
                    currentEventId = existing?.eventId,
                    sizeClass = actualSizeClass,
                    isLoading = false,
                )
            }
        }
    }

    /**
     * Moves to step two for [eventId] — customize, not save. A reconfigure of the same event
     * that already has a binding starts from its existing overrides; anything else (a fresh
     * placement, or re-pointing at a different event) starts from that event's own defaults,
     * the same `WidgetBinding.inheriting` semantics a plain event-only pick has always used.
     */
    fun onEventSelected(eventId: EventId) {
        val event = _uiState.value.events.firstOrNull { it.id == eventId } ?: return
        val existing = existingBinding?.takeIf { it.eventId == eventId }

        _uiState.update {
            it.copy(
                selectedEventId = eventId,
                widgetStyle = existing?.widgetStyleOverride ?: event.defaultWidgetStyle,
                progressStyle = existing?.progressStyleOverride ?: event.defaultProgressStyle,
                accentColor = existing?.accentColorOverride ?: event.accentColor,
                showTitle = existing?.showTitle ?: true,
                showEmoji = existing?.showEmoji ?: true,
                showTargetDate = existing?.showTargetDate ?: false,
                showPercentage = existing?.showPercentage ?: false,
            )
        }
        refreshPreview(event)
        viewModelScope.launch { refreshEntitlements() }
    }

    /** Back to step one, discarding step two's in-memory selections — nothing was ever written. */
    fun onChangeEvent() = _uiState.update {
        it.copy(selectedEventId = null, previewModel = null)
    }

    /**
     * A free style, or a rewarded style this widget already owns, selects immediately exactly
     * like before this feature existed. A locked rewarded style does not — the current selection
     * is left untouched, and [WidgetConfigurationUiState.pendingRewardRequest] is set instead,
     * which is what the unlock-confirmation dialog in `WidgetConfigurationActivity.kt` observes.
     * This is the one place that branch is decided, so the Style row's own tap handling stays
     * exactly what it already was: call this function, unconditionally, for whichever thumbnail
     * was tapped. A free style never reaches [adController] at all — it never even runs this
     * `if`.
     */
    fun onWidgetStyleChange(style: WidgetStyle) {
        val state = _uiState.value
        if (state.isStyleLocked(style)) {
            val id = appWidgetId ?: return
            _uiState.update { it.copy(pendingRewardRequest = RewardRequired(id, style), adFeedback = null) }
            return
        }
        updateAndRefresh { it.copy(widgetStyle = style) }
    }

    /** Dismisses the unlock dialog without granting anything — the user's own "Not now," or tapping outside it. */
    fun onRewardRequestHandled() = _uiState.update { it.copy(pendingRewardRequest = null, adFeedback = null) }

    /**
     * Prepares a rewarded ad the moment the unlock dialog appears, so it has a head start on
     * being ready by the time the user might tap "Watch ad & unlock" — see
     * [RewardedStyleAdController.load]'s own doc for why this is safe to call more than once.
     * Deliberately not called any earlier (e.g. as soon as Customize Widget opens): most widgets
     * are never customized with a locked style at all, and preparing an ad — which, per Google's
     * own UMP guidance, can include showing a one-time consent form — for a screen visit that
     * never needs one would be exactly the kind of eager, unearned interruption this feature's
     * own brief called "an intentional value exchange," not a default to reach for.
     */
    fun onUnlockDialogShown(activity: Activity) = adController.load(activity)

    /**
     * The user's own explicit "Watch ad & unlock" tap — the only place [RewardedStyleAdController.show]
     * is ever called, and therefore the only place an ad can ever appear. [onRewardEarned] is
     * wired to Google's genuine earned-reward callback, never to the ad merely opening or
     * closing — see [RewardedStyleAdController.show]'s own parameter docs for the exact contract
     * this relies on.
     *
     * Guarded on [RewardedAdState.READY]: this is the enforcement behind "the user must not be
     * able to call show() before READY," not just the dialog's own disabled button — a tap that
     * lands while [WidgetConfigurationUiState.rewardedAdState] is still [RewardedAdState.LOADING]
     * (a legitimate load in flight) or already [RewardedAdState.SHOWING] (a duplicate tap) is a
     * no-op here, never a call to [adController].
     */
    fun onWatchAdClicked(activity: Activity) {
        if (_uiState.value.rewardedAdState != RewardedAdState.READY) return
        _uiState.update { it.copy(adFeedback = null) }
        adController.show(
            activity = activity,
            onRewardEarned = ::onRewardEarned,
            // A voluntary close with no reward needs no explanation — the dialog is left exactly
            // as it was, ready for the user to try again once adController's own preload-after-
            // dismiss lifecycle brings state back to READY.
            onDismissed = {},
            onFailed = { reason -> _uiState.update { it.copy(adFeedback = reason) } },
        )
    }

    /**
     * The user's explicit response to a genuine [RewardedAdState.FAILED] — the only way out of
     * that state, since a failure deliberately does not auto-retry (see
     * `AdMobRewardedStyleAdController`'s own "Readiness state" doc). Guarded on
     * [RewardedAdState.FAILED] for the same reason [onWatchAdClicked] guards on
     * [RewardedAdState.READY]: a stray call from a state where there is nothing to retry is a
     * no-op, not a redundant [RewardedStyleAdController.load] call.
     */
    fun onRetryClicked(activity: Activity) {
        if (_uiState.value.rewardedAdState != RewardedAdState.FAILED) return
        _uiState.update { it.copy(adFeedback = null) }
        adController.load(activity)
    }

    /**
     * Called only from [RewardedStyleAdController.show]'s `onRewardEarned` callback — the sole
     * path that may ever call
     * [WidgetStyleEntitlementRepository.grantRewardedStyle]. Opening the ad, the ad displaying,
     * and the ad being dismissed are all deliberately *not* enough on their own; only this is.
     *
     * Order matters and is exactly what the brief specifies: grant, refresh entitlement state,
     * clear the pending request, then select — [refreshEntitlements] is awaited (not
     * fire-and-forget) so [onWidgetStyleChange] below sees the just-granted style as unlocked
     * rather than racing its own stale read of [WidgetConfigurationUiState.unlockedRewardedStyles].
     * Selecting also refreshes the Main Preview, via the same [updateAndRefresh] path every other
     * style change already uses — no separate "update the preview" step is needed.
     */
    private fun onRewardEarned() {
        val request = _uiState.value.pendingRewardRequest ?: return
        viewModelScope.launch {
            widgetStyleEntitlementRepository.grantRewardedStyle(request.appWidgetId, request.style)
            refreshEntitlements()
            _uiState.update { it.copy(pendingRewardRequest = null, adFeedback = null) }
            onWidgetStyleChange(request.style)
        }
    }

    fun onProgressStyleChange(style: ProgressStyle) =
        updateAndRefresh { it.copy(progressStyle = style) }

    fun onShowTitleChange(show: Boolean) = updateAndRefresh { it.copy(showTitle = show) }

    fun onShowEmojiChange(show: Boolean) = updateAndRefresh { it.copy(showEmoji = show) }

    fun onShowTargetDateChange(show: Boolean) = updateAndRefresh { it.copy(showTargetDate = show) }

    fun onShowPercentageChange(show: Boolean) = updateAndRefresh { it.copy(showPercentage = show) }

    fun onAccentColorChange(accentColor: AccentColor) =
        updateAndRefresh { it.copy(accentColor = accentColor) }

    /** Writes the binding step two describes, then finishes — the one place anything is saved. */
    fun onConfirm() {
        val id = appWidgetId ?: return
        val state = _uiState.value
        val eventId = state.selectedEventId ?: return
        if (state.isSaving) return
        _uiState.update { it.copy(isSaving = true) }

        viewModelScope.launch {
            val createdAt = existingBinding?.takeIf { it.eventId == eventId }?.createdAt ?: clock.instant()
            val event = state.events.first { it.id == eventId }
            val binding = WidgetBinding(
                appWidgetId = id,
                eventId = eventId,
                widgetStyleOverride = state.widgetStyle.takeIf { it != event.defaultWidgetStyle },
                progressStyleOverride = state.progressStyle.takeIf { it != event.defaultProgressStyle },
                accentColorOverride = state.accentColor.takeIf { it != event.accentColor },
                showTitle = state.showTitle,
                showEmoji = state.showEmoji,
                showTargetDate = state.showTargetDate,
                showPercentage = state.showPercentage,
                createdAt = createdAt,
            )
            widgetBindingRepository.upsertBinding(binding)
            _uiState.update { it.copy(isSaving = false, isSaved = true) }
        }
    }

    private inline fun updateAndRefresh(transform: (WidgetConfigurationUiState) -> WidgetConfigurationUiState) {
        val next = transform(_uiState.value)
        _uiState.value = next
        val event = next.events.firstOrNull { it.id == next.selectedEventId } ?: return
        refreshPreview(event)
    }

    /**
     * Recomputes [WidgetConfigurationUiState.previewModel] from the current in-memory selections.
     *
     * Threads every selection through [previewBinding], never through a faked copy of [event] —
     * this is the exact pipeline [onConfirm] writes for real, down to
     * [WidgetRenderModelProvider.preview] calling the same
     * [com.countflow.widget.engine.mapper.WidgetRenderMapper] a real render uses, so there is no
     * separate "what the preview shows" logic that could drift from "what Save persists." An
     * earlier version faked the accent specifically by copying it onto [event]
     * (`event.copy(accentColor = it)`) rather than the binding — accurate for the preview alone,
     * but the reason Save had nothing to actually write: no field carried the choice anywhere
     * [onConfirm] could reach it.
     */
    private fun refreshPreview(event: Event) {
        val state = _uiState.value
        val previewBinding = WidgetBinding(
            appWidgetId = appWidgetId ?: AppWidgetId(AppWidgetId.INVALID),
            eventId = event.id,
            widgetStyleOverride = state.widgetStyle.takeIf { it != event.defaultWidgetStyle },
            progressStyleOverride = state.progressStyle.takeIf { it != event.defaultProgressStyle },
            accentColorOverride = state.accentColor.takeIf { it != event.accentColor },
            showTitle = state.showTitle,
            showEmoji = state.showEmoji,
            showTargetDate = state.showTargetDate,
            showPercentage = state.showPercentage,
            createdAt = clock.instant(),
        )
        val model = renderModelProvider.preview(event, previewBinding)
        _uiState.update { it.copy(previewModel = model) }
    }

    /**
     * Resolves [WidgetConfigurationUiState.unlockedRewardedStyles] for the current
     * [appWidgetId] — one [WidgetStyleEntitlementRepository.isStyleUnlocked] check per
     * [WidgetStyle.rewarded] entry, since that is the entire alphabet of styles that can ever be
     * locked. Runs once per event selection and once right after a grant, not on every keystroke
     * like [updateAndRefresh] does for the preview: unlike the preview, what this widget owns does
     * not change just because the user picked a different style or flipped a toggle.
     *
     * A real `suspend fun`, not a fire-and-forget [viewModelScope] launch — [onRewardEarned]
     * depends on awaiting this before it reads [WidgetConfigurationUiState.unlockedRewardedStyles]
     * again, so a caller that only wants "kick this off, I don't need to wait" (like
     * [onEventSelected]) wraps it in its own `launch` instead of this function hiding one inside
     * itself.
     */
    private suspend fun refreshEntitlements() {
        val id = appWidgetId ?: return
        val unlocked = WidgetStyle.rewarded
            .filter { widgetStyleEntitlementRepository.isStyleUnlocked(id, it) }
            .toSet()
        _uiState.update { it.copy(unlockedRewardedStyles = unlocked) }
    }
}
