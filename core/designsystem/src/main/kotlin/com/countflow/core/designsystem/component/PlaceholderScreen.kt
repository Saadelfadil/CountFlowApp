package com.countflow.core.designsystem.component

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp

/**
 * A labelled navigation action rendered as a button on a [PlaceholderScreen].
 *
 * @property label the button text.
 * @property onClick invoked when tapped.
 */
data class PlaceholderAction(
    val label: String,
    val onClick: () -> Unit,
)

/**
 * A themed screen scaffold showing a title, a short description of what will live here, and
 * any navigation actions that lead onward.
 *
 * Milestone 1 delivers navigation and theming only, so every destination renders through this
 * component. Each is replaced by its real screen in the milestone noted by [description].
 * The [actions] exist so the navigation graph is genuinely traversable rather than merely
 * declared — a route nothing can reach is a route nothing has tested.
 *
 * @param title the destination name, shown in the app bar.
 * @param description what this screen will do once implemented.
 * @param modifier applied to the scaffold.
 * @param onNavigateBack shown as a back arrow when non-null. Omit for top-level destinations.
 * @param actions onward navigation offered by this screen.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PlaceholderScreen(
    title: String,
    description: String,
    modifier: Modifier = Modifier,
    onNavigateBack: (() -> Unit)? = null,
    actions: List<PlaceholderAction> = emptyList(),
) {
    Scaffold(
        modifier = modifier,
        topBar = {
            TopAppBar(
                title = { Text(text = title) },
                navigationIcon = {
                    if (onNavigateBack != null) {
                        IconButton(onClick = onNavigateBack) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                                contentDescription = "Navigate up",
                            )
                        }
                    }
                },
            )
        },
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(horizontal = 32.dp),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.headlineSmall,
                color = MaterialTheme.colorScheme.onSurface,
                textAlign = TextAlign.Center,
            )
            Text(
                text = description,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(top = 8.dp),
            )

            actions.forEach { action ->
                Button(
                    onClick = action.onClick,
                    modifier = Modifier.padding(top = 12.dp),
                ) {
                    Text(text = action.label)
                }
            }
        }
    }
}
