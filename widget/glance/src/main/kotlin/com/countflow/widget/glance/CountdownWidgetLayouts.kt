package com.countflow.widget.glance

import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.unit.TextUnit
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
import androidx.glance.layout.width
import androidx.glance.semantics.semantics
import androidx.glance.semantics.testTag
import androidx.glance.text.FontWeight
import androidx.glance.text.Text
import androidx.glance.text.TextAlign
import androidx.glance.text.TextStyle
import androidx.glance.unit.ColorProvider
import com.countflow.core.designsystem.format.TargetDateFormatter
import com.countflow.core.domain.model.ProgressStyle
import com.countflow.widget.engine.model.WidgetRenderModel
import com.countflow.widget.glance.progress.CircularProgressRenderer

/**
 * The per-style, per-[WidgetSizeClass] layouts [CountdownWidgetContent] dispatches to.
 *
 * Each function here is a genuinely different composition — different alignment, different
 * elements shown, different emphasis — not the same tree re-skinned with [WidgetColors]. See
 * `docs/WIDGET_DESIGN_GUIDE.md` for the [WidgetSizeClass.STANDARD] (2×2) design reasoning behind
 * each style, and `docs/WIDGET_SIZE_MATRIX.md` for the full style × size matrix this file
 * executes — that pair of documents is the reasoning; this file is only its execution.
 *
 * Every layout takes the same four things — [WidgetRenderModel] for data,
 * [com.countflow.widget.glance.WidgetHeadline] for the already-resolved content hierarchy,
 * [WidgetColors] for the already-resolved palette, and the card's outer [GlanceModifier]
 * (background, corner radius, semantics, and the click target are already applied) — and are
 * responsible for nothing but arranging them.
 *
 * ### Naming
 *
 * `<Style>Layout` is [WidgetSizeClass.STANDARD] (2×2) — the unqualified name is deliberate: this
 * is the size every style was designed for first (Session 9), and every `Compact`/`Wide` variant
 * is a genuine, from-scratch composition for its size, not that layout stretched or shrunk
 * (Session 10's explicit brief: "Do NOT stretch or shrink the 2×2 layouts mechanically").
 * `<Style>LayoutCompact` is 2×1, `<Style>LayoutWide` is 4×2.
 */

// ═══════════════════════════════════════════════════════════════════ Minimal ══

/**
 * Typography-first. The countdown value is the entire point; everything else is a whisper around
 * it — Minimal draws no identity noise and no chrome. Progress is still available here like every
 * other style (Style and Progress are independent settings), but stays a small, quiet graphic
 * beneath the number rather than anything competing with it for attention.
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
            Text(text = it, style = TextStyle(fontSize = 13.sp, color = colors.onSurfaceMuted, textAlign = TextAlign.Center), maxLines = 1)
        }
        headline.secondary?.let {
            Spacer(modifier = GlanceModifier.height(SPACING_XS))
            Text(text = it, style = TextStyle(fontSize = LABEL_SIZE, color = colors.onSurfaceMuted, textAlign = TextAlign.Center), maxLines = 1)
        }
        if (model.progress.isVisible) {
            Spacer(modifier = GlanceModifier.height(SPACING_SM))
            ProgressGraphic(model, colors, barModifier = GlanceModifier.fillMaxWidth().height(PROGRESS_HEIGHT), ringDp = SECONDARY_RING_DP_STANDARD)
        }
    }
}

/**
 * 2×1. No identity, no unit, no secondary — Minimal at compact stays true to "one thing to look
 * at" by dropping to exactly one thing, rather than shrinking everything Session 9 tuned for a
 * taller card into an illegible row. The bare headline, centered, is the whole widget.
 */
@Composable
internal fun MinimalLayoutCompact(model: WidgetRenderModel, headline: WidgetHeadline, colors: WidgetColors, modifier: GlanceModifier) {
    CompactCenteredHeadline(headline, colors, modifier, size = MINIMAL_COMPACT_SIZE, weight = FontWeight.Bold)
}

/**
 * 4×2. Deliberately still one column, not two — splitting into a second column would itself be a
 * second thing to look at, which is exactly what Minimal exists to avoid. The extra width buys a
 * bigger number instead, the same lever [MinimalLayout] already pulls, just further.
 */
@Composable
internal fun MinimalLayoutWide(model: WidgetRenderModel, headline: WidgetHeadline, colors: WidgetColors, modifier: GlanceModifier) {
    Column(
        modifier = modifier.padding(WIDGET_PADDING),
        verticalAlignment = Alignment.CenterVertically,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        if (headline.showIdentity && (model.showEmoji || model.showTitle)) {
            CenteredIdentity(model, colors, size = 14.sp, weight = FontWeight.Medium)
            Spacer(modifier = GlanceModifier.height(SPACING_LG))
        }
        Text(
            text = headline.primary,
            style = TextStyle(
                fontSize = headlineSize(headline, MINIMAL_WIDE_HEADLINE_SIZE, MINIMAL_WIDE_HEADLINE_WORD_SIZE),
                fontWeight = FontWeight.Bold,
                color = colors.statusColor,
                textAlign = TextAlign.Center,
            ),
            maxLines = 1,
        )
        headline.unit?.let {
            Text(text = it, style = TextStyle(fontSize = 15.sp, color = colors.onSurfaceMuted, textAlign = TextAlign.Center), maxLines = 1)
        }
        headline.secondary?.let {
            Spacer(modifier = GlanceModifier.height(SPACING_XS))
            Text(text = it, style = TextStyle(fontSize = LABEL_SIZE, color = colors.onSurfaceMuted, textAlign = TextAlign.Center), maxLines = 1)
        }
        if (model.progress.isVisible) {
            Spacer(modifier = GlanceModifier.height(SPACING_SM))
            ProgressGraphic(model, colors, barModifier = GlanceModifier.fillMaxWidth().height(PROGRESS_HEIGHT), ringDp = SECONDARY_RING_DP_WIDE_SINGLE_COLUMN)
        }
    }
}

