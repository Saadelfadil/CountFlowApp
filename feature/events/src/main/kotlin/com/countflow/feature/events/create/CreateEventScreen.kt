package com.countflow.feature.events.create

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.countflow.core.designsystem.component.PlaceholderScreen

/**
 * The create/edit event form.
 *
 * Placeholder for Milestone 1. The real implementation lands in Milestone 3 with the title
 * field, emoji picker, category, date and time pickers, theme selection, and a live widget
 * preview.
 *
 * @param onNavigateBack invoked to leave the form.
 * @param modifier applied to the screen root.
 */
@Composable
fun CreateEventScreen(
    onNavigateBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    PlaceholderScreen(
        title = "New event",
        description = "Title, emoji, category, date and time pickers, " +
            "and a live widget preview arrive in Milestone 3.",
        modifier = modifier,
        onNavigateBack = onNavigateBack,
    )
}
