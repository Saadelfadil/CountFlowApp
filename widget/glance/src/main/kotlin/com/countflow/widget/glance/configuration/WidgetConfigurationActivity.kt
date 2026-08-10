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
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.lifecycleScope
import com.countflow.core.designsystem.component.AccentColorPicker
import com.countflow.core.designsystem.theme.CountFlowTheme
import com.countflow.core.domain.model.AccentColor
import com.countflow.core.domain.model.AppWidgetId
import com.countflow.core.domain.model.Event
import com.countflow.core.domain.model.EventId
import com.countflow.core.domain.model.ProgressStyle
import com.countflow.core.domain.model.WidgetStyle
import com.countflow.widget.glance.CountdownGlanceWidget
import com.countflow.widget.glance.WidgetSizeClass
import com.countflow.widget.glance.classifyWidgetSize
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch

/**
 * Lets the user choose which event a newly placed widget shows, then customize how it looks, or
 * re-point and re-customize an existing one.
 *
 * Two steps in one screen, not two screens: [WidgetConfigurationUiState.isCustomizing] switches
 * between them. Both share the same "nothing is written until confirm" rule described on
 * [WidgetConfigurationViewModel] — the whole reason a live preview could be added safely in
 * Session 9 without touching Milestone 4's no-orphan-bindings guarantee.
 *
 * ### How "no orphan bindings" is guaranteed
 *
 * [setResult] is set to `RESULT_CANCELED` in [onCreate], before any UI is shown and before
 * [WidgetConfigurationViewModel] has written anything. Every interaction in both steps —
 * selecting an event, changing a style, flipping a toggle — only updates in-memory state; the
 * database is touched exactly once, in [WidgetConfigurationViewModel.onConfirm]. If the user
 * leaves at any point before that, the default `RESULT_CANCELED` stands, and the widget host
 * removes the just-placed widget on its own.
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

        val sizeClass = currentWidgetSizeClass()

        setContent {
            CountFlowTheme {
                val viewModel: WidgetConfigurationViewModel = hiltViewModel()
                val uiState by viewModel.uiState.collectAsStateWithLifecycle()

                LaunchedEffect(Unit) { viewModel.load(AppWidgetId(appWidgetId)) }
                LaunchedEffect(uiState.isSaved) { if (uiState.isSaved) onEventBound() }

                WidgetConfigurationContent(
                    uiState = uiState,
                    sizeClass = sizeClass,
                    onEventSelected = viewModel::onEventSelected,
                    onChangeEvent = viewModel::onChangeEvent,
                    onWidgetStyleChange = viewModel::onWidgetStyleChange,
                    onProgressStyleChange = viewModel::onProgressStyleChange,
                    onShowTitleChange = viewModel::onShowTitleChange,
                    onShowEmojiChange = viewModel::onShowEmojiChange,
                    onShowTargetDateChange = viewModel::onShowTargetDateChange,
                    onShowPercentageChange = viewModel::onShowPercentageChange,
                    onAccentColorChange = viewModel::onAccentColorChange,
                    onConfirm = viewModel::onConfirm,
                    onNavigateBack = { finish() },
                )
            }
        }
    }

    /**
     * The real footprint this specific placed widget currently occupies, read directly from the
     * host via `AppWidgetManager` — not assumed, not defaulted to 2×2 and hoped correct (BUG-R009
     * happened exactly that way: an assumed size silently disagreeing with a real host). Falls
     * back to [WidgetSizeClass.STANDARD] only when the host has not reported real dimensions yet
     * — a fresh placement mid-configuration, or this activity launched directly for testing with
     * no real `AppWidgetHost` behind [appWidgetId] at all, the same case
     * [onEventBound]'s `runCatching` already has to tolerate.
     */
    private fun currentWidgetSizeClass(): WidgetSizeClass {
        val options = AppWidgetManager.getInstance(this)?.getAppWidgetOptions(appWidgetId)
        val widthDp = options?.getInt(AppWidgetManager.OPTION_APPWIDGET_MIN_WIDTH, 0) ?: 0
        val heightDp = options?.getInt(AppWidgetManager.OPTION_APPWIDGET_MIN_HEIGHT, 0) ?: 0
        if (widthDp <= 0 || heightDp <= 0) return WidgetSizeClass.STANDARD
        return classifyWidgetSize(widthDp.toFloat(), heightDp.toFloat())
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
    sizeClass: WidgetSizeClass,
    onEventSelected: (EventId) -> Unit,
    onChangeEvent: () -> Unit,
    onWidgetStyleChange: (WidgetStyle) -> Unit,
    onProgressStyleChange: (ProgressStyle) -> Unit,
    onShowTitleChange: (Boolean) -> Unit,
    onShowEmojiChange: (Boolean) -> Unit,
    onShowTargetDateChange: (Boolean) -> Unit,
    onShowPercentageChange: (Boolean) -> Unit,
    onAccentColorChange: (AccentColor?) -> Unit,
    onConfirm: () -> Unit,
    onNavigateBack: () -> Unit,
) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(if (uiState.isCustomizing) "Customize widget" else "Choose an event") },
                navigationIcon = {
                    if (uiState.isCustomizing) {
                        IconButton(onClick = onChangeEvent) {
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Choose a different event")
                        }
                    }
                },
                actions = {
                    if (uiState.isCustomizing) {
                        TextButton(onClick = onConfirm, enabled = !uiState.isSaving) { Text("Save") }
                    }
                },
            )
        },
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

            uiState.isCustomizing -> CustomizeStep(
                uiState = uiState,
                sizeClass = sizeClass,
                onWidgetStyleChange = onWidgetStyleChange,
                onProgressStyleChange = onProgressStyleChange,
                onShowTitleChange = onShowTitleChange,
                onShowEmojiChange = onShowEmojiChange,
                onShowTargetDateChange = onShowTargetDateChange,
                onShowPercentageChange = onShowPercentageChange,
                onAccentColorChange = onAccentColorChange,
                modifier = Modifier.padding(innerPadding),
            )

            else -> LazyColumn(
                modifier = Modifier.fillMaxSize().padding(innerPadding),
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                items(items = uiState.events, key = { it.id.value }) { event ->
                    EventPickerRow(
                        event = event,
                        isCurrent = event.id == uiState.currentEventId,
                        onClick = { onEventSelected(event.id) },
                    )
                }
            }
        }
    }
}

