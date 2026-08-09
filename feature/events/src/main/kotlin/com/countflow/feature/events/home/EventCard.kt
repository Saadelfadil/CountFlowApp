package com.countflow.feature.events.home

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Done
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SwipeToDismissBox
import androidx.compose.material3.SwipeToDismissBoxValue
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberSwipeToDismissBoxState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.countflow.core.designsystem.format.asText
import com.countflow.core.domain.repository.EventLifecycleFilter
import com.countflow.feature.events.model.EventCardUiModel

/**
 * One event in the list.
 *
 * Reads entirely from [EventCardUiModel] — no domain types, no clock, no arithmetic. Anything
 * this composable would need to compute has already been computed by the mapper against a single
 * shared instant.
 *
 * The descriptive content (emoji, title, category, countdown) carries one merged accessibility
 * label rather than exposing each as a separate node — TalkBack reading "🎂, Anna's birthday, 12,
 * days, 40 percent" as fragments is far worse than one sentence. That merge is scoped to the
 * descriptive row specifically, not the whole card: the overflow button next to it must stay an
 * independently focusable element with its own "More actions" label, which a card-wide
 * `clearAndSetSemantics` would have swallowed.
 *
 * Complete, archive, and delete are each reachable two ways — a swipe gesture on [tab]
 * `EventLifecycleFilter.UPCOMING` (its two natural actions), and the overflow menu, always
 * (every tab, every action, including delete, which is deliberately never a swipe target — see
 * [DeleteConfirmationDialog]). The menu is not a fallback for the swipe; it is the one way that
 * always exists, per the session brief's explicit accessibility requirement.
 *
 * @param event the row to draw.
 * @param tab which lifecycle bucket this row is being shown in — decides which swipe directions
 *   and menu actions make sense (a Completed row offers "Mark not complete", not "Mark complete").
 * @param onClick invoked when the card is tapped.
 * @param onCompletedChange invoked with the new completed value.
 * @param onArchivedChange invoked with the new archived value.
 * @param onDelete invoked once the user has confirmed deletion in [DeleteConfirmationDialog].
 * @param modifier applied to the card.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun EventCard(
    event: EventCardUiModel,
    tab: EventLifecycleFilter,
    onClick: () -> Unit,
    onCompletedChange: (Boolean) -> Unit,
    onArchivedChange: (Boolean) -> Unit,
    onDelete: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var showMenu by remember { mutableStateOf(false) }
    var showDeleteConfirm by remember { mutableStateOf(false) }

    val content: @Composable (Modifier) -> Unit = { cardModifier ->
        EventCardContent(
            event = event,
            tab = tab,
            onClick = onClick,
            showMenu = showMenu,
            onShowMenuChange = { showMenu = it },
            onCompletedChange = onCompletedChange,
            onArchivedChange = onArchivedChange,
            onDeleteRequest = { showDeleteConfirm = true },
            modifier = cardModifier,
        )
    }

    if (tab == EventLifecycleFilter.UPCOMING) {
        val dismissState = rememberSwipeToDismissBoxState(
            confirmValueChange = { value ->
                when (value) {
                    SwipeToDismissBoxValue.EndToStart -> {
                        onCompletedChange(true)
                        true
                    }
                    SwipeToDismissBoxValue.StartToEnd -> {
                        onArchivedChange(true)
                        true
                    }
                    SwipeToDismissBoxValue.Settled -> false
                }
            },
        )
        SwipeToDismissBox(
            state = dismissState,
            modifier = modifier,
            backgroundContent = { SwipeActionBackground(dismissState.dismissDirection) },
        ) {
            content(Modifier)
        }
    } else {
        content(modifier)
    }

    if (showDeleteConfirm) {
        DeleteConfirmationDialog(
            title = event.title,
            onConfirm = {
                showDeleteConfirm = false
                onDelete()
            },
            onDismiss = { showDeleteConfirm = false },
        )
    }
}

@Composable
private fun EventCardContent(
    event: EventCardUiModel,
    tab: EventLifecycleFilter,
    onClick: () -> Unit,
    showMenu: Boolean,
    onShowMenuChange: (Boolean) -> Unit,
    onCompletedChange: (Boolean) -> Unit,
    onArchivedChange: (Boolean) -> Unit,
    onDeleteRequest: () -> Unit,
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
        modifier = modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
        ),
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Row(
                    modifier = Modifier
                        .weight(1f)
                        .clearAndSetSemantics { contentDescription = spokenDescription },
                    verticalAlignment = Alignment.CenterVertically,
                ) {
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

                Box {
                    IconButton(onClick = { onShowMenuChange(true) }) {
                        Icon(
                            Icons.Filled.MoreVert,
                            contentDescription = "More actions for ${event.title}",
                        )
                    }
                    EventCardMenu(
                        tab = tab,
                        expanded = showMenu,
                        onDismiss = { onShowMenuChange(false) },
                        onCompletedChange = onCompletedChange,
                        onArchivedChange = onArchivedChange,
                        onDeleteRequest = onDeleteRequest,
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

/**
 * The overflow menu — the accessible alternative to swipe, and on [EventLifecycleFilter.COMPLETED]
 * and [EventLifecycleFilter.ARCHIVED] rows, the *only* way to act on the card at all (those two
 * tabs deliberately have no swipe gesture of their own; see [EventCard]'s own documentation).
 */
