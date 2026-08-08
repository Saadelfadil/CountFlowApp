package com.countflow.feature.settings

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.countflow.core.designsystem.component.PlaceholderAction
import com.countflow.core.designsystem.component.PlaceholderScreen

/**
 * App settings.
 *
 * Placeholder for Milestone 1. The real implementation lands in Milestone 6 with theme and
 * dark-mode selection, the dynamic-color toggle, notification preferences, and backup/restore.
 *
 * @param onNavigateToAbout invoked when the user opens About.
 * @param onNavigateToPremium invoked when the user opens the premium screen.
 * @param onNavigateBack invoked to leave settings.
 * @param modifier applied to the screen root.
 */
@Composable
fun SettingsScreen(
    onNavigateToAbout: () -> Unit,
    onNavigateToPremium: () -> Unit,
    onNavigateBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    PlaceholderScreen(
        title = "Settings",
        description = "Theme, dark mode, dynamic colors, notifications, " +
            "and backup arrive in Milestone 6.",
        modifier = modifier,
        onNavigateBack = onNavigateBack,
        actions = listOf(
            PlaceholderAction("CountFlow Premium", onNavigateToPremium),
            PlaceholderAction("About", onNavigateToAbout),
        ),
    )
}
