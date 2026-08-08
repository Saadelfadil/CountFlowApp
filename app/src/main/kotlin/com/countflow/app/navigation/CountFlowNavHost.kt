package com.countflow.app.navigation

import androidx.compose.runtime.Composable
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.rememberNavController
import com.countflow.feature.events.navigation.HomeRoute
import com.countflow.feature.events.navigation.eventsSection
import com.countflow.feature.events.navigation.navigateToCreateEvent
import com.countflow.feature.premium.navigation.navigateToPremium
import com.countflow.feature.premium.navigation.premiumSection
import com.countflow.feature.settings.navigation.navigateToAbout
import com.countflow.feature.settings.navigation.navigateToSettings
import com.countflow.feature.settings.navigation.settingsSection

/**
 * The application's navigation graph.
 *
 * Each feature contributes its own destinations through a `NavGraphBuilder` extension and
 * exposes navigation only as callbacks. That keeps features independent of one another —
 * `:feature:events` has no compile-time knowledge that `:feature:settings` exists — and makes
 * this file the single place where cross-feature routing is decided.
 *
 * @param navController hoisted so tests and previews can supply their own.
 */
@Composable
fun CountFlowNavHost(
    navController: NavHostController = rememberNavController(),
) {
    NavHost(
        navController = navController,
        startDestination = HomeRoute,
    ) {
        eventsSection(
            onNavigateToCreateEvent = navController::navigateToCreateEvent,
            onNavigateToSettings = navController::navigateToSettings,
            onNavigateBack = navController::popBackStack,
        )
        settingsSection(
            onNavigateToAbout = navController::navigateToAbout,
            onNavigateToPremium = navController::navigateToPremium,
            onNavigateBack = navController::popBackStack,
        )
        premiumSection(
            onNavigateBack = navController::popBackStack,
        )
    }
}