// ═══════════════════════════════════════════════════════════════════ Material ══

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
                    maxLines = 1,
                    modifier = GlanceModifier.padding(start = 4.dp),
                )
            }
        }
        headline.secondary?.let {
            Text(text = it, style = TextStyle(fontSize = LABEL_SIZE, color = colors.onSurfaceMuted), maxLines = 1)
        }
        if (model.showDate) {
            TargetDateLine(model, colors)
        }
        if (model.progress.isVisible) {
            Spacer(modifier = GlanceModifier.height(SPACING_SM))
            ProgressGraphic(model, colors, barModifier = GlanceModifier.fillMaxWidth().height(PROGRESS_HEIGHT), ringDp = SECONDARY_RING_DP_STANDARD)
        }
        if (model.showPercentageText) {
            Text(
                text = model.progress.percentText,
                style = TextStyle(fontSize = PERCENT_SIZE, color = colors.onSurfaceMuted),
                maxLines = 1,
            )
        }
    }
}

/**
 * 2×1. One row: identity (weight-1, so a long title yields to the headline rather than pushing it
 * off-card) on the left, the headline and its unit on the right — the brief's own "Emoji + title
 * … 7 days" pattern. No secondary line, no date, no progress: this is glanceability, not a
 * shrunken dashboard.
 */
@Composable
internal fun MaterialLayoutCompact(model: WidgetRenderModel, headline: WidgetHeadline, colors: WidgetColors, modifier: GlanceModifier) {
    Row(
        modifier = modifier.padding(horizontal = WIDGET_PADDING, vertical = COMPACT_VERTICAL_PADDING),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (headline.showIdentity && (model.showEmoji || model.showTitle)) {
            StartIdentity(model, colors, size = 13.sp, weight = FontWeight.Medium, modifier = GlanceModifier.defaultWeight())
        } else {
            Spacer(modifier = GlanceModifier.defaultWeight())
        }
        Text(
            text = headline.primary,
            style = TextStyle(fontSize = headlineSize(headline, MATERIAL_COMPACT_SIZE, MATERIAL_COMPACT_WORD_SIZE), fontWeight = FontWeight.Bold, color = colors.statusColor),
            maxLines = 1,
            modifier = GlanceModifier.padding(start = 6.dp),
        )
        headline.unit?.let {
            Text(
                text = it,
                style = TextStyle(fontSize = 12.sp, color = colors.onSurfaceMuted),
                maxLines = 1,
                modifier = GlanceModifier.padding(start = 3.dp),
            )
        }
    }
}

/**
 * 4×2. Left column: identity, secondary line, and the target date if enabled. Right column: a
 * larger headline and, beneath it, the progress row — the brief's own "LEFT identity + target
 * date / RIGHT large countdown" pattern. The two columns are genuinely independent `Column`s, not
 * one `Column` stretched sideways.
 */
@Composable
internal fun MaterialLayoutWide(model: WidgetRenderModel, headline: WidgetHeadline, colors: WidgetColors, modifier: GlanceModifier) {
    Row(
        modifier = modifier.padding(WIDGET_PADDING),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = GlanceModifier.defaultWeight(), verticalAlignment = Alignment.CenterVertically) {
            if (headline.showIdentity && (model.showEmoji || model.showTitle)) {
                StartIdentity(model, colors, size = 15.sp, weight = FontWeight.Medium)
            }
            headline.secondary?.let {
                Spacer(modifier = GlanceModifier.height(SPACING_XS))
                Text(text = it, style = TextStyle(fontSize = LABEL_SIZE, color = colors.onSurfaceMuted), maxLines = 1)
            }
            if (model.showDate) {
                Spacer(modifier = GlanceModifier.height(SPACING_XS))
                TargetDateLine(model, colors)
            }
        }
        Spacer(modifier = GlanceModifier.width(SPACING_LG))
        Column(horizontalAlignment = Alignment.End) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = headline.primary,
                    style = TextStyle(fontSize = headlineSize(headline, MATERIAL_WIDE_HEADLINE_SIZE, MATERIAL_WIDE_HEADLINE_WORD_SIZE), fontWeight = FontWeight.Bold, color = colors.statusColor),
                    maxLines = 1,
                )
                headline.unit?.let {
                    Text(
                        text = it,
                        style = TextStyle(fontSize = 14.sp, color = colors.onSurfaceMuted),
                        maxLines = 1,
                        modifier = GlanceModifier.padding(start = 4.dp),
                    )
                }
            }
            if (model.progress.isVisible) {
                Spacer(modifier = GlanceModifier.height(SPACING_SM))
                ProgressGraphic(model, colors, barModifier = GlanceModifier.width(WIDE_PROGRESS_COLUMN_WIDTH).height(PROGRESS_HEIGHT), ringDp = SECONDARY_RING_DP_WIDE)
            }
            if (model.showPercentageText) {
                Text(
                    text = model.progress.percentText,
                    style = TextStyle(fontSize = PERCENT_SIZE, color = colors.onSurfaceMuted),
                    maxLines = 1,
                )
            }
        }
    }
}

// ═══════════════════════════════════════════════════════════════════ Progress ══

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
            val ringDp = (minOf(size.width.value, size.height.value) * RING_FRACTION_OF_CELL)
                .coerceIn(MIN_RING_DP, MAX_RING_DP)
            ProgressRing(model, headline, colors, context, ringDp, PROGRESS_RING_HEADLINE_SIZE)
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
            Text(text = it, style = TextStyle(fontSize = LABEL_SIZE, color = colors.onSurfaceMuted, textAlign = TextAlign.Center), maxLines = 1)
        }
    }
}

/**
 * 2×1. No ring: at ~40dp tall there is no diameter left to draw one at (a ring below
 * [MIN_RING_DP] reads as a smudge, not a progress indicator), and forcing [MIN_RING_DP] anyway
 * would overflow the card — a real bug this session found in its own math while designing this
 * layout, not a defect in the shipped 2×2 renderer. Progress converges on Minimal's compact
 * treatment instead of inventing a second one; see `docs/WIDGET_SIZE_MATRIX.md` for why that is
 * an honest, disclosed limitation rather than a gap.
 */
