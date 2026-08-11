package com.countflow.app.ads

import android.app.Activity
import android.util.Log
import com.countflow.BuildConfig
import com.google.android.ump.ConsentInformation
import com.google.android.ump.ConsentRequestParameters
import com.google.android.ump.UserMessagingPlatform
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Debug-build-only diagnostic tag for the rewarded-ad pipeline (this class and
 * [AdMobRewardedStyleAdController]) — added for the Samsung Galaxy A55 "Ad unavailable right now"
 * root-cause investigation. Every call site is guarded by [BuildConfig.DEBUG], so nothing here
 * logs, or even evaluates its log message, in a release build. Filter Logcat for this tag while
 * reproducing the issue.
 */
internal const val ADS_DIAGNOSTIC_TAG = "CountFlowAds"

/**
 * Wraps Google's UMP [ConsentInformation] — the one place CountFlow decides whether a rewarded ad
 * may even be requested. No custom GDPR logic anywhere in this class; every decision comes
 * straight from Google's own SDK ([ConsentInformation.requestConsentInfoUpdate],
 * [UserMessagingPlatform.loadAndShowConsentFormIfRequired], [ConsentInformation.canRequestAds]).
 *
 * Scoped to `WidgetConfigurationActivity` rather than `CountFlowApplication`/`MainActivity` — ads
 * only ever show on that one screen, and `CountFlowApplication`'s own class doc is explicit that
 * work done in `onCreate` is charged directly to cold start; refreshing consent on every app
 * launch would tax the overwhelming majority of sessions that never touch a locked style at all.
 * [refresh] is instead called the moment the unlock dialog appears (see
 * `WidgetConfigurationViewModel.onUnlockDialogShown`), which — for an app with two distinct entry
 * points where only one ever shows ads — is genuinely "the launch of the flow that needs it," not
 * a narrower reading of Google's own "refresh at app launch" guidance.
 */
@Singleton
internal class AdConsentGate @Inject constructor() {

    @Volatile
    private var consentInformation: ConsentInformation? = null

    /**
     * Refreshes consent state per Google's current guidance, showing a consent form if — and
     * only if — Google's own SDK decides one is required, then calls [onReady] once the sequence
     * settles. [onReady] fires whether the refresh itself succeeded or not (e.g. offline): CountFlow's
     * core functionality must never wait on an ad-consent network call, and [canRequestAds] below
     * reflects the true, current state either way, not whether this specific callback fired
     * cleanly.
     */
    fun refresh(activity: Activity, onReady: () -> Unit) {
        val info = UserMessagingPlatform.getConsentInformation(activity)
        consentInformation = info
        if (BuildConfig.DEBUG) Log.d(ADS_DIAGNOSTIC_TAG, "requestConsentInfoUpdate(): starting")
        info.requestConsentInfoUpdate(
            activity,
            ConsentRequestParameters.Builder().build(),
            {
                if (BuildConfig.DEBUG) {
                    Log.d(
                        ADS_DIAGNOSTIC_TAG,
                        "requestConsentInfoUpdate(): success — consentStatus=${info.consentStatus}, " +
                            "canRequestAds=${info.canRequestAds()}",
                    )
                }
                UserMessagingPlatform.loadAndShowConsentFormIfRequired(activity) { formError ->
                    if (BuildConfig.DEBUG) {
                        if (formError == null) {
                            Log.d(
                                ADS_DIAGNOSTIC_TAG,
                                "loadAndShowConsentFormIfRequired(): completed with no error — " +
                                    "consentStatus=${info.consentStatus}, canRequestAds=${info.canRequestAds()}",
                            )
                        } else {
                            Log.w(
                                ADS_DIAGNOSTIC_TAG,
                                "loadAndShowConsentFormIfRequired(): failed — errorCode=${formError.errorCode}, " +
                                    "message=${formError.message}",
                            )
                        }
                    }
                    onReady()
                }
            },
            { formError ->
                if (BuildConfig.DEBUG) {
                    Log.w(
                        ADS_DIAGNOSTIC_TAG,
                        "requestConsentInfoUpdate(): failed — errorCode=${formError.errorCode}, " +
                            "message=${formError.message}",
                    )
                }
                onReady()
            },
        )
    }

    /**
     * Whether an ad may be requested right now — the one gate [AdMobRewardedStyleAdController]
     * checks before ever calling [com.google.android.gms.ads.rewarded.RewardedAd.load].
     */
    fun canRequestAds(): Boolean {
        val result = consentInformation?.canRequestAds() == true
        if (BuildConfig.DEBUG) Log.d(ADS_DIAGNOSTIC_TAG, "canRequestAds() = $result")
        return result
    }
}
