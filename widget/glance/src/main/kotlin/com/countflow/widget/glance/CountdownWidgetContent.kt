package com.countflow.widget.glance

import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.glance.GlanceModifier
import androidx.glance.GlanceTheme
import androidx.glance.LocalContext
import androidx.glance.action.clickable
import androidx.glance.appwidget.LinearProgressIndicator
import androidx.glance.appwidget.cornerRadius
import androidx.glance.background
import androidx.glance.layout.Alignment
import androidx.glance.layout.Column
import androidx.glance.layout.Row
import androidx.glance.layout.Spacer
import androidx.glance.layout.fillMaxSize
import androidx.glance.layout.fillMaxWidth
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
import com.countflow.widget.engine.model.WidgetRenderModel
import com.countflow.widget.glance.action.actionOpenApp
import com.countflow.widget.glance.action.actionOpenConfiguration

/**
 * Draws a [WidgetRenderModel]. Nothing else — no repository, no clock, no countdown arithmetic.
 * This is "widgets should only render" made literal: every value this function reads was
 * already decided upstream, in `:widget:engine`.
 *
 * A top-level function rather than a method on [CountdownGlanceWidget], so a test can call it
 * directly with a fabricated model — the same stateful-loader/stateless-renderer split used
 * throughout the app's own screens (`HomeScreen`, `CreateEventScreen`).
 *
 * Deliberately minimal for this milestone: one background, one accent colour, a progress bar.
 * Per-style layout differences (the seven named themes' distinct *shapes*, not just colours) are
 * Milestone 5's job, once more than one widget size exists to differentiate them across.
 *
 * ### Colour resolution
 *
 * [androidx.glance.GlanceTheme]'s `onSurface`/`onSurfaceVariant`/`surfaceVariant` are tuned to
 * pair with the *dynamic* Material You surface. [com.countflow.widget.engine.model.WidgetTheme]
 * sometimes forces its own background instead — true black for OLED (burn-in prevention, not an
 * aesthetic choice a dynamic tone could satisfy) and a translucent dark surface for Glass — and
 * nothing guarantees those two colour sets agree. Whenever the theme forces a background, text
 * and the progress track are pulled from [ForcedBackgroundPalette] instead of `GlanceTheme`, so
 * a dark-forced widget cannot end up with washed-out text a bright dynamic scheme would have
 * produced. [com.countflow.widget.engine.model.WidgetTheme.isHighContrast] is applied the same
 * way for themes that keep the dynamic background but still ask for a stronger pass: it skips
 * the muted `onSurfaceVariant` tone in favour of full-emphasis `onSurface`.
 */
@Composable
internal fun CountdownWidgetContent(model: WidgetRenderModel?) {
    if (model == null) {
        UnconfiguredContent()
        return
    }

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
    val progressTrack = if (hasForcedBackground) ForcedBackgroundPalette.track else GlanceTheme.colors.surfaceVariant

    val labelText = CountdownLabelFormatter.format(LocalContext.current.resources, model.label)
    val labelColor = if (model.isExpired || model.isCompleted) onSurfaceMuted else accent

    Column(
        modifier = GlanceModifier
            .fillMaxSize()
            .background(background)
            .cornerRadius(theme.cornerRadiusDp.dp)
            .padding(WIDGET_PADDING)
            // One description for the whole tappable card, not five separate text nodes — the
            // same reasoning `EventCard` applies with `clearAndSetSemantics` in the app's own
            // list, so a screen reader announces "Trip to Kyoto. In 12 days. 40% complete." once,
            // rather than reading the emoji, title, number, and label as unrelated fragments.
            .semantics { contentDescription = widgetContentDescription(model, labelText) }
            .clickable(actionOpenApp()),
        verticalAlignment = Alignment.CenterVertically,
        horizontalAlignment = Alignment.Start,
    ) {
        if (model.showEmoji || model.showTitle) {
            Row(modifier = GlanceModifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                if (model.showEmoji) {
                    Text(text = model.emoji, style = TextStyle(fontSize = EMOJI_SIZE))
                }
                if (model.showTitle) {
                    Text(
                        text = model.title,
                        style = TextStyle(
                            fontSize = TITLE_SIZE,
                            fontWeight = FontWeight.Medium,
                            color = onSurface,
                        ),
                        maxLines = 1,
                        modifier = GlanceModifier
                            .defaultWeight()
                            .padding(start = if (model.showEmoji) 6.dp else 0.dp),
                    )
                }
            }
            Spacer(modifier = GlanceModifier.height(SPACING_XS))
        }

        if (model.showDaysValue) {
            Text(
                text = model.daysRemaining.toString(),
                style = TextStyle(fontSize = DAYS_SIZE, fontWeight = FontWeight.Bold, color = accent),
            )
        }

        Text(
            text = labelText,
            style = TextStyle(fontSize = LABEL_SIZE, color = labelColor),
        )

        if (model.progress.isVisible) {
            Spacer(modifier = GlanceModifier.height(SPACING_SM))
            Row(modifier = GlanceModifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                LinearProgressIndicator(
                    progress = model.progress.fraction,
                    modifier = GlanceModifier.defaultWeight().height(PROGRESS_HEIGHT),
                    color = accent,
                    backgroundColor = progressTrack,
                )
                if (model.showPercentageText) {
                    Text(
                        text = model.progress.percentText,
                        style = TextStyle(fontSize = PERCENT_SIZE, color = onSurfaceMuted),
                        modifier = GlanceModifier.padding(start = 6.dp),
                    )
                }
            }
        }
    }
}

/**
 * One sentence a screen reader can say once for the whole card, built from exactly the fields
 * that are actually visible — hidden elements (`showTitle = false`, no progress) are left out
 * rather than announced despite not being drawn.
 */
private fun widgetContentDescription(model: WidgetRenderModel, labelText: String): String = buildString {
    if (model.showTitle) {
        append(model.title)
        append(". ")
    }
    append(labelText)
    if (model.showPercentageText) {
        append(". ")
        append(model.progress.percentText)
        append(" complete")
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
            .cornerRadius(16.dp)
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

/**
 * Fixed colours for a background [com.countflow.widget.engine.model.WidgetTheme] forced itself,
 * rather than one [androidx.glance.GlanceTheme] generated from wallpaper. See the class doc on
 * [CountdownWidgetContent] for why these cannot come from `GlanceTheme` here.
 */
private object ForcedBackgroundPalette {
    val onSurface = ColorProvider(Color(0xFFFFFFFF))
    val onSurfaceMuted = ColorProvider(Color(0xFFC7CBD1))
    val track = ColorProvider(Color(0xFF2E3338))
}

private val WIDGET_PADDING = 12.dp
private val EMOJI_SIZE = 20.sp
private val TITLE_SIZE = 14.sp
private val DAYS_SIZE = 34.sp
private val LABEL_SIZE = 13.sp
private val PERCENT_SIZE = 12.sp
private val UNCONFIGURED_ICON_SIZE = 22.sp
private val SPACING_XS = 4.dp
private val SPACING_SM = 8.dp
private val PROGRESS_HEIGHT = 6.dp
