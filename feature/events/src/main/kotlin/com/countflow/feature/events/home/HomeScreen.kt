package com.countflow.feature.events.home

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.horizontalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.countflow.core.designsystem.format.asText
import com.countflow.core.domain.model.EventCategory
import com.countflow.core.domain.repository.EventSort
import com.countflow.feature.events.model.EventCardUiModel

/**
 * The event list — CountFlow's start destination.
 *
 * Split in two: this reads the ViewModel, the stateless overload below draws. The split is what
 * lets the layout be previewed and tested with fabricated state, and it keeps the drawing code
 * free of any way to reach a repository.
 *
 * @param onNavigateToCreateEvent invoked when the user adds an event.
 * @param onNavigateToEditEvent invoked with an event id when a row is tapped.
 * @param onNavigateToSettings invoked when the user opens settings.
 * @param modifier applied to the screen root.
 * @param viewModel supplies the state.
 */
@Composable
fun HomeScreen(
    onNavigateToCreateEvent: () -> Unit,
    onNavigateToEditEvent: (String) -> Unit,
    onNavigateToSettings: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: EventsViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    HomeScreen(
        uiState = uiState,
        onQueryChange = viewModel::onQueryChange,
        onClearQuery = viewModel::onClearQuery,
        onSortChange = viewModel::onSortChange,
        onCategoryToggle = viewModel::onCategoryToggle,
        onClearFilters = viewModel::onClearFilters,
        onEventClick = onNavigateToEditEvent,
        onNavigateToCreateEvent = onNavigateToCreateEvent,
        onNavigateToSettings = onNavigateToSettings,
        modifier = modifier,
    )
}

/** The stateless list, drawn purely from [uiState]. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun HomeScreen(
    uiState: HomeUiState,
    onQueryChange: (String) -> Unit,
    onClearQuery: () -> Unit,
    onSortChange: (EventSort) -> Unit,
    onCategoryToggle: (EventCategory) -> Unit,
    onClearFilters: () -> Unit,
    onEventClick: (String) -> Unit,
    onNavigateToCreateEvent: () -> Unit,
    onNavigateToSettings: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Scaffold(
        modifier = modifier,
        topBar = {
            TopAppBar(
                title = { Text("CountFlow") },
                actions = {
                    SortMenu(current = uiState.sort, onSortChange = onSortChange)
                    IconButton(onClick = onNavigateToSettings) {
                        Icon(Icons.Default.Settings, contentDescription = "Settings")
                    }
                },
            )
        },
        floatingActionButton = {
            FloatingActionButton(onClick = onNavigateToCreateEvent) {
                Icon(Icons.Default.Add, contentDescription = "New event")
            }
        },
    ) { innerPadding ->
        Column(modifier = Modifier.fillMaxSize().padding(innerPadding)) {
            SearchField(
                query = uiState.query,
                onQueryChange = onQueryChange,
                onClearQuery = onClearQuery,
            )
            CategoryFilterRow(
                selected = uiState.selectedCategories,
                onToggle = onCategoryToggle,
            )

            when (val empty = uiState.emptyState) {
                null -> EventList(events = uiState.events, onEventClick = onEventClick)
                else -> EmptyState(state = empty, onClearFilters = onClearFilters)
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SearchField(
    query: String,
    onQueryChange: (String) -> Unit,
    onClearQuery: () -> Unit,
) {
    OutlinedTextField(
        value = query,
        onValueChange = onQueryChange,
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
        placeholder = { Text("Search events") },
        leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
        trailingIcon = {
            if (query.isNotEmpty()) {
                IconButton(onClick = onClearQuery) {
                    Icon(Icons.Default.Clear, contentDescription = "Clear search")
                }
            }
        },
        singleLine = true,
    )
}

@Composable
private fun CategoryFilterRow(
    selected: Set<EventCategory>,
    onToggle: (EventCategory) -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState())
            .padding(horizontal = 16.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        EventCategory.entries.forEach { category ->
            FilterChip(
                selected = category in selected,
                onClick = { onToggle(category) },
                label = { Text(category.asText()) },
            )
        }
    }
}

@Composable
private fun EventList(
    events: List<EventCardUiModel>,
    onEventClick: (String) -> Unit,
) {
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 88.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        // Keyed by id so reordering after a sort change animates rows rather than rebuilding
        // every one of them.
        items(items = events, key = { it.id }) { event ->
            EventCard(event = event, onClick = { onEventClick(event.id) })
        }
    }
}

@Composable
private fun EmptyState(
    state: HomeEmptyState,
    onClearFilters: () -> Unit,
) {
    Column(
        modifier = Modifier.fillMaxSize().padding(32.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        val (title, body) = when (state) {
            HomeEmptyState.NO_EVENTS ->
                "No countdowns yet" to "Tap the plus button to create your first one."

            HomeEmptyState.NO_MATCHES ->
                "Nothing matches" to "No events match your search and filters."
        }

        Text(
            text = title,
            style = MaterialTheme.typography.titleMedium,
            textAlign = TextAlign.Center,
        )
        Text(
            text = body,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(top = 8.dp),
        )
        if (state == HomeEmptyState.NO_MATCHES) {
            TextButton(onClick = onClearFilters, modifier = Modifier.padding(top = 8.dp)) {
                Text("Clear filters")
            }
        }
    }
}

@Composable
private fun SortMenu(current: EventSort, onSortChange: (EventSort) -> Unit) {
    var expanded by remember { mutableStateOf(false) }

    IconButton(onClick = { expanded = true }) {
        Icon(Icons.Default.MoreVert, contentDescription = "Sort events")
    }
    DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
        EventSort.entries.forEach { sort ->
            DropdownMenuItem(
                text = { Text(sort.displayName) },
                onClick = {
                    onSortChange(sort)
                    expanded = false
                },
                trailingIcon = {
                    if (sort == current) Icon(Icons.Default.Check, contentDescription = null)
                },
            )
        }
    }
}

/**
 * Sort names.
 *
 * Hard-coded rather than pulled from resources, and that is a gap — see TODO.md. The countdown
 * labels and category names are localised; these four are not yet.
 */
private val EventSort.displayName: String
    get() = when (this) {
        EventSort.TARGET_DATE -> "Date"
        EventSort.TITLE -> "Title"
        EventSort.CREATED_DATE -> "Recently added"
        EventSort.CATEGORY -> "Category"
    }
