package com.countflow.widget.glance.refresh

import android.appwidget.AppWidgetManager
import android.content.BroadcastReceiver
import android.content.ComponentName
import android.content.Context
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import com.countflow.core.common.di.ApplicationScope
import com.countflow.core.common.log.Logger
import com.countflow.core.domain.model.AppWidgetId
import com.countflow.core.domain.repository.EventRepository
import com.countflow.widget.engine.lifecycle.WidgetLifecycleCoordinator
import com.countflow.widget.engine.refresh.WidgetRefreshCoordinator
import com.countflow.widget.engine.refresh.WidgetRefreshScheduler
import com.countflow.widget.glance.CountdownGlanceWidgetReceiver
import com.countflow.widget.glance.CountdownGlanceWidgetReceiverCompact
import com.countflow.widget.glance.CountdownGlanceWidgetReceiverWide
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.launch
import java.time.Duration
import javax.inject.Inject
import javax.inject.Singleton

/**
 * The production [WidgetRefreshScheduler] (`docs/WIDGET_REFRESH_ARCHITECTURE.md`): keeps widgets
 * current both while the app is alive *and* after it is not, by pairing a reactive Room
 * subscription with the alarm-based background cycle [WidgetRefreshCoordinator] runs.
 *
 * Three things happen once, from [start] (called from `CountFlowApplication.onCreate`):
 *
 * 1. **Prune orphaned bindings** against the launcher's live widget ids — unchanged from
 *    Milestone 4.
 * 2. **Subscribe to `observeEventsWithWidgets()`.** This Room-backed
 *    [kotlinx.coroutines.flow.Flow] already re-emits on any change to the joined `events`/
 *    `widget_bindings` tables — an event created, edited, deleted, or completed; a widget placed,
 *    removed, or reconfigured — which is exactly the brief's own "rescheduling triggers" list,
 *    satisfied without a single new receiver: every one of those is already a write to a table
 *    this query already watches. Each emission runs a full
 *    [WidgetRefreshCoordinator.refreshAndReschedule] cycle — not just a redraw, as Milestone 4's
 *    version did — so the alarm is recomputed the moment app-visible state changes, not only when
 *    the alarm itself fires.
 * 3. **Enqueue the periodic safety net** ([WidgetRefreshSafetyNetWorker]), `KEEP`-policy so a
 *    process restart never re-arms a second copy of it.
 *
 * Room is always the source of truth (D-002), so a widget under this scheme is never *wrong*
 * between refresh cycles, only stale for as long as the next scheduled wakeup — which is now
 * bounded by the alarm this class arms, not by how long the app process happens to stay alive.
 */
@Singleton
class GlanceWidgetRefreshScheduler @Inject constructor(
    @ApplicationContext private val context: Context,
    private val eventRepository: EventRepository,
    private val widgetLifecycleCoordinator: WidgetLifecycleCoordinator,
    private val refreshCoordinator: WidgetRefreshCoordinator,
    private val logger: Logger,
    @ApplicationScope private val applicationScope: CoroutineScope,
) : WidgetRefreshScheduler {

    private var started = false

    override fun start() {
        if (started) return
        started = true

        applicationScope.launch { pruneOrphanedBindings() }
        enqueueSafetyNet()

        eventRepository.observeEventsWithWidgets()
            .onEach {
                val outcome = refreshCoordinator.refreshAndReschedule()
                logger.debug(
                    TAG,
                    "bound-event data changed: widgetsRefreshed=${outcome.widgetsRefreshed} " +
                        "nextRefreshAt=${outcome.nextRefreshAt ?: "none"}",
                )
            }
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
     *
     * Queries all three widget-picker providers (D-079), not just one — `getAppWidgetIds` is
     * scoped to a single `ComponentName`, and a widget placed through the Compact or Wide picker
     * entry lives under a different provider component than one placed through Square. Missing
     * either of the two newer providers here would make every widget placed through them look
     * orphaned to this method the moment it ran, and silently delete their otherwise-valid
     * bindings — this method's own live-id set must be the union across every provider a
     * CountFlow widget can actually be placed under, not just the original one.
     */
    private suspend fun pruneOrphanedBindings() {
        val manager = AppWidgetManager.getInstance(context)
        val liveIds = COUNTDOWN_WIDGET_PROVIDER_CLASSES
            .flatMap { manager.getAppWidgetIds(ComponentName(context, it)).toList() }
            .map(::AppWidgetId)
            .toSet()

        widgetLifecycleCoordinator.pruneOrphans(liveIds)
    }

    /**
     * Arms [WidgetRefreshSafetyNetWorker] once per install. `KEEP`, not `REPLACE` — this method
     * runs on every app process start, and re-enqueueing with `REPLACE` would reset the six-hour
     * timer every time, which would turn "runs roughly every six hours" into "runs whenever the
     * app last happened to start plus six hours," defeating the point of a periodic safety net.
     */
    private fun enqueueSafetyNet() {
        val request = PeriodicWorkRequestBuilder<WidgetRefreshSafetyNetWorker>(
            SAFETY_NET_INTERVAL,
            SAFETY_NET_FLEX,
        ).build()

        WorkManager.getInstance(context).enqueueUniquePeriodicWork(
            WidgetRefreshSafetyNetWorker.UNIQUE_WORK_NAME,
            ExistingPeriodicWorkPolicy.KEEP,
            request,
        )
    }

    internal companion object {
        const val TAG = "GlanceWidgetRefreshScheduler"
        val SAFETY_NET_INTERVAL: Duration = Duration.ofHours(6)
        val SAFETY_NET_FLEX: Duration = Duration.ofHours(2)

        /**
         * Every `GlanceAppWidgetReceiver` a CountFlow widget can be placed under — one per
         * widget-picker entry (D-079). `internal`, not `private`: a unit test asserts this list
         * stays in sync with the three receivers actually declared in `AndroidManifest.xml`
         * without needing an `AppWidgetManager` (which Robolectric only partially supports) to do
         * it.
         */
        val COUNTDOWN_WIDGET_PROVIDER_CLASSES: List<Class<out BroadcastReceiver>> = listOf(
            CountdownGlanceWidgetReceiverCompact::class.java,
            CountdownGlanceWidgetReceiver::class.java,
            CountdownGlanceWidgetReceiverWide::class.java,
        )
    }
}
