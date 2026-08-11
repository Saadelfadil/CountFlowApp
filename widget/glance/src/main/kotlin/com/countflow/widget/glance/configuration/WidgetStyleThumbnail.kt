package com.countflow.widget.glance.configuration

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.countflow.core.domain.model.ProgressStyle
import com.countflow.core.domain.model.WidgetStyle

/**
 * A design-language *sample* for one [WidgetStyle] — deliberately not a miniature of the user's
 * real event. That question ("what will MY widget look like") belongs to [WidgetPreviewCard]
 * alone; there is exactly one of those on the Customize step, and it is not this. This answers a
 * different, narrower question — "what does this *style* look like" — using a generic "Aa" glyph
 * and abstract shapes rather than any event data.
 *
 * The abstraction stops at *content*, not at *design*: each style's background, corner radius,
 * alignment, weight, and progress treatment below are the same real distinguishing traits
 * `docs/WIDGET_DESIGN_GUIDE.md` and `WidgetThemeResolver` give the actual widget, so a thumbnail
 * never promises a look the real widget can't produce. Colors that mirror a resolved
 * [com.countflow.widget.engine.model.WidgetTheme] value (OLED's true black, Glass's dark surface)
 * are restated here as close approximations rather than imported from `WidgetThemeResolver`
 * directly — that resolver is `internal` to `:widget:engine` for real event data, and a fixed
 * illustrative color is all a content-free sample needs.
 */
@Composable
internal fun WidgetStyleThumbnail(
    style: WidgetStyle,
    selected: Boolean,
    locked: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    ThumbnailCard(
        selected = selected,
        locked = locked,
        onClick = onClick,
        label = style.displayName(),
        background = style.thumbnailBackground(),
        cornerRadius = style.thumbnailCornerRadius(),
        modifier = modifier,
    ) {
        StyleThumbnailContent(style)
    }
}

/** Same "design sample, not the real event" contract as [WidgetStyleThumbnail], for [ProgressStyle]. Never locked — no [ProgressStyle] is rewarded. */
@Composable
internal fun ProgressStyleThumbnail(
    style: ProgressStyle,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    ThumbnailCard(
        selected = selected,
        locked = false,
        onClick = onClick,
        label = style.displayName(),
        background = MaterialTheme.colorScheme.surfaceVariant,
        cornerRadius = DEFAULT_CORNER_RADIUS,
        modifier = modifier,
    ) {
        ProgressThumbnailContent(style)
    }
}

/**
 * [locked] is a restrained, secondary indicator only — appended to the caption below the
 * thumbnail, never drawn over it. The thumbnail's own icon area ([content]) is exactly what an
 * unlocked one shows, unchanged: a locked rewarded style must still read as "this is what Glass
 * looks like," not as a generic padlock card standing in for it. Tapping a locked thumbnail is
 * still a normal, enabled tap (never visually disabled) — [onClick] fires the same as any other
 * thumbnail; whether that tap selects the style or requests a reward is
 * [WidgetConfigurationViewModel.onWidgetStyleChange]'s decision, not this composable's.
 */
@Composable
private fun ThumbnailCard(
    selected: Boolean,
    locked: Boolean,
    onClick: () -> Unit,
    label: String,
    background: Color,
    cornerRadius: Dp,
    modifier: Modifier = Modifier,
    content: @Composable BoxScope.() -> Unit,
) {
    Column(
        modifier = modifier,
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        val shape = RoundedCornerShape(cornerRadius)
        Box(
            modifier = Modifier
                .size(THUMBNAIL_SIZE)
                .background(background, shape)
                .border(
                    width = if (selected) 2.dp else 1.dp,
                    color = if (selected) {
                        MaterialTheme.colorScheme.primary
                    } else {
                        MaterialTheme.colorScheme.outlineVariant
                    },
                    shape = shape,
                )
                .selectable(selected = selected, onClick = onClick, role = Role.RadioButton)
                .semantics {
                    contentDescription = if (locked) "$label style, locked, requires unlocking" else "$label style"
                }
                .padding(10.dp),
            contentAlignment = Alignment.Center,
            content = content,
        )
        Text(
            text = if (locked) "$label 🔒" else label,
            style = MaterialTheme.typography.labelSmall,
            color = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

/**
 * Each branch reproduces the one or two traits `docs/WIDGET_DESIGN_GUIDE.md` names as *the*
 * differentiator for that style — never every field the real layout draws, since a thumbnail this
 * small communicating everything would communicate nothing.
 */
@Composable
private fun StyleThumbnailContent(style: WidgetStyle) {
    when (style) {
        WidgetStyle.MINIMAL ->
            // "The countdown value is the entire point... no progress bar, full stop." Centered,
            // bold, and alone.
            Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Glyph(FontWeight.Bold)
                Line(width = 18.dp, color = mutedLine())
            }

        WidgetStyle.MATERIAL ->
            // "Left-aligned identity row... headline with its unit inline... a labeled progress
            // row." The only style that is both left-aligned and shows a bar.
            Column(
                modifier = Modifier.fillMaxSize(),
                horizontalAlignment = Alignment.Start,
                verticalArrangement = Arrangement.Center,
            ) {
                Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    Glyph(FontWeight.SemiBold)
                    Line(width = 12.dp, color = MaterialTheme.colorScheme.primary, thickness = 3.dp)
                }
                Line(width = 40.dp, color = MaterialTheme.colorScheme.primary, thickness = 3.dp)
            }

        WidgetStyle.GLASS ->
            // "Normal (not medium/bold) type weights... meant to feel like it's sitting above the
            // wallpaper." Lighter weight, softer bar, over the translucent-dark approximation
            // ThumbnailCard already gave it.
            Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Glyph(FontWeight.Normal, color = Color.White)
                Line(width = 30.dp, color = Color.White.copy(alpha = 0.7f), thickness = 2.dp)
            }

        WidgetStyle.OLED ->
            // "No emoji, no title, ever... nothing competing for the handful of lit sub-pixels."
            // The largest glyph of any style, alone, on true black.
            Glyph(FontWeight.Bold, color = Color.White, size = 22.sp)

        WidgetStyle.PROGRESS ->
            // "The progress visualization is the widget... headline sitting inside it."
            Box(contentAlignment = Alignment.Center) {
                CircularProgressIndicator(
                    progress = { PROGRESS_SAMPLE_FRACTION },
                    modifier = Modifier.size(56.dp),
                    color = MaterialTheme.colorScheme.primary,
                    trackColor = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.25f),
                    strokeWidth = 5.dp,
                )
                Glyph(FontWeight.SemiBold, size = 14.sp)
            }

        WidgetStyle.ROUNDED ->
            // "Supporting text sits inside a pill-shaped chip... the one structural difference
            // from Material beyond shape." The pill is the whole point, not the card's own corner.
            Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Glyph(FontWeight.Bold)
                Box(
                    modifier = Modifier
                        .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.18f), RoundedCornerShape(999.dp))
                        .padding(horizontal = 8.dp, vertical = 3.dp),
                ) {
                    Line(width = 14.dp, color = MaterialTheme.colorScheme.primary, thickness = 3.dp)
                }
            }

        WidgetStyle.MODERN ->
            // "Top-and-start anchored like a masthead, not centered like every other style...
            // dense enough that title, headline, date, and percentage can all coexist." The one
            // thumbnail pinned to a corner instead of centered, with the densest line stack.
            Column(
                modifier = Modifier.fillMaxSize(),
                horizontalAlignment = Alignment.Start,
                verticalArrangement = Arrangement.spacedBy(3.dp),
            ) {
                Line(width = 14.dp, color = mutedLine(), thickness = 2.dp)
                Glyph(FontWeight.SemiBold, size = 15.sp)
                Line(width = 20.dp, color = mutedLine(), thickness = 2.dp)
                Line(width = 26.dp, color = MaterialTheme.colorScheme.primary, thickness = 2.dp)
            }
    }
}

