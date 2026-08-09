package com.countflow.feature.events.edit

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TimePicker
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.material3.rememberTimePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.countflow.core.designsystem.component.AccentColorPicker
import com.countflow.core.designsystem.format.asText
import com.countflow.core.domain.model.AccentColor
import com.countflow.core.domain.model.EventCategory
import com.countflow.core.domain.validation.EventField
import com.countflow.core.domain.validation.EventValidationError
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneOffset

/**
 * The create and edit form.
 *
 * One screen serves both; [EditEventUiState.isEditing] only changes the title and whether the
 * fields start populated.
 *
 * @param onNavigateBack invoked to leave the form, and automatically once a save succeeds.
 * @param modifier applied to the screen root.
 * @param viewModel supplies the state.
 */
@Composable
fun CreateEventScreen(
    onNavigateBack: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: EditEventViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    // Navigating from the composable rather than the ViewModel: the ViewModel reports that a
    // save happened, and the screen decides what that means for the back stack.
    LaunchedEffect(uiState.isSaved, uiState.notFound) {
        if (uiState.isSaved || uiState.notFound) onNavigateBack()
    }

    CreateEventScreen(
        uiState = uiState,
        onTitleChange = viewModel::onTitleChange,
        onEmojiChange = viewModel::onEmojiChange,
        onCategoryChange = viewModel::onCategoryChange,
        onDateChange = viewModel::onDateChange,
        onTimeChange = viewModel::onTimeChange,
        onAllDayChange = viewModel::onAllDayChange,
        onAccentColorChange = viewModel::onAccentColorChange,
        onSave = viewModel::onSave,
        onNavigateBack = onNavigateBack,
        modifier = modifier,
    )
}

