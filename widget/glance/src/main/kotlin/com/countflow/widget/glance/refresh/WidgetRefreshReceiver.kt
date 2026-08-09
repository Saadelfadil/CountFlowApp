package com.countflow.widget.glance.refresh

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.countflow.core.common.di.ApplicationScope
import com.countflow.core.common.log.Logger
import com.countflow.widget.engine.refresh.WidgetRefreshCoordinator
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import java.util.TimeZone
import javax.inject.Inject

/**
 * Fires a full refresh cycle from every system event background refresh needs to react to.
 *
 * One receiver for four unrelated triggers, not four — a `BroadcastReceiver` can declare more
 * than one `<intent-filter>` action, and every one of them means exactly the same thing here:
 * "the schedule might now be wrong, recompute it." The one non-system action
 * ([ACTION_REFRESH]) is delivered through an *explicit* [android.app.PendingIntent] targeting
 * this class directly ([AndroidAlarmScheduler]), so it needs no manifest `<intent-filter>` of its
 * own — explicit-component intents bypass manifest action matching entirely.
 *
 * @see ACTION_REFRESH the alarm firing.
 *
 * The other three actions this receiver is manifest-registered for are the real recovery
 * scenarios named in `docs/WIDGET_REFRESH_ARCHITECTURE.md`: `ACTION_BOOT_COMPLETED` (every
 * `AlarmManager` alarm is cleared by a reboot — nothing survives without re-registering),
 * `ACTION_TIMEZONE_CHANGED` and `ACTION_TIME_CHANGED` (a stale schedule computed in the old
 * zone/time is silently wrong, not just late), and `ACTION_DATE_CHANGED` (a defensive re-check
 * for the ordinary midnight rollover, in case a device ever fires it without the alarm that was
 * supposed to be scheduled for that exact moment). `ACTION_MY_PACKAGE_REPLACED` and
 * `ACTION_LOCALE_CHANGED` are deliberately *not* handled here — an app update does not reliably
 * clear `AlarmManager` state the way a reboot does, and a locale change affects only how a label
 * renders (`CountdownLabelFormatter`, resolved at render time), never *when* one changes.
 *
 * `goAsync()` is what makes any of this legal: a `BroadcastReceiver.onReceive` must return
 * quickly, but reading Room and computing the next global refresh genuinely needs a coroutine.
 * [applicationScope] — not a scope this receiver owns — is what the coroutine actually runs on,
 * matching [GlanceWidgetRefreshScheduler]'s own reasoning for the same choice: this work must
 * outlive the receiver's own (very short) lifetime, which only [pendingResult] extends, briefly.
 */
@AndroidEntryPoint
class WidgetRefreshReceiver : BroadcastReceiver() {

    @Inject
    lateinit var coordinator: WidgetRefreshCoordinator

    @Inject
    @ApplicationScope
    lateinit var applicationScope: CoroutineScope

    @Inject
    lateinit var logger: Logger

    override fun onReceive(context: Context, intent: Intent) {
        val pendingResult = goAsync()
        val reason = intent.action ?: "unknown"

        if (intent.action == Intent.ACTION_TIMEZONE_CHANGED) {
            // Busts the JVM-level cache behind ZoneId.systemDefault() (D-064) — without this,
            // a process already running when the zone changes keeps reading the old zone even
            // though LiveDefaultZoneClock re-reads it on every call.
            TimeZone.setDefault(null)
        }

        applicationScope.launch {
            runCatching { coordinator.refreshAndReschedule() }
                .onSuccess { outcome ->
                    logger.debug(
                        TAG,
                        "reason=$reason widgetsRefreshed=${outcome.widgetsRefreshed} " +
                            "nextRefreshAt=${outcome.nextRefreshAt ?: "none"}",
                    )
                }
                .onFailure { error -> logger.error(TAG, "refresh cycle failed for reason=$reason", error) }
            pendingResult.finish()
        }
    }

    companion object {
        /** Delivered only via an explicit [android.app.PendingIntent] — the alarm itself firing. */
        const val ACTION_REFRESH = "com.countflow.widget.action.REFRESH"

        private const val TAG = "WidgetRefreshReceiver"
    }
}