@Composable
internal fun ProgressLayoutCompact(model: WidgetRenderModel, headline: WidgetHeadline, colors: WidgetColors, modifier: GlanceModifier) {
    CompactCenteredHeadline(headline, colors, modifier, size = MINIMAL_COMPACT_SIZE, weight = FontWeight.Bold)
}

/**
 * 4×2. Left column: identity and the headline as text — the "countdown" reading. Right column:
 * the ring, now sized against a half-width column rather than the full square [ProgressLayout]
 * assumes (genuinely larger than the 2×2 ring, not the same bitmap stretched), drawn with **no**
 * number inside it — the brief's own "LEFT countdown / RIGHT progress visualization" pattern read
 * literally: two distinct facts side by side (what the count is, how far along it is), not the
 * same digits shown twice.
 */
@Composable
internal fun ProgressLayoutWide(model: WidgetRenderModel, headline: WidgetHeadline, colors: WidgetColors, modifier: GlanceModifier) {
    val context = LocalContext.current
    val size = LocalSize.current

    Row(
        modifier = modifier.padding(WIDGET_PADDING),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = GlanceModifier.defaultWeight(), verticalAlignment = Alignment.CenterVertically, horizontalAlignment = Alignment.Start) {
            if (headline.showIdentity && (model.showEmoji || model.showTitle)) {
                StartIdentity(model, colors, size = 14.sp, weight = FontWeight.Medium)
                Spacer(modifier = GlanceModifier.height(SPACING_SM))
            }
            Text(
                text = headline.primary,
                style = TextStyle(fontSize = headlineSize(headline, MATERIAL_WIDE_HEADLINE_SIZE, MATERIAL_WIDE_HEADLINE_WORD_SIZE), fontWeight = FontWeight.Bold, color = colors.statusColor),
                maxLines = 1,
            )
            headline.unit?.let {
                Text(text = it, style = TextStyle(fontSize = 14.sp, color = colors.onSurfaceMuted), maxLines = 1)
            }
            headline.secondary?.let {
                Spacer(modifier = GlanceModifier.height(SPACING_XS))
                Text(text = it, style = TextStyle(fontSize = LABEL_SIZE, color = colors.onSurfaceMuted), maxLines = 1)
            }
        }

        if (model.progress.isVisible && headline.isNumeric) {
            Spacer(modifier = GlanceModifier.width(SPACING_LG))
            // Half the card's width, the full height, is the actual room this column has — not
            // the whole card's shorter dimension the STANDARD ring formula assumes.
            val columnWidthDp = size.width.value / 2f - WIDGET_PADDING.value * 2f
            val ringDp = minOf(columnWidthDp, size.height.value - WIDGET_PADDING.value * 2f)
                .coerceIn(MIN_RING_DP, MAX_RING_DP)
            Box(contentAlignment = Alignment.Center) {
                ProgressRing(model, headline, colors, context, ringDp, PROGRESS_RING_WIDE_HEADLINE_SIZE, showHeadline = false)
            }
        }
    }
}

// ═══════════════════════════════════════════════════════════════════════ OLED ══

/**
 * As stark as the display technology it is named for. One number, one line beneath it if that,
 * nothing competing for the handful of lit sub-pixels this theme's entire purpose is minimizing.
 *
 * Identity, target date, and percentage are drawn only when their own toggle asks for them —
 * found on a real Samsung Galaxy A55 that this layout was silently never drawing identity at all,
 * regardless of `showTitle`/`showEmoji` (and never drew target date or percentage either): OLED's
 * genuine starkness comes from [colors] already being the highest-contrast palette any style
 * resolves (forced true black, forced white/near-white text — see `resolveColors`), not from
 * ignoring what the user asked to see. Kept visually the smallest, quietest text in the layout
 * (13sp Medium vs. the headline's own 26–50sp Bold), the same hierarchy every other style already
 * gives its own identity row, so OLED stays the starkest-*looking* style while still honouring
 * every toggle truthfully.
 */
@Composable
internal fun OledLayout(model: WidgetRenderModel, headline: WidgetHeadline, colors: WidgetColors, modifier: GlanceModifier) {
    Column(
        modifier = modifier.padding(WIDGET_PADDING),
        verticalAlignment = Alignment.CenterVertically,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        if (headline.showIdentity && (model.showEmoji || model.showTitle)) {
            CenteredIdentity(model, colors, size = 13.sp, weight = FontWeight.Medium)
            Spacer(modifier = GlanceModifier.height(SPACING_XS))
        }
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
            Text(text = it, style = TextStyle(fontSize = 13.sp, color = colors.onSurfaceMuted, textAlign = TextAlign.Center), maxLines = 1)
        }
        headline.secondary?.let {
            Text(text = it, style = TextStyle(fontSize = LABEL_SIZE, color = colors.onSurfaceMuted, textAlign = TextAlign.Center), maxLines = 1)
        }
        if (model.showDate) {
            Spacer(modifier = GlanceModifier.height(SPACING_XS))
            TargetDateLine(model, colors)
        }
        if (model.showPercentageText) {
            Spacer(modifier = GlanceModifier.height(SPACING_XS))
            Text(
                text = model.progress.percentText,
                style = TextStyle(fontSize = PERCENT_SIZE, fontWeight = FontWeight.Medium, color = colors.accent, textAlign = TextAlign.Center),
                maxLines = 1,
            )
        }
        if (model.progress.isVisible) {
            Spacer(modifier = GlanceModifier.height(SPACING_XS))
            ProgressGraphic(model, colors, barModifier = GlanceModifier.fillMaxWidth().height(PROGRESS_HEIGHT), ringDp = SECONDARY_RING_DP_OLED_STANDARD)
        }
    }
}

/** 2×1. Still no identity, still just the number — the least this layout can show is already what it always shows (an established, size-driven limitation shared with [MinimalLayoutCompact], not touched by this fix). */
@Composable
internal fun OledLayoutCompact(model: WidgetRenderModel, headline: WidgetHeadline, colors: WidgetColors, modifier: GlanceModifier) {
    CompactCenteredHeadline(headline, colors, modifier, size = OLED_COMPACT_SIZE, weight = FontWeight.Bold)
}