/** "None," "Bar," or "Ring" — the progress *treatment* alone, independent of any style. */
@Composable
private fun ProgressThumbnailContent(style: ProgressStyle) {
    when (style) {
        ProgressStyle.NONE ->
            Glyph(FontWeight.SemiBold)

        ProgressStyle.LINEAR ->
            Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Glyph(FontWeight.SemiBold)
                Line(width = 40.dp, color = MaterialTheme.colorScheme.primary, thickness = 4.dp)
            }

        ProgressStyle.CIRCULAR ->
            Box(contentAlignment = Alignment.Center) {
                CircularProgressIndicator(
                    progress = { PROGRESS_SAMPLE_FRACTION },
                    modifier = Modifier.size(56.dp),
                    color = MaterialTheme.colorScheme.primary,
                    trackColor = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.25f),
                    strokeWidth = 5.dp,
                )
                Glyph(FontWeight.SemiBold, size = 14.sp)
            }
    }
}

@Composable
private fun Glyph(weight: FontWeight, color: Color = MaterialTheme.colorScheme.onSurfaceVariant, size: TextUnit = 20.sp) {
    Text(text = "Aa", fontWeight = weight, color = color, fontSize = size)
}

@Composable
private fun Line(width: Dp, color: Color, thickness: Dp = 2.dp) {
    Box(modifier = Modifier.width(width).height(thickness).background(color, RoundedCornerShape(thickness / 2)))
}

@Composable
private fun mutedLine(): Color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)

/**
 * No special background per `WidgetThemeResolver` (`backgroundColorArgb = null`, system/dynamic)
 * falls back to the card's own neutral surface — never left `Color.Unspecified`, which
 * `Modifier.background` would draw as fully transparent rather than "let the theme decide."
 */
@Composable
private fun WidgetStyle.thumbnailBackground(): Color = when (this) {
    // True black — matches WidgetThemeResolver.TRUE_BLACK exactly (0xFF000000).
    WidgetStyle.OLED -> Color.Black
    // Close approximation of WidgetThemeResolver.TRANSLUCENT_DARK_SURFACE (0xCC101418) composited
    // over a typical dark surface — this sample has no wallpaper to composite over, so it is
    // restated as one fixed dark tone rather than imported.
    WidgetStyle.GLASS -> Color(0xFF15181C)
    else -> MaterialTheme.colorScheme.surfaceVariant
}

/**
 * Mirrors `WidgetThemeResolver`'s real per-style corner radius (`docs/WIDGET_DESIGN_GUIDE.md`
 * "Corner radius: TD-011 resolved"): four styles track the system value (approximated here with
 * one modest default, since a thumbnail has no real launcher grid to track), Glass/Rounded/Modern
 * intentionally override it.
 */
private fun WidgetStyle.thumbnailCornerRadius(): Dp = when (this) {
    WidgetStyle.GLASS -> 20.dp
    WidgetStyle.ROUNDED -> 28.dp
    WidgetStyle.MODERN -> 8.dp
    else -> DEFAULT_CORNER_RADIUS
}

private val THUMBNAIL_SIZE = 84.dp
private val DEFAULT_CORNER_RADIUS = 12.dp
private const val PROGRESS_SAMPLE_FRACTION = 0.6f
