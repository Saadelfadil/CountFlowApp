plugins {
    alias(libs.plugins.countflow.android.application)
    alias(libs.plugins.countflow.android.compose)
    alias(libs.plugins.countflow.android.hilt)
    alias(libs.plugins.kotlin.serialization)
}

/**
 * The one source of truth for every AdMob identifier CountFlow uses — the only place these
 * strings are allowed to appear (see the `manifestPlaceholders`/`buildConfigField` wiring below;
 * nowhere in Kotlin source references a literal AdMob ID). DEBUG values are Google's own published
 * sample/test identifiers (developers.google.com/admob/android/test-ads) — always return a test
 * ad, never real inventory. RELEASE values are CountFlow's real, owner-provided production
 * identifiers. [AdMobConfigTest] (`app/src/test/kotlin/com/countflow/app/ads/AdMobConfigTest.kt`)
 * asserts the DEBUG variant's resolved `BuildConfig.REWARDED_STYLE_AD_UNIT_ID` is exactly the test
 * value and never the production one, and that this file's own declared RELEASE constants are the
 * production ones and never the test ones — the guard against DEBUG/RELEASE ever crossing.
 */
private object AdMobConfig {
    // TEST — Google's own published sample identifiers. DEBUG builds only; must never be used
    // in a RELEASE build.
    const val DEBUG_APPLICATION_ID = "ca-app-pub-3940256099942544~3347511713"
    const val DEBUG_REWARDED_AD_UNIT_ID = "ca-app-pub-3940256099942544/5224354917"

    // PRODUCTION — CountFlow's real AdMob identifiers. RELEASE builds only; must never be used
    // in a DEBUG build, and must never be logged (see AdMobRewardedStyleAdController's diagnostic
    // logging, which is itself BuildConfig.DEBUG-gated and therefore never runs in RELEASE anyway).
    const val RELEASE_APPLICATION_ID = "ca-app-pub-3546123128954911~2283615612"
    const val RELEASE_REWARDED_AD_UNIT_ID = "ca-app-pub-3546123128954911/7392472066"
}

android {
    namespace = "com.countflow"

    defaultConfig {
        applicationId = "com.countflow"
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    // BuildConfig.DEBUG gates the rewarded-ad pipeline diagnostic logging in
    // AdConsentGate/AdMobRewardedStyleAdController — off (dead code) in a release build.
    // REWARDED_STYLE_AD_UNIT_ID is the one path AdMobRewardedStyleAdController reads the active
    // variant's rewarded ad unit ID from; the manifest's own AdMob APPLICATION_ID meta-data
    // resolves the matching App ID via the `admobApplicationId` manifest placeholder below — one
    // configuration source per build variant, both driven from AdMobConfig above.
    buildFeatures {
        buildConfig = true
    }

    buildTypes {
        debug {
            manifestPlaceholders["admobApplicationId"] = AdMobConfig.DEBUG_APPLICATION_ID
            buildConfigField("String", "REWARDED_STYLE_AD_UNIT_ID", "\"${AdMobConfig.DEBUG_REWARDED_AD_UNIT_ID}\"")
        }
        release {
            manifestPlaceholders["admobApplicationId"] = AdMobConfig.RELEASE_APPLICATION_ID
            buildConfigField("String", "REWARDED_STYLE_AD_UNIT_ID", "\"${AdMobConfig.RELEASE_REWARDED_AD_UNIT_ID}\"")
        }
    }
}

dependencies {
    // Features
    implementation(projects.feature.events)
    implementation(projects.feature.settings)
    implementation(projects.feature.premium)

    // Core
    implementation(projects.core.designsystem)
    implementation(projects.core.common)
    implementation(projects.core.domain)
    implementation(projects.core.data)
    implementation(projects.core.analytics)
    implementation(projects.core.billing)
    implementation(projects.core.notifications)

    // Widgets
    implementation(projects.widget.glance)
    implementation(projects.widget.engine)

    // AndroidX
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.splashscreen)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.navigation.compose)

    // WorkManager, wired through Hilt so workers can take constructor dependencies.
    implementation(libs.androidx.work.runtime.ktx)
    implementation(libs.androidx.hilt.work)
    ksp(libs.androidx.hilt.compiler)

    implementation(libs.kotlinx.serialization.json)

    // Rewarded-style unlock (test ads only — see AdMobRewardedStyleAdController's own KDoc and
    // DECISIONS.md). Deliberately :app-only: RewardedStyleAdController, the interface
    // :widget:glance actually depends on, has no Google Mobile Ads or UMP import at all.
    implementation(libs.play.services.ads)
    implementation(libs.user.messaging.platform)

    testImplementation(libs.junit4)
    androidTestImplementation(libs.androidx.test.ext.junit)
    androidTestImplementation(libs.androidx.test.espresso.core)
}