/** The stateless form. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun CreateEventScreen(
    uiState: EditEventUiState,
    onTitleChange: (String) -> Unit,
    onEmojiChange: (String) -> Unit,
    onCategoryChange: (EventCategory) -> Unit,
    onDateChange: (LocalDate) -> Unit,
    onTimeChange: (LocalTime) -> Unit,
    onAllDayChange: (Boolean) -> Unit,
    onAccentColorChange: (AccentColor) -> Unit,
    onSave: () -> Unit,
    onNavigateBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var showDatePicker by remember { mutableStateOf(false) }
    var showTimePicker by remember { mutableStateOf(false) }

    Scaffold(
        modifier = modifier,
        topBar = {
            TopAppBar(
                title = { Text(if (uiState.isEditing) "Edit event" else "New event") },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Navigate up")
                    }
                },
                actions = {
                    TextButton(onClick = onSave, enabled = uiState.canSave) { Text("Save") }
                },
            )
        },
    ) { innerPadding ->
        if (uiState.isLoading) {
            Box(Modifier.fillMaxSize().padding(innerPadding), Alignment.Center) {
                CircularProgressIndicator()
            }
            return@Scaffold
        }

        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            uiState.previewModel?.let { model ->
                EventWidgetPreview(model = model, modifier = Modifier.fillMaxWidth())
            }

            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedTextField(
                    value = uiState.emoji,
                    onValueChange = onEmojiChange,
                    modifier = Modifier.width(88.dp),
                    label = { Text("Emoji") },
                    isError = uiState.hasError(EventField.EMOJI),
                    singleLine = true,
                )
                OutlinedTextField(
                    value = uiState.title,
                    onValueChange = onTitleChange,
                    modifier = Modifier.weight(1f),
                    label = { Text("Title") },
                    isError = uiState.hasError(EventField.TITLE),
                    singleLine = true,
                )
            }

            uiState.visibleErrors
                .filter { it.field == EventField.TITLE || it.field == EventField.EMOJI }
                .forEach { ErrorText(it) }

            Text("Category", style = MaterialTheme.typography.titleSmall)
            Row(
                modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                EventCategory.entries.forEach { category ->
                    FilterChip(
                        selected = category == uiState.category,
                        onClick = { onCategoryChange(category) },
                        label = { Text(category.asText()) },
                    )
                }
            }

            Text("Accent color", style = MaterialTheme.typography.titleSmall)
            AccentColorPicker(
                selected = uiState.accentColor,
                onSelect = onAccentColorChange,
                modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
            )

            Text("When", style = MaterialTheme.typography.titleSmall)
            OutlinedButton(
                onClick = { showDatePicker = true },
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(uiState.date?.toString() ?: "Choose a date")
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Column(Modifier.weight(1f)) {
                    Text("All day", style = MaterialTheme.typography.bodyLarge)
                    Text(
                        // Explaining the toggle rather than just labelling it: the difference
                        // only shows up when the user travels, which is too late to discover it.
                        text = if (uiState.isAllDay) {
                            "Counts down to midnight wherever you are"
                        } else {
                            "Counts down to an exact time in this time zone"
                        },
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Switch(checked = uiState.isAllDay, onCheckedChange = onAllDayChange)
            }

            if (!uiState.isAllDay) {
                OutlinedButton(
                    onClick = { showTimePicker = true },
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text(uiState.time.toString())
                }
            }

            uiState.visibleErrors
                .filter { it.field == EventField.TARGET }
                .forEach { ErrorText(it) }
        }
    }

    if (showDatePicker) {
        EventDatePickerDialog(
            initial = uiState.date,
            onDismiss = { showDatePicker = false },
            onConfirm = {
                onDateChange(it)
                showDatePicker = false
            },
        )
    }

    if (showTimePicker) {
        EventTimePickerDialog(
            initial = uiState.time,
            onDismiss = { showTimePicker = false },
            onConfirm = {
                onTimeChange(it)
                showTimePicker = false
            },
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun EventDatePickerDialog(
    initial: LocalDate?,
    onDismiss: () -> Unit,
    onConfirm: (LocalDate) -> Unit,
) {
    // Material's date picker speaks in UTC epoch millis. Converting through UTC in both
    // directions keeps the calendar date the user tapped, which is the only thing that matters
    // here — the real zone is applied when the target is built.
    val state = rememberDatePickerState(
        initialSelectedDateMillis = initial
            ?.atStartOfDay(ZoneOffset.UTC)
            ?.toInstant()
            ?.toEpochMilli(),
    )

    DatePickerDialog(
        onDismissRequest = onDismiss,
        confirmButton = {
            TextButton(
                onClick = {
                    state.selectedDateMillis?.let { millis ->
                        onConfirm(
                            Instant.ofEpochMilli(millis).atZone(ZoneOffset.UTC).toLocalDate(),
                        )
                    }
                },
            ) { Text("OK") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    ) {
        DatePicker(state = state)
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun EventTimePickerDialog(
    initial: LocalTime,
    onDismiss: () -> Unit,
    onConfirm: (LocalTime) -> Unit,
) {
    val state = rememberTimePickerState(
        initialHour = initial.hour,
        initialMinute = initial.minute,
    )

    DatePickerDialog(
        onDismissRequest = onDismiss,
        confirmButton = {
            TextButton(
                onClick = { onConfirm(LocalTime.of(state.hour, state.minute)) },
            ) { Text("OK") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    ) {
        Box(Modifier.fillMaxWidth().padding(16.dp), Alignment.Center) {
            TimePicker(state = state)
        }
    }
}

@Composable
private fun ErrorText(error: EventValidationError) {
    Text(
        text = error.message(),
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.error,
    )
}

/**
 * Turns a validation token into text.
 *
 * Inline for now rather than in string resources — the same gap as the sort names, tracked in
 * TODO.md. The countdown labels and categories are localised; these are not yet.
 */
@Composable
private fun EventValidationError.message(): String = when (this) {
    EventValidationError.BlankTitle -> "Give your countdown a title"
    is EventValidationError.TitleTooLong -> "Titles can be at most $maxLength characters"
    EventValidationError.InvalidEmoji -> "Use a single emoji, or leave this empty"
    EventValidationError.UnknownTimeZone -> "That date could not be understood"
    is EventValidationError.TargetTooFarFuture -> "Pick a date within $maxYears years"
    is EventValidationError.TargetTooFarPast -> "Pick a date within the last $maxYears years"
}

private fun EditEventUiState.hasError(field: EventField): Boolean =
    visibleErrors.any { it.field == field }
