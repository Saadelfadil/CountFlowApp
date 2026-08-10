package com.countflow.core.notifications

import android.content.Context
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.countflow.core.common.log.Logger
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject

/**
 * A low-frequency backstop that just runs the same reminder cycle the alarm would have, in case
 * one was ever lost to something other than an explicit Force Stop — an OEM battery-optimization
 * killer, for instance. Not a second primary mechanism.
 *
 * A missed *reminder* is a worse user-facing failure than a stale widget, so this exists even
 * though the brief did not name it explicitly — reusing "platform scheduling knowledge" from
 * `WidgetRefreshSafetyNetWorker` (Session 12), per the brief's own list of what should be shared,
 * not the class itself. `KEEP` on enqueue means this arms once per install, the same reasoning as
 * that worker's own KDoc.
 */
@HiltWorker
class ReminderSafetyNetWorker @AssistedInject constructor(
    @Assisted context: Context,
    @Assisted params: WorkerParameters,
    private val coordinator: ReminderNotificationCoordinator,
    private val logger: Logger,
) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        val outcome = coordinator.processDueAndReschedule()
        logger.debug(
            TAG,
            "safety-net reminder cycle remindersDelivered=${outcome.remindersDelivered} " +
                "nextReminderAt=${outcome.nextReminderAt ?: "none"}",
        )
        return Result.success()
    }

    companion object {
        const val UNIQUE_WORK_NAME = "reminder_safety_net"
        private const val TAG = "ReminderSafetyNet"
    }
}
