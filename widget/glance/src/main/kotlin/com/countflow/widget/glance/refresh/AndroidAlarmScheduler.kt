package com.countflow.widget.glance.refresh

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import androidx.core.content.getSystemService
import com.countflow.widget.engine.refresh.AlarmScheduler
import dagger.hilt.android.qualifiers.ApplicationContext
import java.time.Instant
import javax.inject.Inject
import javax.inject.Singleton

/**
 * [AlarmScheduler] backed by the real platform `AlarmManager`.
 *
 * `setAndAllowWhileIdle` — not `setExactAndAllowWhileIdle` — is deliberate and matches
 * ARCHITECTURE.md's own D-008 plan: it needs no `SCHEDULE_EXACT_ALARM`/`USE_EXACT_ALARM`
 * permission, survives Doze, and is inexact by at most a few minutes. That imprecision is
 * irrelevant here — the screen is off, nothing is watching a widget update to the second, and a
 * countdown label that's correct within a few minutes of its real transition is indistinguishable
 * from one that's exact.
 *
 * There is only ever one alarm: [scheduleExactRefresh] always targets the same explicit
 * [PendingIntent] (same request code, same target component), so a second call replaces the
 * first rather than adding a competing one — the coalescing [com.countflow.widget.engine.refresh.WidgetRefreshCoordinator]
 * already did the work of deciding *which* single instant matters; this class only ever asks the
 * platform for that one wakeup.
 */
@Singleton
class AndroidAlarmScheduler @Inject constructor(
    @ApplicationContext private val context: Context,
) : AlarmScheduler {

    private val alarmManager: AlarmManager? = context.getSystemService()

    override fun scheduleExactRefresh(at: Instant) {
        val manager = alarmManager ?: return
        manager.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, at.toEpochMilli(), refreshPendingIntent(context))
    }

    override fun cancelScheduledRefresh() {
        alarmManager?.cancel(refreshPendingIntent(context))
    }

    private companion object {
        const val REQUEST_CODE = 1001

        fun refreshPendingIntent(context: Context): PendingIntent {
            val intent = Intent(context, WidgetRefreshReceiver::class.java)
                .setAction(WidgetRefreshReceiver.ACTION_REFRESH)
            return PendingIntent.getBroadcast(
                context,
                REQUEST_CODE,
                intent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
            )
        }
    }
}
