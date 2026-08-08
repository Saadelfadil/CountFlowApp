package com.countflow.widget.glance.refresh

import android.appwidget.AppWidgetManager
import android.content.ComponentName
import android.content.Context
import androidx.glance.appwidget.updateAll
import com.countflow.core.common.di.ApplicationScope
import com.countflow.core.domain.model.AppWidgetId
import com.countflow.core.domain.repository.EventRepository
import com.countflow.widget.engine.lifecycle.WidgetLifecycleCoordinator
import com.countflow.widget.engine.refresh.WidgetRefreshScheduler
import com.countflow.widget.glance.CountdownGlanceWidget
import com.countflow.widget.glance.CountdownGlanceWidgetReceiver
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.launch
import javax.inject.Inject
import javax.inject.Singleton

/**
 * The Milestone 4 [WidgetRefreshScheduler]: while the app process is alive, redraw every
 * countdown widget whenever any event with a widget bound to it changes. Also where the app
 * reconciles stored bindings against reality once, at startup.
 *
 * `observeEventsWithWidgets()` is a Room-backed [kotlinx.coroutines.flow.Flow] that already
 * re-emits on any change to the joined tables, and [EventRepository] does not need to know a
 * widget layer exists for this to work — the dependency runs one way, from here down.
 *
 * This is deliberately not the final refresh strategy. It says nothing about the final day's
 * ticking or about staying current once the process is killed; both are Milestone 8's
 * alarm-based coalesced scheduler (ARCHITECTURE.md D-008). What it buys now, cheaply, is the
 * one behaviour this milestone's success criteria actually asks for: editing an event updates
 * its widgets. Because Room is always the source of truth (D-002), a widget is never *wrong*
 * between redraws, only stale — and closing that staleness window is exactly what Milestone 8
 * exists to do.
 */
@Singleton
class GlanceWidgetRefreshScheduler @Inject constructor(
    @ApplicationContext private val context: Context,
    private val eventRepository: EventRepository,
    private val widgetLifecycleCoordinator: WidgetLifecycleCoordinator,
    @ApplicationScope private val applicationScope: CoroutineScope,
) : WidgetRefreshScheduler {

    private var started = false

    override fun start() {
        if (started) return
        started = true

        applicationScope.launch { pruneOrphanedBindings() }

        eventRepository.observeEventsWithWidgets()
            .onEach { CountdownGlanceWidget().updateAll(context) }
            .launchIn(applicationScope)
    }

    /**
     * Discards bindings for widgets that no longer exist on this device's home screen.
     *
     * Covers two real gaps: a launcher crash that drops `onDeleted`, and — the case that
     * matters for backup — binding rows a restore brought back for widgets the launcher never
     * re-created, since `appWidgetId` values are allocated per device and cannot survive a
     * restore meaningfully. See data_extraction_rules.xml for why this runs here instead of
     * being handled by excluding the table from backup.
     */
    private suspend fun pruneOrphanedBindings() {
        val manager = AppWidgetManager.getInstance(context)
        val provider = ComponentName(context, CountdownGlanceWidgetReceiver::class.java)
        val liveIds = manager.getAppWidgetIds(provider).map(::AppWidgetId).toSet()

        widgetLifecycleCoordinator.pruneOrphans(liveIds)
    }
}
