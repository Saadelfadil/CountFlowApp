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
 * when widgets are removed — shared by every one of CountFlow's widget picker entries
 * ([CountdownGlanceWidgetReceiver], [CountdownGlanceWidgetReceiverCompact],
 * [CountdownGlanceWidgetReceiverWide]), so this logic exists exactly once regardless of how many
 * initial-footprint choices the Android/Samsung widget picker exposes.
 *
 * A separate `GlanceAppWidgetReceiver` subclass per picker entry is required by the platform —
 * each `<receiver>` in `AndroidManifest.xml` needs its own distinct component so
 * `AppWidgetManager`/the launcher can treat them as independently selectable picker entries with
 * their own default footprint and preview — but every one of them points at the exact same
 * [CountdownGlanceWidget] class: the six Style renderers, `WidgetRenderMapper`, and the responsive
 * Compact/Standard/Wide layouts are never duplicated, only this thin platform-glue class is.
 *
 * `@AndroidEntryPoint` here, not only on the concrete subclasses — Hilt supports a hierarchy of
 * `@AndroidEntryPoint`-annotated classes, and this is that pattern: the `@Inject` fields declared
 * here are what each subclass's own `@AndroidEntryPoint` annotation makes available at runtime.
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
abstract class BaseCountdownGlanceWidgetReceiver : GlanceAppWidgetReceiver() {

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

/**
 * The "CountFlow Square" widget picker entry — 2×2 default placement footprint
 * (`res/xml/countdown_widget_info_square.xml`). The original, single provider this app shipped
 * with before [CountdownGlanceWidgetReceiverCompact] and [CountdownGlanceWidgetReceiverWide] were
 * added; its component name is kept stable (never renamed) specifically so widgets already placed
 * under it are not orphaned by this change — Android ties a placed widget instance permanently to
 * its provider's exact component name.
 */
@AndroidEntryPoint
class CountdownGlanceWidgetReceiver : BaseCountdownGlanceWidgetReceiver()
