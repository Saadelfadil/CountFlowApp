package com.countflow.widget.glance.refresh

import android.content.Context
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.countflow.core.common.log.Logger
import com.countflow.widget.engine.refresh.WidgetRefreshCoordinator
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject

/**
 * The self-healing safety net ARCHITECTURE.md's D-008 always planned alongside the alarm: a
 * low-frequency, flexed [androidx.work.PeriodicWorkRequest] that just runs the same refresh cycle
 * the alarm would have, in case an alarm was ever lost to something other than an explicit Force
 * Stop (an OEM battery-optimization killer, for instance) — not a second primary mechanism, and
 * not a workaround for Force Stop, which cancels this worker exactly as it cancels everything
 * else the app scheduled (KNOWN_ISSUES.md BUG-011, D-052).
 *
 * Six hours, flexed, is deliberately infrequent: [WidgetRefreshCoordinator] already reschedules
 * the *real* alarm every time it runs, from anywhere, so this worker's only job is catching the
 * rare case where nothing else did that recently. `KEEP` on enqueue (`GlanceWidgetRefreshScheduler`)
 * means this is set up once per app install, not re-armed on every process start.
 */
@HiltWorker
class WidgetRefreshSafetyNetWorker @AssistedInject constructor(
    @Assisted context: Context,
    @Assisted params: WorkerParameters,
    private val coordinator: WidgetRefreshCoordinator,
    private val logger: Logger,
) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        val outcome = coordinator.refreshAndReschedule()
        logger.debug(
            TAG,
            "safety-net refresh widgetsRefreshed=${outcome.widgetsRefreshed} " +
                "nextRefreshAt=${outcome.nextRefreshAt ?: "none"}",
        )
        return Result.success()
    }

    companion object {
        const val UNIQUE_WORK_NAME = "widget_refresh_safety_net"
        private const val TAG = "WidgetRefreshSafetyNet"
    }
}
