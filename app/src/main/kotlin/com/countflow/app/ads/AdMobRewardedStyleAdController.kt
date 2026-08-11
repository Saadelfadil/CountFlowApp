package com.countflow.app.ads

import android.app.Activity
import android.util.Log
import com.countflow.BuildConfig
import com.countflow.widget.glance.configuration.RewardedAdState
import com.countflow.widget.glance.configuration.RewardedStyleAdController
import com.google.android.gms.ads.AdError
import com.google.android.gms.ads.AdRequest
import com.google.android.gms.ads.FullScreenContentCallback
import com.google.android.gms.ads.LoadAdError
import com.google.android.gms.ads.MobileAds
import com.google.android.gms.ads.initialization.InitializationStatus
import com.google.android.gms.ads.rewarded.RewardedAd
import com.google.android.gms.ads.rewarded.RewardedAdLoadCallback
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * The real [RewardedStyleAdController] — Google Mobile Ads (`play-services-ads`, "maintenance
 * mode" but current and fully documented — see `gradle/libs.versions.toml`'s own note on why the
 * newer, still-evolving "GMA Next-Gen SDK" was not used) plus UMP. This is the one class in
 * CountFlow that imports either SDK; [RewardedStyleAdController] itself, which `:widget:glance`
 * actually depends on, has no such import, which is what keeps that module free of an ads
 * dependency.
 *
 * The rewarded ad unit ID is never a literal in this file — [requestAdLoad] reads
 * [BuildConfig.REWARDED_STYLE_AD_UNIT_ID], resolved per build variant from `AdMobConfig` in
 * `app/build.gradle.kts` (Google's test ID for DEBUG, CountFlow's real production ID for RELEASE —
 * the single source of truth for both; see that file's own KDoc and [AdMobConfigTest]).
 *
 * ### Reward security
 *
 * [show]'s `onRewardEarned` is wired to nothing but [RewardedAd.show]'s own
 * `OnUserEarnedRewardListener` lambda — never to [FullScreenContentCallback.onAdShowedFullScreenContent]
 * or [FullScreenContentCallback.onAdDismissedFullScreenContent], both of which fire whether or not
 * a reward was actually earned. [earned] (a local flag inside [show], not a class field — a fresh
 * one per call) is what stops [FullScreenContentCallback.onAdDismissedFullScreenContent] from also
 * reporting a redundant "dismissed without reward" immediately after a genuine reward already
 * fired for the same ad.
 *
 * ### Readiness state
 *
 * [state] is what the Unlock Style dialog reads to decide whether "Watch ad & unlock" can be
 * tapped yet. A genuine load/show failure ([RewardedAdState.FAILED]) does **not** automatically
 * retry — that would make a "Retry" action meaningless, since there would be nothing left to
 * retry by the time the user saw it. Only a *dismiss with no reward* ([onAdDismissedFullScreenContent])
 * still auto-prepares the next ad, exactly as before this state model existed: that path is not a
 * failure, so there is nothing for the user to retry — the existing preload lifecycle is preserved
 * unchanged for it.
 */
@Singleton
internal class AdMobRewardedStyleAdController @Inject constructor(
    private val consentGate: AdConsentGate,
) : RewardedStyleAdController {

    @Volatile private var rewardedAd: RewardedAd? = null
    @Volatile private var isLoading = false
    @Volatile private var sdkInitialized = false

    private val _state = MutableStateFlow(RewardedAdState.LOADING)
    override val state: StateFlow<RewardedAdState> = _state.asStateFlow()

    override fun load(activity: Activity) {
        if (rewardedAd != null) {
            if (BuildConfig.DEBUG) Log.d(ADS_DIAGNOSTIC_TAG, "load(): skipped — an ad is already loaded")
            _state.value = RewardedAdState.READY
            return
        }
        if (isLoading) {
            if (BuildConfig.DEBUG) Log.d(ADS_DIAGNOSTIC_TAG, "load(): skipped — a load is already in progress")
            return
        }
        isLoading = true
        _state.value = RewardedAdState.LOADING
        consentGate.refresh(activity) {
            val canRequestAds = consentGate.canRequestAds()
            if (!canRequestAds) {
                isLoading = false
                _state.value = RewardedAdState.FAILED
                if (BuildConfig.DEBUG) {
                    Log.w(ADS_DIAGNOSTIC_TAG, "load(): aborting — consent does not currently allow ad requests")
                }
                return@refresh
            }
            ensureSdkInitialized(activity) { requestAdLoad(activity) }
        }
    }

    override fun show(
        activity: Activity,
        onRewardEarned: () -> Unit,
        onDismissed: () -> Unit,
        onFailed: (reason: String) -> Unit,
    ) {
        val ad = rewardedAd
        if (ad == null) {
            // Defensive only — WidgetConfigurationViewModel.onWatchAdClicked never calls show()
            // unless state is already READY. No auto-reload here: a genuine "not actually ready"
            // surprise is exactly what FAILED + an explicit Retry exists for, not a silent retry.
            if (BuildConfig.DEBUG) {
                Log.w(
                    ADS_DIAGNOSTIC_TAG,
                    "show(): no ad loaded yet (rewardedAd == null) — this should not happen once callers " +
                        "gate on RewardedAdState.READY",
                )
            }
            _state.value = RewardedAdState.FAILED
            onFailed(AD_UNAVAILABLE_MESSAGE)
            return
        }
        if (BuildConfig.DEBUG) Log.d(ADS_DIAGNOSTIC_TAG, "show(): presenting the loaded ad")
        _state.value = RewardedAdState.SHOWING
        rewardedAd = null

        var earned = false
        ad.fullScreenContentCallback = object : FullScreenContentCallback() {
            override fun onAdDismissedFullScreenContent() {
                if (BuildConfig.DEBUG) Log.d(ADS_DIAGNOSTIC_TAG, "onAdDismissedFullScreenContent(): earned=$earned")
                if (!earned) onDismissed()
                // The ad just consumed is gone either way — get the next one ready per Google's
                // own recommended reload-after-dismiss lifecycle, not requested again from
                // scratch only if and when the user taps a locked style a second time. Not a
                // failure, so no FAILED state and nothing for a "Retry" action to do here.
                load(activity)
            }

            override fun onAdFailedToShowFullScreenContent(error: AdError) {
                if (BuildConfig.DEBUG) {
                    Log.w(
                        ADS_DIAGNOSTIC_TAG,
                        "onAdFailedToShowFullScreenContent(): code=${error.code} domain=${error.domain} " +
                            "message=${error.message}",
                    )
                }
                _state.value = RewardedAdState.FAILED
                onFailed(AD_UNAVAILABLE_MESSAGE)
            }
        }

        ad.show(activity) { rewardItem ->
            earned = true
            if (BuildConfig.DEBUG) {
                Log.d(
                    ADS_DIAGNOSTIC_TAG,
                    "OnUserEarnedRewardListener: reward earned — type=${rewardItem.type}, amount=${rewardItem.amount}",
                )
            }
            onRewardEarned()
        }
    }

    private fun ensureSdkInitialized(activity: Activity, onInitialized: () -> Unit) {
        if (sdkInitialized) {
            if (BuildConfig.DEBUG) Log.d(ADS_DIAGNOSTIC_TAG, "ensureSdkInitialized(): already initialized")
            onInitialized()
            return
        }
        if (BuildConfig.DEBUG) Log.d(ADS_DIAGNOSTIC_TAG, "MobileAds.initialize(): starting")
        MobileAds.initialize(activity.applicationContext) { status: InitializationStatus ->
            sdkInitialized = true
            if (BuildConfig.DEBUG) {
                val summary = status.adapterStatusMap.entries.joinToString {
                    (adapter, adapterStatus) -> "$adapter=${adapterStatus.initializationState}"
                }
                Log.d(ADS_DIAGNOSTIC_TAG, "MobileAds.initialize(): completed — adapterStatus=[$summary]")
            }
            onInitialized()
        }
    }

    private fun requestAdLoad(activity: Activity) {
        if (BuildConfig.DEBUG) {
            Log.d(ADS_DIAGNOSTIC_TAG, "RewardedAd.load(): calling — adUnitId=${BuildConfig.REWARDED_STYLE_AD_UNIT_ID}")
        }
        RewardedAd.load(
            activity,
            BuildConfig.REWARDED_STYLE_AD_UNIT_ID,
            AdRequest.Builder().build(),
            object : RewardedAdLoadCallback() {
                override fun onAdLoaded(ad: RewardedAd) {
                    isLoading = false
                    rewardedAd = ad
                    _state.value = RewardedAdState.READY
                    if (BuildConfig.DEBUG) Log.d(ADS_DIAGNOSTIC_TAG, "RewardedAdLoadCallback.onAdLoaded(): ad ready")
                }

                override fun onAdFailedToLoad(error: LoadAdError) {
                    isLoading = false
                    rewardedAd = null
                    _state.value = RewardedAdState.FAILED
                    if (BuildConfig.DEBUG) {
                        Log.w(
                            ADS_DIAGNOSTIC_TAG,
                            "RewardedAdLoadCallback.onAdFailedToLoad(): code=${error.code} domain=${error.domain} " +
                                "message=${error.message} responseInfo=${error.responseInfo}",
                        )
                    }
                }
            },
        )
    }

    private companion object {
        const val AD_UNAVAILABLE_MESSAGE = "Ad unavailable right now. Try again later."
    }
}
