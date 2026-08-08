package com.countflow.feature.premium

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.countflow.core.designsystem.component.PlaceholderScreen

/**
 * The premium upgrade screen.
 *
 * Placeholder, and deliberately so: the architecture reserves a place for billing without
 * implementing it. Play Billing is not on the classpath and the entitlement check is a stub
 * that always reports "not premium". Real billing lands no earlier than Milestone 9.
 *
 * @param onNavigateBack invoked to leave the screen.
 * @param modifier applied to the screen root.
 */
@Composable
fun PremiumScreen(
    onNavigateBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    PlaceholderScreen(
        title = "CountFlow Premium",
        description = "Premium widget styles, themes, and fonts. " +
            "Billing is intentionally not implemented yet.",
        modifier = modifier,
        onNavigateBack = onNavigateBack,
    )
}
