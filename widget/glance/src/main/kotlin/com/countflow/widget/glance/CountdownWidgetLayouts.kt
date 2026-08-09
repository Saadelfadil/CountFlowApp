package com.countflow.widget.glance

import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.glance.GlanceModifier
import androidx.glance.Image
import androidx.glance.LocalContext
import androidx.glance.LocalSize
import androidx.glance.appwidget.LinearProgressIndicator
import androidx.glance.appwidget.cornerRadius
import androidx.glance.background
import androidx.glance.layout.Alignment
import androidx.glance.layout.Box
import androidx.glance.layout.Column
import androidx.glance.layout.ContentScale
import androidx.glance.layout.Row
import androidx.glance.layout.Spacer
import androidx.glance.layout.fillMaxWidth
import androidx.glance.layout.height
import androidx.glance.layout.padding
import androidx.glance.layout.size
import androidx.glance.text.FontWeight
import androidx.glance.text.Text
import androidx.glance.text.TextAlign
import androidx.glance.text.TextStyle
import androidx.glance.unit.ColorProvider
import com.countflow.core.designsystem.format.TargetDateFormatter
import com.countflow.widget.engine.model.WidgetRenderModel
import com.countflow.widget.glance.progress.CircularProgressRenderer

/**
 * The seven per-style layouts [CountdownWidgetContent] dispatches to.
 *
 * Each function here is a genuinely different composition — different alignment, different
 * elements shown, different emphasis — not the same tree re-skinned with [WidgetColors]. See
 * `docs/WIDGET_DESIGN_GUIDE.md` for the design reasoning behind each one; this file is the
 * execution of that reasoning, not a second copy of it.
 *
 * Every layout takes the same four things — [WidgetRenderModel] for data,
 * [com.countflow.widget.glance.WidgetHeadline] for the already-resolved content hierarchy,
 * [WidgetColors] for the already-resolved palette, and the card's outer [GlanceModifier]
 * (background, corner radius, semantics, and the click target are already applied) — and are
 * responsible for nothing but arranging them.
 */

// ─────────────────────────────────────────────────────────────────────────── Minimal ──

/**
 * Typography-first. The countdown value is the entire point; everything else is a whisper around
 * it. No progress bar — a bar is a second thing to look at, and Minimal's whole premise is that
 * there is only one thing to look at. Filled with type scale and breathing room, not more
 * elements.
 */
