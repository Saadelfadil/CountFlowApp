package com.countflow.widget.glance.configuration

import androidx.compose.runtime.Immutable
import com.countflow.core.domain.model.AccentColor
import com.countflow.core.domain.model.AppWidgetId
import com.countflow.core.domain.model.Event
import com.countflow.core.domain.model.EventId
import com.countflow.core.domain.model.ProgressStyle
import com.countflow.core.domain.model.WidgetStyle
import com.countflow.widget.engine.model.WidgetRenderModel
import com.countflow.widget.glance.WidgetSizeClass

/**
 * The configuration screen, as one immutable value.
 *
 * Two steps, not two screens: [selectedEventId] being null is "choose an event"; non-null is
 * "customize it," with [previewModel] reflecting exactly what a save right now would produce.
 * Nothing here writes anything — see [com.countflow.widget.glance.configuration.WidgetConfigurationViewModel]'s
 * class doc for why that guarantee matters.
 *
 * @property events the user's events to choose from, soonest first.
 * @property currentEventId the event already bound to this widget, if this is a reconfigure
 *   rather than a first-time setup. Used only to highlight the existing choice in step one.
 * @property selectedEventId the event chosen for step two — the "which event" this widget will
 *   show, distinct from [currentEventId] once the user picks something.
 * @property widgetStyle the style being customized.
 * @property progressStyle the progress presentation being customized.
 * @property showTitle whether the event title will be drawn.
 * @property showEmoji whether the event emoji will be drawn.
 * @property showTargetDate whether the target date will be drawn.
 * @property showPercentage whether the progress percentage will be drawn alongside the bar.
 * @property accentColor the accent being customized — always a resolved value (this widget's own
 *   override if it has one, otherwise the event's), the same shape [widgetStyle]/[progressStyle]
 *   already use, so [onConfirm][com.countflow.widget.glance.configuration.WidgetConfigurationViewModel.onConfirm]
 *   derives the same "only write an override when it actually differs" precedence
 *   [com.countflow.core.domain.model.WidgetBinding] applies to style and progress style (D-013).
 * @property unlockedRewardedStyles which of [WidgetStyle.rewarded] *this* widget currently owns
 *   an entitlement for — resolved once per event selection via
 *   [com.countflow.core.domain.repository.WidgetStyleEntitlementRepository.isStyleUnlocked], never
 *   cached across widgets or events. A free style is never a member and never needs to be; see
 *   [isStyleLocked].
 * @property pendingRewardRequest set when the user taps a rewarded style this widget does not yet
 *   own, instead of selecting it — drives the unlock-confirmation dialog. See [RewardRequired]'s
 *   own KDoc for the full grant contract.
 * @property rewardedAdState a live mirror of [RewardedStyleAdController.state] — what the unlock
 *   dialog's primary button says and whether it can be tapped. [WidgetConfigurationViewModel]
 *   collects the controller's [kotlinx.coroutines.flow.StateFlow] once, in `init`, and copies every
 *   value into this field, so the dialog only ever needs to read [WidgetConfigurationUiState] like
 *   everything else on this screen already does.
 * @property adFeedback a short, user-presentable message for the unlock dialog when a rewarded ad
 *   could not be shown (a genuine [RewardedAdState.FAILED] — unavailable, load error, show error)
 *   — never set merely because [rewardedAdState] is still [RewardedAdState.LOADING], and never set
 *   for a plain user-initiated dismiss, which needs no explanation. Cleared whenever
 *   [pendingRewardRequest] is cleared or a new watch/retry attempt starts.
 * @property previewModel the render model step two's live preview draws — computed by
 *   [com.countflow.widget.engine.provider.WidgetRenderModelProvider.preview] from the selections
 *   above, the same pipeline a real widget render uses, never faked.
 * @property sizeClass the real footprint this placed widget currently occupies, read once (in
 *   [com.countflow.widget.glance.configuration.WidgetConfigurationViewModel.load]) from the actual
 *   size `WidgetConfigurationActivity` reads off `AppWidgetManager`, and never changed from
 *   anywhere else in this UI — there is deliberately no way for the user to pick a different value
 *   here. Physical widget size is launcher-controlled (Android/One UI decides it when the widget is
 *   placed or resized on the home screen); this screen only *displays* it, via [previewModel]
 *   rendering at [sizeClass] and the "Preview · {size}" label reading it, matching the size the
 *   real widget will actually be redrawn at. [WidgetBinding][com.countflow.core.domain.model.WidgetBinding]
 *   has no size field at all, so [com.countflow.widget.glance.configuration.WidgetConfigurationViewModel.onConfirm]
 *   is structurally incapable of persisting this value even by accident.
 * @property isLoading true until the event list has loaded once.
 * @property isSaving true once the user has confirmed and the binding is being written.
 * @property isSaved true once the binding write has completed and the activity should finish
 *   with `RESULT_OK`.
 */
@Immutable
internal data class WidgetConfigurationUiState(
    val events: List<Event> = emptyList(),
    val currentEventId: EventId? = null,
    val selectedEventId: EventId? = null,
    val widgetStyle: WidgetStyle = WidgetStyle.Default,
    val progressStyle: ProgressStyle = ProgressStyle.Default,
    val showTitle: Boolean = true,
    val showEmoji: Boolean = true,
    val showTargetDate: Boolean = false,
    val showPercentage: Boolean = false,
    val accentColor: AccentColor = AccentColor.Default,
    val unlockedRewardedStyles: Set<WidgetStyle> = emptySet(),
    val pendingRewardRequest: RewardRequired? = null,
    val rewardedAdState: RewardedAdState = RewardedAdState.LOADING,
    val adFeedback: String? = null,
    val previewModel: WidgetRenderModel? = null,
    val sizeClass: WidgetSizeClass = WidgetSizeClass.STANDARD,
    val isLoading: Boolean = true,
    val isSaving: Boolean = false,
    val isSaved: Boolean = false,
) {
    /** Whether step two (customize) should show — an event has been chosen for this widget. */
    val isCustomizing: Boolean get() = selectedEventId != null

    /**
     * Whether [style] should render locked in the Style row right now — true for exactly the
     * styles that are [WidgetStyle.isRewarded] and not (yet) in [unlockedRewardedStyles]. A free
     * style is never locked, regardless of what [unlockedRewardedStyles] happens to contain.
     */
    fun isStyleLocked(style: WidgetStyle): Boolean = style.isRewarded && style !in unlockedRewardedStyles
}

/**
 * Requests that [style] be unlocked for [appWidgetId] before it can be selected — raised instead
 * of selecting, the instant a user taps a rewarded style this widget does not yet own. Drives the
 * unlock-confirmation dialog in `WidgetConfigurationActivity.kt`:
 * ```
 * RewardRequired(widgetId, GLASS)
 *         -> unlock-confirmation dialog ("Watch ad & unlock")
 *         -> RewardedStyleAdController.show
 *         -> genuine earned-reward callback
 *         -> WidgetStyleEntitlementRepository.grantRewardedStyle(widgetId, GLASS)
 *         -> refresh entitlement state
 *         -> select Glass
 * ```
 * AdMob itself never sees this type — [RewardedStyleAdController] is the one boundary an ad SDK
 * type may cross, and nothing about this type depends on one.
 */
data class RewardRequired(val appWidgetId: AppWidgetId, val style: WidgetStyle)
