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
import androidx.glance.appwidget.provideContent
import com.countflow.core.domain.model.AppWidgetId
import com.countflow.widget.engine.model.WidgetRenderModel
import com.countflow.widget.engine.provider.WidgetRenderModelProvider
import com.countflow.widget.glance.di.WidgetEntryPoint
import dagger.hilt.EntryPoints

/**
 * CountFlow's countdown widget.
 *
 * One size only for this milestone — `sizeMode` is left at its default,
 * [androidx.glance.appwidget.SizeMode.Single], which is exactly right for the "one basic
 * widget, not about visuals" scope here. Multiple sizes and breakpoints are Milestone 5.
 *
 * Reaches its dependencies through [WidgetEntryPoint] rather than an `@Inject` constructor,
 * because [GlanceAppWidget] is instantiated by Glance's own runtime, not by Hilt
 * (KNOWN_ISSUES.md LIM-005) — this is the one call site that bridges the gap; every other class
 * in the widget layer stays normally injectable.
 *
 * `key(LocalSize.current)` resets composition on a resize rather than diffing across a geometry
 * change, following the same reasoning Google's canonical layouts use for this exact line
 * (ARCHITECTURE.md D-001) — kept even though this milestone has only one size, since it costs
 * nothing today and is one less thing to add when Milestone 5 introduces more.
 */
class CountdownGlanceWidget : GlanceAppWidget() {

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
