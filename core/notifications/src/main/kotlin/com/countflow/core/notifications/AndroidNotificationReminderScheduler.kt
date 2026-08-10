package com.countflow.core.notifications

import android.content.Context
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import com.countflow.core.common.di.ApplicationScope
import com.countflow.core.common.log.Logger
import com.countflow.core.domain.repository.ReminderRepository
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import java.time.Duration
import javax.inject.Inject
import javax.inject.Singleton

/**
 * The production [NotificationReminderScheduler]: pairs a reactive Room subscription with the
 * alarm-based cycle [ReminderNotificationCoordinator] runs — the same two-part shape
 * `GlanceWidgetRefreshScheduler` (`:widget:glance`, Session 12) uses for widgets, deliberately not
 * shared code (see DECISIONS.md D-067).
 *
 * Started once, from `CountFlowApplication.onCreate`:
 *
 * 1. Subscribes to [ReminderRepository.observeActiveReminders]. This Room-backed `Flow` already
 *    re-emits on any change to the joined `reminders`/`events` tables — a reminder toggled, an
 *    event created, edited, completed, archived, restored, or deleted — which is exactly the
 *    brief's own "rescheduling triggers" list, satisfied with zero new receivers: every one of
 *    those is already a write to a table this query already watches. Each emission runs a full
 *    [ReminderNotificationCoordinator.processDueAndReschedule] cycle, ignoring the emitted value
 *    itself — the coordinator always re-reads fresh state, so every trigger means exactly the
 *    same thing: "something might have changed, recompute."
 * 2. Enqueues [ReminderSafetyNetWorker], `KEEP`-policy so a process restart never re-arms a
 *    second copy of it.
 */
@Singleton
class AndroidNotificationReminderScheduler @Inject constructor(
    @ApplicationContext private val context: Context,
    private val reminderRepository: ReminderRepository,
    private val coordinator: ReminderNotificationCoordinator,
    private val logger: Logger,
    @ApplicationScope private val applicationScope: CoroutineScope,
) : NotificationReminderScheduler {

    private var started = false

    override fun start() {
        if (started) return
        started = true

        enqueueSafetyNet()

        reminderRepository.observeActiveReminders()
            .onEach {
                val outcome = coordinator.processDueAndReschedule()
                logger.debug(
                    TAG,
                    "active reminder data changed: remindersDelivered=${outcome.remindersDelivered} " +
                        "nextReminderAt=${outcome.nextReminderAt ?: "none"}",
                )
            }
            .launchIn(applicationScope)
    }

    private fun enqueueSafetyNet() {
        val request = PeriodicWorkRequestBuilder<ReminderSafetyNetWorker>(
            SAFETY_NET_INTERVAL,
            SAFETY_NET_FLEX,
        ).build()

        WorkManager.getInstance(context).enqueueUniquePeriodicWork(
            ReminderSafetyNetWorker.UNIQUE_WORK_NAME,
            ExistingPeriodicWorkPolicy.KEEP,
            request,
        )
    }

    private companion object {
        const val TAG = "NotificationReminderScheduler"
        val SAFETY_NET_INTERVAL: Duration = Duration.ofHours(6)
        val SAFETY_NET_FLEX: Duration = Duration.ofHours(2)
    }
}
