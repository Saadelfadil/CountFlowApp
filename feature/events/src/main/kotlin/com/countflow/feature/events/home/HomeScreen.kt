package com.countflow.feature.events.home

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.countflow.core.designsystem.component.PlaceholderAction
import com.countflow.core.designsystem.component.PlaceholderScreen

/**
 * The events list — CountFlow's start destination.
 *
 * Placeholder for Milestone 1. The real implementation lands in Milestone 3 with the upcoming
 * events list, realtime search, sort, category filtering, and the add-event action.
 *
 * @param onNavigateToCreateEvent invoked when the user adds an event.
 * @param onNavigateToSettings invoked when the user opens settings.
 * @param modifier applied to the screen root.
 */
@Composable
fun HomeScreen(
    onNavigateToCreateEvent: () -> Unit,
    onNavigateToSettings: () -> Unit,
    modifier: Modifier = Modifier,
) {
    PlaceholderScreen(
        title = "CountFlow",
        description = "Your countdown events will appear here. " +
            "List, search, and sort arrive in Milestone 3.",
        modifier = modifier,
        actions = listOf(
            PlaceholderAction("New event", onNavigateToCreateEvent),
            PlaceholderAction("Settings", onNavigateToSettings),
        ),
    )
}
