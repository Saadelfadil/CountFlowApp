package com.countflow.widget.glance

import android.content.Context
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.GlanceAppWidgetReceiver
import com.countflow.core.common.di.ApplicationScope
import com.countflow.core.domain.model.AppWidgetId
import com.countflow.widget.engine.lifecycle.WidgetLifecycleCoordinator
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * Delegates system events for [CountdownGlanceWidget] to the framework, and cleans up bindings
 * when widgets are removed.
 *
 * `@AndroidEntryPoint` with field injection, not a constructor — `BroadcastReceiver`s are
 * instantiated by the OS, so constructor injection is not available the way it is for a
 * ViewModel. `onDeleted` does nothing but convert Android's `IntArray` into the domain's
 * `AppWidgetId` and hand it to [WidgetLifecycleCoordinator]; the actual decision of what removal
 * means lives there; the receiver has no business logic of its own.
 *
 * The cleanup runs on the injected application scope rather than a scope created on the spot.
 * A throwaway `CoroutineScope` here has no supervision and can be cancelled the moment the
 * process is reclaimed after `onReceive` returns — precisely the defect ARCHITECTURE.md (D-008)
 * flagged in Google's sample and set out not to repeat.
 */
@AndroidEntryPoint
class CountdownGlanceWidgetReceiver : GlanceAppWidgetReceiver() {

    override val glanceAppWidget: GlanceAppWidget = CountdownGlanceWidget()

    @Inject
    lateinit var widgetLifecycleCoordinator: WidgetLifecycleCoordinator

    @Inject
    @ApplicationScope
    lateinit var applicationScope: CoroutineScope

    override fun onDeleted(context: Context, appWidgetIds: IntArray) {
        super.onDeleted(context, appWidgetIds)

        val ids = appWidgetIds.map(::AppWidgetId)
        applicationScope.launch {
            widgetLifecycleCoordinator.onWidgetsRemoved(ids)
        }
    }
}
