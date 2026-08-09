package com.countflow.widget.glance

import android.content.res.Resources
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.glance.GlanceModifier
import androidx.glance.GlanceTheme
import androidx.glance.LocalContext
import androidx.glance.LocalSize
import androidx.glance.action.clickable
import androidx.glance.appwidget.cornerRadius
import androidx.glance.background
import androidx.glance.layout.Alignment
import androidx.glance.layout.Column
import androidx.glance.layout.Spacer
import androidx.glance.layout.fillMaxSize
import androidx.glance.layout.height
import androidx.glance.layout.padding
import androidx.glance.semantics.contentDescription
import androidx.glance.semantics.semantics
import androidx.glance.text.FontWeight
import androidx.glance.text.Text
import androidx.glance.text.TextAlign
import androidx.glance.text.TextStyle
import androidx.glance.unit.ColorProvider
import com.countflow.core.designsystem.format.CountdownLabelFormatter
import com.countflow.core.designsystem.format.TargetDateFormatter
import com.countflow.core.domain.countdown.CountdownLabel
import com.countflow.core.domain.model.WidgetStyle
import com.countflow.widget.engine.model.WidgetRenderModel
import com.countflow.widget.glance.action.actionOpenApp
import com.countflow.widget.glance.action.actionOpenConfiguration

/**
 * Draws a [WidgetRenderModel]. Nothing else — no repository, no clock, no countdown arithmetic.
 * This is "widgets should only render" made literal: every value this function reads was already
 * decided upstream, in `:widget:engine`.
 *
 * Two decisions happen here, in the renderer, on purpose — neither belongs in `:widget:engine`,
 * because both are about *how to say something*, not *what is true*, the same boundary
 * [CountdownLabelFormatter] already draws:
 *
 * 1. **Color resolution.** See [resolveColors] — forced backgrounds (OLED, Glass) need their own
 *    fixed on-colors rather than the ambient dynamic scheme's.
 * 2. **Content hierarchy.** See [resolveHeadline] — which fact is the biggest thing on the card,
 *    and what (if anything) genuinely adds information underneath it, changes with the countdown
 *    state. A completed event's headline is "Completed," not a number; a bare day count and its
 *    own label text are often the same fact said twice.
 *
 * From there, [CountdownWidgetContent] dispatches on both [WidgetStyle] and [WidgetSizeClass] to
 * one of the per-style-per-size layouts in `CountdownWidgetLayouts.kt` — each a genuinely
 * different composition, not a re-skin of one shared tree, per `docs/WIDGET_DESIGN_GUIDE.md` and,
 * for the size dimension specifically, `docs/WIDGET_SIZE_MATRIX.md`.
 */
@Composable
internal fun CountdownWidgetContent(model: WidgetRenderModel?) {
    if (model == null) {
        UnconfiguredContent()
        return
    }

    val resources = LocalContext.current.resources
    val colors = resolveColors(model)
    val labelText = CountdownLabelFormatter.format(resources, model.label)
    val headline = resolveHeadline(model, labelText, resources)
    val size = LocalSize.current
    val sizeClass = classifyWidgetSize(size.width.value, size.height.value)

    val cardModifier = GlanceModifier
        .fillMaxSize()
        .background(colors.background)
        .widgetCornerRadius(model.theme.cornerRadiusDp)
        // One description for the whole tappable card, not several separate text nodes — the
        // same reasoning `EventCard` applies with `clearAndSetSemantics` in the app's own list.
        .semantics { contentDescription = widgetContentDescription(model, headline, sizeClass) }
        .clickable(actionOpenApp())

    when (sizeClass) {
        WidgetSizeClass.COMPACT -> when (model.theme.style) {
            WidgetStyle.MINIMAL -> MinimalLayoutCompact(model, headline, colors, cardModifier)
            WidgetStyle.MATERIAL -> MaterialLayoutCompact(model, headline, colors, cardModifier)
            WidgetStyle.PROGRESS -> ProgressLayoutCompact(model, headline, colors, cardModifier)
            WidgetStyle.OLED -> OledLayoutCompact(model, headline, colors, cardModifier)
            WidgetStyle.GLASS -> GlassLayoutCompact(model, headline, colors, cardModifier)
            WidgetStyle.ROUNDED -> RoundedLayoutCompact(model, headline, colors, cardModifier)
            WidgetStyle.MODERN -> ModernLayoutCompact(model, headline, colors, cardModifier)
        }

        WidgetSizeClass.STANDARD -> when (model.theme.style) {
            WidgetStyle.MINIMAL -> MinimalLayout(model, headline, colors, cardModifier)
            WidgetStyle.MATERIAL -> MaterialLayout(model, headline, colors, cardModifier)
            WidgetStyle.PROGRESS -> ProgressLayout(model, headline, colors, cardModifier)
            WidgetStyle.OLED -> OledLayout(model, headline, colors, cardModifier)
            WidgetStyle.GLASS -> GlassLayout(model, headline, colors, cardModifier)
            WidgetStyle.ROUNDED -> RoundedLayout(model, headline, colors, cardModifier)
            WidgetStyle.MODERN -> ModernLayout(model, headline, colors, cardModifier)
        }

        WidgetSizeClass.WIDE -> when (model.theme.style) {
            WidgetStyle.MINIMAL -> MinimalLayoutWide(model, headline, colors, cardModifier)
            WidgetStyle.MATERIAL -> MaterialLayoutWide(model, headline, colors, cardModifier)
            WidgetStyle.PROGRESS -> ProgressLayoutWide(model, headline, colors, cardModifier)
            WidgetStyle.OLED -> OledLayoutWide(model, headline, colors, cardModifier)
            WidgetStyle.GLASS -> GlassLayoutWide(model, headline, colors, cardModifier)
            WidgetStyle.ROUNDED -> RoundedLayoutWide(model, headline, colors, cardModifier)
            WidgetStyle.MODERN -> ModernLayoutWide(model, headline, colors, cardModifier)
        }
    }
}

