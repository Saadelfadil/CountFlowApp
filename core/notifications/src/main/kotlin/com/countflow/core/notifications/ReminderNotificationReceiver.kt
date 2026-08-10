package com.countflow.core.notifications

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.countflow.core.common.di.ApplicationScope
import com.countflow.core.common.log.Logger
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import java.util.TimeZone
import javax.inject.Inject

/**
 * Fires a full reminder cycle from every system event background delivery needs to react to.
 *
 * One receiver for five triggers (the alarm plus four system broadcasts), for the identical
 * reason `WidgetRefreshReceiver` (`:widget:glance`, Session 12) is one receiver for its own five:
 * every one of them means "the reminder schedule might now be wrong, recompute it," so one small
 * class with one `runCatching` block is both correct and the minimum surface. This is a
 * *different* receiver from that one, not a shared one — see [NotificationReminderScheduler]'s
 * KDoc and DECISIONS.md D-067 for why: reminder delivery and widget redraw are independent
 * outcomes, and a shared receiver would only invite the two concerns to start reaching into each
 * other.
 */
@AndroidEntryPoint
class ReminderNotificationReceiver : BroadcastReceiver() {

    @Inject
    lateinit var coordinator: ReminderNotificationCoordinator

    @Inject
    @ApplicationScope
    lateinit var applicationScope: CoroutineScope

    @Inject
    lateinit var logger: Logger

    override fun onReceive(context: Context, intent: Intent) {
        val pendingResult = goAsync()
        val reason = intent.action ?: "unknown"

        if (intent.action == Intent.ACTION_TIMEZONE_CHANGED) {
            // Busts the JVM-level cache behind ZoneId.systemDefault() — the same fix the widget
            // refresh system needed (D-064) applies identically here, since this coordinator also
            // reads `clock.zone` for every all-day reminder's calendar subtraction.
            TimeZone.setDefault(null)
        }

        applicationScope.launch {
            runCatching { coordinator.processDueAndReschedule() }
                .onSuccess { outcome ->
                    logger.debug(
                        TAG,
                        "reason=$reason remindersDelivered=${outcome.remindersDelivered} " +
                            "nextReminderAt=${outcome.nextReminderAt ?: "none"}",
                    )
                }
                .onFailure { error -> logger.error(TAG, "reminder cycle failed for reason=$reason", error) }
            pendingResult.finish()
        }
    }

    companion object {
        /** Delivered only via an explicit [android.app.PendingIntent] — the alarm itself firing. */
        const val ACTION_REMINDER_ALARM = "com.countflow.notifications.action.REMINDER_ALARM"

        private const val TAG = "ReminderNotificationReceiver"
    }
}
