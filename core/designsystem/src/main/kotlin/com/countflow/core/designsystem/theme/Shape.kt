package com.countflow.core.designsystem.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Shapes
import androidx.compose.ui.unit.dp

/**
 * CountFlow corner radii.
 *
 * Deliberately rounder than the Material 3 defaults at the large end: event cards in the app
 * are visual previews of home-screen widgets, and Android widget backgrounds are heavily
 * rounded. Matching them keeps the in-app preview honest about how a widget will actually look.
 */
internal val CountFlowShapes = Shapes(
    extraSmall = RoundedCornerShape(4.dp),
    small = RoundedCornerShape(8.dp),
    medium = RoundedCornerShape(16.dp),
    large = RoundedCornerShape(24.dp),
    extraLarge = RoundedCornerShape(32.dp),
)