/**
 * Resolved colors for one render pass, computed once and threaded into whichever per-style layout
 * draws the model.
 *
 * [onSurface]/[onSurfaceMuted]/[track] cannot come unconditionally from [androidx.glance.GlanceTheme]:
 * those tokens are tuned to pair with the *dynamic* Material You surface, and OLED/Glass force
 * their own background instead, with no guarantee the two color sets agree — see
 * `docs/WIDGET_ARCHITECTURE.md` §6.
 */
internal data class WidgetColors(
    val accent: ColorProvider,
    val background: ColorProvider,
    val onSurface: ColorProvider,
    val onSurfaceMuted: ColorProvider,
    val track: ColorProvider,
    /** [onSurfaceMuted] for a completed/expired event, [accent] otherwise. */
    val statusColor: ColorProvider,
)

@Composable
private fun resolveColors(model: WidgetRenderModel): WidgetColors {
    val theme = model.theme
    val hasForcedBackground = theme.backgroundColorArgb != null

    // ColorProvider(Int) is library-restricted to Glance's own modules despite compiling; the
    // sanctioned public path goes through a Compose Color first.
    val accent = theme.accentColorArgb?.let { ColorProvider(Color(it)) } ?: GlanceTheme.colors.primary
    val background = theme.backgroundColorArgb?.let { ColorProvider(Color(it)) } ?: GlanceTheme.colors.surface
    val onSurface = if (hasForcedBackground) ForcedBackgroundPalette.onSurface else GlanceTheme.colors.onSurface
    val onSurfaceMuted = when {
        hasForcedBackground -> ForcedBackgroundPalette.onSurfaceMuted
        theme.isHighContrast -> GlanceTheme.colors.onSurface
        else -> GlanceTheme.colors.onSurfaceVariant
    }
    val track = if (hasForcedBackground) ForcedBackgroundPalette.track else GlanceTheme.colors.surfaceVariant
    val statusColor = if (model.isExpired || model.isCompleted) onSurfaceMuted else accent

    return WidgetColors(accent, background, onSurface, onSurfaceMuted, track, statusColor)
}

/**
 * The two-line content hierarchy every layout draws: the biggest fact on the card, and — only
 * when it adds real information — a second line underneath it.
 *
 * @property primary the headline: a bare day count for an ordinary countdown, or the state word
 *   itself ("Tomorrow," "Completed") when there is no meaningful count to show instead
 *   ([com.countflow.core.domain.countdown.showsMeaningfulDayCount]).
 * @property unit the pluralized "day"/"days" caption belonging to [primary], only when [primary]
 *   is a bare number — never baked into the same string, so a layout can style them differently
 *   (a huge number, a small caption) or drop the unit entirely for the starkest styles.
 * @property secondary the supporting line, or null when showing one would just repeat [primary]
 *   in different words — the exact "Tomorrow / Tomorrow" redundancy this type exists to prevent.
 * @property showIdentity whether the emoji/title row should be drawn at all. False for
 *   completed/expired states, where the title has already moved into [secondary] — showing it in
 *   both places would be the same redundancy the label/count split above avoids.
 */
internal data class WidgetHeadline(
    val primary: String,
    val unit: String?,
    val secondary: String?,
    val showIdentity: Boolean,
) {
    /**
     * Whether [primary] is a bare day count rather than a word like "Completed" or "Tomorrow".
     *
     * Found on a real device, not in a preview: a status word sized for a 1-3 digit number wraps
     * mid-word with no hyphen ("Compl" / "eted") in a 2×2 cell's width — every layout needs a
     * smaller size for the word case, which this flag selects between.
     */
    val isNumeric: Boolean get() = primary.isNotEmpty() && primary.all(Char::isDigit)
}

/** Not `private`: the configuration screen's live preview reuses this exact logic rather than
 * re-deriving its own approximation of it — see `configuration/WidgetPreviewCard.kt`. */
