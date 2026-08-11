package com.countflow.app.ads

import com.countflow.BuildConfig
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * Guards the one thing this project cannot afford to get wrong about AdMob: DEBUG must always
 * resolve Google's published test Rewarded Ad Unit ID, and RELEASE must always resolve CountFlow's
 * real production one — never the other way around, never crossed.
 *
 * [debug variant, direct]: `:app:test`/`:app:testDebugUnitTest` compiles and runs against the
 * DEBUG variant's own generated `BuildConfig` (AGP's `testBuildType` defaults to, and this project
 * never overrides, `"debug"`) — so the first two tests below read the real, AGP-resolved value for
 * DEBUG directly, not a guess about what it should be.
 *
 * [release variant, source-of-truth]: nothing in this same debug-variant JVM test process can
 * construct or read the RELEASE variant's own `BuildConfig` — AGP only compiles unit tests against
 * one variant per module, and switching that variant project-wide is a bigger change than this
 * task's own "do not redesign anything else" allows. The third test instead reads
 * `app/build.gradle.kts` itself — the single source of truth `AdMobConfig` lives in — and asserts
 * its RELEASE constants are CountFlow's real production identifiers, never the DEBUG/test ones.
 * The stronger, real-build confirmation (that AGP actually wires these into a genuine
 * `assembleRelease` output) is a manual validation step for this task, not an automated test here —
 * see the session's own report for the merged-manifest/generated-BuildConfig evidence.
 */
class AdMobConfigTest {

    @Test
    fun `debug build config uses Google's published test rewarded ad unit id`() {
        assertEquals("ca-app-pub-3940256099942544/5224354917", BuildConfig.REWARDED_STYLE_AD_UNIT_ID)
    }

    @Test
    fun `debug build config never resolves CountFlow's production rewarded ad unit id`() {
        assertNotEquals("ca-app-pub-3546123128954911/7392472066", BuildConfig.REWARDED_STYLE_AD_UNIT_ID)
    }

    @Test
    fun `release buildType in app build gradle kts declares CountFlow's production identifiers, not the test ones`() {
        val buildScript = File("build.gradle.kts").readText()

        assertTrue(
            "AdMobConfig.RELEASE_APPLICATION_ID must be CountFlow's real production App ID",
            buildScript.contains("""RELEASE_APPLICATION_ID = "ca-app-pub-3546123128954911~2283615612""""),
        )
        assertTrue(
            "AdMobConfig.RELEASE_REWARDED_AD_UNIT_ID must be CountFlow's real production Rewarded Ad Unit ID",
            buildScript.contains("""RELEASE_REWARDED_AD_UNIT_ID = "ca-app-pub-3546123128954911/7392472066""""),
        )
        assertTrue(
            "AdMobConfig.DEBUG_APPLICATION_ID must be Google's published test App ID",
            buildScript.contains("""DEBUG_APPLICATION_ID = "ca-app-pub-3940256099942544~3347511713""""),
        )
        assertTrue(
            "AdMobConfig.DEBUG_REWARDED_AD_UNIT_ID must be Google's published test Rewarded Ad Unit ID",
            buildScript.contains("""DEBUG_REWARDED_AD_UNIT_ID = "ca-app-pub-3940256099942544/5224354917""""),
        )
        assertTrue(
            "the release buildType block must wire manifestPlaceholders/buildConfigField from AdMobConfig.RELEASE_*, not DEBUG_*",
            buildScript.substringAfter("release {").substringBefore("\n        }")
                .contains("AdMobConfig.RELEASE_APPLICATION_ID") &&
                buildScript.substringAfter("release {").substringBefore("\n        }")
                    .contains("AdMobConfig.RELEASE_REWARDED_AD_UNIT_ID"),
        )
    }
}
