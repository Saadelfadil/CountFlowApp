package com.countflow.feature.settings.navigation

import androidx.navigation.NavController
import androidx.navigation.NavGraphBuilder
import androidx.navigation.compose.composable
import com.countflow.feature.settings.about.AboutScreen
import com.countflow.feature.settings.SettingsScreen
import kotlinx.serialization.Serializable

/** Routes owned by the settings feature. */
@Serializable
data object SettingsRoute

@Serializable
data object AboutRoute

/** Navigates to settings. */
fun NavController.navigateToSettings() = navigate(SettingsRoute)

/** Navigates to the about screen. */
fun NavController.navigateToAbout() = navigate(AboutRoute)

/**
 * Registers the settings feature's destinations.
 *
 * Settings does not surface a Premium/Upgrade entry point (Session 14) — Billing and Pro features
 * are explicitly out of Milestone 6's scope, and a paywall-adjacent row would misrepresent what
 * the app currently offers. `:feature:premium`'s route stays registered in the nav graph
 * ([CountFlowNavHost][com.countflow.app.navigation.CountFlowNavHost]) for when Milestone 9
 * delivers it; only the link from here was removed.
 *
 * @param onNavigateToAbout invoked when the user opens About.
 * @param onNavigateBack invoked to pop the current destination.
 */
fun NavGraphBuilder.settingsSection(
    onNavigateToAbout: () -> Unit,
    onNavigateBack: () -> Unit,
) {
    composable<SettingsRoute> {
        SettingsScreen(
            onNavigateToAbout = onNavigateToAbout,
            onNavigateBack = onNavigateBack,
        )
    }
    composable<AboutRoute> {
        AboutScreen(onNavigateBack = onNavigateBack)
    }
}
