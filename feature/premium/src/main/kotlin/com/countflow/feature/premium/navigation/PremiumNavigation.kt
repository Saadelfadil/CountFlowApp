package com.countflow.feature.premium.navigation

import androidx.navigation.NavController
import androidx.navigation.NavGraphBuilder
import androidx.navigation.compose.composable
import com.countflow.feature.premium.PremiumScreen
import kotlinx.serialization.Serializable

/** Route owned by the premium feature. */
@Serializable
data object PremiumRoute

/** Navigates to the premium screen. */
fun NavController.navigateToPremium() = navigate(PremiumRoute)

/**
 * Registers the premium destination.
 *
 * @param onNavigateBack invoked to leave the screen.
 */
fun NavGraphBuilder.premiumSection(onNavigateBack: () -> Unit) {
    composable<PremiumRoute> {
        PremiumScreen(onNavigateBack = onNavigateBack)
    }
}