/** 4×2. The extra width goes to an even larger number, the same "stay one thing, get bigger" logic [MinimalLayoutWide] uses. Same identity/date/percentage toggles as [OledLayout] — see its own doc. */
@Composable
internal fun OledLayoutWide(model: WidgetRenderModel, headline: WidgetHeadline, colors: WidgetColors, modifier: GlanceModifier) {
    Column(
        modifier = modifier.padding(WIDGET_PADDING),
        verticalAlignment = Alignment.CenterVertically,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        if (headline.showIdentity && (model.showEmoji || model.showTitle)) {
            CenteredIdentity(model, colors, size = 14.sp, weight = FontWeight.Medium)
            Spacer(modifier = GlanceModifier.height(SPACING_XS))
        }
        Text(
            text = headline.primary,
            style = TextStyle(
                fontSize = headlineSize(headline, OLED_WIDE_HEADLINE_SIZE, OLED_WIDE_HEADLINE_WORD_SIZE),
                fontWeight = FontWeight.Bold,
                color = colors.statusColor,
                textAlign = TextAlign.Center,
            ),
            maxLines = 1,
        )
        headline.unit?.let {
            Text(text = it, style = TextStyle(fontSize = 15.sp, color = colors.onSurfaceMuted, textAlign = TextAlign.Center), maxLines = 1)
        }
        headline.secondary?.let {
            Text(text = it, style = TextStyle(fontSize = LABEL_SIZE, color = colors.onSurfaceMuted, textAlign = TextAlign.Center), maxLines = 1)
        }
        if (model.showDate) {
            Spacer(modifier = GlanceModifier.height(SPACING_XS))
            TargetDateLine(model, colors)
        }
        if (model.showPercentageText) {
            Spacer(modifier = GlanceModifier.height(SPACING_XS))
            Text(
                text = model.progress.percentText,
                style = TextStyle(fontSize = PERCENT_SIZE, fontWeight = FontWeight.Medium, color = colors.accent, textAlign = TextAlign.Center),
                maxLines = 1,
            )
        }
        if (model.progress.isVisible) {
            Spacer(modifier = GlanceModifier.height(SPACING_XS))
            ProgressGraphic(model, colors, barModifier = GlanceModifier.fillMaxWidth().height(PROGRESS_HEIGHT), ringDp = SECONDARY_RING_DP_OLED_WIDE)
        }
    }
}

// ══════════════════════════════════════════════════════════════════════ Glass ══

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
            Text(text = it, style = TextStyle(fontSize = 13.sp, fontWeight = FontWeight.Normal, color = colors.onSurfaceMuted, textAlign = TextAlign.Center), maxLines = 1)
        }
        headline.secondary?.let {
            Spacer(modifier = GlanceModifier.height(SPACING_XS))
            Text(text = it, style = TextStyle(fontSize = LABEL_SIZE, fontWeight = FontWeight.Normal, color = colors.onSurfaceMuted, textAlign = TextAlign.Center), maxLines = 1)
        }
        if (model.progress.isVisible) {
            Spacer(modifier = GlanceModifier.height(SPACING_LG))
            ProgressGraphic(model, colors, barModifier = GlanceModifier.fillMaxWidth().height(4.dp), ringDp = SECONDARY_RING_DP_STANDARD)
        }
    }
}

/**
 * 2×1. One light-weight row: title, then headline — same shape as [MaterialLayoutCompact] but
 * every text node stays [FontWeight.Normal], the one thing that survives every size for this
 * style, per its own doc above.
 */
@Composable
internal fun GlassLayoutCompact(model: WidgetRenderModel, headline: WidgetHeadline, colors: WidgetColors, modifier: GlanceModifier) {
    Row(
        modifier = modifier.padding(horizontal = GLASS_PADDING, vertical = COMPACT_VERTICAL_PADDING),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (headline.showIdentity && (model.showEmoji || model.showTitle)) {
            CenteredIdentity(model, colors, size = 12.sp, weight = FontWeight.Normal, modifier = GlanceModifier.defaultWeight())
        } else {
            Spacer(modifier = GlanceModifier.defaultWeight())
        }
        Text(
            text = headline.primary,
            style = TextStyle(fontSize = headlineSize(headline, MATERIAL_COMPACT_SIZE, MATERIAL_COMPACT_WORD_SIZE), fontWeight = FontWeight.Normal, color = colors.statusColor),
            maxLines = 1,
            modifier = GlanceModifier.padding(start = 6.dp),
        )
    }
}

/**
 * 4×2. Left column: identity and secondary. Right column: headline plus a thin progress bar
 * spanning the column beneath it — the same two-column idea as [MaterialLayoutWide], kept light.
 */
@Composable
internal fun GlassLayoutWide(model: WidgetRenderModel, headline: WidgetHeadline, colors: WidgetColors, modifier: GlanceModifier) {
    Row(
        modifier = modifier.padding(GLASS_PADDING),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = GlanceModifier.defaultWeight()) {
            if (headline.showIdentity && (model.showEmoji || model.showTitle)) {
                CenteredIdentity(model, colors, size = 14.sp, weight = FontWeight.Normal)
            }
            headline.secondary?.let {
                Spacer(modifier = GlanceModifier.height(SPACING_XS))
                Text(text = it, style = TextStyle(fontSize = LABEL_SIZE, fontWeight = FontWeight.Normal, color = colors.onSurfaceMuted, textAlign = TextAlign.Center), maxLines = 1)
            }
        }
        Spacer(modifier = GlanceModifier.width(SPACING_LG))
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                text = headline.primary,
                style = TextStyle(fontSize = headlineSize(headline, GLASS_WIDE_HEADLINE_SIZE, GLASS_WIDE_HEADLINE_WORD_SIZE), fontWeight = FontWeight.Normal, color = colors.statusColor, textAlign = TextAlign.Center),
                maxLines = 1,
            )
            headline.unit?.let {
                Text(text = it, style = TextStyle(fontSize = 14.sp, fontWeight = FontWeight.Normal, color = colors.onSurfaceMuted, textAlign = TextAlign.Center), maxLines = 1)
            }
            if (model.progress.isVisible) {
                Spacer(modifier = GlanceModifier.height(SPACING_SM))
                ProgressGraphic(model, colors, barModifier = GlanceModifier.width(WIDE_PROGRESS_COLUMN_WIDTH).height(4.dp), ringDp = SECONDARY_RING_DP_WIDE)
            }
        }
    }
}

