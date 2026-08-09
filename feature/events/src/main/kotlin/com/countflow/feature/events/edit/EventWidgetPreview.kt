package com.countflow.feature.events.edit

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.countflow.core.designsystem.format.CountdownLabelFormatter
import com.countflow.widget.engine.model.WidgetRenderModel

/**
 * A small, inline preview of what a widget showing this event would look like — for the
 * create/edit form, which needs supporting feedback while filling in a field, not a faithful
 * reproduction of the widget itself.
 *
 * Reuses [model] exactly as [com.countflow.widget.engine.provider.WidgetRenderModelProvider.preview]
 * computed it: the countdown math and the resolved theme (background, accent, corner radius) are
 * never re-derived here, only drawn. What *is* deliberately simplified, relative to the widget
 * configuration screen's own `WidgetPreviewCard`: this card shows [WidgetRenderModel.label] as a
 * single line rather than reproducing `resolveHeadline`'s primary/secondary split. That decision
 * exists to resolve a redundancy problem across seven full-size widget layouts
 * (`docs/WIDGET_DESIGN_GUIDE.md`); a form preview only ever shows one line to begin with, so there
 * is no redundancy for it to resolve, and reproducing the split here would be complexity spent on
 * a card small enough that it was never at risk of the problem in the first place.
 */
@Composable
internal fun EventWidgetPreview(model: WidgetRenderModel, modifier: Modifier = Modifier) {
    val resources = LocalResources.current
    val theme = model.theme
    val labelText = CountdownLabelFormatter.format(resources, model.label)

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

    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(PREVIEW_HEIGHT)
            .background(background, RoundedCornerShape(cornerRadius))
            .padding(12.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            if (model.showEmoji) {
                Text(model.emoji, style = MaterialTheme.typography.titleMedium)
            }
            Column(
                modifier = Modifier.weight(1f).padding(start = if (model.showEmoji) 8.dp else 0.dp),
                verticalArrangement = Arrangement.spacedBy(2.dp),
            ) {
                if (model.showTitle) {
                    Text(
                        text = model.title,
                        color = onSurface,
                        style = MaterialTheme.typography.labelLarge,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    if (model.showDaysValue) {
                        Text(
                            text = model.daysRemaining.toString(),
                            color = statusColor,
                            style = MaterialTheme.typography.titleLarge,
                        )
                    }
                    Text(text = labelText, color = statusColor, style = MaterialTheme.typography.labelMedium, maxLines = 1)
                }
                if (model.progress.isVisible) {
                    LinearProgressIndicator(
                        progress = { model.progress.fraction },
                        modifier = Modifier.fillMaxWidth().padding(top = 2.dp),
                        color = statusColor,
                        trackColor = onSurfaceMuted.copy(alpha = 0.3f),
                    )
                }
            }
        }
    }
}

private val PREVIEW_HEIGHT = 88.dp
