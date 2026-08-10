package com.countflow.feature.settings

import android.content.Intent
import android.provider.Settings as AndroidSettings
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.countflow.core.domain.repository.ThemeMode

/**
 * App settings: appearance, notification status, and About.
 *
 * Deliberately small — Milestone 6's brief is explicit that this is the *essential* settings
 * surface for a v1 release, not a general-purpose preferences dashboard. Billing, backup/restore,
 * accounts, and anything else that would grow this screen belong to a later milestone.
 *
 * @param onNavigateToAbout invoked when the user opens About.
 * @param onNavigateBack invoked to leave settings.
 * @param modifier applied to the screen root.
 */
@Composable
fun SettingsScreen(
    onNavigateToAbout: () -> Unit,
    onNavigateBack: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: SettingsViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    // Notification status can change entirely outside CountFlow — the user can back out to
    // Android's own notification settings and flip it there. Re-reading on every resume (not just
    // once at construction) is what makes the row correct without requiring a restart.
    LifecycleResumeEffect(Unit) {
        viewModel.refreshNotificationStatus()
        onPauseOrDispose { }
    }

    SettingsScreen(
        uiState = uiState,
        onThemeModeChange = viewModel::onThemeModeChange,
        onDynamicColorChange = viewModel::onDynamicColorChange,
        onNavigateToAbout = onNavigateToAbout,
        onNavigateBack = onNavigateBack,
        modifier = modifier,
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SettingsScreen(
    uiState: SettingsUiState,
    onThemeModeChange: (ThemeMode) -> Unit,
    onDynamicColorChange: (Boolean) -> Unit,
    onNavigateToAbout: () -> Unit,
    onNavigateBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    var showThemeDialog by remember { mutableStateOf(false) }

    if (showThemeDialog) {
        ThemeModeDialog(
            selected = uiState.themeMode,
            onSelect = { onThemeModeChange(it); showThemeDialog = false },
            onDismiss = { showThemeDialog = false },
        )
    }

    Scaffold(
        modifier = modifier,
        topBar = {
            TopAppBar(
                title = { Text("Settings") },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Navigate up")
                    }
                },
            )
        },
    ) { innerPadding ->
        Column(modifier = Modifier.padding(innerPadding)) {
            SectionHeader("Appearance")
            ListItem(
                headlineContent = { Text("Theme") },
                supportingContent = { Text(uiState.themeMode.label()) },
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable(onClickLabel = "Change theme") { showThemeDialog = true },
            )
            ListItem(
                headlineContent = { Text("Dynamic color") },
                supportingContent = { Text("Match your wallpaper's colors") },
                trailingContent = {
                    Switch(
                        checked = uiState.useDynamicColor,
                        onCheckedChange = onDynamicColorChange,
                    )
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable(role = Role.Switch) { onDynamicColorChange(!uiState.useDynamicColor) },
            )

            HorizontalDivider()
            SectionHeader("Notifications")
            ListItem(
                headlineContent = { Text("Event reminders") },
                supportingContent = {
                    Text(if (uiState.notificationsAllowed) "Allowed" else "Not allowed")
                },
            )
            ListItem(
                headlineContent = { Text("Manage notifications") },
                supportingContent = { Text("Open CountFlow's Android notification settings") },
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable(onClickLabel = "Manage notifications") {
                        context.startActivity(notificationSettingsIntent(context.packageName))
                    },
            )

            HorizontalDivider()
            SectionHeader("About")
            ListItem(
                headlineContent = { Text("About CountFlow") },
                supportingContent = { Text("Version ${uiState.appVersionLabel}") },
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable(onClickLabel = "About CountFlow", onClick = onNavigateToAbout),
            )
        }
    }
}

@Composable
private fun ThemeModeDialog(
    selected: ThemeMode,
    onSelect: (ThemeMode) -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Theme") },
        text = {
            Column(Modifier.selectableGroup()) {
                ThemeMode.entries.forEach { mode ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .selectable(
                                selected = mode == selected,
                                onClick = { onSelect(mode) },
                                role = Role.RadioButton,
                            )
                            .padding(vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        RadioButton(selected = mode == selected, onClick = null)
                        Text(text = mode.label(), modifier = Modifier.padding(start = 16.dp))
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text("Close") }
        },
    )
}

@Composable
private fun SectionHeader(title: String) {
    Text(
        text = title,
        style = MaterialTheme.typography.labelLarge,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(start = 16.dp, top = 16.dp, bottom = 4.dp),
    )
}

private fun ThemeMode.label(): String = when (this) {
    ThemeMode.SYSTEM -> "System default"
    ThemeMode.LIGHT -> "Light"
    ThemeMode.DARK -> "Dark"
}

private fun notificationSettingsIntent(packageName: String): Intent =
    Intent(AndroidSettings.ACTION_APP_NOTIFICATION_SETTINGS)
        .putExtra(AndroidSettings.EXTRA_APP_PACKAGE, packageName)
