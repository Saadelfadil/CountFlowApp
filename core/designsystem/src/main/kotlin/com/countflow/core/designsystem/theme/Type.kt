package com.countflow.core.designsystem.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

/**
 * CountFlow typography.
 *
 * Built on the platform default font family so the app inherits the user's chosen system font
 * and any accessibility font scaling without bundling a typeface. Sizes stay in `sp`
 * throughout, which is what lets the large-font accessibility setting scale the UI.
 *
 * Only the styles CountFlow actually uses are overridden; everything else falls through to the
 * Material 3 defaults, so an unstyled component never renders with an unset text style.
 */
internal val CountFlowTypography: Typography = Typography().let { default ->
    default.copy(
        // The countdown numeral itself.
        displayLarge = default.displayLarge.copy(
            fontFamily = FontFamily.Default,
            fontWeight = FontWeight.SemiBold,
        ),
        displayMedium = default.displayMedium.copy(
            fontFamily = FontFamily.Default,
            fontWeight = FontWeight.SemiBold,
        ),
        headlineSmall = default.headlineSmall.copy(fontWeight = FontWeight.SemiBold),
        titleLarge = default.titleLarge.copy(fontWeight = FontWeight.SemiBold),
        titleMedium = default.titleMedium.copy(fontWeight = FontWeight.Medium),
        // Slight positive tracking keeps small labels legible at widget sizes.
        labelSmall = default.labelSmall.copy(letterSpacing = 0.6.sp),
    )
}
