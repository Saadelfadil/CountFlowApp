package com.countflow.widget.glance.configuration

import android.appwidget.AppWidgetManager
import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.lifecycleScope
import com.countflow.core.designsystem.theme.CountFlowTheme
import com.countflow.core.domain.model.AppWidgetId
import com.countflow.core.domain.model.Event
import com.countflow.core.domain.model.EventId
import com.countflow.widget.glance.CountdownGlanceWidget
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch

/**
 * Lets the user choose which event a newly placed widget shows, or re-point an existing one.
 *
 * Lifecycle only, deliberately unstyled beyond the app's own theme — this milestone's brief is
 * explicit that visual polish is out of scope here.
 *
 * ### How "no orphan bindings" is guaranteed
 *
 * [setResult] is set to `RESULT_CANCELED` in [onCreate], before any UI is shown and before
 * [WidgetConfigurationViewModel] has written anything. If the user leaves without picking an
 * event — home button, back gesture, task switch — that default stands, and the widget host
 * removes the just-placed widget on its own. The ViewModel only writes a binding in direct
 * response to a selection, so there is no window in which a binding exists without the widget
 * being confirmed.
 *
 * `RESULT_OK` and the confirming extra are set only from [onEventBound], reached once the
 * ViewModel reports the write completed.
 */
@AndroidEntryPoint
class WidgetConfigurationActivity : ComponentActivity() {

    private val appWidgetId: Int
        get() = intent?.getIntExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, AppWidgetId.INVALID)
            ?: AppWidgetId.INVALID

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Default result. See the class doc — this single line is most of what "no orphan
        // bindings" means in practice.
        setResult(RESULT_CANCELED)

        if (appWidgetId == AppWidgetId.INVALID) {
            finish()
            return
        }

        setContent {
            CountFlowTheme {
                val viewModel: WidgetConfigurationViewModel = hiltViewModel()
                val uiState by viewModel.uiState.collectAsStateWithLifecycle()

                LaunchedEffect(Unit) { viewModel.load(AppWidgetId(appWidgetId)) }
                LaunchedEffect(uiState.isSaved) { if (uiState.isSaved) onEventBound() }

                WidgetConfigurationContent(
                    uiState = uiState,
                    onEventSelected = viewModel::onEventSelected,
                )
            }
        }
    }

    /**
     * Confirms the widget, tries to redraw it immediately, and closes configuration.
     *
     * The binding write already succeeded by the time this runs — that is what set
     * `uiState.isSaved` and triggered this call. `RESULT_OK` and `finish()` reflect that fact
     * and must not depend on what follows: forcing an immediate redraw is an optimisation, not
     * a precondition. Without a real widget host behind this `appWidgetId` — unavoidable when
     * this activity is launched directly for testing rather than through a genuine placement —
     * [androidx.glance.appwidget.GlanceAppWidgetManager.getGlanceIdBy] throws, and that must
     * not be able to strand a completed configuration mid-flow. If the immediate redraw is
     * skipped, [com.countflow.widget.glance.refresh.GlanceWidgetRefreshScheduler]'s live
     * observation still catches up shortly after, since the write it depends on already
     * happened.
     */
    private fun onEventBound() {
        val resultValue = Intent().putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, appWidgetId)
        setResult(RESULT_OK, resultValue)

        lifecycleScope.launch {
            runCatching {
                val glanceId = androidx.glance.appwidget.GlanceAppWidgetManager(applicationContext)
                    .getGlanceIdBy(appWidgetId)
                CountdownGlanceWidget().update(context = applicationContext, id = glanceId)
            }
            finish()
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun WidgetConfigurationContent(
    uiState: WidgetConfigurationUiState,
    onEventSelected: (EventId) -> Unit,
) {
    Scaffold(
        topBar = { TopAppBar(title = { Text("Choose an event") }) },
    ) { innerPadding ->
        when {
            uiState.isLoading -> Box(
                modifier = Modifier.fillMaxSize().padding(innerPadding),
                contentAlignment = Alignment.Center,
            ) {
                CircularProgressIndicator()
            }

            uiState.events.isEmpty() -> Box(
                modifier = Modifier.fillMaxSize().padding(innerPadding).padding(32.dp),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = "Create an event in CountFlow first, then add this widget again.",
                    style = MaterialTheme.typography.bodyLarge,
                )
            }

            else -> LazyColumn(
                modifier = Modifier.fillMaxSize().padding(innerPadding),
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                items(items = uiState.events, key = { it.id.value }) { event ->
                    EventPickerRow(
                        event = event,
                        isCurrent = event.id == uiState.currentEventId,
                        isSaving = uiState.isSaving,
                        onClick = { onEventSelected(event.id) },
                    )
                }
            }
        }
    }
}

@Composable
private fun EventPickerRow(
    event: Event,
    isCurrent: Boolean,
    isSaving: Boolean,
    onClick: () -> Unit,
) {
    Card(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth(),
        enabled = !isSaving,
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(text = event.title, style = MaterialTheme.typography.titleMedium)
            if (isCurrent) {
                Text(
                    text = "Currently shown on this widget",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.primary,
                )
            }
        }
    }
}
