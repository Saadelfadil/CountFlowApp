package com.countflow.widget.glance

import android.content.Context
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.glance.GlanceId
import androidx.glance.GlanceTheme
import androidx.glance.LocalSize
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.GlanceAppWidgetManager
import androidx.glance.appwidget.SizeMode
import androidx.glance.appwidget.provideContent
import com.countflow.core.domain.model.AppWidgetId
import com.countflow.widget.engine.model.WidgetRenderModel
import com.countflow.widget.engine.provider.WidgetRenderModelProvider
import com.countflow.widget.glance.di.WidgetEntryPoint
import dagger.hilt.EntryPoints

/**
 * CountFlow's countdown widget.
 *
 * `sizeMode` is [SizeMode.Exact] (Session 10, D-053) — the widget is now resizable across three
 * footprints (2×1, 2×2, 4×2; `res/xml/countdown_widget_info.xml`'s `resizeMode` and
 * `maxResizeWidth`/`maxResizeHeight`), and `Exact` is what makes [LocalSize] report the real,
 * continuous current size at every recomposition rather than one fixed value. It is deliberately
 * **not** [SizeMode.Responsive]: that mode snaps [LocalSize] to the nearest of a fixed
 * `Set<DpSize>` the app declares, which fits a small number of curated exact breakpoints; `Exact`
 * fits this app's actual need better, since the three supported footprints are Android's own
 * `dp = 70×cells − 30` cell values (BUG-R009's formula), not arbitrary constants this app would
 * otherwise have to hand-declare and keep in sync with the manifest. `CountdownWidgetContent`
 * classifies the reported [LocalSize] into a [WidgetSizeClass] itself
 * ([classifyWidgetSize]) rather than relying on Glance to snap to one of a fixed set.
 *
 * Reaches its dependencies through [WidgetEntryPoint] rather than an `@Inject` constructor,
 * because [GlanceAppWidget] is instantiated by Glance's own runtime, not by Hilt
 * (KNOWN_ISSUES.md LIM-005) — this is the one call site that bridges the gap; every other class
 * in the widget layer stays normally injectable.
 *
 * `key(LocalSize.current)` resets composition on a resize rather than diffing across a geometry
 * change, following the same reasoning Google's canonical layouts use for this exact line
 * (ARCHITECTURE.md D-001) — now load-bearing rather than merely future-proofing, since a real
 * drag-resize between footprints is the exact case this line exists for.
 */
class CountdownGlanceWidget : GlanceAppWidget() {

    override val sizeMode: SizeMode = SizeMode.Exact

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val provider = EntryPoints
            .get(context.applicationContext, WidgetEntryPoint::class.java)
            .widgetRenderModelProvider()

        val appWidgetId = AppWidgetId(GlanceAppWidgetManager(context).getAppWidgetId(id))

        // Loaded before provideContent, off the composition, so the first frame already has
        // content rather than a loading flash — the same reasoning Google's canonical layouts
        // use (ARCHITECTURE.md D-001).
        val initialModel = provider.get(appWidgetId)

        provideContent {
            key(LocalSize.current) {
                Content(provider = provider, appWidgetId = appWidgetId, initialModel = initialModel)
            }
        }
    }

    @Composable
    private fun Content(
        provider: WidgetRenderModelProvider,
        appWidgetId: AppWidgetId,
        initialModel: WidgetRenderModel?,
    ) {
        val model by provider.observe(appWidgetId).collectAsState(initial = initialModel)

        GlanceTheme {
            CountdownWidgetContent(model)
        }
    }
}
