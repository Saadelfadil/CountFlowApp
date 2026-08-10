package com.countflow.core.notifications

import android.Manifest
import android.annotation.SuppressLint
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import androidx.core.content.getSystemService
import com.countflow.core.domain.countdown.CountdownEngine
import com.countflow.core.domain.countdown.CountdownLabel
import com.countflow.core.domain.model.Event
import com.countflow.core.domain.model.Reminder
import dagger.hilt.android.qualifiers.ApplicationContext
import java.time.Clock
import javax.inject.Inject
import javax.inject.Singleton

/**
 * [NotificationSender] backed by the real platform `NotificationManager`.
 *
 * The notification body reuses the *decision* [CountdownEngine] already owns — calling
 * [CountdownEngine.countdownAt] for the real label at the moment of delivery, not a second,
 * hand-rolled day-count calculation — while keeping its own short, notification-specific text for
 * that label (D-068), since pulling in `:core:designsystem`'s `CountdownLabelFormatter` would
 * mean pulling in Compose Material3 for one string lookup in a module with no UI of its own.
 */
@Singleton
class AndroidNotificationSender @Inject constructor(
    @ApplicationContext private val context: Context,
    private val countdownEngine: CountdownEngine,
    private val clock: Clock,
) : NotificationSender {

    // Manifest.permission.POST_NOTIFICATIONS' string *value* has always been
    // "android.permission.POST_NOTIFICATIONS" — only the symbolic constant was added in API 33.
    // Referencing the real constant instead of a hand-typed string literal is the correct choice
    // at any minSdk; InlinedApi exists for constants whose meaning genuinely changes across
    // versions, which a permission string is not.
    @SuppressLint("InlinedApi")
    override suspend fun send(reminder: Reminder, event: Event) {
        // `checkSelfPermission` for a permission the current API level doesn't runtime-check
        // (POST_NOTIFICATIONS below API 33) returns GRANTED unconditionally, so this one call is
        // correct on every supported version without an SDK_INT branch. Matches the brief's own
        // rule: a denied/revoked permission must not crash, and the reminder itself was already
        // saved (EditEventViewModel persists it regardless of permission state), so it is simply
        // not delivered this cycle rather than treated as an error — this is delivery, which
        // happened logically at its scheduled time, not something worth retrying just because no
        // one was there to see it.
        val granted = ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) ==
            PackageManager.PERMISSION_GRANTED
        if (!granted) return

        ensureChannel()

        val label = countdownEngine.countdownAt(event, clock.instant(), clock.zone).label
        val title = event.emoji?.let { "$it ${event.title}" } ?: event.title

        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(title)
            .setContentText(bodyFor(label))
            .setAutoCancel(true)
            .setContentIntent(tapPendingIntent(event))
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .build()

        // notificationId is derived from the reminder's own id, not the event's — two reminder
        // types on the same event are two independent notifications, never one overwriting the
        // other. Stable across calls, so a defensive re-send for the same reminder (should never
        // happen once resolved, but costs nothing to guard) replaces rather than stacks.
        NotificationManagerCompat.from(context).notify(reminder.id.value.hashCode(), notification)
    }

    private fun tapPendingIntent(event: Event): PendingIntent {
        val launchIntent = context.packageManager.getLaunchIntentForPackage(context.packageName)
            ?.apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
                putExtra(EXTRA_EVENT_ID, event.id.value)
            }
            ?: Intent()
        return PendingIntent.getActivity(
            context,
            event.id.value.hashCode(),
            launchIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
    }

    private fun ensureChannel() {
        val manager: NotificationManager = context.getSystemService() ?: return
        manager.createNotificationChannel(
            NotificationChannel(CHANNEL_ID, CHANNEL_NAME, NotificationManager.IMPORTANCE_DEFAULT).apply {
                description = CHANNEL_DESCRIPTION
            },
        )
    }

    private fun bodyFor(label: CountdownLabel): String = when (label) {
        CountdownLabel.Completed -> "Completed"
        CountdownLabel.Expired -> "Expired"
        CountdownLabel.StartingSoon -> "Starting soon"
        CountdownLabel.Today -> "Today"
        CountdownLabel.Tomorrow -> "Tomorrow"
        CountdownLabel.Yesterday -> "Yesterday"
        CountdownLabel.NextWeek -> "Next week"
        is CountdownLabel.InDays -> "${label.days} days to go"
        is CountdownLabel.DaysAgo -> "${label.days} days ago"
    }

    companion object {
        /** Read by `MainActivity` to deep-link a tapped notification to its event. */
        const val EXTRA_EVENT_ID = "com.countflow.notifications.EXTRA_EVENT_ID"

        private const val CHANNEL_ID = "event_reminders"
        private const val CHANNEL_NAME = "Event reminders"
        private const val CHANNEL_DESCRIPTION = "Countdown reminders for events you've enabled reminders for"
    }
}