// ═══════════════════════════════════════════════════════════════════ Rounded ══

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
            Text(text = it, style = TextStyle(fontSize = 13.sp, color = colors.onSurfaceMuted, textAlign = TextAlign.Center), maxLines = 1)
        }
        headline.secondary?.let {
            Spacer(modifier = GlanceModifier.height(SPACING_SM))
            Pill(it, colors)
        }
        if (model.progress.isVisible) {
            Spacer(modifier = GlanceModifier.height(SPACING_SM))
            ProgressGraphic(model, colors, barModifier = GlanceModifier.fillMaxWidth().height(PROGRESS_HEIGHT), ringDp = SECONDARY_RING_DP_STANDARD, barColor = colors.accent)
        }
    }
}

/**
 * 2×1. The headline, and — if there is no secondary line competing for the row — the unit inside
 * its signature pill instead of plain text, so even the smallest size keeps one bit of Rounded's
 * own identity rather than converging on Minimal's bare-number treatment the way [ProgressLayoutCompact]
 * does. When a secondary line exists, it takes the pill instead (only one pill fits a single row).
 */
@Composable
internal fun RoundedLayoutCompact(model: WidgetRenderModel, headline: WidgetHeadline, colors: WidgetColors, modifier: GlanceModifier) {
    Row(
        modifier = modifier.padding(horizontal = ROUNDED_PADDING, vertical = COMPACT_VERTICAL_PADDING),
        verticalAlignment = Alignment.CenterVertically,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            text = headline.primary,
            style = TextStyle(fontSize = headlineSize(headline, MATERIAL_COMPACT_SIZE, MATERIAL_COMPACT_WORD_SIZE), fontWeight = FontWeight.Bold, color = colors.statusColor),
            maxLines = 1,
        )
        val pillText = headline.secondary ?: headline.unit
        pillText?.let {
            Spacer(modifier = GlanceModifier.width(SPACING_SM))
            Pill(it, colors)
        }
    }
}

/**
 * 4×2. Left column: identity. Right column: headline, the pill-chip secondary, and a progress
 * bar — everything [RoundedLayout] shows, given a second column to breathe into instead of a
 * taller single one.
 */
@Composable
internal fun RoundedLayoutWide(model: WidgetRenderModel, headline: WidgetHeadline, colors: WidgetColors, modifier: GlanceModifier) {
    Row(
        modifier = modifier.padding(ROUNDED_PADDING),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = GlanceModifier.defaultWeight(), horizontalAlignment = Alignment.CenterHorizontally) {
            if (headline.showIdentity && (model.showEmoji || model.showTitle)) {
                CenteredIdentity(model, colors, size = 14.sp, weight = FontWeight.Medium)
            }
        }
        Spacer(modifier = GlanceModifier.width(SPACING_LG))
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                text = headline.primary,
                style = TextStyle(fontSize = headlineSize(headline, ROUNDED_WIDE_HEADLINE_SIZE, ROUNDED_WIDE_HEADLINE_WORD_SIZE), fontWeight = FontWeight.Bold, color = colors.statusColor, textAlign = TextAlign.Center),
                maxLines = 1,
            )
            headline.unit?.let {
                Text(text = it, style = TextStyle(fontSize = 14.sp, color = colors.onSurfaceMuted, textAlign = TextAlign.Center), maxLines = 1)
            }
            headline.secondary?.let {
                Spacer(modifier = GlanceModifier.height(SPACING_SM))
                Pill(it, colors)
            }
            if (model.progress.isVisible) {
                Spacer(modifier = GlanceModifier.height(SPACING_SM))
                ProgressGraphic(model, colors, barModifier = GlanceModifier.width(WIDE_PROGRESS_COLUMN_WIDTH).height(PROGRESS_HEIGHT), ringDp = SECONDARY_RING_DP_WIDE, barColor = colors.accent)
            }
        }
    }
}

// ═══════════════════════════════════════════════════════════════════ Modern ══

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
            Text(text = it, style = TextStyle(fontSize = 12.sp, color = colors.onSurfaceMuted), maxLines = 1)
        }
        headline.secondary?.let {
            Text(text = it, style = TextStyle(fontSize = 12.sp, color = colors.onSurfaceMuted), maxLines = 1)
        }
        if (model.showDate) {
            TargetDateLine(model, colors, size = 11.sp)
        }
        if (model.showPercentageText) {
            Text(
                text = model.progress.percentText,
                style = TextStyle(fontSize = 11.sp, fontWeight = FontWeight.Medium, color = colors.accent),
                maxLines = 1,
            )
        }
        if (model.progress.isVisible) {
            Spacer(modifier = GlanceModifier.height(SPACING_XS))
            ProgressGraphic(model, colors, barModifier = GlanceModifier.fillMaxWidth().height(4.dp), ringDp = SECONDARY_RING_DP_STANDARD, barColor = colors.accent)
        }
    }
}

/**
 * 2×1. Flush-left, bold, dense — the one compact treatment that stays start-aligned rather than
 * centered, the same alignment choice that sets [ModernLayout] apart from every other style at
 * 2×2. Identity is dropped (Modern shows the most fields of any style at 2×2, so it also has the
 * most to give up here) in favor of the number reading immediately, no centering delay.
 */
@Composable
internal fun ModernLayoutCompact(model: WidgetRenderModel, headline: WidgetHeadline, colors: WidgetColors, modifier: GlanceModifier) {
    Row(
        modifier = modifier.padding(horizontal = WIDGET_PADDING, vertical = COMPACT_VERTICAL_PADDING),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = headline.primary,
            style = TextStyle(fontSize = headlineSize(headline, MATERIAL_COMPACT_SIZE, MATERIAL_COMPACT_WORD_SIZE), fontWeight = FontWeight.Bold, color = colors.statusColor),
            maxLines = 1,
        )
        headline.unit?.let {
            Text(
                text = it,
                style = TextStyle(fontSize = 12.sp, color = colors.onSurfaceMuted),
                maxLines = 1,
                modifier = GlanceModifier.padding(start = 4.dp),
            )
        }
    }
}

