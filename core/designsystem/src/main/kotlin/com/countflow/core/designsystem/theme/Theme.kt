package com.countflow.core.designsystem.theme

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext

/**
 * The CountFlow Material 3 theme.
 *
 * Wraps [MaterialTheme] with CountFlow's color schemes, typography, and shapes.
 *
 * Dynamic color is on by default and is always available at runtime: `minSdk` is 31, so the
 * Material You APIs exist on every supported device. The [Build.VERSION] guard is therefore
 * absent by design — it would be dead code. What remains meaningful is [dynamicColor] itself,
 * which the user can turn off in Settings to fall back to the brand palette.
 *
 * @param darkTheme whether to use the dark scheme. Follows the system setting by default.
 * @param dynamicColor whether to derive colors from the user's wallpaper.
 * @param content the themed content.
 */
@Composable
fun CountFlowTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    dynamicColor: Boolean = true,
    content: @Composable () -> Unit,
) {
    val context = LocalContext.current
    val colorScheme = when {
        dynamicColor && darkTheme -> dynamicDarkColorScheme(context)
        dynamicColor -> dynamicLightColorScheme(context)
        darkTheme -> CountFlowDarkColorScheme
        else -> CountFlowLightColorScheme
    }

    MaterialTheme(
        colorScheme = colorScheme,
        typography = CountFlowTypography,
        shapes = CountFlowShapes,
        content = content,
    )
}
