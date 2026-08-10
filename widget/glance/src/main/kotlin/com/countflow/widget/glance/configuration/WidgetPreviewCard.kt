package com.countflow.widget.glance.configuration

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.countflow.core.designsystem.format.CountdownLabelFormatter
import com.countflow.core.domain.model.WidgetStyle
import com.countflow.widget.engine.model.WidgetRenderModel
import com.countflow.widget.glance.WidgetSizeClass
import com.countflow.widget.glance.resolveHeadline

/**
 * A live preview of what [model] would look like as a real widget — built with plain Compose,
 * not Glance, since [WidgetConfigurationActivity] is a normal Activity and Glance has no way to
 * render a live composition inline inside one.
 *
 * Deliberately **not** a pixel-identical reproduction of `CountdownWidgetContent`'s per-style,
 * per-size layouts. It reuses the exact same content-hierarchy decision
 * ([com.countflow.widget.glance.resolveHeadline]) and the exact same resolved [WidgetRenderModel]
 * a real render would use — nothing about *what* to show is duplicated or re-derived — but *how*
 * it is drawn is a single, simplified card that varies alignment, background, corner radius, and
 * whether progress is a ring or a bar by style, rather than 21 fully independent compositions.
 * Close enough to make style/toggle/color choices meaningful before saving; not a substitute for
 * `docs/RESPONSIVE_WIDGET_REVIEW.md`'s real on-device captures of the actual widget.
 *
 * [sizeClass] (Session 10) shapes the card to the widget's real current footprint — read from
 * `AppWidgetManager`'s own options for the placed widget being reconfigured
 * (`WidgetConfigurationActivity.onCreate`), defaulting to [WidgetSizeClass.STANDARD] for a fresh
 * placement or a direct test launch with no real host behind it yet. It also hides exactly the
 * fields the real [WidgetSizeClass.COMPACT] layouts hide (`docs/WIDGET_SIZE_MATRIX.md`) — showing
 * a percentage or secondary line here that the real 2×1 widget would never draw would make this
 * preview actively misleading rather than merely approximate.
 */
