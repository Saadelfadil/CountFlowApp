package com.countflow.app.navigation

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.rememberNavController
import com.countflow.feature.events.navigation.HomeRoute
import com.countflow.feature.events.navigation.eventsSection
import com.countflow.feature.events.navigation.navigateToCreateEvent
import com.countflow.feature.events.navigation.navigateToEditEvent
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
 * @param pendingEventId a tapped reminder notification's event id, if that is why the activity is
 *   showing right now (Session 13). Always starts at [HomeRoute] regardless — this only pushes
 *   [com.countflow.feature.events.navigation.EditEventRoute] on top once the graph exists, the
 *   same "navigate after start, don't retarget the start destination" approach a deep link into a
 *   single-activity app needs.
 * @param onPendingEventIdConsumed invoked once the navigation above has happened, so rotating the
 *   device or another recomposition does not navigate a second time.
 */
@Composable
fun CountFlowNavHost(
    navController: NavHostController = rememberNavController(),
    pendingEventId: String? = null,
    onPendingEventIdConsumed: () -> Unit = {},
) {
    LaunchedEffect(pendingEventId) {
        if (pendingEventId != null) {
            navController.navigateToEditEvent(pendingEventId)
            onPendingEventIdConsumed()
        }
    }

    NavHost(
        navController = navController,
        startDestination = HomeRoute,
    ) {
        eventsSection(
            onNavigateToCreateEvent = navController::navigateToCreateEvent,
            onNavigateToEditEvent = navController::navigateToEditEvent,
            onNavigateToSettings = navController::navigateToSettings,
            onNavigateBack = navController::popBackStack,
        )
        settingsSection(
            onNavigateToAbout = navController::navigateToAbout,
            onNavigateBack = navController::popBackStack,
        )
        premiumSection(
            onNavigateBack = navController::popBackStack,
        )
    }
}
