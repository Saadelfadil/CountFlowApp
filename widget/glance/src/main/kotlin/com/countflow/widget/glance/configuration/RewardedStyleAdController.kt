package com.countflow.widget.glance.configuration

import android.app.Activity
import kotlinx.coroutines.flow.StateFlow

/**
 * Loads and shows a rewarded ad to unlock one [com.countflow.core.domain.model.WidgetStyle] for
 * one widget.
 *
 * The abstraction that keeps AdMob out of `:core:domain`, `:core:database`, and `:widget:engine`:
 * this interface has no dependency on Google Mobile Ads or UMP whatsoever, only [Activity] (a
 * rewarded ad's own `load`/`show` calls are inherently Activity-scoped, per Google's own APIs) and
 * plain Kotlin callbacks/[StateFlow]. [WidgetConfigurationViewModel] depends on this interface
 * only, never on an ad SDK type, which is what keeps it constructible with a fake in tests and
 * testable without making a real ad request.
 *
 * Implemented once, in `:app`, where the actual `play-services-ads`/UMP dependencies live — see
 * `com.countflow.app.ads.AdMobRewardedStyleAdController`. Nothing here, or in any caller, may
 * grant an entitlement directly; [onRewardEarned] in [show] is the only trigger
 * [WidgetConfigurationViewModel] ever wires to
 * [com.countflow.core.domain.repository.WidgetStyleEntitlementRepository.grantRewardedStyle].
 */
interface RewardedStyleAdController {

    /**
     * The current readiness of the ad this controller is preparing/showing — the Unlock Style
     * dialog reads this (mirrored into [WidgetConfigurationUiState.rewardedAdState]) to decide
     * whether its primary button is disabled ([RewardedAdState.LOADING]/[RewardedAdState.SHOWING]),
     * reads "Watch ad & unlock" ([RewardedAdState.READY]), or reads "Retry"
     * ([RewardedAdState.FAILED]). This is what stops the UI from ever calling [show] before an ad
     * is genuinely ready, and from reporting "Ad unavailable" while a legitimate load is still in
     * progress.
     */
    val state: StateFlow<RewardedAdState>

    /**
     * Prepares a rewarded ad ahead of a possible [show] call, updating [state] as it progresses
     * (typically [RewardedAdState.LOADING] then [RewardedAdState.READY] or
     * [RewardedAdState.FAILED]). Safe to call repeatedly — an implementation must treat this as
     * "make sure one is ready," not "start a new request every time": if one is already loaded,
     * [state] should simply reflect [RewardedAdState.READY] immediately; if one is already
     * loading, a second call must not start a redundant request. Also the one function a
     * [RewardedAdState.FAILED] "Retry" action calls to try again. Must never block or show any UI
     * by itself.
     */
    fun load(activity: Activity)

    /**
     * Shows the loaded ad, if one is ready. Callers must not call this unless [state] is
     * currently [RewardedAdState.READY] — [WidgetConfigurationViewModel.onWatchAdClicked] enforces
     * this before ever reaching an implementation, so a well-behaved implementation can assume it,
     * though it should still fail gracefully via [onFailed] rather than crash if it somehow is not.
     *
     * @param onRewardEarned called if, and only if, the ad's own genuine earned-reward callback
     *   fires — never for the ad merely opening, displaying, or being dismissed. This is the one
     *   signal [WidgetConfigurationViewModel] treats as authorization to grant an entitlement.
     * @param onDismissed called when the user closes the ad having *not* earned a reward — a
     *   voluntary cancel, not a failure. Must not fire if [onRewardEarned] already fired for the
     *   same [show] call.
     * @param onFailed called when the ad cannot be shown at all — none was ready, or the SDK
     *   reported an error while trying to display it. [reason] is a short, user-presentable
     *   message, not a stack trace or SDK error code.
     */
    fun show(
        activity: Activity,
        onRewardEarned: () -> Unit,
        onDismissed: () -> Unit,
        onFailed: (reason: String) -> Unit,
    )
}

/**
 * The rewarded-ad pipeline's readiness, as observed by the Unlock Style dialog through
 * [RewardedStyleAdController.state].
 *
 * Deliberately small — four states, no sub-states — because the UI only ever needs to answer two
 * questions from it: "can the user tap the primary button right now" and "what should it say."
 * [SHOWING] begins the instant the user taps "Watch ad & unlock," before any SDK callback fires,
 * specifically so a second, duplicate tap during that window has nothing to act on.
 */
enum class RewardedAdState {
    /** A load is in progress — the dialog shows "Preparing ad…" with its primary button disabled. */
    LOADING,

    /** An ad is loaded and ready — the dialog enables "Watch ad & unlock." */
    READY,

    /** The user has tapped "Watch ad & unlock" and the ad is being presented — button disabled. */
    SHOWING,

    /**
     * A genuine consent/load/show failure — never set merely because a load is still in progress.
     * The dialog shows the failure message and a "Retry" action, which calls
     * [RewardedStyleAdController.load] again.
     */
    FAILED,
}