@Composable
internal fun WidgetPreviewCard(model: WidgetRenderModel, sizeClass: WidgetSizeClass, modifier: Modifier = Modifier) {
    val resources = LocalResources.current
    val theme = model.theme
    val labelText = CountdownLabelFormatter.format(resources, model.label)
    val headline = resolveHeadline(model, labelText, resources)
    val isCompact = sizeClass == WidgetSizeClass.COMPACT

    val background = theme.backgroundColorArgb?.let { Color(it) } ?: MaterialTheme.colorScheme.surfaceVariant
    val accent = theme.accentColorArgb?.let { Color(it) } ?: MaterialTheme.colorScheme.primary
    val onSurface = if (theme.backgroundColorArgb != null) Color.White else MaterialTheme.colorScheme.onSurface
    val onSurfaceMuted = if (theme.backgroundColorArgb != null) {
        Color(0xFFC7CBD1)
    } else {
        MaterialTheme.colorScheme.onSurfaceVariant
    }
    val statusColor = if (model.isCompleted || model.isExpired) onSurfaceMuted else accent
    val cornerRadius = (theme.cornerRadiusDp ?: 16).dp
    val isEditorial = theme.style == WidgetStyle.MODERN && !isCompact

    // BoxWithConstraints, not a fixed aspectRatio() on the drawn card directly: found on a
    // Samsung Galaxy A55 that the 4×2 (WIDE) preview's own aspect ratio gives its content column
    // less vertical room than 2×2's does for the identical stack of lines (title, headline, unit,
    // secondary, progress bar, percentage) — the percentage text overflowed the card and was cut
    // off. The real Glance widget does not have this problem because its actual WIDE layouts are
    // genuinely different compositions, not a scaled copy (docs/WIDGET_SIZE_MATRIX.md); this
    // preview deliberately stays a single simplified card rather than reproducing that (see this
    // function's own class doc), so instead of guessing a smaller WIDE-specific spacing that might
    // still not fit every style/title combination, `heightIn(min = ...)` keeps the aspect ratio as
    // a *minimum* — identical pixel result for every size whose content already fits it (2×1, 2×2,
    // and most 4×2 content), and grows only the cases that genuinely need more room, which
    // guarantees nothing is ever clipped rather than narrowing the margin for error. See
    // KNOWN_ISSUES.md BUG-R017.
    BoxWithConstraints(modifier = modifier) {
        val minHeight = maxWidth / sizeClass.previewAspectRatio()
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = minHeight)
                .background(background, RoundedCornerShape(cornerRadius))
                .padding(if (isCompact) 10.dp else 16.dp),
            contentAlignment = if (isEditorial) Alignment.TopStart else Alignment.Center,
        ) {
            Column(
                horizontalAlignment = if (isEditorial) Alignment.Start else Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                if (headline.showIdentity && (model.showEmoji || model.showTitle)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        if (model.showEmoji) Text(model.emoji, fontSize = with(MaterialTheme.typography.titleMedium) { fontSize })
                        if (model.showTitle) {
                            Text(
                                text = model.title,
                                color = onSurface,
                                style = MaterialTheme.typography.titleSmall,
                                maxLines = 1,
                                modifier = Modifier.padding(start = if (model.showEmoji) 4.dp else 0.dp),
                            )
                        }
                    }
                }

                if (!isCompact && theme.style == WidgetStyle.PROGRESS && model.progress.isVisible && headline.isNumeric) {
                    Box(modifier = Modifier.size(72.dp), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator(
                            progress = { model.progress.fraction },
                            modifier = Modifier.size(72.dp),
                            color = accent,
                            trackColor = onSurfaceMuted.copy(alpha = 0.3f),
                            strokeWidth = 6.dp,
                        )
                        Text(headline.primary, color = statusColor, style = MaterialTheme.typography.titleLarge)
                    }
                } else {
                    Text(
                        text = headline.primary,
                        color = statusColor,
                        style = if (isCompact) MaterialTheme.typography.titleLarge else MaterialTheme.typography.headlineMedium,
                        maxLines = 1,
                        textAlign = if (isEditorial) TextAlign.Start else TextAlign.Center,
                    )
                }

                headline.unit?.let {
                    Text(it, color = onSurfaceMuted, style = MaterialTheme.typography.labelMedium, maxLines = 1)
                }
                // COMPACT never draws a secondary line, a progress indicator, or a percentage —
                // see docs/WIDGET_SIZE_MATRIX.md. Showing one here would preview a widget that
                // cannot actually be placed.
                if (!isCompact) {
                    headline.secondary?.let {
                        Text(it, color = onSurfaceMuted, style = MaterialTheme.typography.labelMedium, maxLines = 1)
                    }

                    if (model.progress.isVisible && theme.style != WidgetStyle.PROGRESS) {
                        LinearProgressIndicator(
                            progress = { model.progress.fraction },
                            modifier = Modifier.fillMaxWidth().padding(top = 4.dp),
                            color = statusColor,
                            trackColor = onSurfaceMuted.copy(alpha = 0.3f),
                        )
                        if (model.showPercentageText) {
                            Text(model.progress.percentText, color = onSurfaceMuted, style = MaterialTheme.typography.labelSmall, maxLines = 1)
                        }
                    }
                }
            }
        }
    }
}

/**
 * The card's width:height ratio for [this] size class, from real on-device measurements
 * (`WidgetSizeClass.kt`'s own doc has the full account) rather than Android's `dp = 70×cells − 30`
 * formula, which this session confirmed does not match what a real launcher's grid actually
 * renders at — 2×1 measured 172:104dp, 2×2 measured 172:224dp (taller than wide, not square).
 * 4×2's width is reasoned, not measured, same as [WIDE_MIN_WIDTH_DP]. `docs/WIDGET_SIZE_MATRIX.md`
 * documents the same numbers for the real widget; this is the one place they are restated for a
 * plain-Compose ratio calculation, which wants a ratio rather than two dimensions.
 *
 * Used as a *minimum* height (`heightIn(min = maxWidth / this)`), not a hard `aspectRatio()`, so
 * the card can grow taller when a size's real content needs more room than its ratio alone would
 * give — see [WidgetPreviewCard]'s own use of it, and KNOWN_ISSUES.md BUG-R017.
 */
private fun WidgetSizeClass.previewAspectRatio(): Float = when (this) {
    WidgetSizeClass.COMPACT -> 172f / 104f
    WidgetSizeClass.STANDARD -> 172f / 224f
    WidgetSizeClass.WIDE -> 320f / 224f
}
