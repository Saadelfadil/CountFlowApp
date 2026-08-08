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
 * @param onNavigateToAbout invoked when the user opens About.
 * @param onNavigateToPremium invoked when the user opens the premium screen.
 * @param onNavigateBack invoked to pop the current destination.
 */
fun NavGraphBuilder.settingsSection(
    onNavigateToAbout: () -> Unit,
    onNavigateToPremium: () -> Unit,
    onNavigateBack: () -> Unit,
) {
    composable<SettingsRoute> {
        SettingsScreen(
            onNavigateToAbout = onNavigateToAbout,
            onNavigateToPremium = onNavigateToPremium,
            onNavigateBack = onNavigateBack,
        )
    }
    composable<AboutRoute> {
        AboutScreen(onNavigateBack = onNavigateBack)
    }
}
