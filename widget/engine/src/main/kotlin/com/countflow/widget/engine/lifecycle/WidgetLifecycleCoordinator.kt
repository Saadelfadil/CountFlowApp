package com.countflow.widget.engine.lifecycle

import com.countflow.core.domain.model.AppWidgetId
import com.countflow.core.domain.repository.WidgetBindingRepository
import javax.inject.Inject
import javax.inject.Singleton

/**
 * What happens when widgets come and go.
 *
 * This is "widgets should only render" applied to the receiver's lifecycle callbacks, not just
 * its composition: `GlanceAppWidgetReceiver.onDeleted` in `:widget:glance` is Android glue that
 * converts an `IntArray` of ids and calls one method here. Putting the actual decision — which
 * bindings to remove — in a plain injectable class rather than in the receiver override means it
 * is tested with a fake repository and JUnit, not a `BroadcastReceiver` test harness.
 */
@Singleton
class WidgetLifecycleCoordinator @Inject constructor(
    private val widgetBindingRepository: WidgetBindingRepository,
) {

    /**
     * Removes bindings for widgets the user deleted from their home screen.
     *
     * Called from `onDeleted`. Deleting a binding never touches the event it pointed at — the
     * user removed a widget, not the countdown it was showing.
     */
    suspend fun onWidgetsRemoved(appWidgetIds: List<AppWidgetId>) {
        widgetBindingRepository.deleteBindings(appWidgetIds)
    }

    /**
     * Discards bindings for widgets that no longer exist on any home screen, judged against
     * [liveAppWidgetIds] — the ids the launcher currently reports.
     *
     * Covers what `onDeleted` cannot: a launcher crash that drops the callback, or a restore
     * that brings binding rows back for widgets that were never re-created on this device.
     * Meant to run once at application startup, not on every write.
     */
    suspend fun pruneOrphans(liveAppWidgetIds: Set<AppWidgetId>) {
        widgetBindingRepository.pruneOrphanedBindings(liveAppWidgetIds)
    }
}