internal fun resolveHeadline(
    model: WidgetRenderModel,
    labelText: String,
    resources: Resources,
): WidgetHeadline {
    if (model.isCompleted || model.isExpired) {
        // The status word carries the news; the event's own title is what used to sit in the
        // identity row, now demoted to supporting information underneath it.
        return WidgetHeadline(
            primary = labelText,
            unit = null,
            secondary = model.title.takeIf { model.showTitle },
            showIdentity = false,
        )
    }

    if (model.showDaysValue) {
        val unit = resources.getQuantityString(
            com.countflow.core.designsystem.R.plurals.countdown_day_unit,
            model.daysRemaining,
            model.daysRemaining,
        )
        // NextWeek's label ("Next week") says something the bare number doesn't — which calendar
        // week this falls in. InDays/DaysAgo's label ("In 7 days") is the same fact restated;
        // showing both next to a headline that already reads "7" is the redundancy this type
        // exists to prevent.
        val secondary = labelText.takeIf { model.label == CountdownLabel.NextWeek }
        return WidgetHeadline(
            primary = model.daysRemaining.toString(),
            unit = unit,
            secondary = secondary,
            showIdentity = true,
        )
    }

    // Near-term: Today, Tomorrow, Yesterday, Starting soon. A bare count would be noise or an
    // outright error here (com.countflow.core.domain.countdown.showsMeaningfulDayCount already
    // says so) — the label word itself is the headline, and a timed event's clock time is more
    // useful underneath it than repeating that same word a second time.
    val secondary = if (!model.target.isAllDay) {
        TargetDateFormatter.formatTime(resources, model.target, model.targetZone)
    } else {
        null
    }
    return WidgetHeadline(primary = labelText, unit = null, secondary = secondary, showIdentity = true)
}

/**
 * One sentence a screen reader can say once for the whole card, built from exactly what
 * [headline] and [model] say is actually being shown **at [sizeClass]**.
 *
 * [WidgetSizeClass.COMPACT] never draws [WidgetHeadline.secondary] or a percentage readout (see
 * `docs/WIDGET_SIZE_MATRIX.md`) — a screen reader must not announce a fact the layout hid for
 * space, so both are only appended outside of [WidgetSizeClass.COMPACT]. A user who cannot see
 * the percentage on screen should not hear it read aloud either.
 */
private fun widgetContentDescription(
    model: WidgetRenderModel,
    headline: WidgetHeadline,
    sizeClass: WidgetSizeClass,
): String = buildString {
    if (headline.showIdentity && model.showTitle) {
        append(model.title)
        append(". ")
    }
    append(headline.primary)
    headline.unit?.let { append(' '); append(it) }
    if (sizeClass != WidgetSizeClass.COMPACT) {
        headline.secondary?.let { append(". "); append(it) }
        if (model.showPercentageText) {
            append(". ")
            append(model.progress.percentText)
            append(" complete")
        }
    }
}

/**
 * Shown when no event is bound — either the user has not finished configuration, or something
 * else left the binding absent. Tapping it re-opens configuration, the same as a long-press
 * "edit" would once the widget is properly placed.
 */
@Composable
private fun UnconfiguredContent() {
    Column(
        modifier = GlanceModifier
            .fillMaxSize()
            .background(GlanceTheme.colors.surface)
            .widgetCornerRadius(null)
            .padding(WIDGET_PADDING)
            .semantics { contentDescription = UNCONFIGURED_DESCRIPTION }
            .clickable(actionOpenConfiguration()),
        verticalAlignment = Alignment.CenterVertically,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            text = "+",
            style = TextStyle(
                fontSize = UNCONFIGURED_ICON_SIZE,
                fontWeight = FontWeight.Medium,
                color = GlanceTheme.colors.primary,
            ),
        )
        Spacer(modifier = GlanceModifier.height(SPACING_XS))
        Text(
            text = "Tap to choose a countdown",
            style = TextStyle(
                fontSize = LABEL_SIZE,
                color = GlanceTheme.colors.onSurfaceVariant,
                textAlign = TextAlign.Center,
            ),
        )
    }
}

private const val UNCONFIGURED_DESCRIPTION = "Tap to choose a countdown to show"

/** Resolves [radiusDp], or the system's own widget corner radius when it is null (D-045). */
internal fun GlanceModifier.widgetCornerRadius(radiusDp: Int?): GlanceModifier = if (radiusDp != null) {
    cornerRadius(radiusDp.dp)
} else {
    cornerRadius(android.R.dimen.system_app_widget_background_radius)
}

/**
 * Fixed colors for a background [com.countflow.widget.engine.model.WidgetTheme] forced itself,
 * rather than one [androidx.glance.GlanceTheme] generated from wallpaper. See [resolveColors]'s
 * doc for why these cannot come from `GlanceTheme` here.
 */
internal object ForcedBackgroundPalette {
    val onSurface = ColorProvider(Color(0xFFFFFFFF))
    val onSurfaceMuted = ColorProvider(Color(0xFFC7CBD1))
    val track = ColorProvider(Color(0xFF2E3338))
}

internal val WIDGET_PADDING = 12.dp
internal val LABEL_SIZE = 13.sp
internal val PERCENT_SIZE = 12.sp
internal val UNCONFIGURED_ICON_SIZE = 22.sp
internal val SPACING_XS = 4.dp
internal val SPACING_SM = 8.dp
internal val PROGRESS_HEIGHT = 6.dp