@Composable
private fun EventPickerRow(event: Event, isCurrent: Boolean, onClick: () -> Unit) {
    Card(onClick = onClick, modifier = Modifier.fillMaxWidth()) {
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

@Composable
private fun CustomizeStep(
    uiState: WidgetConfigurationUiState,
    sizeClass: WidgetSizeClass,
    onWidgetStyleChange: (WidgetStyle) -> Unit,
    onProgressStyleChange: (ProgressStyle) -> Unit,
    onShowTitleChange: (Boolean) -> Unit,
    onShowEmojiChange: (Boolean) -> Unit,
    onShowTargetDateChange: (Boolean) -> Unit,
    onShowPercentageChange: (Boolean) -> Unit,
    onAccentColorChange: (AccentColor?) -> Unit,
    modifier: Modifier = Modifier,
) {
    // Vertically scrollable (found on a Samsung Galaxy A55 / One UI: this step's controls run
    // taller than the available viewport on real hardware, so anything below "Show on widget"
    // was unreachable — KNOWN_ISSUES.md BUG-R016). fillMaxWidth(), not fillMaxSize(): the column's
    // height should come from its own content so verticalScroll has something real to scroll,
    // not from a height forced to exactly match the viewport. Orthogonal to the Style/Progress/
    // accent-color rows' own horizontalScroll() below — vertical drag scrolls this column,
    // horizontal drag on those rows scrolls them, and Compose's scroll modifiers only claim their
    // own axis, so the two never contend for the same gesture.
    Column(
        modifier = modifier
            .fillMaxWidth()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        uiState.previewModel?.let { model ->
            // Capped to PREVIEW_MAX_WIDTH rather than the old fillMaxWidth() square, which ran
            // ~340dp tall on a typical phone and pushed every control below it near or past the
            // fold — confirmed on-device (docs/RESPONSIVE_WIDGET_REVIEW.md) before settling on
            // this width. Sized by sizeClass, not always square, so the shape itself previews
            // which footprint is actually placed — a 2×1 widget should look like a short wide
            // bar here too, not a shrunken square.
            Column(modifier = Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
                WidgetPreviewCard(
                    model = model,
                    sizeClass = sizeClass,
                    modifier = Modifier.width(PREVIEW_MAX_WIDTH).padding(vertical = 8.dp),
                )
                Text(
                    text = "Preview shown at this widget's current size (${sizeClass.displayName()}). Resize it from the home screen to change size.",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                )
            }
        }

        Text("Style", style = MaterialTheme.typography.titleSmall)
        Row(
            modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            WidgetStyle.entries.forEach { style ->
                FilterChip(
                    selected = uiState.widgetStyle == style,
                    onClick = { onWidgetStyleChange(style) },
                    label = { Text(style.displayName()) },
                )
            }
        }

        Text("Progress", style = MaterialTheme.typography.titleSmall)
        Row(
            modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            ProgressStyle.entries.forEach { style ->
                FilterChip(
                    selected = uiState.progressStyle == style,
                    onClick = { onProgressStyleChange(style) },
                    label = { Text(style.displayName()) },
                )
            }
        }

        Text("Accent color", style = MaterialTheme.typography.titleSmall)
        AccentColorPicker(
            selected = uiState.accentColorOverride ?: AccentColor.Dynamic,
            onSelect = onAccentColorChange,
            modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
        )

        Text("Show on widget", style = MaterialTheme.typography.titleSmall)
        ToggleRow("Title", uiState.showTitle, onShowTitleChange)
        ToggleRow("Emoji", uiState.showEmoji, onShowEmojiChange)
        ToggleRow("Target date", uiState.showTargetDate, onShowTargetDateChange)
        ToggleRow("Percentage", uiState.showPercentage, onShowPercentageChange)
    }
}

@Composable
private fun ToggleRow(label: String, checked: Boolean, onCheckedChange: (Boolean) -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Text(label, style = MaterialTheme.typography.bodyLarge)
        Switch(checked = checked, onCheckedChange = onCheckedChange)
    }
}

private fun WidgetStyle.displayName(): String = when (this) {
    WidgetStyle.MINIMAL -> "Minimal"
    WidgetStyle.MATERIAL -> "Material"
    WidgetStyle.GLASS -> "Glass"
    WidgetStyle.OLED -> "OLED"
    WidgetStyle.PROGRESS -> "Progress"
    WidgetStyle.ROUNDED -> "Rounded"
    WidgetStyle.MODERN -> "Modern"
}

private fun ProgressStyle.displayName(): String = when (this) {
    ProgressStyle.NONE -> "None"
    ProgressStyle.LINEAR -> "Bar"
    ProgressStyle.CIRCULAR -> "Ring"
}

private fun WidgetSizeClass.displayName(): String = when (this) {
    WidgetSizeClass.COMPACT -> "2×1"
    WidgetSizeClass.STANDARD -> "2×2"
    WidgetSizeClass.WIDE -> "4×2"
}

/**
 * Down from the old unbounded `fillMaxWidth()` square (~340dp tall on a typical phone) to a fixed
 * width every size class's preview scales its own aspect ratio against — about a 40% reduction in
 * the [WidgetSizeClass.STANDARD] case, more for [WidgetSizeClass.COMPACT] and
 * [WidgetSizeClass.WIDE] since both are shorter than they are wide. See
 * `docs/RESPONSIVE_WIDGET_REVIEW.md` for the on-device confirmation this still reads clearly at
 * this size.
 */
private val PREVIEW_MAX_WIDTH = 200.dp
