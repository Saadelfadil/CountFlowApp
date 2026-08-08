package com.countflow.feature.events.navigation

import androidx.navigation.NavController
import androidx.navigation.NavGraphBuilder
import androidx.navigation.compose.composable
import com.countflow.feature.events.create.CreateEventScreen
import com.countflow.feature.events.home.HomeScreen
import kotlinx.serialization.Serializable

/**
 * Routes owned by the events feature.
 *
 * Declared as `@Serializable` types rather than strings so navigation arguments are checked at
 * compile time. A typo in a route becomes a compile error instead of a runtime crash.
 */
@Serializable
data object HomeRoute

@Serializable
data object CreateEventRoute

/**
 * Editing reuses the create screen with a pre-loaded event.
 *
 * @property eventId the UUID of the event being edited.
 */
@Serializable
data class EditEventRoute(val eventId: String)

/** Navigates to the events list, which is the app's start destination. */
fun NavController.navigateToHome() = navigate(HomeRoute)

/** Navigates to the new-event form. */
fun NavController.navigateToCreateEvent() = navigate(CreateEventRoute)

/** Navigates to the edit form for [eventId]. */
fun NavController.navigateToEditEvent(eventId: String) = navigate(EditEventRoute(eventId))

/**
 * Registers the events feature's destinations.
 *
 * Navigation out of this feature is expressed as callbacks so the module never depends on
 * another feature — cross-feature wiring is `:app`'s responsibility.
 *
 * @param onNavigateToCreateEvent invoked when the user starts a new event.
 * @param onNavigateToSettings invoked when the user opens settings.
 * @param onNavigateBack invoked to pop the current destination.
 */
fun NavGraphBuilder.eventsSection(
    onNavigateToCreateEvent: () -> Unit,
    onNavigateToSettings: () -> Unit,
    onNavigateBack: () -> Unit,
) {
    composable<HomeRoute> {
        HomeScreen(
            onNavigateToCreateEvent = onNavigateToCreateEvent,
            onNavigateToSettings = onNavigateToSettings,
        )
    }
    composable<CreateEventRoute> {
        CreateEventScreen(onNavigateBack = onNavigateBack)
    }
    composable<EditEventRoute> {
        CreateEventScreen(onNavigateBack = onNavigateBack)
    }
}
