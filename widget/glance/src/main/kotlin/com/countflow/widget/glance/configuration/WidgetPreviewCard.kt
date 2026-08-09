package com.countflow.widget.glance.configuration

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
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
import com.countflow.widget.glance.resolveHeadline

/**
 * A live preview of what [model] would look like as a real widget — built with plain Compose,
 * not Glance, since [WidgetConfigurationActivity] is a normal Activity and Glance has no way to
 * render a live composition inline inside one.
 *
 * Deliberately **not** a pixel-identical reproduction of `CountdownWidgetContent`'s seven
 * layouts. It reuses the exact same content-hierarchy decision
 * ([com.countflow.widget.glance.resolveHeadline]) and the exact same resolved [WidgetRenderModel]
 * a real render would use — nothing about *what* to show is duplicated or re-derived — but *how*
 * it is drawn is a single, simplified card that varies alignment, background, corner radius, and
 * whether progress is a ring or a bar by style, rather than seven fully independent
 * compositions. Close enough to make style/toggle/color choices meaningful before saving; not a
 * substitute for `docs/SCREENSHOT_GUIDE.md`'s real on-device captures of the actual widget.
 */
@Composable
internal fun WidgetPreviewCard(model: WidgetRenderModel, modifier: Modifier = Modifier) {
    val resources = LocalResources.current
    val theme = model.theme
    val labelText = CountdownLabelFormatter.format(resources, model.label)
    val headline = resolveHeadline(model, labelText, resources)

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
    val isEditorial = theme.style == WidgetStyle.MODERN

    Box(
        modifier = modifier
            .aspectRatio(1f)
            .background(background, RoundedCornerShape(cornerRadius))
            .padding(16.dp),
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

            if (theme.style == WidgetStyle.PROGRESS && model.progress.isVisible && headline.isNumeric) {
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
                    style = MaterialTheme.typography.headlineMedium,
                    maxLines = 1,
                    textAlign = if (isEditorial) TextAlign.Start else TextAlign.Center,
                )
            }

            headline.unit?.let {
                Text(it, color = onSurfaceMuted, style = MaterialTheme.typography.labelMedium)
            }
            headline.secondary?.let {
                Text(it, color = onSurfaceMuted, style = MaterialTheme.typography.labelMedium)
            }

            if (model.progress.isVisible && theme.style != WidgetStyle.PROGRESS) {
                LinearProgressIndicator(
                    progress = { model.progress.fraction },
                    modifier = Modifier.fillMaxWidth().padding(top = 4.dp),
                    color = statusColor,
                    trackColor = onSurfaceMuted.copy(alpha = 0.3f),
                )
                if (model.showPercentageText) {
                    Text(model.progress.percentText, color = onSurfaceMuted, style = MaterialTheme.typography.labelSmall)
                }
            }
        }
    }
}