@Composable
private fun EventCardMenu(
    tab: EventLifecycleFilter,
    expanded: Boolean,
    onDismiss: () -> Unit,
    onCompletedChange: (Boolean) -> Unit,
    onArchivedChange: (Boolean) -> Unit,
    onDeleteRequest: () -> Unit,
) {
    DropdownMenu(expanded = expanded, onDismissRequest = onDismiss) {
        when (tab) {
            EventLifecycleFilter.UPCOMING -> {
                DropdownMenuItem(
                    text = { Text("Mark complete") },
                    onClick = { onDismiss(); onCompletedChange(true) },
                )
                DropdownMenuItem(
                    text = { Text("Archive") },
                    onClick = { onDismiss(); onArchivedChange(true) },
                )
            }

            EventLifecycleFilter.COMPLETED -> {
                DropdownMenuItem(
                    text = { Text("Mark not complete") },
                    onClick = { onDismiss(); onCompletedChange(false) },
                )
                DropdownMenuItem(
                    text = { Text("Archive") },
                    onClick = { onDismiss(); onArchivedChange(true) },
                )
            }

            EventLifecycleFilter.ARCHIVED -> {
                DropdownMenuItem(
                    text = { Text("Restore") },
                    onClick = { onDismiss(); onArchivedChange(false) },
                )
            }
        }
        DropdownMenuItem(
            text = { Text("Delete") },
            onClick = { onDismiss(); onDeleteRequest() },
            leadingIcon = { Icon(Icons.Filled.Delete, contentDescription = null) },
        )
    }
}

/**
 * The colored scrim revealed behind a card as it swipes, naming the action a full swipe would
 * trigger. No icon for the archive direction — `material-icons-core` (the only icon set this
 * project links, D-050) has no archive glyph, and the text label already says exactly what will
 * happen.
 */
@Composable
private fun SwipeActionBackground(direction: SwipeToDismissBoxValue) {
    val (color, alignment, label, icon) = when (direction) {
        SwipeToDismissBoxValue.EndToStart ->
            SwipeAction(MaterialTheme.colorScheme.primaryContainer, Alignment.CenterEnd, "Complete", Icons.Filled.Done)
        SwipeToDismissBoxValue.StartToEnd ->
            SwipeAction(MaterialTheme.colorScheme.secondaryContainer, Alignment.CenterStart, "Archive", null)
        SwipeToDismissBoxValue.Settled ->
            SwipeAction(Color.Transparent, Alignment.Center, "", null)
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(color, MaterialTheme.shapes.medium)
            .padding(horizontal = 20.dp),
        contentAlignment = alignment,
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            icon?.let { Icon(it, contentDescription = null) }
            if (label.isNotEmpty()) Text(label, style = MaterialTheme.typography.labelLarge)
        }
    }
}

private data class SwipeAction(
    val color: Color,
    val alignment: Alignment,
    val label: String,
    val icon: ImageVector?,
)

/**
 * "Delete is destructive, so it is confirmed, never a direct swipe target" — this dialog is the
 * entire mechanism behind that rule. Its wording states only what the app actually does: the
 * event and its reminders are deleted outright; a widget bound to it does not disappear, it falls
 * back to the unconfigured state, because [com.countflow.core.domain.repository.EventRepository.deleteEvent]'s
 * cascade removes the *binding*, not the placed widget itself.
 */
@Composable
private fun DeleteConfirmationDialog(
    title: String,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Delete \"$title\"?") },
        text = {
            Text(
                "This countdown and its reminders will be deleted. Any widgets showing it will " +
                    "return to the unconfigured state.",
            )
        },
        confirmButton = {
            TextButton(onClick = onConfirm) {
                Text("Delete", color = MaterialTheme.colorScheme.error)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        },
    )
}
