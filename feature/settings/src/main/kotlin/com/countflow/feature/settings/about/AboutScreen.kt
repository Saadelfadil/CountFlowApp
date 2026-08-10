package com.countflow.feature.settings.about

import android.content.Intent
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemColors
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.core.net.toUri
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle

/**
 * About and legal information.
 *
 * @param onNavigateBack invoked to leave the screen.
 * @param modifier applied to the screen root.
 */
@Composable
fun AboutScreen(
    onNavigateBack: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: AboutViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    AboutScreen(uiState = uiState, onNavigateBack = onNavigateBack, modifier = modifier)
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AboutScreen(
    uiState: AboutUiState,
    onNavigateBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val privacyPolicyUrl = uiState.privacyPolicyUrl

    Scaffold(
        modifier = modifier,
        topBar = {
            TopAppBar(
                title = { Text("About") },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Navigate up")
                    }
                },
            )
        },
    ) { innerPadding ->
        Column(modifier = Modifier.padding(innerPadding)) {
            ListItem(
                headlineContent = { Text("CountFlow") },
                supportingContent = { Text("Version ${uiState.appVersionLabel}") },
            )

            ListItem(
                headlineContent = { Text("Privacy Policy") },
                supportingContent = { if (privacyPolicyUrl == null) Text("Not yet available") },
                colors = if (privacyPolicyUrl == null) disabledListItemColors() else ListItemDefaults.colors(),
                modifier = Modifier
                    .fillMaxWidth()
                    .let { base ->
                        if (privacyPolicyUrl != null) {
                            base.clickable(onClickLabel = "Open Privacy Policy") {
                                context.startActivity(Intent(Intent.ACTION_VIEW, privacyPolicyUrl.toUri()))
                            }
                        } else {
                            base
                        }
                    },
            )

            // No lightweight, dependency-free way to enumerate this project's OSS licenses exists
            // yet — see TODO.md's release-preparation section. Shown, not hidden, so the row's
            // eventual arrival doesn't require a layout change, exactly as the Privacy Policy row
            // above already does for its own not-yet-available state.
            ListItem(
                headlineContent = { Text("Open-source licenses") },
                supportingContent = { Text("Coming soon") },
                colors = disabledListItemColors(),
            )
        }
    }
}

@Composable
private fun disabledListItemColors(): ListItemColors {
    val disabled = LocalContentColor.current.copy(alpha = 0.38f)
    return ListItemDefaults.colors(headlineColor = disabled, supportingColor = disabled)
}
