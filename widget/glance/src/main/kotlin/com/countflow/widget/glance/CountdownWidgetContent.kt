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
import androidx.glance.layout.padding
import androidx.glance.layout.size
import androidx.glance.text.FontWeight
import androidx.glance.text.Text
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
 */
@Composable
internal fun CountdownWidgetContent(model: WidgetRenderModel?) {
    if (model == null) {
        UnconfiguredContent()
        return
    }

    val theme = model.theme
    // ColorProvider(Int) is library-restricted to Glance's own modules despite compiling; the
    // sanctioned public path goes through a Compose Color first.
    val accent = theme.accentColorArgb?.let { ColorProvider(Color(it)) } ?: GlanceTheme.colors.primary
    val background = theme.backgroundColorArgb?.let { ColorProvider(Color(it)) } ?: GlanceTheme.colors.surface

    Column(
        modifier = GlanceModifier
            .fillMaxSize()
            .background(background)
            .cornerRadius(theme.cornerRadiusDp.dp)
            .padding(WIDGET_PADDING)
            .clickable(actionOpenApp()),
        verticalAlignment = Alignment.CenterVertically,
        horizontalAlignment = Alignment.Start,
    ) {
        if (model.showEmoji || model.showTitle) {
            Row(modifier = GlanceModifier.fillMaxWidth()) {
                if (model.showEmoji) {
                    Text(text = model.emoji, style = TextStyle(fontSize = EMOJI_SIZE))
                }
                if (model.showTitle) {
                    Text(
                        text = model.title,
                        style = TextStyle(
                            fontSize = TITLE_SIZE,
                            fontWeight = FontWeight.Medium,
                            color = GlanceTheme.colors.onSurface,
                        ),
                        maxLines = 1,
                        modifier = GlanceModifier.padding(start = if (model.showEmoji) 6.dp else 0.dp),
                    )
                }
            }
            Spacer(modifier = GlanceModifier.size(4.dp))
        }

        if (model.showDaysValue) {
            Text(
                text = model.daysRemaining.toString(),
                style = TextStyle(fontSize = DAYS_SIZE, fontWeight = FontWeight.Bold, color = accent),
            )
        }

        Text(
            text = CountdownLabelFormatter.format(LocalContext.current.resources, model.label),
            style = TextStyle(
                fontSize = LABEL_SIZE,
                color = if (model.isExpired || model.isCompleted) {
                    GlanceTheme.colors.onSurfaceVariant
                } else {
                    accent
                },
            ),
        )

        if (model.progress.isVisible) {
            Spacer(modifier = GlanceModifier.size(6.dp))
            LinearProgressIndicator(
                progress = model.progress.fraction,
                modifier = GlanceModifier.fillMaxWidth(),
                color = accent,
                backgroundColor = GlanceTheme.colors.surfaceVariant,
            )
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
            .cornerRadius(16.dp)
            .padding(WIDGET_PADDING)
            .clickable(actionOpenConfiguration()),
        verticalAlignment = Alignment.CenterVertically,
        horizontalAlignment = Alignment.Start,
    ) {
        Text(
            text = "Tap to set up",
            style = TextStyle(fontSize = LABEL_SIZE, color = GlanceTheme.colors.onSurface),
        )
    }
}

private val WIDGET_PADDING = 12.dp
private val EMOJI_SIZE = 20.sp
private val TITLE_SIZE = 14.sp
private val DAYS_SIZE = 32.sp
private val LABEL_SIZE = 13.sp