/**
 * 4×2. Left column: identity, date, and percentage stacked top-anchored. Right column: a larger
 * headline and a progress bar. The most "dashboard" of any wide layout, because Modern was
 * already the most information-dense style at 2×2 — a second column is this style benefiting the
 * most from the extra width, not just tolerating it.
 */
@Composable
internal fun ModernLayoutWide(model: WidgetRenderModel, headline: WidgetHeadline, colors: WidgetColors, modifier: GlanceModifier) {
    Row(
        modifier = modifier.padding(WIDGET_PADDING),
        verticalAlignment = Alignment.Top,
    ) {
        Column(modifier = GlanceModifier.defaultWeight(), verticalAlignment = Alignment.Top, horizontalAlignment = Alignment.Start) {
            if (headline.showIdentity && (model.showEmoji || model.showTitle)) {
                StartIdentity(model, colors, size = 13.sp, weight = FontWeight.Medium)
                Spacer(modifier = GlanceModifier.height(SPACING_XS))
            }
            headline.secondary?.let {
                Text(text = it, style = TextStyle(fontSize = 12.sp, color = colors.onSurfaceMuted), maxLines = 1)
            }
            if (model.showDate) {
                TargetDateLine(model, colors, size = 11.sp)
            }
            if (model.showPercentageText) {
                Text(
                    text = model.progress.percentText,
                    style = TextStyle(fontSize = 11.sp, fontWeight = FontWeight.Medium, color = colors.accent),
                    maxLines = 1,
                )
            }
        }
        Spacer(modifier = GlanceModifier.width(SPACING_LG))
        Column(horizontalAlignment = Alignment.Start) {
            Text(
                text = headline.primary,
                style = TextStyle(fontSize = headlineSize(headline, MODERN_WIDE_HEADLINE_SIZE, MODERN_WIDE_HEADLINE_WORD_SIZE), fontWeight = FontWeight.Bold, color = colors.statusColor),
                maxLines = 1,
            )
            headline.unit?.let {
                Text(text = it, style = TextStyle(fontSize = 14.sp, color = colors.onSurfaceMuted), maxLines = 1)
            }
            if (model.progress.isVisible) {
                Spacer(modifier = GlanceModifier.height(SPACING_SM))
                ProgressGraphic(model, colors, barModifier = GlanceModifier.width(WIDE_PROGRESS_COLUMN_WIDTH).height(4.dp), ringDp = SECONDARY_RING_DP_WIDE, barColor = colors.accent)
            }
        }
    }
}

// ═══════════════════════════════════════════════════════════════════ shared ══

