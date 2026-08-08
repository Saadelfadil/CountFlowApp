package com.countflow.feature.events.home

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.countflow.core.designsystem.format.asText
import com.countflow.feature.events.model.EventCardUiModel

/**
 * One event in the list.
 *
 * Reads entirely from [EventCardUiModel] — no domain types, no clock, no arithmetic. Anything
 * this composable would need to compute has already been computed by the mapper against a single
 * shared instant.
 *
 * The whole card carries one accessibility label rather than exposing the emoji, title, number,
 * unit, and percentage as five separate nodes. TalkBack reading "🎂, Anna's birthday, 12, days,
 * 40 percent" as fragments is far worse than one sentence.
 *
 * @param event the row to draw.
 * @param onClick invoked when the card is tapped.
 * @param modifier applied to the card.
 */
@Composable
internal fun EventCard(
    event: EventCardUiModel,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val labelText = event.label.asText()
    val categoryText = event.category.asText()
    val accent = event.accentArgb?.let(::Color) ?: MaterialTheme.colorScheme.primary

    val spokenDescription = buildString {
        append(event.title)
        append(", ")
        append(labelText)
        if (event.showProgress) {
            append(", ")
            append(event.progressPercent)
            append("%")
        }
        append(", ")
        append(categoryText)
    }

    Card(
        onClick = onClick,
        modifier = modifier
            .fillMaxWidth()
            .clearAndSetSemantics { contentDescription = spokenDescription },
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
        ),
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = event.emoji,
                    style = MaterialTheme.typography.headlineSmall,
                    modifier = Modifier.size(32.dp),
                )
                Column(modifier = Modifier.weight(1f).padding(start = 12.dp)) {
                    Text(
                        text = event.title,
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onSurface,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Text(
                        text = categoryText,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Column(horizontalAlignment = Alignment.End) {
                    if (event.showDaysValue) {
                        Text(
                            text = event.daysValue.toString(),
                            style = MaterialTheme.typography.headlineMedium,
                            color = accent,
                        )
                    }
                    Text(
                        text = labelText,
                        style = MaterialTheme.typography.labelMedium,
                        color = if (event.isPast) {
                            MaterialTheme.colorScheme.onSurfaceVariant
                        } else {
                            accent
                        },
                    )
                }
            }

            if (event.showProgress) {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(top = 12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    LinearProgressIndicator(
                        progress = { event.progress },
                        modifier = Modifier.weight(1f),
                        color = accent,
                    )
                    Text(
                        text = "${event.progressPercent}%",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
    }
}
