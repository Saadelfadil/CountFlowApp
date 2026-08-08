package com.countflow.feature.settings.about

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.countflow.core.designsystem.component.PlaceholderScreen

/**
 * About and legal information.
 *
 * Placeholder for Milestone 1. Version details, licences, and the privacy policy link land in
 * Milestone 6.
 *
 * @param onNavigateBack invoked to leave the screen.
 * @param modifier applied to the screen root.
 */
@Composable
fun AboutScreen(
    onNavigateBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    PlaceholderScreen(
        title = "About",
        description = "Version, licences, and privacy policy arrive in Milestone 6.",
        modifier = modifier,
        onNavigateBack = onNavigateBack,
    )
}