@Composable
private fun CenteredIdentity(
    model: WidgetRenderModel,
    colors: WidgetColors,
    size: TextUnit,
    weight: FontWeight,
    modifier: GlanceModifier = GlanceModifier.fillMaxWidth(),
) {
    Row(modifier = modifier, verticalAlignment = Alignment.CenterVertically, horizontalAlignment = Alignment.CenterHorizontally) {
        if (model.showEmoji) {
            Text(text = model.emoji, style = TextStyle(fontSize = size, textAlign = TextAlign.Center), maxLines = 1)
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
private fun StartIdentity(
    model: WidgetRenderModel,
    colors: WidgetColors,
    size: TextUnit,
    weight: FontWeight,
    modifier: GlanceModifier = GlanceModifier.fillMaxWidth(),
) {
    Row(modifier = modifier, verticalAlignment = Alignment.CenterVertically) {
        if (model.showEmoji) {
            Text(text = model.emoji, style = TextStyle(fontSize = size), maxLines = 1)
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
private fun TargetDateLine(model: WidgetRenderModel, colors: WidgetColors, size: TextUnit = LABEL_SIZE) {
    val resources = LocalContext.current.resources
    val dateText = TargetDateFormatter.formatDate(resources, model.target, model.targetZone)
    Text(text = dateText, style = TextStyle(fontSize = size, color = colors.onSurfaceMuted), maxLines = 1)
}

/**
 * The progress *graphic* slot every real style now shares — a bar, a ring, or nothing, chosen by
 * `model.progress.style` alone, never by which [com.countflow.core.domain.model.WidgetStyle] is
 * active. This is the one place that makes Style and Progress genuinely independent axes: every
 * style already decided *whether* to reserve room for a graphic and where (its own
 * `model.progress.isVisible` check at each call site, unchanged) — this decides *which* graphic
 * fills that room, uniformly, instead of each style hand-rolling its own bar-only treatment (the
 * bug a Samsung Galaxy A55 exposed: picking Ring never actually drew one outside the old
 * dedicated Progress style).
 *
 * Deliberately a small, secondary graphic beside/beneath the headline the style already drew,
 * never a replacement for it — unlike [ProgressLayout]'s ring, which *is* the headline's frame.
 * Swallowing every style's own carefully-sized headline into a ring here would undo exactly what
 * each style's own layout above already tuned (OLED's headline, for one real example, is the
 * *largest* of any style specifically so nothing else competes with it — shrinking it to fit
 * inside a ring would contradict that on the one style whose entire premise is that number being
 * as big as possible). [testTag] lets a test tell bar and ring apart without a pixel comparison.
 */
@Composable
private fun ProgressGraphic(
    model: WidgetRenderModel,
    colors: WidgetColors,
    barModifier: GlanceModifier,
    ringDp: Float,
    barColor: ColorProvider = colors.statusColor,
) {
    when (model.progress.style) {
        ProgressStyle.LINEAR -> LinearProgressIndicator(
            progress = model.progress.fraction,
            modifier = barModifier.semantics { testTag = "progress-bar" },
            color = barColor,
            backgroundColor = colors.track,
        )

        ProgressStyle.CIRCULAR -> {
            val context = LocalContext.current
            Box(
                modifier = GlanceModifier.size(ringDp.dp).semantics { testTag = "progress-ring" },
                contentAlignment = Alignment.Center,
            ) {
                ProgressRingGraphic(model, colors, context, ringDp)
            }
        }

        // Unreachable in practice — every call site already guards with `model.progress.isVisible`
        // (false exactly when style is NONE) — kept so this `when` stays exhaustive rather than
        // relying on that external guarantee holding at every call site forever.
        ProgressStyle.NONE -> Unit
    }
}

/** The pill-shaped chip [RoundedLayout] and its size variants use for one line of supporting text. */
@Composable
private fun Pill(text: String, colors: WidgetColors) {
    Box(
        modifier = GlanceModifier
            .background(colors.track)
            .cornerRadius(999.dp)
            .padding(horizontal = 10.dp, vertical = 4.dp),
    ) {
        Text(text = text, style = TextStyle(fontSize = 12.sp, color = colors.onSurface, textAlign = TextAlign.Center), maxLines = 1)
    }
}

/**
 * The centered, identity-free, bare-headline treatment [MinimalLayoutCompact],
 * [ProgressLayoutCompact], and [OledLayoutCompact] all converge on — three styles whose 2×2
 * identity is already "as little as possible," so their 2×1 forms are legitimately the same
 * shape at a smaller size, not three copies of one idea that should have been shared from the
 * start. (Their 2×2 and 4×2 forms differ from each other — background color, weight — this
 * convergence is specific to the size where nothing else is left to differentiate with.)
 */
@Composable
private fun CompactCenteredHeadline(
    headline: WidgetHeadline,
    colors: WidgetColors,
    modifier: GlanceModifier,
    size: TextUnit,
    weight: FontWeight,
) {
    Box(
        modifier = modifier.padding(horizontal = WIDGET_PADDING, vertical = COMPACT_VERTICAL_PADDING),
        contentAlignment = Alignment.Center,
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = headline.primary,
                style = TextStyle(fontSize = headlineSize(headline, size, size * COMPACT_WORD_FRACTION), fontWeight = weight, color = colors.statusColor, textAlign = TextAlign.Center),
                maxLines = 1,
            )
            headline.unit?.let {
                Text(
                    text = it,
                    style = TextStyle(fontSize = size.value.times(0.4f).sp, color = colors.onSurfaceMuted),
                    maxLines = 1,
                    modifier = GlanceModifier.padding(start = 3.dp),
                )
            }
        }
    }
}

/**
 * The ring [ProgressLayout] and [ProgressLayoutWide] both build, parameterized by the diameter
 * and headline size each has room for. [showHeadline] is false only for [ProgressLayoutWide],
 * whose left column already draws the headline as text — the ring there is a pure visual
 * indicator, not a second place the same number is written (see that layout's own doc for why
 * that distinction matters).
 */
@Composable
private fun ProgressRing(
    model: WidgetRenderModel,
    headline: WidgetHeadline,
    colors: WidgetColors,
    context: android.content.Context,
    ringDp: Float,
    headlineSize: TextUnit,
    showHeadline: Boolean = true,
) {
    Box(modifier = GlanceModifier.size(ringDp.dp), contentAlignment = Alignment.Center) {
        ProgressRingGraphic(model, colors, context, ringDp, modifier = GlanceModifier.size(ringDp.dp))
        if (showHeadline) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(
                    text = headline.primary,
                    style = TextStyle(fontSize = headlineSize, fontWeight = FontWeight.Bold, color = colors.statusColor, textAlign = TextAlign.Center),
                    maxLines = 1,
                )
                headline.unit?.let {
                    Text(text = it, style = TextStyle(fontSize = 12.sp, color = colors.onSurfaceMuted, textAlign = TextAlign.Center), maxLines = 1)
                }
            }
        }
    }
}

/**
 * The bitmap ring itself, with no headline drawn over it — [ProgressRing] wraps this with an
 * optional headline for [ProgressLayout]/[ProgressLayoutWide]; [ProgressGraphic] uses it bare, as
 * a small decorative graphic beside a headline the caller already drew as plain text.
 */
@Composable
private fun ProgressRingGraphic(
    model: WidgetRenderModel,
    colors: WidgetColors,
    context: android.content.Context,
    ringDp: Float,
    modifier: GlanceModifier = GlanceModifier.size(ringDp.dp),
) {
    val density = context.resources.displayMetrics.density
    // Quantized to the nearest 8px, not the exact pixel value: SizeMode.Exact (D-053) reports a
    // widget's genuine current size, which can vary by a handful of dp between otherwise-identical
    // placements (density rounding, per-launcher grid quirks). Without quantizing, every such
    // placement would mint its own CircularProgressRenderer cache entry for no visual difference —
    // this keeps the cache's real key space close to "how many distinct percents are on screen
    // right now," which is what its LRU capacity was actually budgeted against (D-047, D-054).
    val ringPx = (((ringDp * density).toInt() / RING_PX_QUANTUM) * RING_PX_QUANTUM).coerceAtLeast(RING_PX_QUANTUM)
    val strokePx = RING_STROKE_DP * density

    Image(
        provider = CircularProgressRenderer.provider(
            sizePx = ringPx,
            percent = model.progress.percent,
            trackArgb = colors.track.getColor(context).toArgb(),
            progressArgb = colors.accent.getColor(context).toArgb(),
            strokeWidthPx = strokePx,
        ),
        contentDescription = null,
        modifier = modifier,
        contentScale = ContentScale.Fit,
    )
}

/**
 * Scales a style's base headline size down for content that needs more room than the size was
 * tuned around. Multiplicative against each style's own numeric/word base — every base stays
 * exactly Session 9's tuned value for the content those screenshots actually showed (1–3 digit
 * counts, this app's six status words, all ≤13 characters); scaling only ever engages for content
 * outside that range, so no existing screenshot's font size changes (Session 10 brief: "Regression-test
 * the existing appearance against the Session 9 screenshots"). Glance has no autosizing text
 * (LIM-004), so this multiply-by-tier is the entire content-fit mechanism — there is no
 * measure-and-shrink fallback beneath it.
 */
private fun WidgetHeadline.contentFitScale(): Float = if (isNumeric) {
    if (primary.length <= 3) 1f else 0.72f // a 4+ digit day count is new ground past Session 9
} else {
    when {
        primary.length <= 13 -> 1f // every status word this app ships today ("Starting soon" = 13)
        primary.length <= 20 -> 0.85f // headroom for a longer localized translation
        else -> 0.7f
    }
}

/**
 * [numeric] for a bare day count, [word] for a status word like "Completed" or "Starting soon" —
 * found on a real device (`docs/WIDGET_DESIGN_REVIEW.md`): a size tuned for "7" wraps a longer
 * word mid-syllable with no hyphen, since Glance has no autosizing text (LIM-004) to shrink it
 * automatically. [WidgetHeadline.contentFitScale] (Session 10) further scales either base down
 * for content neither base was tuned around — a 4+ digit count, or an unusually long word.
 * `maxLines = 1` at every call site turns anything still too long into a clean ellipsis instead of
 * a second wrapped line — confirmed to actually ellipsize on a real device, not just clip (D-038's
 * sibling finding, TD-013).
 */
private fun headlineSize(headline: WidgetHeadline, numeric: TextUnit, word: TextUnit): TextUnit {
    val base = if (headline.isNumeric) numeric else word
    return (base.value * headline.contentFitScale()).sp
}

private val MINIMAL_HEADLINE_SIZE = 46.sp
private val MINIMAL_HEADLINE_WORD_SIZE = 26.sp
private val MINIMAL_WIDE_HEADLINE_SIZE = 58.sp
private val MINIMAL_WIDE_HEADLINE_WORD_SIZE = 32.sp
private val MINIMAL_COMPACT_SIZE = 28.sp

private val MATERIAL_HEADLINE_SIZE = 32.sp
private val MATERIAL_HEADLINE_WORD_SIZE = 22.sp
private val MATERIAL_WIDE_HEADLINE_SIZE = 40.sp
private val MATERIAL_WIDE_HEADLINE_WORD_SIZE = 26.sp
private val MATERIAL_COMPACT_SIZE = 22.sp
private val MATERIAL_COMPACT_WORD_SIZE = 16.sp

private val PROGRESS_RING_HEADLINE_SIZE = 26.sp
private val PROGRESS_RING_WIDE_HEADLINE_SIZE = 30.sp

private val OLED_HEADLINE_SIZE = 50.sp
private val OLED_HEADLINE_WORD_SIZE = 28.sp
private val OLED_WIDE_HEADLINE_SIZE = 62.sp
private val OLED_WIDE_HEADLINE_WORD_SIZE = 34.sp
private val OLED_COMPACT_SIZE = 30.sp

private val GLASS_HEADLINE_SIZE = 34.sp
private val GLASS_HEADLINE_WORD_SIZE = 22.sp
private val GLASS_WIDE_HEADLINE_SIZE = 42.sp
private val GLASS_WIDE_HEADLINE_WORD_SIZE = 26.sp

private val ROUNDED_HEADLINE_SIZE = 34.sp
private val ROUNDED_HEADLINE_WORD_SIZE = 22.sp
private val ROUNDED_WIDE_HEADLINE_SIZE = 42.sp
private val ROUNDED_WIDE_HEADLINE_WORD_SIZE = 26.sp

private val MODERN_HEADLINE_SIZE = 30.sp
private val MODERN_HEADLINE_WORD_SIZE = 20.sp
private val MODERN_WIDE_HEADLINE_SIZE = 38.sp
private val MODERN_WIDE_HEADLINE_WORD_SIZE = 24.sp

private val GLASS_PADDING = 14.dp
private val ROUNDED_PADDING = 14.dp
private val SPACING_LG = 12.dp
private val COMPACT_VERTICAL_PADDING = 4.dp
private val WIDE_PROGRESS_COLUMN_WIDTH = 120.dp
private const val COMPACT_WORD_FRACTION = 0.62f

private const val RING_FRACTION_OF_CELL = 0.62f
private const val MIN_RING_DP = 64f
private const val MAX_RING_DP = 120f
private const val RING_STROKE_DP = 8f
private const val RING_PX_QUANTUM = 8

// [ProgressGraphic]'s decorative ring, deliberately smaller than [MIN_RING_DP] — that constant
// sizes a ring that *is* the headline's frame (ProgressLayout); this one sits beside a headline
// every other style already drew at full size, so it has to share the card rather than dominate
// it.
//
// Found on a real Samsung Galaxy A55: WIDE and STANDARD share the *same* real measured card
// height (224dp — WidgetSizeClass.kt's own doc; only width differs), so a WIDE ring sized larger
// than STANDARD's on the assumption that extra width means extra vertical room was wrong, and the
// actual cause of real-device clipping. Two further splits, both from the same 224dp budget minus
// WIDGET_PADDING (12dp × 2 = 24dp, leaving ~200dp) and each style's own already-tuned constants
// above, not a blanket shrink:
//   - Single-column layouts (Minimal, OLED) stack identity + headline + unit + secondary + this
//     ring all in one column, unlike Material/Glass/Rounded/Modern's WIDE layouts, which are two
//     columns — the ring's column there holds only a headline + unit, with identity/secondary/date
//     in the *other* column. Single-column WIDE therefore needs a real fraction of its budget
//     back from the ring; two-column WIDE has ample headroom to keep the larger ring.
//   - OLED specifically uses the largest headline of any style (50sp/62sp, vs Minimal's 46sp/58sp)
//     and, as of this fix, also draws identity/date/percentage Minimal does not — its own smaller
//     constants leave adequate room for its realistic default case (title/emoji + a ring, no
//     optional date/percentage) with margin; the single most crowded combination (every optional
//     field on at once) was not separately re-measured on a real device, consistent with this
//     project's standing practice of not claiming a confirmation that was not obtained.
private const val SECONDARY_RING_DP_STANDARD = 56f
private const val SECONDARY_RING_DP_OLED_STANDARD = 44f
private const val SECONDARY_RING_DP_WIDE = 64f
private const val SECONDARY_RING_DP_WIDE_SINGLE_COLUMN = 40f
private const val SECONDARY_RING_DP_OLED_WIDE = 36f
