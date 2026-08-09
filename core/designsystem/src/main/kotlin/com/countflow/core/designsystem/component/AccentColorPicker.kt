package com.countflow.core.designsystem.component

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.countflow.core.domain.model.AccentColor

/**
 * Lets a user choose the accent for an event and every widget that shows it.
 *
 * [AccentColor.Dynamic] is always the first swatch, drawn distinctly (outlined, not filled, with
 * a plain "A" glyph rather than an icon — `material-icons-extended` is deliberately not a
 * dependency of this module, see `core/designsystem/build.gradle.kts`) so "let the system decide"
 * reads as a real choice, not a gap in the row. The rest are a curated, fixed set — an open RGB
 * picker is deliberately out of scope (Session 9 brief): it invites a user to pick a colour that
 * fails contrast against a forced-dark widget background before anyone has built the guardrail
 * that would catch it (see D-041's reasoning for GLASS specifically). Every preset here was
 * chosen to read clearly against both a dynamic Material You surface and the fixed dark
 * backgrounds OLED/Glass force.
 */
@Composable
fun AccentColorPicker(
    selected: AccentColor,
    onSelect: (AccentColor) -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(modifier = modifier, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        DynamicSwatch(isSelected = selected == AccentColor.Dynamic, onClick = { onSelect(AccentColor.Dynamic) })
        PRESET_COLORS.forEach { preset ->
            FixedSwatch(
                argb = preset,
                isSelected = (selected as? AccentColor.Fixed)?.argb == preset,
                onClick = { onSelect(AccentColor.Fixed(preset)) },
            )
        }
    }
}

@Composable
private fun DynamicSwatch(isSelected: Boolean, onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .size(SWATCH_SIZE)
            .border(
                width = if (isSelected) 2.dp else 1.dp,
                color = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline,
                shape = CircleShape,
            )
            .selectable(selected = isSelected, onClick = onClick)
            .semantics { contentDescription = "Dynamic color, follows your wallpaper" },
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = "A",
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun FixedSwatch(argb: Int, isSelected: Boolean, onClick: () -> Unit) {
    val color = Color(argb)
    Box(
        modifier = Modifier
            .size(SWATCH_SIZE)
            .background(color, CircleShape)
            .then(
                if (isSelected) {
                    Modifier.border(2.dp, MaterialTheme.colorScheme.onSurface, CircleShape)
                } else {
                    Modifier
                },
            )
            .selectable(selected = isSelected, onClick = onClick)
            .semantics { contentDescription = "Accent color" },
        contentAlignment = Alignment.Center,
    ) {
        if (isSelected) {
            Icon(
                imageVector = Icons.Filled.Check,
                contentDescription = null,
                tint = color.contrastingOnColor(),
                modifier = Modifier.size(18.dp),
            )
        }
    }
}

/** A near-white or near-black tint, whichever reads more clearly against [this]. */
private fun Color.contrastingOnColor(): Color {
    val luminance = 0.2126f * red + 0.7152f * green + 0.0722f * blue
    return if (luminance > 0.55f) Color.Black else Color.White
}

private val SWATCH_SIZE = 36.dp

/**
 * Chosen to read clearly on both a dynamic Material You surface and OLED/Glass's fixed dark
 * backgrounds — nothing near-black, nothing so pale it disappears on a light dynamic surface.
 */
private val PRESET_COLORS = listOf(
    0xFF00897B.toInt(), // teal — the brand seed
    0xFF1E88E5.toInt(), // blue
    0xFF5E35B1.toInt(), // indigo
    0xFFD81B60.toInt(), // rose
    0xFFE64A19.toInt(), // orange
    0xFF43A047.toInt(), // green
    0xFFFDD835.toInt(), // amber
    0xFFE53935.toInt(), // red
)
