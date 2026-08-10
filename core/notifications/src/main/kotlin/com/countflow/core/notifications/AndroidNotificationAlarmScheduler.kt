package com.countflow.core.notifications

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import androidx.core.content.getSystemService
import dagger.hilt.android.qualifiers.ApplicationContext
import java.time.Instant
import javax.inject.Inject
import javax.inject.Singleton

/**
 * [NotificationAlarmScheduler] backed by the real platform `AlarmManager`.
 *
 * `setAndAllowWhileIdle`, a fixed request code, and the reasoning behind both are identical to
 * `AndroidAlarmScheduler` (`:widget:glance`, Session 12, D-063) — a few minutes of slack is
 * irrelevant to a reminder the user set in whole-day increments, and this needs no restricted
 * exact-alarm permission. The request code (2001) is deliberately different from the widget
 * scheduler's (1001) — two independent alarms are expected to coexist, one per system, and using
 * the same code would make one silently replace the other.
 */
@Singleton
class AndroidNotificationAlarmScheduler @Inject constructor(
    @ApplicationContext private val context: Context,
) : NotificationAlarmScheduler {

    private val alarmManager: AlarmManager? = context.getSystemService()

    override fun scheduleNextReminder(at: Instant) {
        val manager = alarmManager ?: return
        manager.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, at.toEpochMilli(), reminderPendingIntent(context))
    }

    override fun cancelScheduledReminder() {
        alarmManager?.cancel(reminderPendingIntent(context))
    }

    private companion object {
        const val REQUEST_CODE = 2001

        fun reminderPendingIntent(context: Context): PendingIntent {
            val intent = Intent(context, ReminderNotificationReceiver::class.java)
                .setAction(ReminderNotificationReceiver.ACTION_REMINDER_ALARM)
            return PendingIntent.getBroadcast(
                context,
                REQUEST_CODE,
                intent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
            )
        }
    }
}