@Composable
internal fun MinimalLayout(model: WidgetRenderModel, headline: WidgetHeadline, colors: WidgetColors, modifier: GlanceModifier) {
    Column(
        modifier = modifier.padding(WIDGET_PADDING),
        verticalAlignment = Alignment.CenterVertically,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        if (headline.showIdentity && (model.showEmoji || model.showTitle)) {
            CenteredIdentity(model, colors, size = 13.sp, weight = FontWeight.Medium)
            Spacer(modifier = GlanceModifier.height(SPACING_LG))
        }
        Text(
            text = headline.primary,
            style = TextStyle(
                fontSize = headlineSize(headline, MINIMAL_HEADLINE_SIZE, MINIMAL_HEADLINE_WORD_SIZE),
                fontWeight = FontWeight.Bold,
                color = colors.statusColor,
                textAlign = TextAlign.Center,
            ),
            maxLines = 1,
        )
        headline.unit?.let {
            Text(text = it, style = TextStyle(fontSize = 13.sp, color = colors.onSurfaceMuted, textAlign = TextAlign.Center))
        }
        headline.secondary?.let {
            Spacer(modifier = GlanceModifier.height(SPACING_XS))
            Text(text = it, style = TextStyle(fontSize = LABEL_SIZE, color = colors.onSurfaceMuted, textAlign = TextAlign.Center))
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────── Material ──

/**
 * The safe, balanced default: identity, headline, supporting context, and progress all present,
 * dynamic-colored, nothing fighting for attention. If a user has no reason to pick a different
 * style, this is the one that should never look like a mistake.
 */
@Composable
internal fun MaterialLayout(model: WidgetRenderModel, headline: WidgetHeadline, colors: WidgetColors, modifier: GlanceModifier) {
    Column(
        modifier = modifier.padding(WIDGET_PADDING),
        verticalAlignment = Alignment.CenterVertically,
        horizontalAlignment = Alignment.Start,
    ) {
        if (headline.showIdentity && (model.showEmoji || model.showTitle)) {
            StartIdentity(model, colors, size = 14.sp, weight = FontWeight.Medium)
            Spacer(modifier = GlanceModifier.height(SPACING_SM))
        }
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = headline.primary,
                style = TextStyle(fontSize = headlineSize(headline, MATERIAL_HEADLINE_SIZE, MATERIAL_HEADLINE_WORD_SIZE), fontWeight = FontWeight.Bold, color = colors.statusColor),
                maxLines = 1,
            )
            headline.unit?.let {
                Text(
                    text = it,
                    style = TextStyle(fontSize = 13.sp, color = colors.onSurfaceMuted),
                    modifier = GlanceModifier.padding(start = 4.dp),
                )
            }
        }
        headline.secondary?.let {
            Text(text = it, style = TextStyle(fontSize = LABEL_SIZE, color = colors.onSurfaceMuted))
        }
        if (model.showDate) {
            TargetDateLine(model, colors)
        }
        if (model.progress.isVisible) {
            Spacer(modifier = GlanceModifier.height(SPACING_SM))
            ProgressRow(model, colors)
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────── Progress ──

/**
 * The progress visualization *is* the widget — everything else supports it. A determinate
 * circular ring (drawn to a cached bitmap; Glance has no determinate circular indicator of its
 * own, KNOWN_ISSUES.md LIM-001) fills most of the card, with the headline sitting inside it the
 * way a stopwatch's face holds its own reading.
 */
@Composable
internal fun ProgressLayout(model: WidgetRenderModel, headline: WidgetHeadline, colors: WidgetColors, modifier: GlanceModifier) {
    val context = LocalContext.current
    val size = LocalSize.current

    Column(
        modifier = modifier.padding(WIDGET_PADDING),
        verticalAlignment = Alignment.CenterVertically,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        if (headline.showIdentity && (model.showEmoji || model.showTitle)) {
            CenteredIdentity(model, colors, size = 13.sp, weight = FontWeight.Medium)
            Spacer(modifier = GlanceModifier.height(SPACING_XS))
        }

        // The ring only draws for a numeric headline — cramming a word like "Completed" into an
        // ~80dp circle produces worse results than the plain fallback below, no matter how small
        // the font gets. A day count is compact by construction; a status word is not.
        if (model.progress.isVisible && headline.isNumeric) {
            val density = context.resources.displayMetrics.density
            val ringDp = (minOf(size.width.value, size.height.value) * RING_FRACTION_OF_CELL)
                .coerceIn(MIN_RING_DP, MAX_RING_DP)
            val ringPx = (ringDp * density).toInt()
            val strokePx = RING_STROKE_DP * density

            Box(
                modifier = GlanceModifier.size(ringDp.dp),
                contentAlignment = Alignment.Center,
            ) {
                Image(
                    provider = CircularProgressRenderer.provider(
                        sizePx = ringPx,
                        percent = model.progress.percent,
                        trackArgb = colors.track.getColor(context).toArgb(),
                        progressArgb = colors.accent.getColor(context).toArgb(),
                        strokeWidthPx = strokePx,
                    ),
                    contentDescription = null,
                    modifier = GlanceModifier.size(ringDp.dp),
                    contentScale = ContentScale.Fit,
                )
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        text = headline.primary,
                        style = TextStyle(fontSize = PROGRESS_RING_HEADLINE_SIZE, fontWeight = FontWeight.Bold, color = colors.statusColor, textAlign = TextAlign.Center),
                        maxLines = 1,
                    )
                    headline.unit?.let {
                        Text(text = it, style = TextStyle(fontSize = 12.sp, color = colors.onSurfaceMuted, textAlign = TextAlign.Center))
                    }
                }
            }
        } else {
            // No progress to visualize (ProgressStyle.NONE), or a status word this style's ring
            // cannot hold gracefully — fall back to the plain headline rather than force either
            // into a shape that does not fit them.
            Text(
                text = headline.primary,
                style = TextStyle(
                    fontSize = headlineSize(headline, MINIMAL_HEADLINE_SIZE, MINIMAL_HEADLINE_WORD_SIZE),
                    fontWeight = FontWeight.Bold,
                    color = colors.statusColor,
                    textAlign = TextAlign.Center,
                ),
                maxLines = 1,
            )
        }

        headline.secondary?.let {
            Spacer(modifier = GlanceModifier.height(SPACING_XS))
            Text(text = it, style = TextStyle(fontSize = LABEL_SIZE, color = colors.onSurfaceMuted, textAlign = TextAlign.Center))
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────── OLED ──

/**
 * As stark as the display technology it is named for. One number, one line beneath it if that,
 * nothing competing for the handful of lit sub-pixels this theme's entire purpose is minimizing.
 * No identity row — the burn-in-safe theme is the one place a user has explicitly chosen bare
 * information over context.
 */
@Composable
internal fun OledLayout(model: WidgetRenderModel, headline: WidgetHeadline, colors: WidgetColors, modifier: GlanceModifier) {
    Column(
        modifier = modifier.padding(WIDGET_PADDING),
        verticalAlignment = Alignment.CenterVertically,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            text = headline.primary,
            style = TextStyle(
                fontSize = headlineSize(headline, OLED_HEADLINE_SIZE, OLED_HEADLINE_WORD_SIZE),
                fontWeight = FontWeight.Bold,
                color = colors.statusColor,
                textAlign = TextAlign.Center,
            ),
            maxLines = 1,
        )
        headline.unit?.let {
            Text(text = it, style = TextStyle(fontSize = 13.sp, color = colors.onSurfaceMuted, textAlign = TextAlign.Center))
        }
        headline.secondary?.let {
            Text(text = it, style = TextStyle(fontSize = LABEL_SIZE, color = colors.onSurfaceMuted, textAlign = TextAlign.Center))
        }
    }
}

// ────────────────────────────────────────────────────────────────────────────── Glass ──

/**
 * Lighter than Material in every sense — normal (not medium/bold) weights, softer color use, more
 * air between elements — meant to feel like it is sitting *above* the wallpaper rather than
 * replacing it. Contrast is never sacrificed for that lightness: [colors] already carries the
 * fixed, WCAG-checked palette D-041 exists to guarantee, regardless of what layout draws it.
 */
@Composable
internal fun GlassLayout(model: WidgetRenderModel, headline: WidgetHeadline, colors: WidgetColors, modifier: GlanceModifier) {
    Column(
        modifier = modifier.padding(GLASS_PADDING),
        verticalAlignment = Alignment.CenterVertically,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        if (headline.showIdentity && (model.showEmoji || model.showTitle)) {
            CenteredIdentity(model, colors, size = 13.sp, weight = FontWeight.Normal)
            Spacer(modifier = GlanceModifier.height(SPACING_LG))
        }
        Text(
            text = headline.primary,
            style = TextStyle(
                fontSize = headlineSize(headline, GLASS_HEADLINE_SIZE, GLASS_HEADLINE_WORD_SIZE),
                fontWeight = FontWeight.Normal,
                color = colors.statusColor,
                textAlign = TextAlign.Center,
            ),
            maxLines = 1,
        )
        headline.unit?.let {
            Text(text = it, style = TextStyle(fontSize = 13.sp, fontWeight = FontWeight.Normal, color = colors.onSurfaceMuted, textAlign = TextAlign.Center))
        }
        headline.secondary?.let {
            Spacer(modifier = GlanceModifier.height(SPACING_XS))
            Text(text = it, style = TextStyle(fontSize = LABEL_SIZE, fontWeight = FontWeight.Normal, color = colors.onSurfaceMuted, textAlign = TextAlign.Center))
        }
        if (model.progress.isVisible) {
            Spacer(modifier = GlanceModifier.height(SPACING_LG))
            LinearProgressIndicator(
                progress = model.progress.fraction,
                modifier = GlanceModifier.fillMaxWidth().height(4.dp),
                color = colors.statusColor,
                backgroundColor = colors.track,
            )
        }
    }
}

// ──────────────────────────────────────────────────────────────────────────── Rounded ──

/**
 * Friendly rather than crisp: the softest corner radius of any style (`WidgetThemeResolver`,
 * D-045), generous padding, and supporting text held in a pill-shaped chip rather than sitting
 * bare on the background — the one structural difference from Material beyond corner radius
 * alone, which the brief specifically asked this style to have.
 */
@Composable
internal fun RoundedLayout(model: WidgetRenderModel, headline: WidgetHeadline, colors: WidgetColors, modifier: GlanceModifier) {
    Column(
        modifier = modifier.padding(ROUNDED_PADDING),
        verticalAlignment = Alignment.CenterVertically,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        if (headline.showIdentity && (model.showEmoji || model.showTitle)) {
            CenteredIdentity(model, colors, size = 13.sp, weight = FontWeight.Medium)
            Spacer(modifier = GlanceModifier.height(SPACING_SM))
        }
        Text(
            text = headline.primary,
            style = TextStyle(fontSize = headlineSize(headline, ROUNDED_HEADLINE_SIZE, ROUNDED_HEADLINE_WORD_SIZE), fontWeight = FontWeight.Bold, color = colors.statusColor, textAlign = TextAlign.Center),
            maxLines = 1,
        )
        headline.unit?.let {
            Text(text = it, style = TextStyle(fontSize = 13.sp, color = colors.onSurfaceMuted, textAlign = TextAlign.Center))
        }
        headline.secondary?.let {
            Spacer(modifier = GlanceModifier.height(SPACING_SM))
            Box(
                modifier = GlanceModifier
                    .background(colors.track)
                    .cornerRadius(999.dp)
                    .padding(horizontal = 10.dp, vertical = 4.dp),
            ) {
                Text(text = it, style = TextStyle(fontSize = 12.sp, color = colors.onSurface, textAlign = TextAlign.Center))
            }
        }
        if (model.progress.isVisible) {
            Spacer(modifier = GlanceModifier.height(SPACING_SM))
            LinearProgressIndicator(
                progress = model.progress.fraction,
                modifier = GlanceModifier.fillMaxWidth().height(PROGRESS_HEIGHT),
                color = colors.accent,
                backgroundColor = colors.track,
            )
        }
    }
}

// ───────────────────────────────────────────────────────────────────────────── Modern ──

/**
 * Editorial, not decorative: top-and-start anchored like a masthead rather than centered like
 * every other style, dense enough that title, headline, target date, and percentage can all
 * coexist without feeling cluttered. The one layout in this set built around alignment as the
 * differentiator, not just type or color.
 */
@Composable
internal fun ModernLayout(model: WidgetRenderModel, headline: WidgetHeadline, colors: WidgetColors, modifier: GlanceModifier) {
    Column(
        modifier = modifier.padding(WIDGET_PADDING),
        verticalAlignment = Alignment.Top,
        horizontalAlignment = Alignment.Start,
    ) {
        if (headline.showIdentity && (model.showEmoji || model.showTitle)) {
            StartIdentity(model, colors, size = 12.sp, weight = FontWeight.Medium)
            Spacer(modifier = GlanceModifier.height(SPACING_XS))
        }
        Text(
            text = headline.primary,
            style = TextStyle(fontSize = headlineSize(headline, MODERN_HEADLINE_SIZE, MODERN_HEADLINE_WORD_SIZE), fontWeight = FontWeight.Bold, color = colors.statusColor),
            maxLines = 1,
        )
        headline.unit?.let {
            Text(text = it, style = TextStyle(fontSize = 12.sp, color = colors.onSurfaceMuted))
        }
        headline.secondary?.let {
            Text(text = it, style = TextStyle(fontSize = 12.sp, color = colors.onSurfaceMuted))
        }
        if (model.showDate) {
            TargetDateLine(model, colors, size = 11.sp)
        }
        if (model.showPercentageText) {
            Text(
                text = model.progress.percentText,
                style = TextStyle(fontSize = 11.sp, fontWeight = FontWeight.Medium, color = colors.accent),
            )
        }
        if (model.progress.isVisible) {
            Spacer(modifier = GlanceModifier.height(SPACING_XS))
            LinearProgressIndicator(
                progress = model.progress.fraction,
                modifier = GlanceModifier.fillMaxWidth().height(4.dp),
                color = colors.accent,
                backgroundColor = colors.track,
            )
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────── shared ──

@Composable
private fun CenteredIdentity(model: WidgetRenderModel, colors: WidgetColors, size: androidx.compose.ui.unit.TextUnit, weight: FontWeight) {
    Row(modifier = GlanceModifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalAlignment = Alignment.CenterHorizontally) {
        if (model.showEmoji) {
            Text(text = model.emoji, style = TextStyle(fontSize = size, textAlign = TextAlign.Center))
        }
        if (model.showTitle) {
            Text(
                text = model.title,
                style = TextStyle(fontSize = size, fontWeight = weight, color = colors.onSurface, textAlign = TextAlign.Center),
                maxLines = 1,
                modifier = GlanceModifier.padding(start = if (model.showEmoji) 6.dp else 0.dp),
            )
        }
    }
}

@Composable
private fun StartIdentity(model: WidgetRenderModel, colors: WidgetColors, size: androidx.compose.ui.unit.TextUnit, weight: FontWeight) {
    Row(modifier = GlanceModifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        if (model.showEmoji) {
            Text(text = model.emoji, style = TextStyle(fontSize = size))
        }
        if (model.showTitle) {
            Text(
                text = model.title,
                style = TextStyle(fontSize = size, fontWeight = weight, color = colors.onSurface),
                maxLines = 1,
                modifier = GlanceModifier.defaultWeight().padding(start = if (model.showEmoji) 6.dp else 0.dp),
            )
        }
    }
}

@Composable
private fun TargetDateLine(model: WidgetRenderModel, colors: WidgetColors, size: androidx.compose.ui.unit.TextUnit = LABEL_SIZE) {
    val resources = LocalContext.current.resources
    val dateText = TargetDateFormatter.formatDate(resources, model.target, model.targetZone)
    Text(text = dateText, style = TextStyle(fontSize = size, color = colors.onSurfaceMuted))
}

@Composable
private fun ProgressRow(model: WidgetRenderModel, colors: WidgetColors) {
    Row(modifier = GlanceModifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        LinearProgressIndicator(
            progress = model.progress.fraction,
            modifier = GlanceModifier.defaultWeight().height(PROGRESS_HEIGHT),
            color = colors.statusColor,
            backgroundColor = colors.track,
        )
        if (model.showPercentageText) {
            Text(
                text = model.progress.percentText,
                style = TextStyle(fontSize = PERCENT_SIZE, color = colors.onSurfaceMuted),
                modifier = GlanceModifier.padding(start = 6.dp),
            )
        }
    }
}

/**
 * [numeric] for a bare day count, [word] for a status word like "Completed" or "Starting soon" —
 * found on a real device (`docs/WIDGET_DESIGN_REVIEW.md`): a size tuned for "7" wraps a longer
 * word mid-syllable with no hyphen, since Glance has no autosizing text (LIM-004) to shrink it
 * automatically. `maxLines = 1` at every call site turns a word that still doesn't fit into a
 * clean ellipsis instead of a second wrapped line — confirmed to actually ellipsize on a real
 * device, not just clip (D-038's sibling finding, TD-013).
 */
private fun headlineSize(
    headline: WidgetHeadline,
    numeric: androidx.compose.ui.unit.TextUnit,
    word: androidx.compose.ui.unit.TextUnit,
) = if (headline.isNumeric) numeric else word

private val MINIMAL_HEADLINE_SIZE = 46.sp
private val MINIMAL_HEADLINE_WORD_SIZE = 26.sp
private val MATERIAL_HEADLINE_SIZE = 32.sp
private val MATERIAL_HEADLINE_WORD_SIZE = 22.sp
private val PROGRESS_RING_HEADLINE_SIZE = 26.sp
private val OLED_HEADLINE_SIZE = 50.sp
private val OLED_HEADLINE_WORD_SIZE = 28.sp
private val GLASS_HEADLINE_SIZE = 34.sp
private val GLASS_HEADLINE_WORD_SIZE = 22.sp
private val ROUNDED_HEADLINE_SIZE = 34.sp
private val ROUNDED_HEADLINE_WORD_SIZE = 22.sp
private val MODERN_HEADLINE_SIZE = 30.sp
private val MODERN_HEADLINE_WORD_SIZE = 20.sp

private val GLASS_PADDING = 14.dp
private val ROUNDED_PADDING = 14.dp
private val SPACING_LG = 12.dp

private const val RING_FRACTION_OF_CELL = 0.62f
private const val MIN_RING_DP = 64f
private const val MAX_RING_DP = 120f
private const val RING_STROKE_DP = 8f
